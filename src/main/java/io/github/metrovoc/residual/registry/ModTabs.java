package io.github.metrovoc.residual.registry;

import io.github.metrovoc.residual.refine.Stage;
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
            .icon(() -> new ItemStack(ModItems.MACHINES.get(Op.ADD).get()))
            .displayItems((params, out) -> {
                out.accept(ModBlocks.CORE.get());
                out.accept(ModBlocks.ACCELERATOR.get());
                ModBlocks.UNITS.values().forEach(unit -> out.accept(unit.get()));
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
                    if (spec.makesSomething()) {
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
                out.accept(ModBlocks.FORECASTER_II.get());
                out.accept(ModBlocks.FORECASTER_III.get());
                out.accept(ModItems.RELAY_DUMP.get());
                for (Stage stage : Stage.values()) {
                    out.accept(ModBlocks.REFINERY.get(stage).get());
                }
                out.accept(ModItems.TOKEN_CELL.get());
                out.accept(ModBlocks.COMBUSTION_GENERATOR.get());
                out.accept(ModBlocks.BOILER.get());
                out.accept(ModBlocks.TURBINE.get());
                out.accept(ModBlocks.SOLAR_PANEL.get());
                out.accept(ModBlocks.REACTOR_CORE.get());
                out.accept(ModBlocks.REACTOR_CASING.get());
                out.accept(ModBlocks.REACTOR_GLASS.get());
                out.accept(ModBlocks.FUEL_ROD.get());
                out.accept(ModItems.REACTOR_FUEL.get());
                out.accept(ModBlocks.POWER_CABLE.get());
                out.accept(ModBlocks.HEAVY_CABLE.get());
                out.accept(ModBlocks.BUS_BAR.get());
                out.accept(ModBlocks.ENERGY_CELL.get());
                out.accept(ModBlocks.CAPACITOR_BANK.get());
                out.accept(ModBlocks.SMELTER.get());
                out.accept(ModItems.WAFER_28.get());
                out.accept(ModItems.WAFER_7.get());
                out.accept(ModItems.WAFER_3.get());
                ModBlocks.ACCELERATORS.forEach(a -> out.accept(a.get()));
                out.accept(ModBlocks.RACK_MODULE.get());
                out.accept(ModBlocks.RACK_SWITCH.get());
                out.accept(ModBlocks.POD_SWITCH.get());
                out.accept(ModBlocks.INTERCONNECT.get());
                out.accept(ModBlocks.CHILLER.get());
                out.accept(ModBlocks.COOLANT_PIPE.get());
                out.accept(ModBlocks.HALL_CONSOLE.get());
                out.accept(ModBlocks.LOAD_WALL.get());
                out.accept(ModItems.FIELD_LOG.get());
                out.accept(ModItems.HOURGLASS.get());
                out.accept(ModBlocks.SURVEYOR.get());
                out.accept(ModBlocks.WATCH_POST.get());
                out.accept(ModItems.FLIGHT_FRAME.get());
                out.accept(ModItems.EXOSUIT.get());
                ModItems.DATASETS.values().forEach(dataset -> out.accept(dataset.get()));
                for (Wrecks.Wreck w : Wrecks.ALL) {
                    out.accept(WreckItem.of(ModItems.WRECK.get(), w));
                }
                ModItems.SCHEMATICS.values().forEach(schematic -> out.accept(schematic.get()));
                out.accept(ModItems.AGENT.get());
                out.accept(ModItems.SURVEY_ROD.get());
                out.accept(ModItems.WAYFINDER.get());
                out.accept(ModItems.PROGRAM_CARD.get());
                ModItems.UPGRADES.values().forEach(u -> out.accept(u.get()));
                out.accept(ModBlocks.SERVER.get());
                out.accept(ModBlocks.RELAY.get());
                out.accept(ModBlocks.RELAY_GATE.get());
                out.accept(ModBlocks.RELAY_LINK.get());
                out.accept(ModItems.RELAY_CARD.get());
                out.accept(ModItems.KV_CACHE.get());
                out.accept(ModItems.WEIGHT_PACKER.get());
                out.accept(ModItems.TERMINAL.get());
                out.accept(ModBlocks.DRIVE_RACK.get());
                out.accept(ModItems.STORAGE_DRIVE.get());
                out.accept(ModItems.DENSE_DRIVE.get());
                out.accept(ModItems.STACKED_DRIVE.get());
                out.accept(ModItems.DEEP_DRIVE.get());
                out.accept(ModItems.PORTABLE_DRIVE.get());
                out.accept(ModBlocks.DRIVE_DOCK.get());
                out.accept(ModBlocks.STORAGE_LINK.get());
                out.accept(ModBlocks.STORAGE_TERMINAL.get());
                out.accept(ModBlocks.ACCESS_POINT.get());
                out.accept(ModItems.WIRELESS_TERMINAL.get());
                out.accept(ModBlocks.LINK_DRIVER.get());
                out.accept(ModBlocks.PORT.get());
                out.accept(ModBlocks.ASSEMBLER.get());
                out.accept(ModBlocks.PROCESSOR.get());
            })
            .build());

    private ModTabs() {
    }
}
