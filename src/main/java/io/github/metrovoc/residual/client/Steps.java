package io.github.metrovoc.residual.client;

import guideme.compiler.PageCompiler;
import guideme.compiler.tags.BlockTagCompiler;
import guideme.document.LytRect;
import guideme.document.block.LytBlock;
import guideme.document.block.LytBlockContainer;
import guideme.document.block.LytBox;
import guideme.document.block.LytNode;
import guideme.document.block.LytVBox;
import guideme.document.interaction.InteractiveElement;
import guideme.layout.LayoutContext;
import guideme.libs.mdast.mdx.model.MdxJsxElementFields;
import guideme.libs.mdast.model.MdAstAnyContent;
import guideme.render.RenderContext;
import guideme.ui.GuideUiHost;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonInfo;
import org.jspecify.annotations.Nullable;

/**
 * A step-through on a guide page: {@code <residual:Steps>} holding {@code <residual:Step>}s, each any page content, a scene,
 * a shot, a caption, shown one at a time behind back and on buttons. The block is as tall as its tallest step, so the buttons
 * and what follows stay where they are. The website shows the same steps ({@code src/website/live.js}).
 */
final class Steps {
    private Steps() {
    }

    static final class Tag extends BlockTagCompiler {
        @Override
        public Set<String> getTagNames() {
            return Set.of("residual:Steps");
        }

        @Override
        protected void compile(PageCompiler compiler, LytBlockContainer parent, MdxJsxElementFields el) {
            Stepper stepper = new Stepper();
            for (MdAstAnyContent child : el.children()) {
                if (child instanceof MdxJsxElementFields step && "residual:Step".equals(step.name())) {
                    LytVBox page = new LytVBox();
                    compiler.compileBlockContext(step.children(), page);
                    stepper.append(page);
                }
            }
            if (stepper.getChildren().isEmpty()) {
                parent.appendError(compiler, "No residual:Step in residual:Steps", el);
                return;
            }
            parent.append(stepper);
        }
    }

    private static final class Stepper extends LytBox implements InteractiveElement, Parts.Drawn {
        private static final int BAR = 14, BUTTON = 22, GAP = 3;
        private final Font font = Minecraft.getInstance().font;
        private int current;
        /** The button the mouse is on: -1 back, 1 on, 0 neither. */
        private int hovered;

        Stepper() {
            setMarginTop(4);
            setMarginBottom(6);
        }

        @Override
        protected LytRect computeBoxLayout(LayoutContext context, int x, int y, int availableWidth) {
            int tallest = 0;
            for (LytBlock page : children) {
                tallest = Math.max(tallest, page.layout(context, x, y + BAR + GAP, availableWidth).height());
            }
            return new LytRect(x, y, availableWidth, BAR + GAP + tallest);
        }

        /** Only the step shown can be hovered and clicked; the export and the ticks still reach them all. */
        @Override
        public @Nullable LytNode pickNode(int x, int y) {
            if (!bounds.contains(x, y)) {
                return null;
            }
            LytNode node = children.get(current).pickNode(x, y);
            return node != null ? node : this;
        }

        @Override
        public void render(RenderContext context) {
            GuiGraphicsExtractor g = context.guiGraphics();
            int x = bounds.x(), y = bounds.y(), w = bounds.width(), n = children.size();
            g.fill(x, y, x + w, y + BAR, Ui.SCREEN);
            button(g, x, "‹", current > 0, hovered < 0);
            button(g, x + w - BUTTON, "›", current < n - 1, hovered > 0);
            // a mark for each step, lit up to the one shown
            String where = (current + 1) + " / " + n;
            g.text(font, where, x + BUTTON + 5, y + 3, Ui.TEXT, false);
            int marks = x + BUTTON + 5 + font.width(where) + 6, room = x + w - BUTTON - 5 - marks, each = Math.min(16, room / n);
            for (int i = 0; i < n; i++) {
                g.fill(marks + i * each, y + 6, marks + (i + 1) * each - 2, y + 8, i <= current ? Ui.TEAL : Ui.GRID);
            }
            g.nextStratum();
            children.get(current).render(context);
        }

        private void button(GuiGraphicsExtractor g, int x, String mark, boolean live, boolean hover) {
            int y = bounds.y();
            g.fill(x, y, x + BUTTON, y + BAR, live ? hover ? Ui.TEAL : Ui.PANEL : Ui.GRID);
            g.text(font, mark, x + (BUTTON - font.width(mark)) / 2, y + 3, live ? Ui.LABEL : Ui.DIM, false);
        }

        private int buttonAt(int x, int y) {
            if (y < bounds.y() || y >= bounds.y() + BAR) {
                return 0;
            }
            return x < bounds.x() + BUTTON ? -1 : x >= bounds.right() - BUTTON ? 1 : 0;
        }

        @Override
        public boolean mouseClicked(GuideUiHost screen, int x, int y, MouseButtonInfo button, boolean doubleClick) {
            int pressed = buttonAt(x, y);
            if (pressed == 0) {
                return false;
            }
            current = Math.max(0, Math.min(children.size() - 1, current + pressed));
            return true;
        }

        @Override
        public boolean mouseMoved(GuideUiHost screen, int x, int y) {
            hovered = buttonAt(x, y);
            return false;
        }

        @Override
        public void onMouseLeave() {
            hovered = 0;
        }

        /** {@code steps:back}, {@code steps:on}, and {@code steps:2} is there while the second step shows. */
        @Override
        public void parts(Parts out) {
            out.add("steps:back", bounds.x(), bounds.y(), BUTTON, BAR);
            out.add("steps:on", bounds.right() - BUTTON, bounds.y(), BUTTON, BAR);
            out.add("steps:" + (current + 1), bounds.x(), bounds.y(), bounds.width(), BAR);
        }
    }
}
