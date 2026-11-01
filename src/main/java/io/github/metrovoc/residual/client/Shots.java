package io.github.metrovoc.residual.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import guideme.color.ConstantColor;
import guideme.compiler.PageCompiler;
import guideme.compiler.tags.BlockTagCompiler;
import guideme.document.LytRect;
import guideme.document.block.LytBlock;
import guideme.document.block.LytBlockContainer;
import guideme.document.block.LytList;
import guideme.document.block.LytListItem;
import guideme.document.block.LytParagraph;
import guideme.document.block.LytVBox;
import guideme.document.interaction.ContentTooltip;
import guideme.document.interaction.GuideTooltip;
import guideme.document.interaction.InteractiveElement;
import guideme.layout.LayoutContext;
import guideme.libs.mdast.mdx.model.MdxJsxElementFields;
import guideme.libs.mdast.model.MdAstAnyContent;
import guideme.render.GuidePageTexture;
import guideme.render.RenderContext;
import guideme.ui.GuideUiHost;
import io.github.metrovoc.residual.Residual;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/**
 * A screen as a rehearsal shot it, with its parts marked: {@code <residual:Shot name="core_train" alt="...">} holding
 * {@code <residual:Part name="tab:train">what it is for</residual:Part>}s. {@code shots/core_train.png} is the picture and
 * {@code shots/core_train.json} says where the parts are ({@code tools/screens.py} makes the two from a rehearsal's shot). The
 * parts below it are numbered as they are marked on it, and pointing at a mark says what it is. A shot not taken yet leaves
 * the list. The website shows the same ({@code src/website/live.js}).
 */
final class Shots {
    private Shots() {
    }

    /** A part's place in the shot, in the screen's own pixels, or null when the shot has no such part. */
    private record Mark(int number, String name, int[] rect, LytListItem item) {
    }

    static final class Tag extends BlockTagCompiler {
        @Override
        public Set<String> getTagNames() {
            return Set.of("residual:Shot");
        }

        @Override
        protected void compile(PageCompiler compiler, LytBlockContainer parent, MdxJsxElementFields el) {
            String name = el.getAttributeString("name", "");
            Identifier png = Residual.id("shots/" + name + ".png");
            byte[] picture = compiler.loadAsset(png), where = compiler.loadAsset(Residual.id("shots/" + name + ".json"));
            JsonObject sidecar = where == null ? null : JsonParser.parseString(new String(where, StandardCharsets.UTF_8)).getAsJsonObject();
            LytList legend = new LytList(true, 1);
            List<Mark> marks = new ArrayList<>();
            for (MdAstAnyContent child : el.children()) {
                if (child instanceof MdxJsxElementFields part && "residual:Part".equals(part.name())) {
                    LytListItem item = new LytListItem();
                    compiler.compileBlockContext(part.children(), item);
                    // a list's items lie close, as the compiler lays out those of a Markdown list
                    if (!item.getChildren().isEmpty() && item.getChildren().getFirst() instanceof LytBlock first) {
                        first.setMarginTop(0);
                        first.setMarginBottom(0);
                    }
                    legend.append(item);
                    String partName = part.getAttributeString("name", "");
                    int[] rect = sidecar == null ? null : rect(sidecar, partName);
                    marks.add(new Mark(marks.size() + 1, partName, rect, item));
                }
            }
            LytVBox box = new LytVBox();
            if (picture != null && sidecar != null) {
                box.append(new Picture(name, GuidePageTexture.load(png, picture), sidecar.get("width").getAsInt(), sidecar.get("height").getAsInt(), marks));
            }
            box.append(legend);
            parent.append(box);
        }

        private static int[] rect(JsonObject sidecar, String part) {
            if (!(sidecar.getAsJsonObject("parts").get(part) instanceof JsonArray r)) {
                return null;
            }
            return new int[]{r.get(0).getAsInt(), r.get(1).getAsInt(), r.get(2).getAsInt(), r.get(3).getAsInt()};
        }
    }

    /** The shot with a numbered mark at each part; a part pointed at is outlined, and says what it is. */
    private static final class Picture extends LytBlock implements InteractiveElement, Parts.Drawn {
        private static final int BADGE = 9;
        private final Font font = Minecraft.getInstance().font;
        private final String name;
        private final GuidePageTexture texture;
        private final int width, height;
        private final List<Mark> marks;
        private Mark hovered;

        Picture(String name, GuidePageTexture texture, int width, int height, List<Mark> marks) {
            this.name = name;
            this.texture = texture;
            this.width = width;
            this.height = height;
            this.marks = marks;
            setMarginTop(2);
            setMarginBottom(4);
        }

        @Override
        protected LytRect computeLayout(LayoutContext context, int x, int y, int availableWidth) {
            int w = Math.min(width, availableWidth);
            return new LytRect(x, y, w, w * height / width);
        }

        @Override
        protected void onLayoutMoved(int deltaX, int deltaY) {
        }

        private float scale() {
            return bounds.width() / (float) width;
        }

        private LytRect place(int[] rect) {
            float s = scale();
            return new LytRect(bounds.x() + Math.round(rect[0] * s), bounds.y() + Math.round(rect[1] * s), Math.max(1, Math.round(rect[2] * s)), Math.max(1, Math.round(rect[3] * s)));
        }

        @Override
        public void render(RenderContext context) {
            context.fillTexturedRect(bounds, texture);
            GuiGraphicsExtractor g = context.guiGraphics();
            g.nextStratum();
            for (Mark mark : marks) {
                if (mark.rect() == null) {
                    continue;
                }
                LytRect at = place(mark.rect());
                if (mark == hovered) {
                    g.outline(at.x() - 1, at.y() - 1, at.width() + 2, at.height() + 2, Ui.TEAL);
                }
                String number = Integer.toString(mark.number());
                int w = Math.max(BADGE, font.width(number) + 3);
                g.fill(at.x() - 1, at.y() - 1, at.x() - 1 + w, at.y() - 1 + BADGE, mark == hovered ? Ui.TEAL : Ui.SCREEN);
                g.text(font, number, at.x() + (w - font.width(number)) / 2, at.y(), mark == hovered ? Ui.SCREEN : Ui.AMBER, false);
            }
        }

        private Mark at(int x, int y) {
            Mark best = null;
            for (Mark mark : marks) {
                if (mark.rect() != null && place(mark.rect()).contains(x, y)
                        && (best == null || mark.rect()[2] * mark.rect()[3] <= best.rect()[2] * best.rect()[3])) {
                    best = mark;
                }
            }
            return best;
        }

        @Override
        public boolean mouseMoved(GuideUiHost screen, int x, int y) {
            hovered = at(x, y);
            return false;
        }

        @Override
        public void onMouseLeave() {
            hovered = null;
        }

        @Override
        public Optional<GuideTooltip> getTooltip(float x, float y) {
            Mark mark = at((int) x, (int) y);
            if (mark == null) {
                return Optional.empty();
            }
            LytParagraph text = new LytParagraph();
            text.appendText(mark.item().getTextContent());
            return Optional.of(new ContentTooltip(text));
        }

        @Override
        public void parts(Parts out) {
            for (Mark mark : marks) {
                if (mark.rect() != null) {
                    LytRect at = place(mark.rect());
                    out.add("shot:" + name + ":" + mark.name(), at.x(), at.y(), at.width(), at.height());
                }
            }
        }
    }
}
