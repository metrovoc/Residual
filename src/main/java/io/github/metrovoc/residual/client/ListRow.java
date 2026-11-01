package io.github.metrovoc.residual.client;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * A line of a list on a dark display: an icon, its label and what it is doing, lit under the mouse. It is a button, so that
 * it is read out and scripts find it by its label; one with nothing to do when clicked is made inactive.
 */
final class ListRow extends AbstractButton {
    /** Where the detail starts, from the row's left. */
    private final int detailX;
    @Nullable
    private final ItemStack icon;
    private final Component detail;
    private final int labelColor, detailColor;
    private final Runnable press;
    private boolean selected;

    ListRow(int x, int y, int width, int height, @Nullable ItemStack icon, Component label, int labelColor, Component detail, int detailX,
            int detailColor, @Nullable Runnable press) {
        super(x, y, width, height, label);
        this.icon = icon;
        this.labelColor = labelColor;
        this.detail = detail;
        this.detailX = detailX;
        this.detailColor = detailColor;
        this.press = press;
        active = press != null;
    }

    /** A row with only a label. */
    ListRow(int x, int y, int width, int height, Component label, int labelColor, @Nullable Runnable press) {
        this(x, y, width, height, null, label, labelColor, Component.empty(), 0, Ui.DIM, press);
    }

    ListRow selected(boolean selected) {
        this.selected = selected;
        return this;
    }

    ListRow tooltip(List<Component> lines) {
        Component text = Component.empty();
        for (int i = 0; i < lines.size(); i++) {
            text = i == 0 ? lines.get(i) : text.copy().append(CommonComponents.NEW_LINE).append(lines.get(i));
        }
        setTooltip(Tooltip.create(text));
        return this;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        if (press != null) {
            press.run();
        }
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        Font font = Minecraft.getInstance().font;
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        if (selected || active && isHovered()) {
            g.fill(x, y, x + w, y + h, Ui.SELECTION);
        }
        int textX = x + 4, textY = y + (h - 8) / 2;
        if (icon != null) {
            g.item(icon, x + 2, y + (h - 16) / 2);
            textX = x + 22;
        }
        int labelEnd = detailX > 0 ? x + detailX - 4 : x + w - 4;
        g.text(font, Ui.fit(font, getMessage().getString(), labelEnd - textX), textX, textY, labelColor, false);
        if (detailX > 0) {
            g.text(font, Ui.fit(font, detail.getString(), w - detailX - 4), x + detailX, textY, detailColor, false);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
