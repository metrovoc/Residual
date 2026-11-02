package io.github.metrovoc.residual.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ObjLongConsumer;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class HaulTest {
    private static final Picker ALL = new Picker() {
        @Override
        public boolean exactly(ItemStack stack) {
            return true;
        }

        @Override
        public String thing() {
            return "anything";
        }
    };

    @BeforeAll
    static void bind() {
        for (var item : List.of(Items.STONE, Items.DIRT, Items.IRON_PICKAXE)) {
            item.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
        }
    }

    @Test
    void aDeliveryKeepsExistingToolsAndSuppliesEvenOfTheSameKind() {
        Store hands = new Store(), source = new Store(), destination = new Store();
        var stone = ItemResource.of(Items.STONE);
        var pickaxe = ItemResource.of(Items.IRON_PICKAXE);
        hands.give(pickaxe, 1, false);
        hands.give(stone, 12, false);
        source.give(stone, 8, false);
        Places.Ends cargo = Places.cargo(hands);

        assertEquals(8, Places.move(source, cargo, ALL, 768));
        assertEquals(8, Places.move(cargo, destination, ALL, 768));
        assertEquals(Map.of(pickaxe, 1L, stone, 12L), hands.items);
        assertEquals(Map.of(stone, 8L), destination.items);
        assertEquals(0, Places.move(cargo, destination, ALL, 768));
    }

    @Test
    void anEmptySourceCannotUnloadAnythingAlreadyCarried() {
        Store hands = new Store(), source = new Store(), destination = new Store();
        var tool = ItemResource.of(Items.IRON_PICKAXE);
        hands.give(tool, 1, false);
        Places.Ends cargo = Places.cargo(hands);

        assertEquals(0, Places.move(source, cargo, ALL, 768));
        assertEquals(0, Places.move(cargo, destination, ALL, 768));
        assertEquals(Map.of(tool, 1L), hands.items);
        assertEquals(Map.of(), destination.items);
    }

    @Test
    void aPartialDeliveryAndRefundLeaveTheRestAvailable() {
        Store hands = new Store(), source = new Store(), destination = new Store();
        var dirt = ItemResource.of(Items.DIRT);
        hands.give(dirt, 10, false);
        source.give(dirt, 8, false);
        Places.Ends cargo = Places.cargo(hands);
        assertEquals(8, Places.move(source, cargo, ALL, 768));
        destination.accept = 3; // The destination accepts less than its earlier capacity check.
        assertEquals(3, Places.move(cargo, destination, ALL, 768));
        assertEquals(15, hands.items.get(dirt));
        assertEquals(5, cargo.take(dirt, 100, true));
        destination.accept = Long.MAX_VALUE;
        assertEquals(5, Places.move(cargo, destination, ALL, 768));
        assertEquals(Map.of(dirt, 10L), hands.items);
        assertEquals(Map.of(dirt, 8L), destination.items);
    }

    private static final class Store implements Places.Ends {
        private final Map<ItemResource, Long> items = new LinkedHashMap<>();
        private long accept = Long.MAX_VALUE;

        @Override
        public long give(ItemResource kind, long n, boolean simulate) {
            if (simulate) {
                return n;
            }
            long stored = Math.min(n, accept);
            items.merge(kind, stored, Long::sum);
            return stored;
        }

        @Override
        public long take(ItemResource kind, long n, boolean simulate) {
            long held = items.getOrDefault(kind, 0L), taken = Math.min(n, held);
            if (!simulate && taken > 0) {
                if (taken == held) {
                    items.remove(kind);
                } else {
                    items.put(kind, held - taken);
                }
            }
            return taken;
        }

        @Override
        public void forEach(ObjLongConsumer<ItemResource> each) {
            items.forEach((kind, n) -> each.accept(kind, n));
        }
    }
}
