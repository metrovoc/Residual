package io.github.metrovoc.residual.compute;

import io.github.metrovoc.residual.Awards;
import io.github.metrovoc.residual.net.HallSync;
import io.github.metrovoc.residual.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Watches the cluster it is part of: whether it has become a hall, and what its screen and the players near it are told. */
public class HallConsoleBlockEntity extends BlockEntity implements MenuProvider {
    public HallConsoleBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.HALL_CONSOLE.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, HallConsoleBlockEntity be) {
        if (Math.floorMod(level.getGameTime() + pos.asLong(), 20) == 0) {
            be.look((ServerLevel) level, state);
        }
    }

    private void look(ServerLevel level, BlockState state) {
        Cluster cluster = Cluster.at(level, worldPosition);
        boolean formed = cluster.hall();
        if (state.getValue(HallConsoleBlock.FORMED) != formed) {
            level.setBlock(worldPosition, state.setValue(HallConsoleBlock.FORMED, formed), Block.UPDATE_CLIENTS);
        }
        if (formed && cluster.busy() > 0) {
            for (ServerPlayer player : level.players()) {
                if (player.distanceToSqr(worldPosition.getCenter()) < 48 * 48) {
                    Awards.grant(player, "compute/hall");
                }
            }
        }
    }

    /** What its screen shows now. */
    public HallSync reading(int menu) {
        Cluster cluster = Cluster.at((ServerLevel) level, worldPosition);
        Accounts.Capacity at = cluster.capacity();
        long largest = Accounts.largestModel(at.memoryBytes());
        // a run at about twenty tokens a parameter, the share that training at scale has settled on
        Accounts.Booking run = Accounts.book(largest, 20.0 * largest, at);
        return new HallSync(menu, cluster.hall(), cluster.racks().size(), cluster.racksDark(), cluster.pods(), at.devices(), at.flopsPerSecond(),
                at.memoryBytes(), cluster.flopsNow(), cluster.busy(), cluster.feNow(), (float) cluster.pue(), largest, run.flops(), run.days(),
                run.energy(), cluster.rackLoads());
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.residual.hall_console");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new HallConsoleMenu(id, inventory, worldPosition);
    }
}
