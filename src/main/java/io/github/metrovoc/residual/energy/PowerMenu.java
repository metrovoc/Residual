package io.github.metrovoc.residual.energy;

import io.github.metrovoc.residual.registry.ModMenus;
import java.util.function.IntSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** The screen of a generator or a machine on power: its slots, its stored power, its work, and the player's inventory. */
public class PowerMenu extends AbstractContainerMenu {
    public enum Kind { GENERATOR, SMELTER }

    /** What its screen shows: power and capacity as 15-bit halves (data slots carry 16 bits), work and FE a tick. */
    public static final int ENERGY_LO = 0, ENERGY_HI = 1, CAPACITY_LO = 2, CAPACITY_HI = 3, WORK = 4, WORK_TOTAL = 5, RATE = 6,
            DATA = 7;
    public static final int FUEL_X = 80, IN_X = 56, OUT_X = 116, SLOT_Y = 35, INVENTORY_X = 8, INVENTORY_Y = 84;

    public final BlockPos pos;
    public final Kind kind;
    public final ContainerData data;
    private final int own;

    public PowerMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, buf.readBlockPos(), buf.readEnum(Kind.class));
    }

    private PowerMenu(int id, Inventory inventory, BlockPos pos, Kind kind) {
        this(id, inventory, pos, kind, new SimpleContainer(kind == Kind.GENERATOR ? GeneratorBlockEntity.SLOTS : SmelterBlockEntity.SLOTS),
                new SimpleContainerData(DATA));
    }

    public PowerMenu(int id, Inventory inventory, BlockPos pos, Kind kind, Container items, ContainerData data) {
        super(ModMenus.POWER.get(), id);
        this.pos = pos;
        this.kind = kind;
        this.data = data;
        if (kind == Kind.GENERATOR) {
            addSlot(new Slot(items, GeneratorBlockEntity.FUEL, FUEL_X, SLOT_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return items.canPlaceItem(GeneratorBlockEntity.FUEL, stack);
                }
            });
        } else {
            addSlot(new Slot(items, SmelterBlockEntity.IN, IN_X, SLOT_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return items.canPlaceItem(SmelterBlockEntity.IN, stack);
                }
            });
            addSlot(new Slot(items, SmelterBlockEntity.OUT, OUT_X, SLOT_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }
            });
        }
        own = slots.size();
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

    /** What a power device's screen shows, read from the device as it is. */
    static ContainerData data(PowerBlockEntity be, IntSupplier work, IntSupplier total, IntSupplier rate) {
        return new ContainerData() {
            @Override
            public int get(int i) {
                long e = be.energy().getAmountAsLong(), c = be.energy().getCapacityAsLong();
                return switch (i) {
                    case ENERGY_LO -> (int) (e & 0x7FFF);
                    case ENERGY_HI -> (int) (e >> 15);
                    case CAPACITY_LO -> (int) (c & 0x7FFF);
                    case CAPACITY_HI -> (int) (c >> 15);
                    case WORK -> work.getAsInt();
                    case WORK_TOTAL -> total.getAsInt();
                    case RATE -> rate.getAsInt();
                    default -> 0;
                };
            }

            @Override
            public void set(int i, int value) {
            }

            @Override
            public int getCount() {
                return DATA;
            }
        };
    }

    public long energy() {
        return (long) data.get(ENERGY_HI) << 15 | data.get(ENERGY_LO);
    }

    public long capacity() {
        return (long) data.get(CAPACITY_HI) << 15 | data.get(CAPACITY_LO);
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockEntity(pos) instanceof PowerBlockEntity && player.distanceToSqr(pos.getCenter()) < 64;
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
        } else if (!slots.getFirst().mayPlace(stack) || !moveItemStackTo(stack, 0, 1, false)) {
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
