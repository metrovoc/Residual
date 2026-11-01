package io.github.metrovoc.residual.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.metrovoc.residual.item.ExosuitItem;
import io.github.metrovoc.residual.item.FlightFrameItem;
import io.github.metrovoc.residual.item.SuitAbility;
import io.github.metrovoc.residual.item.Vein;
import io.github.metrovoc.residual.learn.Benchmark;
import io.github.metrovoc.residual.learn.Played;
import io.github.metrovoc.residual.model.Board;
import io.github.metrovoc.residual.model.ModelInfo;
import io.github.metrovoc.residual.net.SuitEdit;
import io.github.metrovoc.residual.net.SuitVein;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/**
 * An exosuit's screen. A dark display says what its board found: the model's weights, what they take as plain floats and in
 * each form it can run in, and how each scores, then its compute, what each ability running draws from it, and the model
 * at work on board. Under it, each ability, turned on and off with its button in the toolbar beside the window; those left on
 * run cheapest first, as far as the compute goes.
 */
public class ExosuitScreen extends Screen implements Parts.Drawn {
    private static final KeyMapping KEY = new KeyMapping("key.residual.exosuit", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, KeyMapping.Category.INVENTORY);
    /** Held, a break takes its whole vein. */
    static final KeyMapping VEIN = new KeyMapping("key.residual.vein", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_GRAVE_ACCENT, KeyMapping.Category.GAMEPLAY);
    /** What the server last heard about {@link #VEIN}. */
    private static boolean veinHeld;
    private static final int W = 196, LINE = 11, DISPLAY = 80, ROW = 19;
    private static final int DISPLAY_Y = 18, ROWS_Y = DISPLAY_Y + DISPLAY + 4;
    private static final int H = ROWS_Y + SuitAbility.values().length * ROW + 5;
    /** A segment of the compute bar, for each ability running in turn. */
    private static final int[] SEGMENTS = {Ui.TEAL, 0xFF2BA99B};

    public ExosuitScreen() {
        super(Component.translatable("item.residual.exosuit"));
    }

    public static void keys(RegisterKeyMappingsEvent event) {
        event.register(KEY);
        event.register(VEIN);
    }

    /** The one key opens the screen of the suit its presser wears; the server hears when the other is held. */
    public static void pressed(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        boolean held = mc.player != null && VEIN.isDown();
        if (mc.player == null) {
            veinHeld = false;
        } else if (held != veinHeld) {
            veinHeld = held;
            ClientPacketDistributor.sendToServer(new SuitVein(held));
        }
        while (KEY.consumeClick()) {
            if (mc.screen == null && mc.player != null && mc.player.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof ExosuitItem) {
                mc.setScreen(new ExosuitScreen());
            }
        }
    }

    private ItemStack suit() {
        return ExosuitItem.on(minecraft.player);
    }

    private int left() {
        return (width - W - IconButton.SIZE) / 2;
    }

    private int top() {
        return (height - H) / 2;
    }

    @Override
    protected void init() {
        ItemStack suit = suit();
        List<String> states = List.of(I18n.get("residual.exosuit.off"), I18n.get("residual.exosuit.on"));
        for (SuitAbility a : SuitAbility.values()) {
            addRenderableWidget(IconButton.setting(left() + W + 2, top() + ROWS_Y + a.ordinal() * ROW, List.of("suit_" + a.id),
                    Component.translatable("residual.ability." + a.id), states, ExosuitItem.isOn(suit, a) ? 1 : 0, hint(a),
                    on -> ClientPacketDistributor.sendToServer(new SuitEdit(a.ordinal(), on == 1))));
        }
    }

    private static Component hint(SuitAbility a) {
        return switch (a) {
            case REACH -> Component.translatable("residual.ability.reach.hint", (int) ExosuitItem.REACH, a.power);
            case SHIELD -> Component.translatable("residual.ability.shield.hint", count(ExosuitItem.SHIELD_POWER));
            case VEIN_MINING -> Component.translatable("residual.ability.vein_mining.hint", Component.keybind("key.residual.vein"), count(Vein.POWER));
            case FLIGHT -> Component.translatable("residual.ability.flight.hint", a.power, ExosuitItem.CAPACITY / a.power / 1200);
            default -> Component.translatable("residual.ability." + a.id + ".hint", a.power);
        };
    }

    private static String count(long v) {
        return String.format(Locale.ROOT, "%,d", v);
    }

    /** A thousand and up as so many K, rounded down, as far as anyone may count on it. */
    private static String kilo(long v) {
        return v / 1000 + "K";
    }

    private static String percent(float score) {
        return Math.round(100 * score) + "%";
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        ItemStack suit = suit();
        int x = left(), y = top();
        Ui.window(g, x, y, W, H);
        g.text(font, title, x + 8, y + 6, Ui.LABEL, false);
        int charged = x + W - 8 - 64;
        Ui.bar(g, charged, y + 8, 64, 4, (float) FlightFrameItem.charge(suit) / ExosuitItem.CAPACITY, Ui.TEAL);
        int dx = x + 8, dy = y + DISPLAY_Y, dw = W - 16;
        Ui.screen(g, dx, dy, dw, DISPLAY);
        ModelInfo model = FlightFrameItem.pilot(suit);
        Board board = ExosuitItem.board(suit);
        List<FormattedCharSequence> tip = new ArrayList<>();
        if (Ui.inside(mouseX, mouseY, charged, y + 6, 64, 8)) {
            add(tip, Component.translatable("residual.frame.charge", count(FlightFrameItem.charge(suit)), count(ExosuitItem.CAPACITY)));
        }
        if (model == null) {
            g.text(font, Component.translatable("residual.exosuit.no_model.short"), dx + 4, dy + 4, Ui.DIM, false);
        } else if (board == null) {
            g.text(font, Component.translatable("residual.exosuit.testing", model.tag()), dx + 4, dy + 4, Ui.TEXT, false);
        } else {
            display(g, model, board, suit, dx, dy, dw, mouseX, mouseY, tip);
        }
        Set<SuitAbility> running = ExosuitItem.running(suit);
        long free = ExosuitItem.free(suit);
        for (SuitAbility s : SuitAbility.values()) {
            int rx = x + 8, ry = y + ROWS_Y + s.ordinal() * ROW + 1, rw = W - 16;
            Ui.screen(g, rx, ry, rw, 16);
            boolean on = ExosuitItem.isOn(suit, s), runs = running.contains(s);
            int color = runs ? Ui.TEXT : Ui.DIM;
            g.text(font, Component.translatable("residual.ability." + s.id), rx + 4, ry + 4, color, false);
            String draw = kilo(s.compute);
            g.text(font, draw, rx + rw - 4 - font.width(draw), ry + 4, runs ? Ui.TEAL : on ? Ui.AMBER : Ui.DIM, false);
            if (Ui.inside(mouseX, mouseY, rx, ry, rw, 16)) {
                add(tip, Component.translatable("residual.ability." + s.id));
                add(tip, hint(s).copy().withStyle(ChatFormatting.GRAY));
                if (s == SuitAbility.VEIN_MINING && runs) {
                    add(tip, Component.translatable("residual.exosuit.vein_size", Vein.size(suit)).withStyle(ChatFormatting.GRAY));
                } else if (on && !runs) {
                    Board.Form form = board == null ? null : board.running(ExosuitItem.memory());
                    add(tip, (s == SuitAbility.VEIN_MINING && form != null && !form.passesSurvey()
                            ? Component.translatable("residual.exosuit.needs_survey")
                            : Component.translatable("residual.exosuit.needs", count(s.compute), count(free))).withStyle(ChatFormatting.GOLD));
                }
            }
        }
        super.extractRenderState(g, mouseX, mouseY, a);
        if (!tip.isEmpty()) {
            g.setTooltipForNextFrame(tip, mouseX, mouseY);
        }
    }

    private void add(List<FormattedCharSequence> tip, Component line) {
        tip.addAll(font.split(line, 200));
    }

    /** What the board found, a line an idea, and where the compute goes. */
    private void display(GuiGraphicsExtractor g, ModelInfo model, Board board, ItemStack suit, int dx, int dy, int dw, int mouseX, int mouseY, List<FormattedCharSequence> tip) {
        int x = dx + 4, y = dy + 4, right = dx + dw - 4;
        g.text(font, Component.translatable("residual.exosuit.model", model.tag(), count(board.parameters())), x, y, Ui.TEXT, false);
        y += LINE;
        // as plain floats it runs on a Core, not on a board
        form(g, "float", board.parameters() * Float.BYTES, model.score(Benchmark.PILOT), "", Ui.DIM, x, y, right);
        if (Ui.inside(mouseX, mouseY, dx, y - 1, dw, LINE)) {
            add(tip, Component.translatable("residual.exosuit.float", percent(model.score(Benchmark.PILOT))));
        }
        Board.Form running = board.running(ExosuitItem.memory());
        for (Board.Form form : List.of(board.int8(), board.int4())) {
            y += LINE;
            boolean runs = form == running;
            String state = runs ? "residual.exosuit.runs" : form.bytes() > ExosuitItem.memory() ? "residual.exosuit.too_big"
                    : form.pilot() < Benchmark.PILOT.target ? "residual.exosuit.falls" : "residual.exosuit.spare";
            form(g, "int" + form.bits(), form.bytes(), form.pilot(), I18n.get(state), runs ? Ui.TEAL : Ui.DIM, x, y, right);
            if (Ui.inside(mouseX, mouseY, dx, y - 1, dw, LINE)) {
                add(tip, Component.translatable("residual.exosuit.form", form.bits(), count(form.bytes()), count(ExosuitItem.memory())));
                add(tip, Component.translatable("residual.exosuit.scores", percent(form.pilot()), percent(form.survey())).withStyle(ChatFormatting.GRAY));
            }
        }
        y += LINE;
        long compute = ExosuitItem.compute(suit);
        g.text(font, Component.translatable("residual.exosuit.compute", count(compute)), x, y, compute > 0 ? Ui.TEXT : Ui.RED, false);
        String left = I18n.get("residual.exosuit.free", kilo(ExosuitItem.free(suit)));
        g.text(font, left, right - font.width(left), y, Ui.DIM, false);
        if (Ui.inside(mouseX, mouseY, dx, y - 1, dw, LINE + 6)) {
            add(tip, Component.translatable("residual.exosuit.compute", count(compute)));
            add(tip, Component.translatable("residual.exosuit.compute_hint").withStyle(ChatFormatting.GRAY));
        }
        y += LINE - 1;
        int w = dw - 8;
        double most = Math.max(compute, 1);
        g.fill(x, y, x + w, y + 3, Ui.GRID);
        double used = 0;
        int i = 0;
        for (SuitAbility a : ExosuitItem.running(suit)) {
            int from = (int) Math.round(w * used / most);
            used += a.compute;
            g.fill(x + from, y, x + (int) Math.round(w * used / most), y + 3, SEGMENTS[i++ % SEGMENTS.length]);
        }
        y += 6;
        int strip = Played.LOOKS * 7;
        SuitHud.strip(g, x + (w - strip) / 2, y, 7, 2);
        if (Ui.inside(mouseX, mouseY, dx, y, dw, 16)) {
            add(tip, Component.translatable("residual.exosuit.practice"));
            add(tip, Component.translatable("residual.exosuit.practice_hint").withStyle(ChatFormatting.GRAY));
        }
    }

    /** A line for one form: its name, its size, how it scores on Pilot and what becomes of it. */
    private void form(GuiGraphicsExtractor g, String name, long bytes, float pilot, String state, int color, int x, int y, int right) {
        g.text(font, name, x, y, color, false);
        g.text(font, Component.translatable("residual.exosuit.bytes", count(bytes)), x + 30, y, color, false);
        g.text(font, percent(pilot), x + 96, y, color, false);
        g.text(font, state, right - font.width(state), y, color, false);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (KEY.matches(event)) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** {@code row:flight} and so on for each ability's line; its button goes by {@code ability.flight}. */
    @Override
    public void parts(Parts out) {
        int x = left(), y = top();
        out.add("window", x, y, W, H);
        out.add("charge", x + W - 8 - 64, y + 6, 64, 8);
        out.add("display", x + 8, y + DISPLAY_Y, W - 16, DISPLAY);
        for (SuitAbility s : SuitAbility.values()) {
            out.add("row:" + s.id, x + 8, y + ROWS_Y + s.ordinal() * ROW + 1, W - 16, 16);
        }
    }
}
