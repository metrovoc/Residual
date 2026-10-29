package io.github.metrovoc.residual.client;

import io.github.metrovoc.residual.agent.Program;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

/** The pieces of programs an agent's screen lays out beside its Program tab, to click together instead of typing. */
final class Palette {
    /** A piece: what its chip says, what it adds to a program and how, and what it does, as a translation key. */
    record Piece(String label, String text, Program.Join join, String help) {
    }

    /** Pieces of one kind under a heading, their chips written in one colour. */
    record Group(String title, int color, List<Piece> pieces) {
    }

    static final List<Group> GROUPS = List.of(steps(), blocks(), checks());

    private Palette() {
    }

    private static Group steps() {
        List<Piece> pieces = new ArrayList<>();
        for (Program.Verb v : Program.Verb.values()) {
            // a number where one reads plainly; a thing is named by clicking the program with it
            String rest = switch (v.arg) {
                case NONE, THING_OR_ALL -> "";
                case MARK -> " 1";
                case THING, THING_AT_MARK -> " ";
                case SECONDS -> " 5";
                case WORDS -> " hello";
            };
            pieces.add(new Piece(v.word, v.word + rest, Program.Join.STEP, "residual.palette." + v.word));
        }
        return new Group("residual.palette.steps", Ui.TEXT, pieces);
    }

    private static Group blocks() {
        return new Group("residual.palette.blocks", Ui.TEAL, List.of(
                new Piece("repeat (…)", "repeat (", Program.Join.STEP, "residual.palette.repeat"),
                new Piece("repeat 3 (…)", "repeat 3 (", Program.Join.STEP, "residual.palette.repeat_times"),
                new Piece("if … (…)", "if ", Program.Join.STEP, "residual.palette.if"),
                new Piece("else (…)", " else (", Program.Join.ON, "residual.palette.else"),
                new Piece("until … (…)", "until ", Program.Join.STEP, "residual.palette.until"),
                new Piece("wait until …", "wait until ", Program.Join.STEP, "residual.palette.wait_until"),
                new Piece(")", ")", Program.Join.ON, "residual.palette.close")));
    }

    private static Group checks() {
        List<Piece> pieces = new ArrayList<>();
        for (Program.Sense s : Program.Sense.values()) {
            String rest = switch (s.arg) {
                case MARK -> " 1";
                case NONE -> "";
                default -> " ";
            };
            pieces.add(new Piece(s.word, s.word + rest, Program.Join.WORD, "residual.palette." + s.word));
        }
        pieces.add(new Piece("not", "not ", Program.Join.WORD, "residual.palette.not"));
        return new Group("residual.palette.checks", Ui.AMBER, pieces);
    }

    /** A piece on a dark chip, outlined in its colour while pointed at. */
    static final class Chip extends AbstractButton {
        static final int HEIGHT = 12;
        private final int color;
        private final Runnable onPress;

        Chip(int x, int y, Piece piece, int color, Runnable onPress) {
            super(x, y, Minecraft.getInstance().font.width(piece.label()) + 6, HEIGHT, Component.literal(piece.label()));
            this.color = color;
            this.onPress = onPress;
            setTooltip(Tooltip.create(Component.translatable(piece.help())));
        }

        @Override
        public void onPress(InputWithModifiers input) {
            onPress.run();
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
            boolean lit = isHoveredOrFocused();
            g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), lit ? Ui.SELECTION : Ui.SCREEN);
            if (lit) {
                g.outline(getX(), getY(), getWidth(), getHeight(), color);
            }
            g.text(Minecraft.getInstance().font, getMessage(), getX() + 3, getY() + 2, color, false);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }
}
