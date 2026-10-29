package io.github.metrovoc.residual.menu;

import io.github.metrovoc.residual.agent.Agent;
import io.github.metrovoc.residual.item.CrystalItem;
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

/** An agent's screen: what it carries, the model slot, and the player's inventory. */
public class AgentMenu extends AbstractContainerMenu {
    public static final int CARRY_X = 8;
    public static final int CARRY_Y = 104;
    public static final int MODEL_X = 152;
    public static final int MODEL_Y = 104;
    public static final int INVENTORY_X = 8;
    public static final int INVENTORY_Y = 166;

    /** Null on the client if the agent isn't loaded there. */
    @Nullable
    public final Agent agent;
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
        Container c = agent != null ? agent.items : new SimpleContainer(Agent.CARRY + 1);
        for (int i = 0; i < Agent.CARRY; i++) {
            addSlot(new Slot(c, i, CARRY_X + i % 6 * 18, CARRY_Y + i / 6 * 18));
        }
        addSlot(new Slot(c, Agent.MODEL, MODEL_X, MODEL_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return isModel(stack);
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
        int own = Agent.CARRY + 1;
        if (index < own) {
            if (!moveItemStackTo(stack, own, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!(isModel(stack) && moveItemStackTo(stack, Agent.MODEL, own, false))
                && !moveItemStackTo(stack, 0, Agent.CARRY, false)) {
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
