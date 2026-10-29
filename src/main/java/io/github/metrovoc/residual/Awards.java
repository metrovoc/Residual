package io.github.metrovoc.residual;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.level.ServerPlayer;

/** Advancements that only code can tell were earned. Each has a single criterion, {@code done}. */
public final class Awards {
    private Awards() {
    }

    public static void grant(ServerPlayer player, String path) {
        AdvancementHolder advancement = player.level().getServer().getAdvancements().get(Residual.id(path));
        if (advancement != null) {
            player.getAdvancements().award(advancement, "done");
        }
    }
}
