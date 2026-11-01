package io.github.metrovoc.residual.client;

import io.github.metrovoc.residual.item.ExosuitItem;
import io.github.metrovoc.residual.item.FlightFrameItem;
import io.github.metrovoc.residual.item.SuitAbility;
import io.github.metrovoc.residual.learn.Played;
import io.github.metrovoc.residual.net.SuitFlight;
import java.util.Set;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * A worn exosuit's corner of the screen: the flight its model is flying on board as it plays out, a look at a time with
 * the hold shaded across, its charge, and a light for each ability running. While the board tests a model it says so.
 */
public final class SuitHud {
    private static final int X = 4, Y = 4, WIDTH = 48;
    private static final int HOLD = 0xFF1E3D33;
    private static String line = "";
    private static long heardAt;

    private SuitHud() {
    }

    public static void receive(SuitFlight flight) {
        Minecraft mc = Minecraft.getInstance();
        line = flight.line();
        heardAt = mc.level == null ? 0 : mc.level.getGameTime();
    }

    /** The flight so far, a look {@code look} wide and a band {@code band} high, in a box of 24 looks by 8 bands from {@code (x, y)}. */
    static void strip(GuiGraphicsExtractor g, int x, int y, int look, int band) {
        Minecraft mc = Minecraft.getInstance();
        g.fill(x, y + 3 * band, x + Played.LOOKS * look, y + 5 * band, HOLD);
        long played = mc.level == null ? 0 : (mc.level.getGameTime() - heardAt) / ExosuitItem.LOOK_EVERY + 1;
        for (int k = 0; k < played && 2 + 2 * k < line.length(); k++) {
            int height = line.charAt(1 + 2 * k) - 'a';
            int top = y + (7 - height) * band;
            g.fill(x + k * look, top, x + (k + 1) * look, top + band, height == 3 || height == 4 ? Ui.GREEN : Ui.AMBER);
        }
    }

    public static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.screen != null || mc.player == null) {
            return;
        }
        ItemStack suit = mc.player.getItemBySlot(EquipmentSlot.CHEST);
        if (!(suit.getItem() instanceof ExosuitItem) || FlightFrameItem.pilot(suit) == null) {
            return;
        }
        if (ExosuitItem.board(suit) == null) {
            g.text(mc.font, Component.translatable("residual.exosuit.quantizing"), X, Y, Ui.TEXT, true);
            return;
        }
        Set<SuitAbility> running = ExosuitItem.running(suit);
        if (running.isEmpty()) {
            return;
        }
        Ui.screen(g, X, Y, WIDTH, 16);
        strip(g, X, Y, WIDTH / Played.LOOKS, 2);
        Ui.bar(g, X, Y + 18, WIDTH, 2, (float) FlightFrameItem.charge(suit) / ExosuitItem.CAPACITY, Ui.TEAL);
        for (SuitAbility a : SuitAbility.values()) {
            int x = X + a.ordinal() * (WIDTH + 2) / SuitAbility.values().length;
            g.fill(x, Y + 22, x + 8, Y + 24, running.contains(a) ? Ui.GREEN : Ui.GRID);
        }
    }
}
