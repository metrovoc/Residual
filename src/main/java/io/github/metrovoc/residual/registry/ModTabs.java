package io.github.metrovoc.residual.registry;

import io.github.metrovoc.residual.Residual;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Residual.MOD_ID);

    public static final Supplier<CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.residual"))
            .icon(() -> new ItemStack(ModItems.CORE.get()))
            .displayItems((params, out) -> {
                out.accept(ModItems.CORE.get());
                out.accept(ModItems.MODEL_DRIVE.get());
                out.accept(ModItems.SHARD.get());
                out.accept(ModItems.ARCHIVE_DRIVE.get());
                out.accept(ModItems.WAYFINDER.get());
                out.accept(ModItems.FIELD_LOG.get());
            })
            .build());

    private ModTabs() {
    }
}
