package io.github.metrovoc.residual.registry;

import io.github.metrovoc.residual.Residual;
import io.github.metrovoc.residual.progress.Progress;
import java.util.function.Supplier;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Residual.MOD_ID);

    /** Completed lessons, kept through death and synced to the player so screens can show it. */
    public static final Supplier<AttachmentType<Progress>> PROGRESS = ATTACHMENTS.register("progress",
            () -> AttachmentType.builder(() -> Progress.EMPTY)
                    .serialize(Progress.CODEC)
                    .copyOnDeath()
                    .sync((holder, player) -> holder == player, Progress.STREAM_CODEC)
                    .build());
    /** Whether the player has been given the guide. */
    public static final Supplier<AttachmentType<Boolean>> GUIDE_GIVEN = ATTACHMENTS.register("guide_given",
            () -> AttachmentType.builder(() -> false)
                    .serialize(com.mojang.serialization.Codec.BOOL.fieldOf("given"))
                    .copyOnDeath()
                    .build());

    private ModAttachments() {
    }
}
