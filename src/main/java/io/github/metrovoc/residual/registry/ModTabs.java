package io.github.metrovoc.residual.registry;

import io.github.metrovoc.residual.Residual;
import io.github.metrovoc.residual.graph.Op;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Residual.MOD_ID);

    public static final Supplier<CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.residual"))
            .icon(() -> new ItemStack(ModBlocks.CORE.get()))
            .displayItems((params, out) -> {
                out.accept(ModBlocks.CORE.get());
                out.accept(ModBlocks.ACCELERATOR.get());
                out.accept(ModBlocks.CASING.get());
                out.accept(ModItems.PROBE.get());
                out.accept(ModItems.WEIGHT_CRYSTAL.get());
                out.accept(ModBlocks.CABLE.get());
                for (DyeColor color : DyeColor.values()) {
                    out.accept(ModBlocks.COLORED_CABLES.get(color).get());
                }
                for (Op op : Op.values()) {
                    out.accept(ModBlocks.OPERATORS.get(op).get());
                }
                ModItems.INSIGHTS.values().forEach(insight -> out.accept(insight.get()));
            })
            .build());

    private ModTabs() {
    }
}
