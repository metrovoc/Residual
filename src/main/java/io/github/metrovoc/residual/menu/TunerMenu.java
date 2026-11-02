package io.github.metrovoc.residual.menu;

import io.github.metrovoc.residual.block.TunerBlockEntity;
import io.github.metrovoc.residual.item.CrystalItem;
import io.github.metrovoc.residual.item.DatasetItem;
import io.github.metrovoc.residual.item.DriveItem;
import io.github.metrovoc.residual.item.LinkItem;
import io.github.metrovoc.residual.item.PreferenceLogItem;
import io.github.metrovoc.residual.net.TunerSync;
import io.github.metrovoc.residual.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/** A Tuner's screen: what goes in on the left, what comes out on the right, what it is doing between them, and the player's inventory. */
public class TunerMenu extends AbstractContainerMenu {
    public static final int BASE_X = 8, BASE_Y = 22, AID_Y = 42, AID2_Y = 60, OUT_X = 152, OUT_Y = 42;
    public static final int INVENTORY_X = 8, INVENTORY_Y = 104;
    private static final int EVERY = 10;

    public final BlockPos pos;
    private final Player player;
    private int ticks;
    /** On the client: what it is doing, as last sent. */
    @Nullable
    public TunerSync sync;

    public TunerMenu(int id, Inventory inventory, BlockPos pos) {
        this(id, inventory, pos, new SimpleContainer(TunerBlockEntity.SLOTS));
    }

    public TunerMenu(int id, Inventory inventory, BlockPos pos, Container items) {
        super(ModMenus.TUNER.get(), id);
        this.pos = pos;
        this.player = inventory.player;
        addSlot(new Slot(items, TunerBlockEntity.BASE, BASE_X, BASE_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return CrystalItem.model(stack) != null;
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        addSlot(new Slot(items, TunerBlockEntity.AID, BASE_X, AID_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return aid(stack);
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        addSlot(new Slot(items, TunerBlockEntity.AID2, BASE_X, AID2_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return aid(stack);
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        addSlot(new Slot(items, TunerBlockEntity.OUT, OUT_X, OUT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof CrystalItem && CrystalItem.model(stack) == null;
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, 9 + row * 9 + col, INVENTORY_X + col * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, INVENTORY_X + col * 18, INVENTORY_Y + 58));
        }
    }

    /** What a method may ask for beside the base. */
    private static boolean aid(ItemStack stack) {
        return stack.getItem() instanceof DatasetItem || stack.getItem() instanceof DriveItem || stack.getItem() instanceof PreferenceLogItem
                || stack.getItem() instanceof LinkItem || CrystalItem.model(stack) != null;
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (player instanceof ServerPlayer server && ticks++ % EVERY == 0 && player.level().getBlockEntity(pos) instanceof TunerBlockEntity be) {
            PacketDistributor.sendToPlayer(server, be.sync(containerId));
        }
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (player instanceof ServerPlayer server && player.level().getBlockEntity(pos) instanceof TunerBlockEntity be) {
            be.button(id, server);
        }
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockEntity(pos) instanceof TunerBlockEntity && player.distanceToSqr(pos.getCenter()) < 64;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack stack = slots.get(index).getItem();
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack before = stack.copy();
        if (index < TunerBlockEntity.SLOTS) {
            if (!moveItemStackTo(stack, TunerBlockEntity.SLOTS, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (stack.getItem() instanceof CrystalItem && CrystalItem.model(stack) == null) {
            moveItemStackTo(stack, TunerBlockEntity.OUT, TunerBlockEntity.OUT + 1, false);
        } else if (CrystalItem.model(stack) != null) {
            if (!moveItemStackTo(stack, TunerBlockEntity.BASE, TunerBlockEntity.BASE + 1, false)) {
                moveItemStackTo(stack, TunerBlockEntity.AID, TunerBlockEntity.AID2 + 1, false);
            }
        } else if (aid(stack)) {
            moveItemStackTo(stack, TunerBlockEntity.AID, TunerBlockEntity.AID2 + 1, false);
        }
        slots.get(index).setChanged();
        return stack.getCount() == before.getCount() ? ItemStack.EMPTY : before;
    }
}
