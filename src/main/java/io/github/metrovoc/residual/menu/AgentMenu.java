package io.github.metrovoc.residual.menu;

import io.github.metrovoc.residual.agent.Agent;
import io.github.metrovoc.residual.agent.Upgrades;
import io.github.metrovoc.residual.item.CrystalItem;
import io.github.metrovoc.residual.learn.Benchmark;
import io.github.metrovoc.residual.model.ModelInfo;
import io.github.metrovoc.residual.registry.ModItems;
import io.github.metrovoc.residual.registry.ModMenus;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** An agent's screen: what it carries, its model and filter slots, and the player's inventory. */
public class AgentMenu extends AbstractContainerMenu {
    public static final int CARRY_X = 34;
    public static final int CARRY_Y = 118;
    /** The model slots, in a panel beside the window, tucked under its edge: the filter above the model. */
    public static final int SIDE_X = 172, SIDE_Y = 4, SIDE_W = 47, SIDE_H = 126;
    public static final int SLOTS_X = 179, FILTER_Y = 10, MODEL_Y = 28;
    public static final int INVENTORY_X = 8;
    public static final int INVENTORY_Y = 166;
    /** Its model and its filter, among the screen's slots. */
    public static final int MODEL = Agent.CARRY, FILTER = Agent.CARRY + 1;
    /** Its upgrades, after the player's inventory, in a panel beside the window; then what it carries beyond its first slots. */
    public static final int UPGRADES = Agent.CARRY + 2 + 36;
    public static final int MORE_CARRIED = UPGRADES + Upgrades.SLOTS;
    public static final int UPGRADES_X = 179, UPGRADES_Y = 50, MORE_X = 230, MORE_Y = 10;

    /** Null on the client if the agent isn't loaded there. */
    @Nullable
    public final Agent agent;
    private final Container upgrades;
    private boolean hidden;
    public final int entityId;

    public AgentMenu(int id, Inventory inventory, int entityId) {
        this(id, inventory, inventory.player.level().getEntity(entityId) instanceof Agent a ? a : null, entityId);
    }

    public AgentMenu(int id, Inventory inventory, Agent agent) {
        this(id, inventory, agent, agent.getId());
    }

    private AgentMenu(int id, Inventory inventory, @Nullable Agent agent, int entityId) {
        super(ModMenus.AGENT.get(), id);
        this.agent = agent;
        this.entityId = entityId;
        Container c = agent != null ? agent.items : new SimpleContainer(Agent.SLOTS);
        Container u = agent != null ? agent.upgrades : new SimpleContainer(Upgrades.SLOTS);
        this.upgrades = u;
        for (int i = 0; i < Agent.CARRY; i++) {
            addSlot(new Shown(c, i, CARRY_X + i % 6 * 18, CARRY_Y + i / 6 * 18));
        }
        addSlot(modelSlot(c, Agent.MODEL, MODEL_Y));
        addSlot(modelSlot(c, Agent.FILTER, FILTER_Y));
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Shown(inventory, 9 + row * 9 + col, INVENTORY_X + col * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Shown(inventory, col, INVENTORY_X + col * 18, INVENTORY_Y + 58));
        }
        for (int i = 0; i < Upgrades.SLOTS; i++) {
            addSlot(upgradeSlot(u, i, UPGRADES_X + i % 2 * 18, UPGRADES_Y + i / 2 * 18, this));
        }
        for (int i = Agent.CARRY; i < Agent.MOST_CARRIED; i++) {
            int n = i - Agent.CARRY;
            int slot = i;
            addSlot(new Slot(c, i, MORE_X + n % 6 * 18, MORE_Y + n / 6 * 18) {
                @Override
                public boolean isActive() {
                    return !hidden && slot < carried();
                }
            });
        }
    }

    /** How many slots it carries in, by the upgrades shown. */
    public int carried() {
        return Agent.CARRY + 6 * Upgrades.count(upgrades, Upgrades.Kind.CARRY);
    }

    /** Clears the screen of slots, or brings them back, so the program can have the whole window. */
    public void hideSlots(boolean hide) {
        hidden = hide;
    }

    /** A slot that stands aside while the program has the whole window. */
    private class Shown extends Slot {
        Shown(Container c, int index, int x, int y) {
            super(c, index, x, y);
        }

        @Override
        public boolean isActive() {
            return !hidden;
        }
    }

    private Slot upgradeSlot(Container c, int index, int x, int y, AgentMenu menu) {
        return new Slot(c, index, x, y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return Upgrades.accepts(stack);
            }

            @Override
            public int getMaxStackSize(ItemStack stack) {
                return Upgrades.limit(stack);
            }

            @Override
            public boolean isActive() {
                return !menu.hidden;
            }
        };
    }

    private Slot modelSlot(Container c, int index, int y) {
        return new Shown(c, index, SLOTS_X, y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return isModel(stack);
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        };
    }

    /** A model that sorts goes to the filter slot first, any other to the model slot first. */
    private boolean intoModelSlot(ItemStack stack) {
        ModelInfo info = CrystalItem.model(stack);
        int first = info != null && info.passes(Benchmark.SORTING) ? FILTER : MODEL;
        int second = first == FILTER ? MODEL : FILTER;
        return moveItemStackTo(stack, first, first + 1, false) || moveItemStackTo(stack, second, second + 1, false);
    }

    public static boolean isModel(ItemStack stack) {
        return stack.getItem() instanceof CrystalItem && CrystalItem.model(stack) != null || stack.is(ModItems.RELAY_CARD.get());
    }

    @Override
    public boolean stillValid(Player player) {
        return agent != null && agent.isAlive() && player.distanceToSqr(agent) < 64;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack before = stack.copy();
        int own = Agent.CARRY + 2;
        if (index < own || index >= UPGRADES) {
            if (!moveItemStackTo(stack, own, UPGRADES, true)) {
                return ItemStack.EMPTY;
            }
        } else if (Upgrades.accepts(stack) && !(stack.is(ModItems.RELAY_CARD.get()) && !slots.get(MODEL).hasItem())) {
            if (!moveItemStackTo(stack, UPGRADES, MORE_CARRIED, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!(isModel(stack) && intoModelSlot(stack)) && !moveItemStackTo(stack, 0, Agent.CARRY, false)
                && !moveItemStackTo(stack, MORE_CARRIED, MORE_CARRIED + carried() - Agent.CARRY, false)) {
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
