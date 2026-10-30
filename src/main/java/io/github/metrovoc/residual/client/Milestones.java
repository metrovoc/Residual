package io.github.metrovoc.residual.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import guideme.compiler.PageCompiler;
import guideme.compiler.tags.BlockTagCompiler;
import guideme.document.LytRect;
import guideme.document.block.LytBlock;
import guideme.document.block.LytBlockContainer;
import guideme.document.block.LytParagraph;
import guideme.document.flow.InlineBlockAlignment;
import guideme.document.flow.LytFlowBreak;
import guideme.document.flow.LytFlowInlineBlock;
import guideme.document.flow.LytFlowSpan;
import guideme.document.interaction.GuideTooltip;
import guideme.document.interaction.InteractiveElement;
import guideme.document.interaction.TextTooltip;
import guideme.layout.LayoutContext;
import guideme.libs.mdast.mdx.model.MdxJsxElementFields;
import guideme.libs.mdast.model.MdAstAnyContent;
import guideme.libs.mdast.model.MdAstParagraph;
import guideme.render.RenderContext;
import guideme.siteexport.ExportPostProcessor;
import guideme.siteexport.ExportableResourceProvider;
import guideme.siteexport.ResourceExporter;
import guideme.style.TextStyle;
import io.github.metrovoc.residual.Residual;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.advancements.AdvancementWidgetType;
import net.minecraft.client.multiplayer.ClientAdvancements;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * Advancements on guide pages: {@code <residual:Milestone id="agent/made">how to</residual:Milestone>} shows one as a
 * paragraph, its icon in the frame the advancement screen gives it, lit once it is made, then its title, what it asks
 * and how to go about it.
 */
final class Milestones {
    /** What the client has heard of each advancement's progress, which vanilla keeps for the advancement screen. */
    private static final Field PROGRESS;

    static {
        try {
            PROGRESS = ClientAdvancements.class.getDeclaredField("progress");
            PROGRESS.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException(e);
        }
    }

    private Milestones() {
    }

    /** Whether the player has made the advancement. A made one is always among those the server tells the client of. */
    @SuppressWarnings("unchecked")
    static boolean made(Identifier id) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        AdvancementHolder holder = connection == null ? null : connection.getAdvancements().get(id);
        if (holder == null) {
            return false;
        }
        try {
            AdvancementProgress progress = ((Map<?, AdvancementProgress>) PROGRESS.get(connection.getAdvancements())).get(holder);
            return progress != null && progress.isDone();
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * How the advancement looks, from the mod's own data: a client hears only of the advancements near those it has
     * made, and a guide exported on startup has no world at all.
     */
    private static @Nullable DisplayInfo display(Identifier id) {
        try (InputStream in = Milestones.class.getResourceAsStream(
                "/data/%s/advancement/%s.json".formatted(id.getNamespace(), id.getPath()))) {
            if (in == null) {
                return null;
            }
            JsonElement display = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject().get("display");
            return display == null ? null : DisplayInfo.CODEC.parse(JsonOps.INSTANCE, display).getOrThrow();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** What the advancement asks, as the sentence its milestone opens with. */
    private static String sentence(DisplayInfo display) {
        return Component.translatable("residual.milestone.description", display.getDescription()).getString();
    }

    static final class Tag extends BlockTagCompiler {
        @Override
        public Set<String> getTagNames() {
            return Set.of("residual:Milestone");
        }

        @Override
        protected void compile(PageCompiler compiler, LytBlockContainer parent, MdxJsxElementFields el) {
            Identifier id = Residual.id(el.getAttributeString("id", ""));
            DisplayInfo display = display(id);
            if (display == null) {
                parent.appendError(compiler, "No advancement " + id, el);
                return;
            }
            LytParagraph paragraph = new LytParagraph();
            LytFlowInlineBlock icon = LytFlowInlineBlock.of(new Icon(id, display));
            icon.setAlignment(InlineBlockAlignment.FLOAT_LEFT);
            paragraph.append(icon);
            LytFlowSpan title = new LytFlowSpan();
            title.setStyle(TextStyle.builder().bold(true).build());
            title.appendText(display.getTitle().getString());
            paragraph.append(title);
            paragraph.appendBreak();
            paragraph.appendText(sentence(display));
            for (MdAstAnyContent child : el.children()) {
                if (child instanceof MdAstParagraph text) {
                    compiler.compileFlowContext(text, paragraph);
                } else {
                    compiler.compileFlowContext(List.of(child), paragraph);
                }
            }
            // the next milestone starts below this one's icon
            LytFlowBreak clear = new LytFlowBreak();
            clear.setClearLeft(true);
            paragraph.append(clear);
            parent.append(paragraph);
        }
    }

    /**
     * Milestones on the website made from a guide export, where nothing is made yet: each becomes a paragraph of its
     * icon, title, what it asks and how to go about it.
     */
    static final class Web implements ExportPostProcessor {
        @Override
        public void postProcess(JsonObject root) {
            for (JsonElement page : root.getAsJsonObject("pages").asMap().values()) {
                rewrite(page.getAsJsonObject().getAsJsonObject("astRoot"));
            }
        }

        private static void rewrite(JsonObject node) {
            if (!(node.get("children") instanceof JsonArray children)) {
                return;
            }
            JsonArray rewritten = new JsonArray();
            for (JsonElement element : children) {
                JsonObject child = element.getAsJsonObject();
                if (!(child.get("name") instanceof JsonPrimitive name && name.getAsString().equals("residual:Milestone"))) {
                    rewrite(child);
                    rewritten.add(child);
                } else if (child.get("type").getAsString().equals("mdxJsxTextElement")) {
                    // written inline, so already in a paragraph
                    rewritten.addAll(content(child));
                } else {
                    JsonObject paragraph = node("paragraph");
                    paragraph.add("children", content(child));
                    rewritten.add(paragraph);
                }
            }
            node.add("children", rewritten);
        }

        private static JsonArray content(JsonObject milestone) {
            String path = "";
            for (JsonElement attribute : milestone.getAsJsonArray("attributes")) {
                if (attribute.getAsJsonObject().get("name").getAsString().equals("id")) {
                    path = attribute.getAsJsonObject().get("value").getAsString();
                }
            }
            JsonArray content = new JsonArray();
            DisplayInfo display = display(Residual.id(path));
            if (display != null) {
                content.add(JsonParser.parseString("""
                        {"type": "mdxJsxTextElement", "name": "ItemIcon", "children": [],
                         "attributes": [{"type": "mdxJsxAttribute", "name": "id", "value": "%s"}]}"""
                        .formatted(BuiltInRegistries.ITEM.getKey(display.getIcon().item().value()))));
                content.add(text(" "));
                JsonObject title = node("strong");
                title.getAsJsonArray("children").add(text(display.getTitle().getString()));
                content.add(title);
                content.add(JsonParser.parseString("{\"type\": \"break\"}"));
                content.add(text(sentence(display)));
            }
            for (JsonElement child : milestone.getAsJsonArray("children")) {
                if (child.getAsJsonObject().get("type").getAsString().equals("paragraph")) {
                    content.addAll(child.getAsJsonObject().getAsJsonArray("children"));
                } else {
                    content.add(child);
                }
            }
            return content;
        }

        private static JsonObject node(String type) {
            JsonObject node = new JsonObject();
            node.addProperty("type", type);
            node.add("children", new JsonArray());
            return node;
        }

        private static JsonObject text(String value) {
            JsonObject node = new JsonObject();
            node.addProperty("type", "text");
            node.addProperty("value", value);
            return node;
        }
    }

    /** An advancement's icon in its frame, as the advancement screen draws it: lit once it is made. */
    private static final class Icon extends LytBlock implements InteractiveElement, ExportableResourceProvider {
        private static final int SIZE = 26;
        private final Identifier id;
        private final DisplayInfo display;

        Icon(Identifier id, DisplayInfo display) {
            this.id = id;
            this.display = display;
            setMarginRight(4);
        }

        @Override
        protected LytRect computeLayout(LayoutContext context, int x, int y, int availableWidth) {
            return new LytRect(x, y, SIZE, SIZE);
        }

        @Override
        protected void onLayoutMoved(int deltaX, int deltaY) {
        }

        @Override
        public void render(RenderContext context) {
            AdvancementWidgetType frame = made(id) ? AdvancementWidgetType.OBTAINED : AdvancementWidgetType.UNOBTAINED;
            context.guiGraphics().blitSprite(RenderPipelines.GUI_TEXTURED, frame.frameSprite(display.getType()),
                    bounds.x(), bounds.y(), SIZE, SIZE);
            context.renderItem(display.getIcon().create(), bounds.x() + 5, bounds.y() + 5, 16, 16);
        }

        @Override
        public Optional<GuideTooltip> getTooltip(float x, float y) {
            return Optional.of(new TextTooltip(Component.translatable(made(id) ? "residual.milestone.made" : "residual.milestone.not_yet")));
        }

        /** The website shows the icon, so the export draws it. */
        @Override
        public void exportResources(ResourceExporter exporter) {
            exporter.referenceItem(display.getIcon());
        }
    }
}
