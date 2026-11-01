package io.github.metrovoc.residual.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.logging.LogUtils;
import guideme.color.ColorValue;
import guideme.color.ConstantColor;
import guideme.compiler.PageCompiler;
import guideme.compiler.tags.BlockTagCompiler;
import guideme.document.LytRect;
import guideme.document.block.LytBlock;
import guideme.document.block.LytBlockContainer;
import guideme.document.interaction.InteractiveElement;
import guideme.layout.LayoutContext;
import guideme.libs.mdast.mdx.model.MdxJsxElementFields;
import guideme.render.RenderContext;
import guideme.siteexport.ExportPostProcessor;
import guideme.ui.GuideUiHost;
import io.github.metrovoc.residual.Residual;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jspecify.annotations.Nullable;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;

/**
 * Animations on guide pages: {@code <residual:Clip name="forecaster" alt="what it shows" />} plays
 * {@code clips/forecaster.clip} in a loop, and a click pauses it. A clip holds its first frame whole, then the tiles each
 * later frame changes, so a screen or a still camera stays small; the website plays the same file in a canvas
 * ({@code src/website/web-assets/templates/layout.html}).
 */
final class Clips {
    private static final Logger LOG = LogUtils.getLogger();
    /** A clip nobody has drawn for this long gives its memory back. */
    private static final long IDLE = 3_000_000_000L;
    /** The most a clip moves on between two draws, so one scrolled away or a slow frame picks up where it stopped. */
    private static final long STEP = 100_000_000L;
    private static final ColorValue BLANK = new ConstantColor(0xFF20242A);
    private static final ColorValue BAR = new ConstantColor(0xC0FFFFFF);
    private static final List<Playback> PLAYING = new ArrayList<>();
    private static int count;

    private Clips() {
    }

    static void tick(ClientTickEvent.Post event) {
        if (!PLAYING.isEmpty()) {
            long now = System.nanoTime();
            PLAYING.removeIf(p -> {
                if (now - p.drawn <= IDLE) {
                    return false;
                }
                p.close();
                return true;
            });
        }
    }

    /** A .clip as stored (.metrovoc/tools/guide_clip.py). */
    private record Film(int width, int height, int display, int ms, int hold, int frames, int columns, int tile, byte[] first,
                        byte[] atlas, int[] tiles, short[] placed) {
        static Film read(byte[] bytes) throws IOException {
            try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
                if (in.readInt() != 0x52434C50) {
                    throw new IOException("not a clip");
                }
                int width = in.readUnsignedShort(), height = in.readUnsignedShort(), display = in.readUnsignedShort(),
                        ms = in.readUnsignedShort(), hold = in.readUnsignedShort(), frames = in.readUnsignedShort(),
                        columns = in.readUnsignedShort(), tile = in.readUnsignedShort();
                byte[] first = in.readNBytes(in.readInt()), atlas = in.readNBytes(in.readInt());
                int[] tiles = new int[frames];
                int total = 0;
                for (int f = 1; f < frames; f++) {
                    tiles[f] = in.readUnsignedShort();
                    total += tiles[f];
                }
                short[] placed = new short[total * 3];
                for (int i = 0; i < placed.length; i++) {
                    placed[i] = in.readShort();
                }
                return new Film(width, height, display, ms, hold, frames, columns, tile, first, atlas, tiles, placed);
            }
        }
    }

    /** A PNG or a JPEG; NativeImage's own reader takes only PNG. */
    private static NativeImage decode(byte[] bytes) throws IOException {
        ByteBuffer in = MemoryUtil.memAlloc(bytes.length).put(bytes).flip();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer w = stack.mallocInt(1), h = stack.mallocInt(1), channels = stack.mallocInt(1);
            ByteBuffer pixels = STBImage.stbi_load_from_memory(in, w, h, channels, 4);
            if (pixels == null) {
                throw new IOException(STBImage.stbi_failure_reason());
            }
            NativeImage image = new NativeImage(w.get(0), h.get(0), false);
            MemoryUtil.memCopy(MemoryUtil.memAddress(pixels), image.getPointer(), pixels.remaining());
            STBImage.stbi_image_free(pixels);
            return image;
        } finally {
            MemoryUtil.memFree(in);
        }
    }

    /** A clip being played: its images, decoded off the client thread when it first comes into view, and where it is. */
    private static final class Playback {
        final Film film;
        final Identifier id = Residual.id("clip/" + count++);
        /** The first frame and the atlas; null until decoded. */
        final CompletableFuture<NativeImage[]> images;
        @Nullable
        DynamicTexture texture;
        long drawn = System.nanoTime();
        /** Nanoseconds into the clip, and the frame the texture shows, and where that frame's tiles end. */
        long clock;
        int frame;
        int placedTo;
        boolean closed;

        Playback(Film film) {
            this.film = film;
            images = CompletableFuture.supplyAsync(() -> {
                try {
                    return new NativeImage[]{decode(film.first), film.atlas.length == 0 ? null : decode(film.atlas)};
                } catch (IOException e) {
                    LOG.error("Can't decode a clip", e);
                    return null;
                }
            }, Util.backgroundExecutor());
        }

        /** Whether it is ready to draw, starting it once its images are decoded. */
        boolean ready() {
            NativeImage[] decoded = images.getNow(null);
            if (decoded == null) {
                return false;
            }
            if (texture == null) {
                NativeImage canvas = new NativeImage(decoded[0].getWidth(), decoded[0].getHeight(), false);
                canvas.copyFrom(decoded[0]);
                texture = new Texture(canvas);
                Minecraft.getInstance().getTextureManager().register(id, texture);
            }
            return true;
        }

        void advance(long now, boolean paused) {
            long step = Math.min(STEP, now - drawn);
            drawn = now;
            if (paused) {
                return;
            }
            clock += step;
            long ms = film.ms * 1_000_000L;
            NativeImage[] decoded = images.join();
            if (clock >= (film.frames - 1) * ms + film.hold * 1_000_000L) {
                clock = 0;
                frame = 0;
                placedTo = 0;
                texture.getPixels().copyFrom(decoded[0]);
                texture.upload();
                return;
            }
            int target = (int) Math.min(film.frames - 1, clock / ms);
            if (target == frame) {
                return;
            }
            int size = film.tile;
            for (; frame < target; frame++) {
                for (int i = 0; i < film.tiles[frame + 1]; i++, placedTo += 3) {
                    int index = Short.toUnsignedInt(film.placed[placedTo + 2]);
                    decoded[1].copyRect(texture.getPixels(), index % film.columns * size, index / film.columns * size,
                            Short.toUnsignedInt(film.placed[placedTo]) * size, Short.toUnsignedInt(film.placed[placedTo + 1]) * size,
                            size, size, false, false);
                }
            }
            texture.upload();
        }

        void close() {
            closed = true;
            if (texture != null) {
                Minecraft.getInstance().getTextureManager().release(id);
            }
            images.thenAccept(decoded -> {
                if (decoded != null) {
                    decoded[0].close();
                    if (decoded[1] != null) {
                        decoded[1].close();
                    }
                }
            });
        }
    }

    /** A clip's frame, smoothed, since a page rarely shows it one pixel to one. */
    private static final class Texture extends DynamicTexture {
        Texture(NativeImage canvas) {
            super(() -> "Residual clip", canvas);
            sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        }
    }

    static final class Tag extends BlockTagCompiler {
        @Override
        public Set<String> getTagNames() {
            return Set.of("residual:Clip");
        }

        @Override
        protected void compile(PageCompiler compiler, LytBlockContainer parent, MdxJsxElementFields el) {
            String name = el.getAttributeString("name", "");
            byte[] bytes = compiler.loadAsset(Residual.id("clips/" + name + ".clip"));
            if (bytes == null) {
                parent.appendError(compiler, "No clip " + name, el);
                return;
            }
            try {
                parent.append(new Player(Film.read(bytes)));
            } catch (IOException e) {
                parent.appendError(compiler, "Can't read clip " + name + ": " + e.getMessage(), el);
            }
        }
    }

    /** Clips on the website: a canvas the page's script plays the same file in, copied beside the pages. */
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
            for (JsonElement element : children) {
                JsonObject child = element.getAsJsonObject();
                if (child.get("name") instanceof JsonPrimitive name && name.getAsString().equals("residual:Clip")) {
                    child.addProperty("name", "canvas");
                    JsonArray attributes = new JsonArray();
                    for (JsonElement attribute : child.getAsJsonArray("attributes")) {
                        JsonObject a = attribute.getAsJsonObject();
                        String value = a.get("value").getAsString();
                        attributes.add(attribute("name".equals(a.get("name").getAsString()) ? "data-clip" : "aria-label", value));
                    }
                    attributes.add(attribute("role", "img"));
                    child.add("attributes", attributes);
                } else {
                    rewrite(child);
                }
            }
        }

        private static JsonObject attribute(String name, String value) {
            JsonObject attribute = new JsonObject();
            attribute.addProperty("type", "mdxJsxAttribute");
            attribute.addProperty("name", name);
            attribute.addProperty("value", value);
            return attribute;
        }
    }

    /** A clip on a page, centred, as wide as it was made to be shown or as the page allows. */
    private static final class Player extends LytBlock implements InteractiveElement {
        private final Film film;
        @Nullable
        private Playback playing;
        private boolean paused;

        Player(Film film) {
            this.film = film;
            setMarginTop(4);
            setMarginBottom(6);
        }

        @Override
        protected LytRect computeLayout(LayoutContext context, int x, int y, int availableWidth) {
            int width = Math.min(film.display, availableWidth);
            return new LytRect(x + (availableWidth - width) / 2, y, width, width * film.height / film.width);
        }

        @Override
        protected void onLayoutMoved(int deltaX, int deltaY) {
        }

        @Override
        public void render(RenderContext context) {
            long now = System.nanoTime();
            if (playing == null || playing.closed) {
                playing = new Playback(film);
                PLAYING.add(playing);
            }
            playing.drawn = playing.texture == null ? now : playing.drawn;
            if (!playing.ready()) {
                context.fillRect(bounds, BLANK);
                return;
            }
            playing.advance(now, paused);
            context.fillTexturedRect(bounds, playing.id);
            int played = bounds.width() * (playing.frame + 1) / film.frames;
            context.fillRect(new LytRect(bounds.x(), bounds.bottom() - 1, played, 1), BAR);
            if (paused) {
                context.fillRect(new LytRect(bounds.x() + 3, bounds.y() + 3, 2, 7), BAR);
                context.fillRect(new LytRect(bounds.x() + 7, bounds.y() + 3, 2, 7), BAR);
            }
        }

        @Override
        public boolean mouseClicked(GuideUiHost screen, int x, int y, MouseButtonInfo button, boolean doubleClick) {
            paused = !paused;
            return true;
        }
    }
}
