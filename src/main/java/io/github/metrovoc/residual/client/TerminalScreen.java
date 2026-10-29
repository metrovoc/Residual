package io.github.metrovoc.residual.client;

import io.github.metrovoc.residual.net.TerminalAnswer;
import io.github.metrovoc.residual.net.TerminalAsk;
import io.github.metrovoc.residual.registry.ModComponents;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Writes with the model a Model Server serves, from anywhere its relay network reaches. */
public class TerminalScreen extends Screen {
    private static final int W = 260;
    private static final int H = 190;
    private final InteractionHand hand;
    private EditBox prompt;
    private Button write;

    public TerminalScreen(InteractionHand hand) {
        super(Component.translatable("item.residual.terminal"));
        this.hand = hand;
    }

    @Override
    protected void init() {
        int x = (width - W) / 2, y = (height - H) / 2;
        prompt = addRenderableWidget(new EditBox(font, x + 8, y + 32, W - 16, 16, Component.translatable("residual.terminal.prompt")));
        prompt.setMaxLength(200);
        prompt.setHint(Component.translatable("residual.terminal.hint"));
        setInitialFocus(prompt);
        write = addRenderableWidget(Button.builder(Component.translatable("residual.terminal.write"), b -> {
            AgentView.asking = true;
            ClientPacketDistributor.sendToServer(new TerminalAsk(hand == InteractionHand.OFF_HAND, prompt.getValue()));
        }).bounds(x + 8, y + 52, 70, 16).build());
        addRenderableWidget(Button.builder(Component.translatable("residual.terminal.clear"), b -> {
            prompt.setValue("");
            AgentView.answer = null;
        }).bounds(x + 82, y + 52, 50, 16).build());
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isConfirmation() && prompt.isFocused() && write.active) {
            write.onPress(event);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        int x = (width - W) / 2, y = (height - H) / 2;
        Ui.window(g, x, y, W, H);
        g.text(font, title, x + 8, y + 7, Ui.LABEL, false);
        ItemStack held = minecraft.player.getItemInHand(hand);
        GlobalPos link = held.get(ModComponents.LINK.get());
        Component where = link == null ? Component.translatable("residual.link.none") : Component.translatable("residual.link.to", link.pos().toShortString());
        g.text(font, Ui.fit(font, where.getString(), W - 16), x + 8, y + 19, Ui.LABEL_DIM, false);
        write.active = !AgentView.asking;
        Ui.screen(g, x + 8, y + 74, W - 16, H - 82);
        TerminalAnswer answer = AgentView.answer;
        if (AgentView.asking) {
            g.text(font, Component.translatable("residual.terminal.thinking"), x + 12, y + 78, Ui.DIM, false);
        } else if (answer != null && !answer.problem().isEmpty()) {
            for (FormattedCharSequence line : font.split(Component.translatable(answer.problem(), answer.args().toArray()), W - 24)) {
                g.text(font, line, x + 12, y + 78, Ui.AMBER, false);
                y += 10;
            }
        } else if (answer != null) {
            Component text = Component.literal(answer.prompt()).withColor(Ui.DIM).append(Component.literal(answer.text()).withColor(Ui.TEXT));
            List<FormattedCharSequence> lines = font.split(text, W - 24);
            for (int i = 0; i < Math.min(lines.size(), 10); i++) {
                g.text(font, lines.get(i), x + 12, y + 78 + i * 10, Ui.TEXT, false);
            }
        }
        super.extractRenderState(g, mouseX, mouseY, a);
    }

    @Override
    public void removed() {
        super.removed();
        AgentView.asking = false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
