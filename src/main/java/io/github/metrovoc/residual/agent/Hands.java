package io.github.metrovoc.residual.agent;

import com.mojang.authlib.GameProfile;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.jetbrains.annotations.Nullable;

/**
 * A body acting as a player does, for what only a player can do to a block or an entity: right clicks and blows. It holds
 * what it uses in the hand of a stand-in, puts itself where the body is, and takes back what the stand-in is left with.
 */
final class Hands {
    private static final Map<ServerLevel, Stand> STANDS = new WeakHashMap<>();
    private static final UUID ID = UUID.nameUUIDFromBytes("residual:hands".getBytes(java.nio.charset.StandardCharsets.UTF_8));

    /** The stand-in, whose swing is always ready. */
    private static final class Stand extends FakePlayer {
        Stand(ServerLevel level) {
            super(level, new GameProfile(ID, "Agent"));
        }

        void ready() {
            attackStrengthTicker = 10_000;
        }
    }

    private Hands() {
    }

    private static Stand stand(Body a, Vec3 target, int slot, boolean sneak) {
        return stand(a, a.center(), target, slot, sneak);
    }

    private static Stand stand(Body a, Vec3 at, Vec3 target, int slot, boolean sneak) {
        ServerLevel level = a.world();
        Stand p = STANDS.computeIfAbsent(level, Stand::new);
        p.setPos(at.x, at.y - p.getEyeHeight(), at.z);
        Vec3 to = target.subtract(at);
        double flat = Math.sqrt(to.x * to.x + to.z * to.z);
        p.setYRot((float) (Math.atan2(to.z, to.x) * 180 / Math.PI) - 90);
        p.setXRot((float) -(Math.atan2(to.y, flat) * 180 / Math.PI));
        p.setShiftKeyDown(sneak);
        p.getInventory().clearContent();
        p.setItemInHand(InteractionHand.MAIN_HAND, slot < 0 ? ItemStack.EMPTY : a.items().getItem(slot));
        return p;
    }

    /** Gives back to its hands what the stand-in now holds, spilling what has no room. */
    private static void settle(Body a, Stand p, int slot) {
        ItemStack held = p.getItemInHand(InteractionHand.MAIN_HAND);
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        if (slot >= 0) {
            a.items().setItem(slot, held.isEmpty() ? ItemStack.EMPTY : held);
        } else if (!held.isEmpty()) {
            spill(a, a.keep(held));
        }
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack extra = p.getInventory().removeItemNoUpdate(i);
            if (!extra.isEmpty()) {
                spill(a, a.keep(extra));
            }
        }
        a.items().setChanged();
    }

    private static void spill(Body a, ItemStack rest) {
        if (!rest.isEmpty()) {
            Block.popResource(a.world(), a.place(), rest);
        }
    }

    /** A right click on a block's face with what is in {@code slot}, or an empty hand with -1; false if nothing came of it. */
    static boolean use(Body a, int slot, BlockPos pos, @Nullable Direction face, boolean sneak) {
        Direction side = face != null ? face : Direction.UP;
        Vec3 hit = Vec3.atCenterOf(pos).add(Vec3.atLowerCornerOf(side.getUnitVec3i()).scale(0.5));
        // a body with no legs looks from just off the face it acts on: from inside its own block it would see only that block
        Vec3 eye = a.legs() ? a.center() : hit.add(Vec3.atLowerCornerOf(side.getUnitVec3i()).scale(0.4));
        Stand p = stand(a, eye, hit, slot, sneak);
        ItemStack held = p.getItemInHand(InteractionHand.MAIN_HAND);
        BlockHitResult ray = new BlockHitResult(hit, side, pos, false);
        InteractionResult result = p.gameMode.useItemOn(p, a.world(), held, InteractionHand.MAIN_HAND, ray);
        if (!result.consumesAction() && !held.isEmpty()) {
            result = p.gameMode.useItem(p, a.world(), held, InteractionHand.MAIN_HAND);
        }
        settle(a, p, slot);
        p.setShiftKeyDown(false);
        return result.consumesAction();
    }

    /** A right click on an entity. */
    static boolean use(Body a, int slot, Entity target, boolean sneak) {
        Stand p = stand(a, target.getEyePosition(), slot, sneak);
        InteractionResult result = p.interactOn(target, InteractionHand.MAIN_HAND, target.position());
        settle(a, p, slot);
        p.setShiftKeyDown(false);
        return result.consumesAction();
    }

    /** A blow with what is in {@code slot}, its weapon's strength counted. */
    static void attack(Body a, int slot, Entity target) {
        Stand p = stand(a, target.getEyePosition(), slot, false);
        ItemStack held = p.getItemInHand(InteractionHand.MAIN_HAND);
        held.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            var instance = p.getAttributes().getInstance(attribute);
            if (instance != null) {
                instance.addOrUpdateTransientModifier(modifier);
            }
        });
        p.ready();
        p.attack(target);
        held.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            var instance = p.getAttributes().getInstance(attribute);
            if (instance != null) {
                instance.removeModifier(modifier);
            }
        });
        settle(a, p, slot);
    }

    /** Whether something can be struck. */
    static boolean alive(Entity e) {
        return e.isAlive() && e instanceof LivingEntity;
    }
}
