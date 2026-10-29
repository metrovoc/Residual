package io.github.metrovoc.residual.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** A button that shows an item instead of words; its name is still read out, and its tooltip says what it does. */
public class IconButton extends AbstractButton {
    private final ItemStack icon;
    private final Runnable onPress;

    public IconButton(int x, int y, int width, int height, ItemStack icon, Component name, Runnable onPress) {
        super(x, y, width, height, name);
        this.icon = icon;
        this.onPress = onPress;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        onPress.run();
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        extractDefaultSprite(g);
        g.item(icon, getX() + (getWidth() - 16) / 2, getY() + (getHeight() - 16) / 2);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
