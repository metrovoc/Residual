package io.github.metrovoc.residual.client;

import io.github.metrovoc.residual.block.TunerBlockEntity;
import io.github.metrovoc.residual.block.TunerBlockEntity.Method;
import io.github.metrovoc.residual.learn.Benchmark;
import io.github.metrovoc.residual.menu.TunerMenu;
import io.github.metrovoc.residual.net.TunerAsk;
import io.github.metrovoc.residual.net.TunerSync;
import io.github.metrovoc.residual.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * A Tuner's screen: base and what it learns from on the left, what comes out on the right, and between them a display of how it is going: steps, a line of what it is raising with the
 * truth under it where that is known, the attempts at a question, passed and failed, and an answer as it stands. Its settings are a toolbar beside the window. Choosing shows two answers
 * instead, to pick from.
 */
public class TunerScreen extends AbstractContainerScreen<TunerMenu> implements Parts.Drawn {
    private static final int WINDOW_W = 176, SHOW_X = 28, SHOW_Y = 18, SHOW_W = 120, SHOW_H = 74;
    private static final int TOOLBAR_X = WINDOW_W + 2;
    private static final int CURVE_Y = 12, CURVE_H = 18, MARKS_Y = 32, WROTE_Y = 40;
    private IconButton method, budget, option, auto;
    private IconButton run;
    private Button first, second, neither;
    private EditBox said;
    private int pressed = 100;
    private Method shown = Method.FINE_TUNE;

    public TunerScreen(TunerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WINDOW_W + 2 + IconButton.SIZE, TunerMenu.INVENTORY_Y + 82);
        inventoryLabelX = TunerMenu.INVENTORY_X;
        inventoryLabelY = TunerMenu.INVENTORY_Y - 10;
    }

    @Override
    protected void init() {
        super.init();
        List<String> names = new ArrayList<>(), icons = new ArrayList<>();
        for (Method m : Method.values()) {
            names.add(Component.translatable("residual.tuner.method." + m.id).getString());
            icons.add("tune_" + m.id);
        }
        int x = leftPos + TOOLBAR_X, y = topPos + 4, step = IconButton.SIZE + 1;
        run = addRenderableWidget(IconButton.action(x, y, "run", Component.translatable("residual.tuner.run"), Component.translatable("residual.tuner.run.hint"), () -> press(0)));
        method = addRenderableWidget(IconButton.setting(x, y + step, icons, Component.translatable("residual.tuner.method"), names, 0, Component.translatable("residual.tuner.method.hint"),
                i -> press(10 + i)));
        List<String> budgets = new ArrayList<>();
        for (int b : TunerBlockEntity.BUDGETS) {
            budgets.add(Integer.toString(b));
        }
        budget = addRenderableWidget(IconButton.setting(x, y + 2 * step, List.of("tune_steps"), Component.translatable("residual.tuner.budget"), budgets, 3, Component.translatable("residual.tuner.budget.hint"),
                i -> press(30 + i)));
        auto = addRenderableWidget(IconButton.setting(x, y + 4 * step, List.of("tune_auto"), Component.translatable("residual.tuner.auto"),
                List.of(Component.translatable("residual.exosuit.off").getString(), Component.translatable("residual.exosuit.on").getString()), 0, Component.translatable("residual.tuner.auto.hint"), i -> press(1)));
        options();
        first = addRenderableWidget(Button.builder(Component.empty(), b -> press(3)).bounds(leftPos + SHOW_X + 2, topPos + SHOW_Y + 20, SHOW_W - 4, 16).build());
        second = addRenderableWidget(Button.builder(Component.empty(), b -> press(4)).bounds(leftPos + SHOW_X + 2, topPos + SHOW_Y + 37, SHOW_W - 4, 16).build());
        neither = addRenderableWidget(Button.builder(Component.translatable("residual.tuner.neither"), b -> press(5)).bounds(leftPos + SHOW_X + 2, topPos + SHOW_Y + 54, 56, 16).build());
        said = addRenderableWidget(new EditBox(font, leftPos + SHOW_X + 60, topPos + SHOW_Y + 54, SHOW_W - 62, 16, Component.translatable("residual.tuner.said")));
        said.setMaxLength(40);
        said.setHint(Component.translatable("residual.tuner.said.hint"));
        chooser(false);
    }

    /** The third button says what's asked of the method on show. */
    private void options() {
        if (option != null) {
            removeWidget(option);
            option = null;
        }
        List<String> choices = TunerBlockEntity.OPTIONS.get(shown);
        if (choices != null) {
            List<String> names = choices.stream().map(c -> Component.translatable("residual.tuner.option." + shown.id + "." + c.replace('.', '_')).getString()).toList();
            option = addRenderableWidget(IconButton.setting(leftPos + TOOLBAR_X, topPos + 4 + 3 * (IconButton.SIZE + 1), List.of("tune_option"), Component.translatable("residual.tuner.option." + shown.id),
                    names, 0, Component.translatable("residual.tuner.option." + shown.id + ".hint"), i -> press(50 + i)));
        }
    }

    private void chooser(boolean on) {
        first.visible = second.visible = neither.visible = said.visible = on;
    }

    private void press(int id) {
        pressed = 0;
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (said != null && said.isFocused() && said.visible) {
            if (event.isConfirmation()) {
                ClientPacketDistributor.sendToServer(new TunerAsk(menu.pos, said.getValue()));
                return true;
            }
            if (!event.isEscape()) {
                said.keyPressed(event);
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        TunerSync s = menu.sync;
        if (s == null) {
            return;
        }
        Method now = Method.values()[Math.floorMod(s.method(), Method.values().length)];
        if (++pressed > 20) {
            method.set(s.method());
            budget.set(s.budget());
            auto.set(s.auto() ? 1 : 0);
            if (now != shown) {
                shown = now;
                options();
            }
            if (option != null) {
                option.set(s.option());
            }
        }
        boolean choosing = now == Method.CHOOSE && !s.running() && !s.first().isEmpty();
        chooser(choosing || now == Method.CHOOSE && !s.running());
        first.visible = second.visible = choosing;
        neither.visible = choosing;
        if (choosing) {
            first.setMessage(Component.literal(Ui.fit(font, s.first(), SHOW_W - 10)));
            second.setMessage(Component.literal(Ui.fit(font, s.second(), SHOW_W - 10)));
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractBackground(g, mouseX, mouseY, a);
        Ui.window(g, leftPos, topPos, WINDOW_W, imageHeight);
        for (Slot slot : menu.slots) {
            Ui.slot(g, leftPos + slot.x, topPos + slot.y);
        }
        if (!menu.getSlot(TunerBlockEntity.BASE).hasItem()) {
            Ui.ghost(g, new ItemStack(ModItems.CHECKPOINT.get()), leftPos + TunerMenu.BASE_X, topPos + TunerMenu.BASE_Y);
        }
        if (!menu.getSlot(TunerBlockEntity.OUT).hasItem()) {
            Ui.ghost(g, new ItemStack(ModItems.CHECKPOINT.get()), leftPos + TunerMenu.OUT_X, topPos + TunerMenu.OUT_Y);
        }
        ItemStack wants = switch (shown) {
            case FINE_TUNE, ADAPTER -> new ItemStack(ModItems.DATASETS.get(Benchmark.ORDERS).get());
            case CHOOSE, JUDGE -> new ItemStack(ModItems.PREFERENCE_LOG.get());
            case MERGE, AGAINST_JUDGE -> new ItemStack(ModItems.CHECKPOINT.get());
            case DISTILL -> new ItemStack(ModItems.RELAY_CARD.get());
            case VERIFIED -> ItemStack.EMPTY;
        };
        if (!wants.isEmpty() && !menu.getSlot(TunerBlockEntity.AID).hasItem()) {
            Ui.ghost(g, wants, leftPos + TunerMenu.BASE_X, topPos + TunerMenu.AID_Y);
        }
        Ui.screen(g, leftPos + SHOW_X, topPos + SHOW_Y, SHOW_W, SHOW_H);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, title, 8, 6, Ui.LABEL, false);
        g.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, Ui.LABEL, false);
        TunerSync s = menu.sync;
        if (s == null) {
            return;
        }
        int x = SHOW_X + 3, y = SHOW_Y + 2;
        if (s.running()) {
            g.text(font, Component.translatable("residual.tuner.steps", s.steps(), s.total()), x, y, Ui.TEXT, false);
            curve(g, s, x, y + CURVE_Y, SHOW_W - 6, CURVE_H);
            int cell = 8;
            for (int i = 0; i < s.marks(); i++) {
                g.fill(x + i * cell, y + MARKS_Y, x + i * cell + cell - 2, y + MARKS_Y + 4, i < s.passed() ? Ui.GREEN : Ui.RED);
            }
            int line = y + WROTE_Y;
            for (FormattedCharSequence seq : font.split(Component.literal(s.wrote()), SHOW_W - 6)) {
                if (line + 9 > SHOW_Y + SHOW_H) {
                    break;
                }
                g.text(font, seq, x, line, Ui.TEAL, false);
                line += 10;
            }
        } else if (shown == Method.CHOOSE && !s.first().isEmpty()) {
            g.text(font, Component.literal(Ui.fit(font, s.prompt().substring(2, Math.max(2, s.prompt().indexOf('\n'))), SHOW_W - 6)), x, y, Ui.TEXT, false);
            g.text(font, Component.translatable("residual.tuner.which", s.choices()), x, y + 9, Ui.DIM, false);
        } else if (!s.problem().isEmpty()) {
            int line = y;
            for (FormattedCharSequence seq : font.split(Component.translatable(s.problem()), SHOW_W - 6)) {
                g.text(font, seq, x, line, Ui.AMBER, false);
                line += 10;
            }
        } else {
            g.text(font, Component.translatable("residual.tuner.method." + shown.id), x, y, Ui.TEXT, false);
            int line = y + 11;
            for (FormattedCharSequence seq : font.split(Component.translatable("residual.tuner.method." + shown.id + ".needs"), SHOW_W - 6)) {
                if (line + 9 > SHOW_Y + SHOW_H) {
                    break;
                }
                g.text(font, seq, x, line, Ui.DIM, false);
                line += 10;
            }
        }
    }

    private void curve(GuiGraphicsExtractor g, TunerSync s, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, Ui.GRID);
        if (s.rewards().isEmpty()) {
            return;
        }
        float low = Float.MAX_VALUE, high = -Float.MAX_VALUE;
        for (float f : s.rewards()) {
            low = Math.min(low, f);
            high = Math.max(high, f);
        }
        boolean share = shown == Method.VERIFIED || shown == Method.CHOOSE || shown == Method.JUDGE;
        if (share) {
            low = 0;
            high = 1;
        }
        float span = Math.max(high - low, 1e-3f);
        int n = s.rewards().size();
        for (int i = 0; i < n; i++) {
            int px = x + i * w / Math.max(1, n);
            int py = y + h - 1 - Math.round((s.rewards().get(i) - low) / span * (h - 1));
            g.fill(px, py, px + 1, py + 1, Ui.TEAL);
            float t = s.truths().get(i);
            if (!Float.isNaN(t)) {
                int ty = y + h - 1 - Math.round((t - low) / span * (h - 1));
                g.fill(px, ty, px + 1, ty + 1, Ui.AMBER);
            }
        }
    }

    private static String number(float v) {
        return Float.isNaN(v) ? "–" : String.format(Locale.ROOT, "%.3f", v);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        int mx = mouseX - leftPos, my = mouseY - topPos;
        List<FormattedCharSequence> lines = new ArrayList<>();
        TunerSync s = menu.sync;
        if (s != null && s.running() && Ui.inside(mx, my, SHOW_X, SHOW_Y, SHOW_W, SHOW_H)) {
            lines.addAll(font.split(Component.translatable("residual.tuner.method." + shown.id), 220));
            lines.add(Component.translatable("residual.tuner.loss", number(s.loss())).withStyle(ChatFormatting.GRAY).getVisualOrderText());
            if (!Float.isNaN(s.reward())) {
                lines.add(Component.translatable("residual.tuner.reward", number(s.reward())).withStyle(ChatFormatting.AQUA).getVisualOrderText());
            }
            if (!Float.isNaN(s.truth())) {
                lines.add(Component.translatable("residual.tuner.truth", number(s.truth())).withStyle(ChatFormatting.GOLD).getVisualOrderText());
            }
            if (!s.said().isEmpty()) {
                lines.addAll(font.split(Component.literal(s.said() + " → " + s.wrote()).withStyle(ChatFormatting.GRAY), 220));
            }
        } else if (s != null && Ui.inside(mx, my, SHOW_X, SHOW_Y, SHOW_W, SHOW_H) && !first.visible) {
            lines.addAll(font.split(Component.translatable("residual.tuner.method." + shown.id + ".does").withStyle(ChatFormatting.GRAY), 220));
        } else if (Ui.inside(mx, my, TunerMenu.BASE_X, TunerMenu.BASE_Y, 16, 16) && !menu.getSlot(TunerBlockEntity.BASE).hasItem()) {
            lines.addAll(font.split(Component.translatable("residual.tuner.base_slot").withStyle(ChatFormatting.GRAY), 200));
        } else if ((Ui.inside(mx, my, TunerMenu.BASE_X, TunerMenu.AID_Y, 16, 16) && !menu.getSlot(TunerBlockEntity.AID).hasItem()
                || Ui.inside(mx, my, TunerMenu.BASE_X, TunerMenu.AID2_Y, 16, 16) && !menu.getSlot(TunerBlockEntity.AID2).hasItem())) {
            lines.addAll(font.split(Component.translatable("residual.tuner.method." + shown.id + ".needs").withStyle(ChatFormatting.GRAY), 200));
        } else if (Ui.inside(mx, my, TunerMenu.OUT_X, TunerMenu.OUT_Y, 16, 16) && !menu.getSlot(TunerBlockEntity.OUT).hasItem()) {
            lines.addAll(font.split(Component.translatable("residual.tuner.out_slot").withStyle(ChatFormatting.GRAY), 200));
        }
        if (!lines.isEmpty() && menu.getCarried().isEmpty()) {
            g.setTooltipForNextFrame(lines, mouseX, mouseY);
        }
    }

    @Override
    public void parts(Parts out) {
        out.add("base", this, menu.getSlot(TunerBlockEntity.BASE));
        out.add("aid", this, menu.getSlot(TunerBlockEntity.AID));
        out.add("aid2", this, menu.getSlot(TunerBlockEntity.AID2));
        out.add("out", this, menu.getSlot(TunerBlockEntity.OUT));
        out.add("show", leftPos + SHOW_X, topPos + SHOW_Y, SHOW_W, SHOW_H);
    }
}
