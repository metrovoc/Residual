package io.github.metrovoc.residual.item;

import io.github.metrovoc.residual.Awards;
import io.github.metrovoc.residual.registry.ModComponents;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Vein mining: a player whose exosuit runs it breaks a block while sneaking, and the blocks of its kind joined to it go too,
 * spreading out from it a few each tick, each as if the player had broken it, with their drops coming to the player's feet.
 * It takes {@link #LEAST} blocks at least and one more for every thousand of compute the suit has free, and stops before it
 * would break the tool or run the suit out of charge.
 */
public final class Vein {
    public static final int LEAST = 64;
    /** Power a block beyond the first costs: a coal's worth for {@link #LEAST} of them. */
    public static final int POWER = 1000;
    private static final int EACH_TICK = 8;
    private static final Map<UUID, Vein> UNDER_WAY = new HashMap<>();

    private final ItemStack tool;
    private final Kind kind;
    private final List<BlockPos> blocks;
    private int next;
    private int broken = 1;

    private Vein(ItemStack tool, Kind kind, List<BlockPos> blocks) {
        this.tool = tool;
        this.kind = kind;
        this.blocks = blocks;
    }

    /** Which blocks go together: the same block, or ores of one thing, in stone or in deepslate. */
    private record Kind(Block block, @Nullable TagKey<Block> ore) {
        static Kind of(BlockState state) {
            TagKey<Block> ore = state.typeHolder().tags()
                    .filter(tag -> tag.location().getNamespace().equals("c") && tag.location().getPath().startsWith("ores/"))
                    .findFirst().orElse(null);
            return new Kind(state.getBlock(), ore);
        }

        boolean includes(BlockState state) {
            return state.is(block) || ore != null && state.is(ore);
        }
    }

    /** How many blocks a vein takes for {@code suit}, the first among them. */
    public static int size(ItemStack suit) {
        return LEAST + (int) (ExosuitItem.free(suit) / 1000);
    }

    /**
     * The blocks that would go with the one at {@code origin}, nearest first, with up to {@code size} in all: those of its kind
     * joined to it through any face, edge or corner. None for a block a player could not take with what they hold, or
     * one with contents.
     */
    public static List<BlockPos> find(Level level, BlockPos origin, BlockState state, Player player, int size) {
        if (state.hasBlockEntity() || state.getDestroySpeed(level, origin) < 0 || !state.canHarvestBlock(level, origin, player)) {
            return List.of();
        }
        Kind kind = Kind.of(state);
        List<BlockPos> out = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>(Set.of(origin));
        ArrayDeque<BlockPos> edge = new ArrayDeque<>(List.of(origin));
        while (!edge.isEmpty() && out.size() + 1 < size) {
            BlockPos at = edge.poll();
            for (BlockPos n : BlockPos.betweenClosed(at.offset(-1, -1, -1), at.offset(1, 1, 1))) {
                if (out.size() + 1 < size && level.isLoaded(n) && !seen.contains(n) && kind.includes(level.getBlockState(n))) {
                    BlockPos p = n.immutable();
                    seen.add(p);
                    out.add(p);
                    edge.add(p);
                }
            }
        }
        return out;
    }

    /** A sneaking wearer broke a block: the blocks joined to it go after it, unless something else stopped the break. */
    public static void breaking(BreakBlockEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player) || UNDER_WAY.containsKey(player.getUUID()) || !player.isShiftKeyDown()) {
            return;
        }
        ItemStack suit = player.getItemBySlot(EquipmentSlot.CHEST);
        if (!(suit.getItem() instanceof ExosuitItem) || !ExosuitItem.running(suit).contains(SuitAbility.VEIN_MINING)) {
            return;
        }
        List<BlockPos> rest = find(player.level(), event.getPos(), event.getState(), player, size(suit));
        if (!rest.isEmpty()) {
            UNDER_WAY.put(player.getUUID(), new Vein(player.getMainHandItem(), Kind.of(event.getState()), rest));
        }
    }

    /** All a vein breaks comes to its miner's feet, so nothing lies out of reach in the tunnels it leaves. */
    public static void drops(BlockDropsEvent event) {
        if (event.getBreaker() instanceof ServerPlayer player && UNDER_WAY.containsKey(player.getUUID())) {
            for (ItemEntity drop : event.getDrops()) {
                drop.setPos(player.position());
                drop.setNoPickUpDelay();
            }
        }
    }

    public static void tick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && !UNDER_WAY.isEmpty()) {
            Vein vein = UNDER_WAY.get(player.getUUID());
            if (vein != null && !vein.spread(player)) {
                UNDER_WAY.remove(player.getUUID());
                if (vein.broken >= LEAST) {
                    Awards.grant(player, "exosuit/vein");
                }
            }
        }
    }

    /** Breaks this tick's blocks, and says whether there are more to come. */
    private boolean spread(ServerPlayer player) {
        ServerLevel level = player.level();
        ItemStack suit = player.getItemBySlot(EquipmentSlot.CHEST);
        for (int done = 0; done < EACH_TICK && next < blocks.size(); done++) {
            if (player.getMainHandItem() != tool || tool.isDamageableItem() && tool.getDamageValue() + 1 >= tool.getMaxDamage()
                    || !(suit.getItem() instanceof ExosuitItem) || FlightFrameItem.charge(suit) < POWER) {
                return false;
            }
            BlockPos p = blocks.get(next++);
            BlockState state = level.getBlockState(p);
            if (kind.includes(state) && player.gameMode.destroyBlock(p)) {
                broken++;
                suit.set(ModComponents.CHARGE.get(), FlightFrameItem.charge(suit) - POWER);
                // the game shows the player only the blocks their own client broke
                level.levelEvent(2001, p, Block.getId(state));
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 3, 0.25, 0.25, 0.25, 0.05);
            }
        }
        return next < blocks.size();
    }
}
