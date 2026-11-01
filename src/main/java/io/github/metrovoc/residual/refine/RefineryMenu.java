package io.github.metrovoc.residual.refine;

import io.github.metrovoc.residual.item.DriveItem;
import io.github.metrovoc.residual.item.DumpItem;
import io.github.metrovoc.residual.item.TokenCellItem;
import io.github.metrovoc.residual.item.TokenizerItem;
import io.github.metrovoc.residual.menu.AgentMenu;
import io.github.metrovoc.residual.registry.ModMenus;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
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

/** A Refinery machine's screen: its slots, if it has any, in a panel beside the window; its display; and the player's inventory. */
public class RefineryMenu extends AbstractContainerMenu {
    public static final int WINDOW_W = 256, SIDE_X = WINDOW_W - 4, SIDE_Y = 4;
    public static final int INVENTORY_X = 48, INVENTORY_Y = 122;
    /** The top left slot in the side panel. */
    public static final int SLOT_X = SIDE_X + 7, SLOT_Y = SIDE_Y + 6;
    private static final int EVERY = 10;

    public final BlockPos pos;
    public final Stage stage;
    public final ContainerData data;
    private final Player player;
    private final int own;
    private List<RefinerySync.Row> sent = List.of();
    private int ticks;
    /** On the client: its display's rows, as last sent. */
    public List<RefinerySync.Row> rows = List.of();

    public RefineryMenu(int id, Inventory inventory, BlockPos pos, Stage stage) {
        this(id, inventory, pos, stage, new SimpleContainer(Math.max(1, stage.slots)), new SimpleContainerData(RefineryBlockEntity.SETTINGS + stage.settings.size()));
    }

    public RefineryMenu(int id, Inventory inventory, BlockPos pos, Stage stage, Container items, ContainerData data) {
        super(ModMenus.REFINERY.get(), id);
        this.pos = pos;
        this.stage = stage;
        this.data = data;
        this.player = inventory.player;
        this.own = stage.slots;
        for (int i = 0; i < stage.slots; i++) {
            Predicate<ItemStack> allowed = allowed(stage, i);
            addSlot(new Slot(items, i, SLOT_X + i % 3 * 18, SLOT_Y + i / 3 * 18) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return allowed.test(stack);
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
                }
            });
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, 9 + row * 9 + col, INVENTORY_X + col * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, INVENTORY_X + col * 18, INVENTORY_Y + 58));
        }
        addDataSlots(data);
    }

    private static Predicate<ItemStack> allowed(Stage stage, int slot) {
        return switch (stage) {
            case INTAKE -> s -> s.getItem() instanceof DriveItem || s.getItem() instanceof DumpItem;
            case CLASSIFIER -> AgentMenu::isModel;
            case PROGRAMS -> s -> s.has(DataComponents.WRITTEN_BOOK_CONTENT) || s.has(DataComponents.WRITABLE_BOOK_CONTENT);
            case TANK -> slot == Stage.TOKENIZER ? s -> s.getItem() instanceof TokenizerItem : s -> s.getItem() instanceof TokenCellItem;
            default -> s -> false;
        };
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (player instanceof ServerPlayer && player.level().getBlockEntity(pos) instanceof RefineryBlockEntity be) {
            be.press(id);
        }
        return true;
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (player instanceof ServerPlayer server && ticks++ % EVERY == 0 && player.level().getBlockEntity(pos) instanceof RefineryBlockEntity be) {
            List<RefinerySync.Row> now = be.rows();
            if (!now.equals(sent)) {
                sent = now;
                PacketDistributor.sendToPlayer(server, new RefinerySync(containerId, now));
            }
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockEntity(pos) instanceof RefineryBlockEntity && player.distanceToSqr(pos.getCenter()) < 64;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack before = stack.copy();
        if (index < own) {
            if (!moveItemStackTo(stack, own, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (own == 0 || !moveItemStackTo(stack, 0, own, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return before;
    }
}
