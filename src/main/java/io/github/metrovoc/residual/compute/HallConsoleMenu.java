package io.github.metrovoc.residual.compute;

import io.github.metrovoc.residual.net.HallSync;
import io.github.metrovoc.residual.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/** The Hall Console's screen: nothing to put in, only what the hall is doing, sent every second while it is open. */
public class HallConsoleMenu extends AbstractContainerMenu {
    public final BlockPos pos;
    private final Player player;
    private int ticks;
    /** On the client: the hall, as last sent. */
    @Nullable
    public HallSync reading;

    public HallConsoleMenu(int id, Inventory inventory, BlockPos pos) {
        super(ModMenus.HALL_CONSOLE.get(), id);
        this.pos = pos;
        this.player = inventory.player;
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (player instanceof ServerPlayer sp && ticks++ % 20 == 0 && sp.level().getBlockEntity(pos) instanceof HallConsoleBlockEntity be) {
            PacketDistributor.sendToPlayer(sp, be.reading(containerId));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockEntity(pos) instanceof HallConsoleBlockEntity && player.distanceToSqr(pos.getCenter()) < 64;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
