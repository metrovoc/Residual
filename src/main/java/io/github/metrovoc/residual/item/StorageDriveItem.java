package io.github.metrovoc.residual.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.metrovoc.residual.registry.ModComponents;
import io.netty.buffer.ByteBuf;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * A Storage Drive: in a Drive Rack it keeps any number of kinds of item, up to its capacity in all. What it holds is
 * kept with the world under the drive's id; the item carries the id and a note of how full it is.
 */
public class StorageDriveItem extends Item {
    /** The id of a drive's contents, and how much it held when its rack last looked. */
    public record Label(UUID id, long stored, int kinds) {
        public static final Codec<Label> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("id").forGetter(Label::id),
                Codec.LONG.fieldOf("stored").forGetter(Label::stored),
                Codec.INT.fieldOf("kinds").forGetter(Label::kinds)).apply(i, Label::new));
        public static final StreamCodec<ByteBuf, Label> STREAM_CODEC = StreamCodec.composite(UUIDUtil.STREAM_CODEC, Label::id,
                ByteBufCodecs.VAR_LONG, Label::stored, ByteBufCodecs.VAR_INT, Label::kinds, Label::new);
    }

    public final long capacity;

    public StorageDriveItem(long capacity, Properties properties) {
        super(properties);
        this.capacity = capacity;
    }

    /** The id of the drive's contents, given to it the first time it is asked for. */
    public static UUID id(ItemStack stack) {
        Label label = stack.get(ModComponents.STORAGE_DRIVE.get());
        if (label == null) {
            label = new Label(UUID.randomUUID(), 0, 0);
            stack.set(ModComponents.STORAGE_DRIVE.get(), label);
        }
        return label.id();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        Label label = stack.get(ModComponents.STORAGE_DRIVE.get());
        long stored = label == null ? 0 : label.stored();
        int kinds = label == null ? 0 : label.kinds();
        out.accept(Component.translatable("residual.storage_drive.usage", String.format(Locale.ROOT, "%,d", stored),
                String.format(Locale.ROOT, "%,d", capacity), kinds).withStyle(ChatFormatting.GRAY));
    }
}
