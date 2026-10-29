package io.github.metrovoc.residual.registry;

import com.mojang.serialization.Codec;
import io.github.metrovoc.residual.Residual;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Residual.MOD_ID);

    /** Whether the player has been given the guide. */
    public static final Supplier<AttachmentType<Boolean>> GUIDE_GIVEN = ATTACHMENTS.register("guide_given",
            () -> AttachmentType.builder(() -> false)
                    .serialize(Codec.BOOL.fieldOf("given"))
                    .copyOnDeath()
                    .build());

    /** The specs this player has verified a design for, whose modules their Bench offers as parts. */
    public static final Supplier<AttachmentType<List<String>>> VERIFIED = ATTACHMENTS.register("verified",
            () -> AttachmentType.<List<String>>builder(() -> List.of())
                    .serialize(Codec.STRING.listOf().fieldOf("specs"))
                    .copyOnDeath()
                    .sync((holder, to) -> holder == to, ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()))
                    .build());

    private ModAttachments() {
    }
}
