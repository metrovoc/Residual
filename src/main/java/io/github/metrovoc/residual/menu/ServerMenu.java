package io.github.metrovoc.residual.menu;

import io.github.metrovoc.residual.block.ServerBlockEntity;
import io.github.metrovoc.residual.item.CrystalItem;
import io.github.metrovoc.residual.net.ServerSync;
import io.github.metrovoc.residual.registry.ModItems;
import io.github.metrovoc.residual.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * A Model Server's screen: the model it serves on the window, its KV Caches, its packer and its draft in a panel beside it, the player's
 * inventory, and what it is doing. Buttons set the form of its weights, the size of a batch, and how many answers it writes to a question.
 */
public class ServerMenu extends AbstractContainerMenu {
    public static final int MODEL_X = 8, MODEL_Y = 36;
    public static final int SIDE_X = 172, SIDE_Y = 4, SIDE_W = 29, SIDE_H = 82, CARD_X = 179, CARD_Y = 10, CARD_STEP = 18;
    public static final int INVENTORY_X = 8, INVENTORY_Y = 100;
    private static final int EVERY = 10;

    public final BlockPos pos;
    private final Player player;
    private int ticks;
    /** On the client: what it is doing, as last sent. */
    @Nullable
    public ServerSync sync;

    public ServerMenu(int id, Inventory inventory, BlockPos pos) {
        this(id, inventory, pos, new SimpleContainer(ServerBlockEntity.SLOTS));
    }

    public ServerMenu(int id, Inventory inventory, BlockPos pos, Container items) {
        super(ModMenus.SERVER.get(), id);
        this.pos = pos;
        this.player = inventory.player;
        addSlot(new Slot(items, ServerBlockEntity.MODEL, MODEL_X, MODEL_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return CrystalItem.model(stack) != null;
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        addSlot(new Slot(items, ServerBlockEntity.CACHE, CARD_X, CARD_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(ModItems.KV_CACHE.get());
            }
        });
        addSlot(new Slot(items, ServerBlockEntity.PACKER, CARD_X, CARD_Y + CARD_STEP) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(ModItems.WEIGHT_PACKER.get());
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        addSlot(new Slot(items, ServerBlockEntity.DRAFT, CARD_X, CARD_Y + 2 * CARD_STEP) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return CrystalItem.model(stack) != null;
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        addSlot(new Slot(items, ServerBlockEntity.JUDGE, CARD_X, CARD_Y + 3 * CARD_STEP) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return CrystalItem.model(stack) != null;
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

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (player instanceof ServerPlayer server && ticks++ % EVERY == 0 && player.level().getBlockEntity(pos) instanceof ServerBlockEntity be) {
            PacketDistributor.sendToPlayer(server, be.sync(containerId));
        }
    }

    /** The two settings, by button: a form of its weights, as an index, or a batch size. */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(player.level().getBlockEntity(pos) instanceof ServerBlockEntity be)) {
            return false;
        }
        if (id < ServerBlockEntity.FORMS.length) {
            be.setBits(ServerBlockEntity.FORMS[id]);
        } else if (id < ServerBlockEntity.FORMS.length + ServerBlockEntity.BATCHES.length) {
            be.setBatch(ServerBlockEntity.BATCHES[id - ServerBlockEntity.FORMS.length]);
        } else {
            be.setEffort(ServerBlockEntity.EFFORTS[id - ServerBlockEntity.FORMS.length - ServerBlockEntity.BATCHES.length]);
        }
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockEntity(pos) instanceof ServerBlockEntity && player.distanceToSqr(pos.getCenter()) < 64;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack stack = slots.get(index).getItem();
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack before = stack.copy();
        if (index < ServerBlockEntity.SLOTS) {
            if (!moveItemStackTo(stack, ServerBlockEntity.SLOTS, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (stack.is(ModItems.KV_CACHE.get())) {
            moveItemStackTo(stack, ServerBlockEntity.CACHE, ServerBlockEntity.CACHE + 1, false);
        } else if (stack.is(ModItems.WEIGHT_PACKER.get())) {
            moveItemStackTo(stack, ServerBlockEntity.PACKER, ServerBlockEntity.PACKER + 1, false);
        } else if (CrystalItem.model(stack) != null) {
            if (!moveItemStackTo(stack, ServerBlockEntity.MODEL, ServerBlockEntity.MODEL + 1, false)) {
                if (!moveItemStackTo(stack, ServerBlockEntity.DRAFT, ServerBlockEntity.DRAFT + 1, false)) {
                    moveItemStackTo(stack, ServerBlockEntity.JUDGE, ServerBlockEntity.JUDGE + 1, false);
                }
            }
        }
        slots.get(index).setChanged();
        return stack.getCount() == before.getCount() ? ItemStack.EMPTY : before;
    }
}
