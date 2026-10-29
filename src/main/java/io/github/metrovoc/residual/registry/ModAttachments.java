package io.github.metrovoc.residual.registry;

import io.github.metrovoc.residual.Residual;
import java.util.function.Supplier;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Residual.MOD_ID);

    /** Whether the player has been given the guide. */
    public static final Supplier<AttachmentType<Boolean>> GUIDE_GIVEN = ATTACHMENTS.register("guide_given",
            () -> AttachmentType.builder(() -> false)
                    .serialize(com.mojang.serialization.Codec.BOOL.fieldOf("given"))
                    .copyOnDeath()
                    .build());

    private ModAttachments() {
    }
}
