package io.github.metrovoc.residual.menu;

import io.github.metrovoc.residual.block.CoreBlock;
import io.github.metrovoc.residual.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/** The Model Core's screen has no slots; the menu only ties the screen to its block. */
public class CoreMenu extends AbstractContainerMenu {
    public final BlockPos pos;

    public CoreMenu(int id, BlockPos pos) {
        super(ModMenus.CORE.get(), id);
        this.pos = pos;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockState(pos).getBlock() instanceof CoreBlock && player.isWithinBlockInteractionRange(pos, 4.0);
    }
}
