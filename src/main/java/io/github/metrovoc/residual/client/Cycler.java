package io.github.metrovoc.residual.client;

import java.util.List;
import java.util.function.IntConsumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;

/** A button showing "label: value" that steps through its values: left click forward, right click (or shift) back. */
public class Cycler extends AbstractButton {
    private final Component label;
    private final List<String> values;
    private final IntConsumer onChange;
    private int index;

    public Cycler(int x, int y, int width, Component label, List<String> values, int index, IntConsumer onChange) {
        super(x, y, width, 16, Component.empty());
        this.label = label;
        this.values = values;
        this.onChange = onChange;
        set(index);
    }

    /** Shows a value without reporting a change (for updates coming from the server). */
    public void set(int i) {
        index = Math.floorMod(i, values.size());
        setMessage(Component.translatable("residual.cycler", label, values.get(index)));
    }

    public int index() {
        return index;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        boolean back = input.hasShiftDown() || input instanceof MouseButtonEvent m && m.button() == 1;
        set(index + (back ? -1 : 1));
        onChange.accept(index);
    }

    @Override
    protected boolean isValidClickButton(MouseButtonInfo info) {
        return info.button() == 0 || info.button() == 1;
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        extractDefaultSprite(g);
        extractDefaultLabel(g.textRendererForWidget(this, GuiGraphicsExtractor.HoveredTextEffects.NONE));
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
