package io.github.metrovoc.residual;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(Residual.MOD_ID)
public final class Residual {
    public static final String MOD_ID = "residual";
    public static final Logger LOG = LogUtils.getLogger();

    public Residual(IEventBus modBus, ModContainer container) {
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
