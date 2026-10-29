package io.github.metrovoc.residual.registry;

import io.github.metrovoc.residual.Residual;
import io.github.metrovoc.residual.bench.MaskData;
import io.github.metrovoc.residual.bench.Spec;
import io.github.metrovoc.residual.bench.Specs;
import io.github.metrovoc.residual.graph.Op;
import io.github.metrovoc.residual.item.MaskItem;
import io.github.metrovoc.residual.item.WreckItem;
import io.github.metrovoc.residual.learn.Wrecks;
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
                out.accept(ModBlocks.STREAM.get());
                out.accept(ModBlocks.BENCH.get());
                out.accept(ModBlocks.FABRICATOR.get());
                out.accept(ModItems.WAFER.get());
                out.accept(ModItems.PHOTOMASK.get());
                for (Spec spec : Specs.all()) {
                    if (!spec.unlocks().isEmpty()) {
                        out.accept(MaskItem.of(ModItems.PHOTOMASK.get(), MaskData.salvaged(spec)));
                    }
                }
                for (Op op : Op.values()) {
                    out.accept(ModItems.MACHINES.get(op).get());
                }
                out.accept(ModItems.CHECKPOINT.get());
                out.accept(ModItems.ARCHIVE_DRIVE.get());
                out.accept(ModBlocks.FORGE.get());
                out.accept(ModItems.TOKENIZER.get());
                out.accept(ModItems.SHARD.get());
                out.accept(ModBlocks.READER.get());
                out.accept(ModBlocks.FORECASTER.get());
                out.accept(ModItems.FIELD_LOG.get());
                ModItems.DATASETS.values().forEach(dataset -> out.accept(dataset.get()));
                for (Wrecks.Wreck w : Wrecks.ALL) {
                    out.accept(WreckItem.of(ModItems.WRECK.get(), w));
                }
                ModItems.SCHEMATICS.values().forEach(schematic -> out.accept(schematic.get()));
                out.accept(ModItems.AGENT.get());
                out.accept(ModItems.SURVEY_ROD.get());
                out.accept(ModBlocks.SERVER.get());
                out.accept(ModBlocks.RELAY.get());
                out.accept(ModItems.RELAY_CARD.get());
                out.accept(ModItems.TERMINAL.get());
            })
            .build());

    private ModTabs() {
    }
}
