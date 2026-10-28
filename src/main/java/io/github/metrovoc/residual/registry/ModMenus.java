package io.github.metrovoc.residual.registry;

import io.github.metrovoc.residual.Residual;
import io.github.metrovoc.residual.menu.CoreMenu;
import io.github.metrovoc.residual.menu.OperatorMenu;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, Residual.MOD_ID);

    public static final Supplier<MenuType<CoreMenu>> CORE = MENUS.register("model_core",
            () -> IMenuTypeExtension.create((id, inv, buf) -> new CoreMenu(id, buf.readBlockPos())));
    public static final Supplier<MenuType<OperatorMenu>> OPERATOR = MENUS.register("operator",
            () -> IMenuTypeExtension.create((id, inv, buf) -> new OperatorMenu(id, buf.readBlockPos())));

    private ModMenus() {
    }
}
