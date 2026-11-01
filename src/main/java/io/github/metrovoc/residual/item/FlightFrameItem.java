package io.github.metrovoc.residual.item;

import io.github.metrovoc.residual.Awards;
import io.github.metrovoc.residual.energy.CellBlockEntity;
import io.github.metrovoc.residual.energy.PowerNetwork;
import io.github.metrovoc.residual.learn.Benchmark;
import io.github.metrovoc.residual.model.ModelInfo;
import io.github.metrovoc.residual.registry.ModAttachments;
import io.github.metrovoc.residual.registry.ModComponents;
import io.github.metrovoc.residual.registry.ModItems;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * A flight frame, worn on the back. With power in it and a pilot that passes Pilot, the model that holds it steady, its
 * wearer flies as freely as in creative play, for {@link #DRAIN} FE a tick. The pilot is a carried model, put in as
 * into a bundle: click the frame with a checkpoint, and click it empty-handed to take the pilot out again. It charges
 * from an Energy Cell it is used on.
 */
public class FlightFrameItem extends Item {
    public static final int CAPACITY = 400_000, DRAIN = 80;
    /** The players a frame is holding up, so it lets go only of those. */
    private static final Set<UUID> HELD = new HashSet<>();

    public FlightFrameItem(Properties properties) {
        super(properties);
    }

    /** The most power it holds. */
    protected int capacity() {
        return CAPACITY;
    }

    public static int charge(ItemStack frame) {
        return frame.getOrDefault(ModComponents.CHARGE.get(), 0);
    }

    @Nullable
    public static ModelInfo pilot(ItemStack frame) {
        return frame.get(ModComponents.MODEL.get());
    }

    /** Whether {@code frame} can hold its wearer up: charged, with a pilot it can carry that passes Pilot. */
    public static boolean holds(ItemStack frame) {
        ModelInfo p = pilot(frame);
        return frame.is(ModItems.FLIGHT_FRAME.get()) && charge(frame) > 0 && p != null && p.causal() && p.passes(Benchmark.PILOT)
                && p.fitsOnboard();
    }

    @Override
    public boolean overrideOtherStackedOnMe(ItemStack self, ItemStack other, Slot slot, ClickAction action, Player player, SlotAccess carried) {
        if (action != ClickAction.SECONDARY || !slot.allowModification(player)) {
            return false;
        }
        ModelInfo pilot = pilot(self);
        if (other.isEmpty() && pilot != null) {
            ItemStack out = new ItemStack(ModItems.CHECKPOINT.get());
            out.set(ModComponents.MODEL.get(), pilot);
            self.remove(ModComponents.MODEL.get());
            carried.set(out);
            player.playSound(SoundEvents.BUNDLE_REMOVE_ONE, 0.8f, 1f);
            return true;
        }
        if (pilot == null && other.is(ModItems.CHECKPOINT.get()) && CrystalItem.model(other) instanceof ModelInfo model) {
            self.set(ModComponents.MODEL.get(), model);
            other.shrink(1);
            player.playSound(SoundEvents.BUNDLE_INSERT, 0.8f, 1f);
            return true;
        }
        return false;
    }

    /** A frame crafted into something else gives back the pilot it carried, on a checkpoint. */
    @Override
    @Nullable
    public ItemStackTemplate getCraftingRemainder(ItemInstance frame) {
        ModelInfo pilot = frame.get(ModComponents.MODEL.get());
        return pilot == null ? null : new ItemStackTemplate(ModItems.CHECKPOINT.get(), DataComponentPatch.builder().set(ModComponents.MODEL.get(), pilot).build());
    }

    /** Used on an Energy Cell, it takes as much of the charge of the cells on that network as it has room for. */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel().getBlockEntity(context.getClickedPos()) instanceof CellBlockEntity)) {
            return InteractionResult.PASS;
        }
        if (context.getLevel() instanceof ServerLevel level) {
            ItemStack frame = context.getItemInHand();
            int moved = PowerNetwork.at(level, context.getClickedPos()).take(capacity() - charge(frame));
            if (moved > 0) {
                frame.set(ModComponents.CHARGE.get(), charge(frame) + moved);
                level.playSound(null, context.getClickedPos(), SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 0.5f, 1.6f);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean isBarVisible(ItemStack frame) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack frame) {
        return Math.round(13f * charge(frame) / capacity());
    }

    @Override
    public int getBarColor(ItemStack frame) {
        return 0x3CD6C6;
    }

    @Override
    public void appendHoverText(ItemStack frame, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        out.accept(Component.translatable("residual.frame.charge", String.format("%,d", charge(frame)), String.format("%,d", CAPACITY))
                .withStyle(ChatFormatting.GRAY));
        ModelInfo pilot = pilot(frame);
        if (pilot == null) {
            out.accept(Component.translatable("residual.frame.no_pilot").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            boolean ok = pilot.causal() && pilot.passes(Benchmark.PILOT) && pilot.fitsOnboard();
            out.accept(Component.translatable(ok ? "residual.frame.pilot" : "residual.frame.pilot_not", pilot.tag())
                    .withStyle(ok ? ChatFormatting.AQUA : ChatFormatting.RED));
        }
    }

    /** Lets a player fly while the frame on their back holds them, spends its charge while they do, and lets go after. */
    public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack frame = player.getItemBySlot(EquipmentSlot.CHEST);
        Abilities abilities = player.getAbilities();
        boolean thrust = !player.isSpectator() && player.isAlive() && abilities.flying
                && (holds(frame) || frame.getItem() instanceof ExosuitItem && ExosuitItem.running(frame).contains(SuitAbility.FLIGHT));
        if (player.getData(ModAttachments.FRAME_FLYING) != thrust) {
            player.setData(ModAttachments.FRAME_FLYING, thrust);
        }
        if (player.isCreative() || player.isSpectator()) {
            return;
        }
        if (!holds(frame)) {
            if (HELD.remove(player.getUUID())) {
                abilities.mayfly = false;
                abilities.flying = false;
                player.onUpdateAbilities();
            }
            return;
        }
        if (HELD.add(player.getUUID()) || !abilities.mayfly) {
            abilities.mayfly = true;
            player.onUpdateAbilities();
        }
        if (!abilities.flying) {
            return;
        }
        frame.set(ModComponents.CHARGE.get(), Math.max(0, charge(frame) - DRAIN));
        Awards.grant(player, "flight/frame");
    }
}
