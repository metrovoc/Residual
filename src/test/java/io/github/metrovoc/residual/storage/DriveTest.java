package io.github.metrovoc.residual.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import java.util.List;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The storage bar's capacity and kinds rows, on a drive itself. */
class DriveTest {
    /** With no server, a holder has no default components until something gives it some. */
    @BeforeAll
    static void bind() {
        for (var item : List.of(Items.PAPER, Items.STONE, Items.DIRT)) {
            item.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
        }
        for (var fluid : List.of(Fluids.EMPTY, Fluids.WATER, Fluids.LAVA)) {
            fluid.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
        }
    }

    private static ItemResource kind(int i) {
        ItemStack stack = new ItemStack(Items.PAPER);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("kind " + i));
        return ItemResource.of(stack);
    }

    @Test
    void aKindCostsNothingButWhatThereIsOfIt() {
        // AE2's cell gives up 1/128 of itself for each of at most 63 kinds; a hundred thousand kinds cost nothing here
        Drive drive = new Drive(262_144);
        for (int i = 0; i < 100_000; i++) {
            assertEquals(1, drive.insert(kind(i), 1, false));
        }
        assertEquals(100_000, drive.kinds());
        assertEquals(100_000, drive.stored());
        assertEquals(162_144, drive.insert(ItemResource.of(Items.STONE), 1_000_000, false));
        assertEquals(262_144, drive.stored());
        assertEquals(0, drive.insert(ItemResource.of(Items.DIRT), 1, false));
    }

    @Test
    void aBucketTakesTheRoomOfAnItem() {
        FluidResource water = FluidResource.of(Fluids.WATER), lava = FluidResource.of(Fluids.LAVA);
        Drive drive = new Drive(10);
        assertEquals(4, drive.insert(ItemResource.of(Items.STONE), 4, false));
        assertEquals(1500, drive.insert(water, 1500, false));
        // a part of a bucket counts whole for the items that come after it, and fluids fill it to the millibucket
        assertEquals(6, drive.stored());
        assertEquals(4, drive.insert(ItemResource.of(Items.DIRT), 100, false));
        assertEquals(500, drive.insert(lava, 5000, false));
        assertEquals(10, drive.stored());
        assertEquals(0, drive.insert(lava, 1, false));
        assertEquals(1500, drive.extract(water, 5000, false));
        assertEquals(3, drive.kinds(), "stone, dirt and lava remain");
        assertEquals(500, drive.amount(lava));
        assertEquals(500, drive.extract(lava, 500, true));
        assertEquals(500, drive.amount(lava));
    }

    @Test
    void theContentsSurviveASave() {
        Drive drive = new Drive(1_000);
        drive.insert(ItemResource.of(Items.STONE), 12, false);
        drive.insert(FluidResource.of(Fluids.WATER), 2_500, false);
        DataResult<com.google.gson.JsonElement> saved = Drive.CODEC.encodeStart(JsonOps.INSTANCE, drive);
        Drive back = Drive.CODEC.parse(JsonOps.INSTANCE, saved.getOrThrow()).getOrThrow();
        assertEquals(1_000, back.capacity);
        assertEquals(12, back.count(ItemResource.of(Items.STONE)));
        assertEquals(2_500, back.amount(FluidResource.of(Fluids.WATER)));
        assertEquals(15, back.stored());
    }

    @Test
    void theDrivesAreAsLargeAsTheBarAsks() {
        // a 256k cell, a full ME Drive of ten of them, a 256k portable cell and a 256M cell, one kind each
        long cell256k = 2_080_768, meDrive = 20_807_680, portable = 1_032_192, cell256m = (1L << 28) * 8 * 127 / 128;
        long dense = 8_388_608, deep = 8_589_934_592L, carried = 1_048_576;
        assertTrue(dense >= cell256k);
        assertTrue(8 * dense >= meDrive);
        assertTrue(carried >= portable);
        assertTrue(deep >= cell256m);
        assertFalse(new Drive(deep).insert(ItemResource.of(Items.STONE), deep, false) < deep);
    }
}
