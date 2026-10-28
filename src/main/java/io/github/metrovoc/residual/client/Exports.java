package io.github.metrovoc.residual.client;

import io.github.metrovoc.residual.Residual;
import io.github.metrovoc.residual.net.ExportChunk;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** Puts an exported model back together and saves it under {@code residual/exports} in the game folder. */
final class Exports {
    @Nullable
    private static String folder;
    private static String code = "";
    private static ByteArrayOutputStream data = new ByteArrayOutputStream();
    private static int next;

    private Exports() {
    }

    static void accept(ExportChunk chunk) {
        if (chunk.index() == 0) {
            folder = chunk.folder().replaceAll("[^A-Za-z0-9_-]", "_");
            code = chunk.code();
            data = new ByteArrayOutputStream();
            next = 0;
        }
        if (folder == null || chunk.index() != next) {
            return;
        }
        data.writeBytes(chunk.data());
        next++;
        if (next == chunk.total()) {
            save(folder);
            folder = null;
        }
    }

    private static void save(String name) {
        Minecraft mc = Minecraft.getInstance();
        Path dir = mc.gameDirectory.toPath().resolve("residual").resolve("exports").resolve(name);
        try {
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("model.py"), code);
            Files.write(dir.resolve("weights.bin"), data.toByteArray());
        } catch (IOException e) {
            Residual.LOG.error("Could not save the exported model", e);
            message(Component.translatable("residual.export.failed", e.getMessage()).withStyle(ChatFormatting.RED));
            return;
        }
        Component link = Component.literal(dir.toString())
                .withStyle(s -> s.withUnderlined(true).withColor(ChatFormatting.AQUA).withClickEvent(new ClickEvent.OpenFile(dir.toFile())));
        message(Component.translatable("residual.export.done", link));
    }

    private static void message(Component text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendSystemMessage(text);
        }
    }
}
