package io.github.metrovoc.residual.menu;

import io.github.metrovoc.residual.block.WatchPostBlockEntity;
import io.github.metrovoc.residual.net.WatchSync;
import io.github.metrovoc.residual.registry.ModMenus;
import java.util.List;
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

/** The Watch Post's screen: its model slot, in a panel beside the window, the player's inventory, and what it watches. */
public class WatchPostMenu extends AbstractContainerMenu {
    public static final int SIDE_X = 172, SIDE_Y = 4, SIDE_W = 29, SIDE_H = 28, MODEL_X = 179, MODEL_Y = 10;
    public static final int INVENTORY_X = 8, INVENTORY_Y = 100;
    /** Ticks between two looks at whether what it shows has changed. */
    private static final int EVERY = 20;

    public final BlockPos pos;
    private final Player player;
    private List<WatchSync.Row> sent = List.of();
    private int ticks;
    /** On the client: what it watches, as last sent. */
    public List<WatchSync.Row> rows = List.of();

    public WatchPostMenu(int id, Inventory inventory, BlockPos pos) {
        this(id, inventory, pos, new SimpleContainer(1));
    }

    public WatchPostMenu(int id, Inventory inventory, BlockPos pos, Container items) {
        super(ModMenus.WATCH_POST.get(), id);
        this.pos = pos;
        this.player = inventory.player;
        addSlot(new Slot(items, WatchPostBlockEntity.MODEL, MODEL_X, MODEL_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return AgentMenu.isModel(stack);
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
        if (player instanceof ServerPlayer server && ticks++ % EVERY == 0
                && player.level().getBlockEntity(pos) instanceof WatchPostBlockEntity post) {
            List<WatchSync.Row> now = post.rows();
            if (!WatchSync.same(now, sent)) {
                sent = now;
                PacketDistributor.sendToPlayer(server, new WatchSync(containerId, now));
            }
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockEntity(pos) instanceof WatchPostBlockEntity && player.distanceToSqr(pos.getCenter()) < 64;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack stack = slots.get(index).getItem();
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (index == WatchPostBlockEntity.MODEL) {
            moveItemStackTo(stack, 1, slots.size(), true);
        } else if (AgentMenu.isModel(stack)) {
            moveItemStackTo(stack, WatchPostBlockEntity.MODEL, WatchPostBlockEntity.MODEL + 1, false);
        }
        slots.get(index).setChanged();
        return ItemStack.EMPTY;
    }
}
