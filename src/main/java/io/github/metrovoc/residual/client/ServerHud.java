package io.github.metrovoc.residual.client;

import io.github.metrovoc.residual.item.LinkItem;
import io.github.metrovoc.residual.net.ServerLoad;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** While a tuned Terminal or Relay Card is in hand: how its server is doing, a corner of the screen. */
public final class ServerHud {
    private static final int W = 72, H = 16, EDGE = 4;
    private static ServerLoad last;
    private static long heardAt;

    private ServerHud() {
    }

    public static void receive(ServerLoad load) {
        Minecraft mc = Minecraft.getInstance();
        last = load;
        heardAt = mc.level == null ? 0 : mc.level.getGameTime();
    }

    public static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.screen != null || mc.player == null || last == null || mc.level == null || mc.level.getGameTime() - heardAt > 40) {
            return;
        }
        ItemStack main = mc.player.getMainHandItem(), off = mc.player.getOffhandItem();
        if (!(main.getItem() instanceof LinkItem) && !(off.getItem() instanceof LinkItem)) {
            return;
        }
        int x = g.guiWidth() - W - EDGE, y = EDGE;
        Ui.screen(g, x, y, W, H);
        boolean turning = last.turning();
        Component line = turning ? Component.translatable("residual.server.hud.turning")
                : Component.translatable("residual.server.hud", Math.round(last.rate()), last.waiting());
        g.text(mc.font, line, x + 2, y + 2, turning ? Ui.RED : Ui.TEXT, false);
        Ui.bar(g, x + 2, y + 12, W - 4, 2, last.load(), last.load() > 0.95f ? Ui.RED : last.load() > 0.7f ? Ui.AMBER : Ui.TEAL);
    }
}
