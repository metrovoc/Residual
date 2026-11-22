package io.github.metrovoc.residual.interior;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Lays a hall out in blocks: the floor with its lanes, a band where each part stands, the rim, the consoles and light. */
public final class HallBuilder {
    /** Layers above the floor a hall may hold blocks in. */
    private static final int ABOVE = 4;
    private static final int LIGHT_EVERY = 5;

    private HallBuilder() {
    }

    /** Takes away everything {@link #build} put down for {@code plan}. */
    public static void clear(ServerLevel level, BlockPos origin, HallPlan plan) {
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int x = -1; x <= plan.width(); x++) {
            for (int z = -1; z <= plan.length(); z++) {
                for (int y = 0; y <= ABOVE; y++) {
                    at.setWithOffset(origin, x, y, z);
                    if (!level.getBlockState(at).isAir()) {
                        level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
    }

    public static void build(ServerLevel level, BlockPos origin, HallPlan plan) {
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        int width = plan.width(), length = plan.length();
        for (int x = -1; x <= width; x++) {
            for (int z = -1; z <= length; z++) {
                boolean rim = x < 0 || z < 0 || x == width || z == length;
                set(level, at.setWithOffset(origin, x, 0, z), rim ? Blocks.POLISHED_DEEPSLATE.defaultBlockState() : floor(plan, x, z));
                if (rim) {
                    set(level, at.setWithOffset(origin, x, 1, z), Blocks.TINTED_GLASS.defaultBlockState());
                    set(level, at.setWithOffset(origin, x, 2, z), Blocks.TINTED_GLASS.defaultBlockState());
                } else if (x % LIGHT_EVERY == 2 && z % LIGHT_EVERY == 2) {
                    set(level, at.setWithOffset(origin, x, ABOVE, z), Blocks.LIGHT.defaultBlockState());
                }
            }
        }
        for (HallPlan.Console c : plan.consoles()) {
            set(level, at.setWithOffset(origin, c.x(), 1, c.z()), HallSetup.CONSOLE.get().defaultBlockState().setValue(ConsoleBlock.ROLE, c.role()));
        }
    }

    private static BlockState floor(HallPlan plan, int x, int z) {
        if (x < HallPlan.WALK - 1) {
            return Blocks.DEEPSLATE_TILES.defaultBlockState();
        }
        if (x < HallPlan.WALK || x >= HallPlan.WALK + plan.lanes() * plan.pitch()) {
            return Blocks.POLISHED_DEEPSLATE.defaultBlockState();
        }
        for (HallPlan.Station s : plan.stations()) {
            if (z == s.z()) {
                Block band = switch (s.type()) {
                    case TOKENS -> Blocks.WHITE_CONCRETE;
                    case EMBED -> Blocks.LIGHT_BLUE_CONCRETE;
                    case POSITION -> s.on() ? Blocks.ORANGE_CONCRETE : Blocks.LIGHT_GRAY_CONCRETE;
                    case ATTENTION -> s.on() ? Blocks.PURPLE_CONCRETE : Blocks.LIGHT_GRAY_CONCRETE;
                    case FEED_FORWARD -> s.on() ? Blocks.LIME_CONCRETE : Blocks.LIGHT_GRAY_CONCRETE;
                    case READOUT -> Blocks.CYAN_CONCRETE;
                    default -> null;
                };
                if (band != null) {
                    return band.defaultBlockState();
                }
            }
        }
        int lane = (x - HallPlan.WALK) / plan.pitch();
        return (lane % 2 == 0 ? Blocks.BLACK_CONCRETE : Blocks.GRAY_CONCRETE).defaultBlockState();
    }

    private static void set(ServerLevel level, BlockPos at, BlockState state) {
        if (level.getBlockState(at) != state) {
            level.setBlock(at, state, Block.UPDATE_CLIENTS);
        }
    }
}
