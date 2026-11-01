package io.github.metrovoc.residual.agent;

import io.github.metrovoc.residual.item.LinkItem;
import io.github.metrovoc.residual.item.UpgradeItem;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/**
 * What upgrades do for a body, in numbers. An operation takes 8 ticks and moves 8 items, 250 mB or 1,000 FE, and reaches 8
 * blocks; each upgrade halves the ticks, doubles the items and fluid, quadruples the energy, or reaches 8 blocks further.
 * The most of each kind are where the bar stands: an operation a tick of 64 items (1,280 a second), 4,000 mB (80,000 mB a
 * second) and 256,000 FE, 48 blocks of reach, and 48 carrying slots.
 */
public final class Upgrades {
    /** Slots for upgrades in a body: one kind to a slot, and a Relay Card. */
    public static final int SLOTS = 8;

    /** The kinds of upgrade, with how many of each a body can use. */
    public enum Kind {
        SPEED(3), STACK(3), RANGE(5), FLUID(4), ENERGY(4), CARRY(6), LIFT(1);

        public final int most;

        Kind(int most) {
            this.most = most;
        }
    }

    /** Ticks an operation takes, with no upgrade: as long as a turtle's action. */
    public static final int BASE_INTERVAL = 8;
    public static final int BASE_ITEMS = 8;
    public static final int BASE_FLUID = 250;
    public static final int BASE_ENERGY = 1_000;
    public static final int BASE_REACH = 8;
    /** Forge Energy a body holds for each energy upgrade. */
    public static final int BATTERY = 800_000;
    /** Millibuckets a body's tank holds, and more for each fluid upgrade. */
    public static final int TANK = 16_000;
    /** What the relay network takes for carrying one operation. */
    public static final int RELAY_COST = 100;
    /** What a jump to a place takes, and a tick in the air. */
    public static final int TELEPORT_COST = 10_000, FLIGHT_COST = 10;

    private Upgrades() {
    }

    public static int count(Container upgrades, Kind kind) {
        int n = 0;
        for (int i = 0; i < upgrades.getContainerSize(); i++) {
            ItemStack s = upgrades.getItem(i);
            if (s.getItem() instanceof UpgradeItem u && u.kind == kind) {
                n += s.getCount();
            }
        }
        return Math.min(n, kind.most);
    }

    public static int interval(Container u) {
        return Math.max(1, BASE_INTERVAL >> count(u, Kind.SPEED));
    }

    public static int items(Container u) {
        return BASE_ITEMS << count(u, Kind.STACK);
    }

    public static int fluid(Container u) {
        return BASE_FLUID << count(u, Kind.FLUID);
    }

    public static int energy(Container u) {
        return BASE_ENERGY << 2 * count(u, Kind.ENERGY);
    }

    public static int reach(Container u) {
        return BASE_REACH + 8 * count(u, Kind.RANGE);
    }

    public static int battery(Container u) {
        return BATTERY * count(u, Kind.ENERGY);
    }

    public static int tank(Container u) {
        return TANK * (1 + count(u, Kind.FLUID));
    }

    /** The Relay Card among its upgrades, or nothing. */
    public static ItemStack relay(Container u) {
        for (int i = 0; i < u.getContainerSize(); i++) {
            if (u.getItem(i).getItem() instanceof LinkItem) {
                return u.getItem(i);
            }
        }
        return ItemStack.EMPTY;
    }

    /** What goes in a body's upgrade slots. */
    public static boolean accepts(ItemStack stack) {
        return stack.getItem() instanceof UpgradeItem || stack.getItem() instanceof LinkItem;
    }

    /** How many of an item a slot takes. */
    public static int limit(ItemStack stack) {
        return stack.getItem() instanceof UpgradeItem u ? u.kind.most : 1;
    }
}
