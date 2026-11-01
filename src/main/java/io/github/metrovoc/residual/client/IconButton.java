package io.github.metrovoc.residual.client;

import io.github.metrovoc.residual.Residual;
import java.util.List;
import java.util.function.IntConsumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * A button that shows an item or an icon instead of words; its name is still read out, and its tooltip says what it
 * does. Icon buttons sit in a screen's toolbar, as AE2's do, and may be settings that step through values, left click
 * forward and right click (or shift) back, with an icon for each value or one for all; a setting's label reads
 * "name: value", so scripts and narration find it by name.
 */
public class IconButton extends AbstractButton {
    /** A toolbar button: an icon of 16 in a frame of 18. */
    public static final int SIZE = 18;

    @Nullable
    private final ItemStack item;
    private final Component name;
    private final List<String> icons;
    private final List<String> values;
    @Nullable
    private final Component hint;
    private final IntConsumer onChange;
    private int index;

    public IconButton(int x, int y, int width, int height, ItemStack icon, Component name, Runnable onPress) {
        super(x, y, width, height, name);
        this.item = icon;
        this.name = name;
        this.icons = List.of();
        this.values = List.of();
        this.hint = null;
        this.onChange = i -> onPress.run();
    }

    private IconButton(int x, int y, Component name, List<String> icons, List<String> values, int index, Component hint,
                       IntConsumer onChange) {
        super(x, y, SIZE, SIZE, Component.empty());
        this.item = null;
        this.name = name;
        this.icons = icons;
        this.values = values;
        this.hint = hint;
        this.onChange = onChange;
        set(index);
    }

    /** A toolbar button that does one thing. */
    public static IconButton action(int x, int y, String icon, Component name, Component hint, Runnable press) {
        return new IconButton(x, y, name, List.of(icon), List.of(), 0, hint, i -> press.run());
    }

    /** A toolbar setting with {@code values}, one icon for all of them or one each. */
    public static IconButton setting(int x, int y, List<String> icons, Component name, List<String> values, int index, Component hint,
                                     IntConsumer change) {
        return new IconButton(x, y, name, icons, values, index, hint, change);
    }

    /** Shows a value without reporting a change (for updates coming from the server). */
    public void set(int i) {
        index = values.isEmpty() ? 0 : Math.floorMod(i, values.size());
        Component label = values.isEmpty() ? name : Component.translatable("residual.cycler", name, shown(values.get(index)));
        setMessage(label);
        if (hint != null) {
            Component text = Component.empty().append(label).append("\n").append(hint.copy().withStyle(ChatFormatting.GRAY));
            if (!values.isEmpty()) {
                text = text.copy().append("\n").append(Component.translatable("residual.icon.cycle").withStyle(ChatFormatting.GRAY));
            }
            setTooltip(Tooltip.create(text));
        }
    }

    /** A value that is a translation key reads as its text. */
    private static Component shown(String value) {
        return value.startsWith("residual.") ? Component.translatable(value) : Component.literal(value);
    }

    @Override
    public void onPress(InputWithModifiers input) {
        if (values.isEmpty()) {
            onChange.accept(0);
            return;
        }
        boolean back = input.hasShiftDown() || input instanceof MouseButtonEvent m && m.button() == 1;
        set(index + (back ? -1 : 1));
        onChange.accept(index);
    }

    @Override
    protected boolean isValidClickButton(MouseButtonInfo info) {
        return info.button() == 0 || info.button() == 1 && !values.isEmpty();
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        extractDefaultSprite(g);
        if (item != null) {
            g.item(item, getX() + (getWidth() - 16) / 2, getY() + (getHeight() - 16) / 2);
            return;
        }
        String icon = icons.get(Math.min(index, icons.size() - 1));
        g.blitSprite(RenderPipelines.GUI_TEXTURED, Residual.id("icon/" + icon), getX() + 1, getY() + 1, 16, 16, active ? 1f : 0.4f);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
