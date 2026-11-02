package io.github.metrovoc.residual.client;

import io.github.metrovoc.residual.block.ServerBlockEntity;
import io.github.metrovoc.residual.menu.ServerMenu;
import io.github.metrovoc.residual.net.ServerSync;
import io.github.metrovoc.residual.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * A Model Server's screen: what it serves on the left and a display of how it is doing, one idea a line, each with what stands behind it
 * in a tooltip. Its cards sit in a panel beside the window and its two settings in a toolbar under that.
 */
public class ServerScreen extends AbstractContainerScreen<ServerMenu> implements Parts.Drawn {
    private static final int WINDOW_W = 176, SHOW_X = 30, SHOW_Y = 18, SHOW_W = 138, SHOW_H = 70;
    private static final int BAR_X = 4, BAR_W = SHOW_W - 8, BAR_H = 4;
    private static final int RATE_Y = 3, LOAD_Y = 13, MEMORY_LABEL_Y = 20, MEMORY_Y = 30, DRAWN_LABEL_Y = 36, DRAWN_Y = 46, NOTE_Y = 53;
    private static final int TOOLBAR_X = ServerMenu.SIDE_X + 6, TOOLBAR_Y = ServerMenu.SIDE_Y + ServerMenu.SIDE_H + 2;
    /** A colour for each who, as they come. */
    private static final int[] WHO = {Ui.TEAL, Ui.AMBER, 0xFF9C8CFF, Ui.GREEN, Ui.BLUE, 0xFFFF9C7C, 0xFFD8D8D8, 0xFF7F8D99, 0xFFFFD86B};
    private IconButton form, batch, effort;
    /** Ticks since a button was pressed: what the server said before it heard of that is out of date. */
    private int pressed = 100;

    public ServerScreen(ServerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, ServerMenu.SIDE_X + ServerMenu.SIDE_W, ServerMenu.INVENTORY_Y + 82);
        inventoryLabelX = ServerMenu.INVENTORY_X;
        inventoryLabelY = ServerMenu.INVENTORY_Y - 10;
    }

    @Override
    protected void init() {
        super.init();
        List<String> forms = List.of("float", "int8", "int4");
        form = addRenderableWidget(IconButton.setting(leftPos + TOOLBAR_X, topPos + TOOLBAR_Y, List.of("form_float", "form_int8", "form_int4"),
                Component.translatable("residual.server.form"), forms, 0, Component.translatable("residual.server.form.hint"), i -> button(i)));
        List<String> batches = new ArrayList<>();
        for (int b : ServerBlockEntity.BATCHES) {
            batches.add(Integer.toString(b));
        }
        batch = addRenderableWidget(IconButton.setting(leftPos + TOOLBAR_X, topPos + TOOLBAR_Y + IconButton.SIZE + 1, List.of("batch"),
                Component.translatable("residual.server.batch"), batches, 0, Component.translatable("residual.server.batch.hint"), i -> button(ServerBlockEntity.FORMS.length + i)));
        List<String> efforts = new ArrayList<>();
        for (int e : ServerBlockEntity.EFFORTS) {
            efforts.add(Integer.toString(e));
        }
        effort = addRenderableWidget(IconButton.setting(leftPos + TOOLBAR_X, topPos + TOOLBAR_Y + 2 * (IconButton.SIZE + 1), List.of("effort"),
                Component.translatable("residual.server.effort"), efforts, 0, Component.translatable("residual.server.effort.hint"),
                i -> button(ServerBlockEntity.FORMS.length + ServerBlockEntity.BATCHES.length + i)));
    }

    private void button(int id) {
        pressed = 0;
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        ServerSync s = menu.sync;
        if (s != null && form != null && ++pressed > 20) {
            form.set(s.bits() == 8 ? 1 : s.bits() == 4 ? 2 : 0);
            int at = 0;
            for (int i = 0; i < ServerBlockEntity.BATCHES.length; i++) {
                if (ServerBlockEntity.BATCHES[i] == s.batch()) {
                    at = i;
                }
            }
            batch.set(at);
            at = 0;
            for (int i = 0; i < ServerBlockEntity.EFFORTS.length; i++) {
                if (ServerBlockEntity.EFFORTS[i] == s.effort()) {
                    at = i;
                }
            }
            effort.set(at);
        }
        form.active = menu.getSlot(ServerBlockEntity.PACKER).hasItem();
        batch.active = effort.active = menu.getSlot(ServerBlockEntity.CACHE).hasItem();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractBackground(g, mouseX, mouseY, a);
        Ui.window(g, leftPos + ServerMenu.SIDE_X, topPos + ServerMenu.SIDE_Y, ServerMenu.SIDE_W, ServerMenu.SIDE_H);
        Ui.window(g, leftPos, topPos, WINDOW_W, imageHeight);
        for (Slot slot : menu.slots) {
            Ui.slot(g, leftPos + slot.x, topPos + slot.y);
        }
        if (!menu.getSlot(ServerBlockEntity.MODEL).hasItem()) {
            Ui.ghost(g, new ItemStack(ModItems.CHECKPOINT.get()), leftPos + ServerMenu.MODEL_X, topPos + ServerMenu.MODEL_Y);
        }
        if (!menu.getSlot(ServerBlockEntity.CACHE).hasItem()) {
            Ui.ghost(g, new ItemStack(ModItems.KV_CACHE.get()), leftPos + ServerMenu.CARD_X, topPos + ServerMenu.CARD_Y);
        }
        if (!menu.getSlot(ServerBlockEntity.PACKER).hasItem()) {
            Ui.ghost(g, new ItemStack(ModItems.WEIGHT_PACKER.get()), leftPos + ServerMenu.CARD_X, topPos + ServerMenu.CARD_Y + ServerMenu.CARD_STEP);
        }
        if (!menu.getSlot(ServerBlockEntity.DRAFT).hasItem()) {
            Ui.ghost(g, new ItemStack(ModItems.CHECKPOINT.get()), leftPos + ServerMenu.CARD_X, topPos + ServerMenu.CARD_Y + 2 * ServerMenu.CARD_STEP);
        }
        if (!menu.getSlot(ServerBlockEntity.JUDGE).hasItem()) {
            Ui.ghost(g, new ItemStack(ModItems.CHECKPOINT.get()), leftPos + ServerMenu.CARD_X, topPos + ServerMenu.CARD_Y + 3 * ServerMenu.CARD_STEP);
        }
        Ui.screen(g, leftPos + SHOW_X, topPos + SHOW_Y, SHOW_W, SHOW_H);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, title, 8, 6, Ui.LABEL, false);
        g.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, Ui.LABEL, false);
        ServerSync s = menu.sync;
        if (s == null) {
            return;
        }
        int x = SHOW_X + BAR_X, y = SHOW_Y;
        if (!menu.getSlot(ServerBlockEntity.MODEL).hasItem() || !s.problem().isEmpty()) {
            Component why = s.problem().isEmpty() ? Component.translatable("residual.server.none") : Component.translatable(s.problem());
            int line = y + RATE_Y;
            for (FormattedCharSequence seq : font.split(why, BAR_W)) {
                g.text(font, seq, x, line, s.problem().isEmpty() ? Ui.DIM : Ui.AMBER, false);
                line += 10;
            }
            return;
        }
        g.text(font, Component.translatable("residual.server.rate", String.format(Locale.ROOT, "%.1f", s.rate())), x, y + RATE_Y, Ui.TEXT, false);
        Ui.bar(g, x, y + LOAD_Y, BAR_W, BAR_H, s.load(), s.load() > 0.95f ? Ui.RED : s.load() > 0.7f ? Ui.AMBER : Ui.TEAL);
        // weights, then the draft, then what the cache holds, of all it has
        g.text(font, Component.translatable("residual.server.memory"), x, y + MEMORY_LABEL_Y, Ui.DIM, false);
        g.fill(x, y + MEMORY_Y, x + BAR_W, y + MEMORY_Y + BAR_H, Ui.GRID);
        int at = x;
        at = segment(g, at, y + MEMORY_Y, s.weights(), s.memory(), Ui.TEAL);
        at = segment(g, at, y + MEMORY_Y, s.draft(), s.memory(), Ui.AMBER);
        segment(g, at, y + MEMORY_Y, s.cached(), s.memory(), 0xFF9C8CFF);
        g.text(font, Component.translatable("residual.server.drawn"), x, y + DRAWN_LABEL_Y, Ui.DIM, false);
        g.fill(x, y + DRAWN_Y, x + BAR_W, y + DRAWN_Y + BAR_H, Ui.GRID);
        float from = 0;
        for (int i = 0; i < s.drawn().size(); i++) {
            float to = from + s.drawn().get(i).share();
            g.fill(x + Math.round(from * BAR_W), y + DRAWN_Y, x + Math.round(to * BAR_W), y + DRAWN_Y + BAR_H, WHO[i % WHO.length]);
            from = to;
        }
        Component note = s.waiting() >= ServerBlockEntity.QUEUE ? Component.translatable("residual.server.turning")
                : Component.translatable("residual.server.waiting", s.waiting());
        g.text(font, note, x, y + NOTE_Y, s.waiting() >= ServerBlockEntity.QUEUE ? Ui.RED : s.waiting() > ServerBlockEntity.QUEUE / 2 ? Ui.AMBER : Ui.DIM, false);
    }

    private static int segment(GuiGraphicsExtractor g, int x, int y, long part, long of, int color) {
        int w = (int) Math.round(BAR_W * (double) part / Math.max(of, 1));
        g.fill(x, y, Math.min(x + w, SHOW_X + BAR_X + BAR_W), y + BAR_H, color);
        return x + w;
    }

    private static String bytes(long b) {
        return String.format(Locale.ROOT, "%,d", b);
    }

    private static String micros(float nanos) {
        return String.format(Locale.ROOT, "%.0f", nanos / 1000);
    }

    private static String percent(float score) {
        return String.format(Locale.ROOT, "%.1f", 100 * score);
    }

    private void add(List<FormattedCharSequence> lines, Component line) {
        lines.addAll(font.split(line, 220));
    }

    private void addGray(List<FormattedCharSequence> lines, Component line) {
        add(lines, line.copy().withStyle(ChatFormatting.GRAY));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        int mx = mouseX - leftPos, my = mouseY - topPos;
        List<FormattedCharSequence> lines = new ArrayList<>();
        ServerSync s = menu.sync;
        if (s != null && Ui.inside(mx, my, SHOW_X, SHOW_Y, SHOW_W, SHOW_H) && s.problem().isEmpty() && menu.getSlot(ServerBlockEntity.MODEL).hasItem()) {
            int y = my - SHOW_Y;
            if (y < MEMORY_LABEL_Y) {
                add(lines, Component.translatable("residual.server.rate.title", String.format(Locale.ROOT, "%.1f", s.rate())));
                addGray(lines, Component.translatable("residual.server.rate.hint", s.threads()));
                addGray(lines, Component.translatable("residual.server.load", Math.round(100 * s.load())));
                if (s.hot() > 0) {
                    addGray(lines, Component.translatable("residual.server.hot.hint", s.hot()));
                }
                if (s.plainNanos() > 0) {
                    addGray(lines, Component.translatable("residual.server.measured", micros(s.plainNanos()), micros(s.passNanos()), micros(s.rowNanos())));
                }
                if (s.rows() + s.saved() > 0) {
                    addGray(lines, Component.translatable("residual.server.rows", bytes(s.rows()), bytes(s.passes()), bytes(s.saved())));
                }
                if (s.proposed() > 0) {
                    addGray(lines, Component.translatable("residual.server.stood", bytes(s.accepted()), bytes(s.proposed()), Math.round(100f * s.accepted() / s.proposed())));
                }
                if (!s.decodes()) {
                    addGray(lines, Component.translatable("residual.server.reads_ahead"));
                }
            } else if (y < DRAWN_LABEL_Y) {
                add(lines, Component.translatable("residual.server.memory.title", bytes(s.memory())));
                addGray(lines, Component.translatable("residual.server.memory.weights", bytes(s.weights()), s.bits() == 32 ? "float" : "int" + s.bits()));
                if (s.draft() > 0) {
                    addGray(lines, Component.translatable("residual.server.memory.draft", bytes(s.draft())));
                }
                addGray(lines, Component.translatable("residual.server.memory.cached", bytes(s.cached())));
                for (ServerSync.Scored c : s.scores()) {
                    if (c.saved() != c.now() || s.bits() != 32) {
                        add(lines, Component.translatable("residual.server.score", Component.translatable("residual.benchmark." + c.benchmark()), percentOrLoss(c.benchmark(), c.saved()),
                                percentOrLoss(c.benchmark(), c.now())).withStyle(c.passes() ? ChatFormatting.GREEN : ChatFormatting.RED));
                    }
                }
            } else {
                add(lines, Component.translatable("residual.server.drawn.title"));
                if (s.drawn().isEmpty()) {
                    addGray(lines, Component.translatable("residual.server.drawn.none"));
                }
                for (ServerSync.Drawn d : s.drawn()) {
                    addGray(lines, Component.translatable("residual.server.drawn.line", Component.translatable("residual.server.who." + d.who()), Math.round(100 * d.share())));
                }
                addGray(lines, Component.translatable("residual.server.turned", bytes(s.turnedAway()), bytes(s.answered())));
            }
        } else if (Ui.inside(mx, my, ServerMenu.CARD_X, ServerMenu.CARD_Y, 16, 16) && !menu.getSlot(ServerBlockEntity.CACHE).hasItem() && menu.getCarried().isEmpty()) {
            add(lines, Component.translatable("item.residual.kv_cache"));
            addGray(lines, Component.translatable("residual.server.cache_slot"));
        } else if (Ui.inside(mx, my, ServerMenu.CARD_X, ServerMenu.CARD_Y + ServerMenu.CARD_STEP, 16, 16) && !menu.getSlot(ServerBlockEntity.PACKER).hasItem() && menu.getCarried().isEmpty()) {
            add(lines, Component.translatable("item.residual.weight_packer"));
            addGray(lines, Component.translatable("residual.server.packer_slot"));
        } else if (Ui.inside(mx, my, ServerMenu.CARD_X, ServerMenu.CARD_Y + 2 * ServerMenu.CARD_STEP, 16, 16) && !menu.getSlot(ServerBlockEntity.DRAFT).hasItem() && menu.getCarried().isEmpty()) {
            add(lines, Component.translatable("residual.server.draft"));
            addGray(lines, Component.translatable("residual.server.draft_slot"));
        } else if (Ui.inside(mx, my, ServerMenu.CARD_X, ServerMenu.CARD_Y + 3 * ServerMenu.CARD_STEP, 16, 16) && !menu.getSlot(ServerBlockEntity.JUDGE).hasItem() && menu.getCarried().isEmpty()) {
            add(lines, Component.translatable("residual.server.judge"));
            addGray(lines, Component.translatable("residual.server.judge_slot"));
        } else if (Ui.inside(mx, my, ServerMenu.MODEL_X, ServerMenu.MODEL_Y, 16, 16) && !menu.getSlot(ServerBlockEntity.MODEL).hasItem() && menu.getCarried().isEmpty()) {
            add(lines, Component.translatable("residual.agent.model_slot"));
            addGray(lines, Component.translatable("residual.server.model_slot"));
        }
        if (!lines.isEmpty()) {
            g.setTooltipForNextFrame(lines, mouseX, mouseY);
        }
    }

    private static String percentOrLoss(String benchmark, float score) {
        return "reading".equals(benchmark) || "forecasting".equals(benchmark) ? String.format(Locale.ROOT, "%.3f", score) : percent(score) + "%";
    }

    @Override
    public void parts(Parts out) {
        out.add("model", this, menu.getSlot(ServerBlockEntity.MODEL));
        out.add("cache", this, menu.getSlot(ServerBlockEntity.CACHE));
        out.add("packer", this, menu.getSlot(ServerBlockEntity.PACKER));
        out.add("draft", this, menu.getSlot(ServerBlockEntity.DRAFT));
        out.add("judge", this, menu.getSlot(ServerBlockEntity.JUDGE));
        out.add("rate", leftPos + SHOW_X, topPos + SHOW_Y, SHOW_W, MEMORY_LABEL_Y);
        out.add("memory", leftPos + SHOW_X, topPos + SHOW_Y + MEMORY_LABEL_Y, SHOW_W, DRAWN_LABEL_Y - MEMORY_LABEL_Y);
        out.add("drawn", leftPos + SHOW_X, topPos + SHOW_Y + DRAWN_LABEL_Y, SHOW_W, SHOW_H - DRAWN_LABEL_Y);
    }
}
