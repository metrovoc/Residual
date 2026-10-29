package io.github.metrovoc.residual.client;

import io.github.metrovoc.residual.agent.Job;
import io.github.metrovoc.residual.menu.AgentMenu;
import io.github.metrovoc.residual.model.ModelInfo;
import io.github.metrovoc.residual.net.AgentAction;
import io.github.metrovoc.residual.net.AgentSync;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** An agent's screen: its job, what it is doing, the last thing it asked its model, and what it carries. */
public class AgentScreen extends AbstractContainerScreen<AgentMenu> {
    private final List<Button> jobs = new ArrayList<>();
    private EditBox order;
    private Button tell;

    public AgentScreen(AgentMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 248);
    }

    private AgentSync sync() {
        AgentSync s = AgentView.sync;
        return s != null && s.entity() == menu.entityId ? s : null;
    }

    private void send(AgentAction.Kind kind, int job, String said) {
        ClientPacketDistributor.sendToServer(new AgentAction(menu.entityId, kind, job, said));
    }

    @Override
    protected void init() {
        super.init();
        jobs.clear();
        for (Job j : Job.values()) {
            jobs.add(addRenderableWidget(Button.builder(Component.translatable("residual.job." + j.id), b -> send(AgentAction.Kind.JOB, j.ordinal(), ""))
                    .bounds(leftPos + 8 + j.ordinal() * 32, topPos + 18, 31, 14).tooltip(net.minecraft.client.gui.components.Tooltip.create(
                            Component.translatable("residual.job." + j.id + ".help"))).build()));
        }
        addRenderableWidget(Button.builder(Component.translatable("residual.agent.set_home"), b -> send(AgentAction.Kind.HOME, 0, ""))
                .bounds(leftPos + 122, topPos + 126, 48, 14).tooltip(net.minecraft.client.gui.components.Tooltip.create(
                        Component.translatable("residual.agent.set_home.help"))).build());
        order = addRenderableWidget(new EditBox(font, leftPos + 8, topPos + 146, 118, 14, Component.translatable("residual.agent.order")));
        order.setMaxLength(60);
        order.setHint(Component.translatable("residual.agent.order_hint"));
        tell = addRenderableWidget(Button.builder(Component.translatable("residual.agent.tell"), b -> {
            if (!order.getValue().isBlank()) {
                send(AgentAction.Kind.SAY, 0, order.getValue());
                order.setValue("");
            }
        }).bounds(leftPos + 130, topPos + 145, 40, 16).build());
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isEscape()) {
            return super.keyPressed(event);
        }
        if (order != null && order.isFocused()) {
            if (event.isConfirmation()) {
                tell.onPress(event);
                return true;
            }
            return order.keyPressed(event) || order.canConsumeInput() || super.keyPressed(event);
        }
        return super.keyPressed(event);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractBackground(g, mouseX, mouseY, a);
        Ui.window(g, leftPos, topPos, imageWidth, imageHeight);
        Ui.screen(g, leftPos + 8, topPos + 36, 160, 54);
        for (Slot slot : menu.slots) {
            Ui.slot(g, leftPos + slot.x, topPos + slot.y);
        }
        AgentSync s = sync();
        for (int i = 0; i < jobs.size(); i++) {
            jobs.get(i).active = s == null || s.job() != i;
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, title, 8, 6, Ui.LABEL, false);
        g.text(font, Component.translatable("residual.agent.carrying"), AgentMenu.CARRY_X, 94, Ui.LABEL, false);
        Component model = Component.translatable("residual.agent.model_slot");
        g.text(font, model, AgentMenu.MODEL_X + 8 - font.width(model) / 2, 94, Ui.LABEL, false);
        AgentSync s = sync();
        if (s == null) {
            g.text(font, Component.translatable("residual.status.connecting"), 12, 40, Ui.DIM, false);
            return;
        }
        int w = 152;
        Component status = Component.translatable(s.status(), s.statusArgs().toArray());
        ModelInfo info = s.model();
        String tag = info == null ? "?" : info.tag();
        boolean working = s.problem().isEmpty();
        Component modelLine = !working ? Component.translatable(s.problem(), s.problemArgs().toArray())
                : s.served() ? Component.translatable("residual.agent.model_served", tag)
                : Component.translatable("residual.agent.model_onboard", tag, info == null ? "?" : Ui.count(info.parameters()));
        int modelColor = working ? Ui.TEAL : Ui.AMBER;
        if (s.decisions() == 0 || !working) {
            // no decision worth showing: the status and the model may take the four lines above the record
            List<FormattedCharSequence> said = font.split(status, w);
            said = said.subList(0, Math.min(2, said.size()));
            List<FormattedCharSequence> modelLines = font.split(modelLine, w);
            int y = 40;
            for (FormattedCharSequence line : said) {
                g.text(font, line, 12, y, Ui.TEXT, false);
                y += 10;
            }
            for (int i = 0; i < modelLines.size() && y <= 70; i++, y += 10) {
                g.text(font, modelLines.get(i), 12, y, modelColor, false);
            }
        } else {
            g.text(font, fit(status, w), 12, 40, Ui.TEXT, false);
            g.text(font, fit(modelLine, w), 12, 50, modelColor, false);
            String asked = s.prompt().replace("\n", "⏎");
            Component label = Component.translatable("residual.agent.asked");
            g.text(font, label, 12, 60, Ui.DIM, false);
            int lw = font.width(label) + 3;
            g.text(font, tail(asked, w - lw), 12 + lw, 60, Ui.TEXT, false);
            label = Component.translatable("residual.agent.answered");
            g.text(font, label, 12, 70, Ui.DIM, false);
            lw = font.width(label) + 3;
            String answer = s.answer().replace("\n", "⏎") + (Float.isNaN(s.confidence()) ? ""
                    : String.format(Locale.ROOT, "  · %.0f%%", 100 * s.confidence()));
            g.text(font, Ui.fit(font, answer, w - lw), 12 + lw, 70, Ui.TEXT, false);
        }
        Component record = s.decisions() == 0 ? Component.translatable("residual.agent.record_none")
                : Component.translatable("residual.agent.record", s.right(), s.decisions());
        if (s.marks() > 0) {
            record = s.marks() == 1 ? Component.translatable("residual.agent.with_mark", record)
                    : Component.translatable("residual.agent.with_marks", record, s.marks());
        }
        g.text(font, fit(record, w), 12, 80, Ui.DIM, false);
        // whatever had to be cut short, in full
        if (Ui.inside(mouseX - leftPos, mouseY - topPos, 8, 36, 160, 54) && (font.width(status) > w || font.width(modelLine) > w)) {
            List<FormattedCharSequence> lines = new ArrayList<>();
            for (Component c : List.of(status, modelLine)) {
                lines.addAll(font.split(c, 220));
            }
            g.setTooltipForNextFrame(lines, mouseX, mouseY);
        }
    }

    private String fit(Component c, int w) {
        return Ui.fit(font, c.getString(), w);
    }

    /** The end of {@code text}, cut from the front to fit: the latest part of a prompt matters most. */
    private String tail(String text, int w) {
        if (font.width(text) <= w) {
            return text;
        }
        String s = text;
        while (!s.isEmpty() && font.width("…" + s) > w) {
            s = s.substring(1);
        }
        return "…" + s;
    }
}
