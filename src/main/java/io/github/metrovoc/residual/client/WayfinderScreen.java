package io.github.metrovoc.residual.client;

import io.github.metrovoc.residual.net.FindRuin;
import io.github.metrovoc.residual.registry.ModComponents;
import io.github.metrovoc.residual.world.RuinKinds;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Chooses what a Wayfinder looks for: the kinds of ruin its holder has read of, one a line. */
public class WayfinderScreen extends Screen {
    private static final int W = 176;
    private static final int ROW = 22;

    private final InteractionHand hand;
    private List<RuinKinds.Kind> kinds = List.of();

    public WayfinderScreen(InteractionHand hand) {
        super(Component.translatable("item.residual.wayfinder"));
        this.hand = hand;
    }

    private int top() {
        return (height - (34 + kinds.size() * ROW + 16)) / 2;
    }

    @Override
    protected void init() {
        kinds = RuinKinds.known(minecraft.player);
        String tuned = minecraft.player.getItemInHand(hand).get(ModComponents.WAYFINDER_KIND.get());
        int x = (width - W) / 2, y = top() + 22;
        for (RuinKinds.Kind k : kinds) {
            Component name = Component.translatable("residual.ruin." + k.name());
            Button b = Button.builder(k.name().equals(tuned) ? Component.literal("› ").append(name) : name, button -> {
                ClientPacketDistributor.sendToServer(new FindRuin(k.name(), hand == InteractionHand.MAIN_HAND));
                onClose();
            }).bounds(x + 8, y, W - 16, 18).build();
            addRenderableWidget(b);
            y += ROW;
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        int x = (width - W) / 2, y = top(), h = 34 + kinds.size() * ROW + 16;
        Ui.window(g, x, y, W, h);
        g.text(font, title, x + 8, y + 7, Ui.LABEL, false);
        if (kinds.size() < RuinKinds.all().size()) {
            g.text(font, Ui.fit(font, Component.translatable("residual.wayfinder.more").getString(), W - 16), x + 8, y + h - 14, Ui.LABEL_DIM, false);
        }
        super.extractRenderState(g, mouseX, mouseY, a);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
