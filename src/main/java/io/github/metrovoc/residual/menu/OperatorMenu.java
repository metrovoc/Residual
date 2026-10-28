package io.github.metrovoc.residual.menu;

import io.github.metrovoc.residual.block.OperatorBlock;
import io.github.metrovoc.residual.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/** A machine's settings screen; slotless, the block entity syncs everything the screen shows. */
public class OperatorMenu extends AbstractContainerMenu {
    public final BlockPos pos;

    public OperatorMenu(int id, BlockPos pos) {
        super(ModMenus.OPERATOR.get(), id);
        this.pos = pos;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockState(pos).getBlock() instanceof OperatorBlock && player.isWithinBlockInteractionRange(pos, 4.0);
    }
}
