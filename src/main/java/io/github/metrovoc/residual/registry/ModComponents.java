package io.github.metrovoc.residual.registry;

import com.mojang.serialization.Codec;
import io.github.metrovoc.residual.Residual;
import io.github.metrovoc.residual.learn.Vocab;
import io.github.metrovoc.residual.model.ModelInfo;
import io.github.metrovoc.residual.model.Recovered;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModComponents {
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Residual.MOD_ID);

    /** The model a Checkpoint Crystal holds. */
    public static final Supplier<DataComponentType<ModelInfo>> MODEL = COMPONENTS.registerComponentType("model",
            b -> b.persistent(ModelInfo.CODEC).networkSynchronized(ModelInfo.STREAM_CODEC));
    /** The record an Archive Shard carries. */
    public static final Supplier<DataComponentType<Recovered>> SHARD = COMPONENTS.registerComponentType("shard",
            b -> b.persistent(Recovered.CODEC).networkSynchronized(Recovered.STREAM_CODEC));
    /** Words a shard's record most often mentions, left by the ruin it came from until the shard learns its record. */
    public static final Supplier<DataComponentType<List<String>>> SHARD_ABOUT = COMPONENTS.registerComponentType("shard_about",
            b -> b.persistent(Codec.STRING.listOf()).networkSynchronized(ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list())));
    /** The kind of ruin a Wayfinder is tuned to. */
    public static final Supplier<DataComponentType<String>> WAYFINDER_KIND = COMPONENTS.registerComponentType("wayfinder_kind",
            b -> b.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));
    /** Every record filed into an Archive Drive, in record order. */
    public static final Supplier<DataComponentType<List<Recovered>>> RECORDS = COMPONENTS.registerComponentType("records",
            b -> b.persistent(Recovered.CODEC.listOf()).networkSynchronized(Recovered.STREAM_CODEC.apply(ByteBufCodecs.list())));
    /** Which wreck a Salvaged Model is. */
    public static final Supplier<DataComponentType<String>> WRECK = COMPONENTS.registerComponentType("wreck",
            b -> b.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));

    /** The vocabulary on a Tokenizer. */
    public static final Supplier<DataComponentType<Vocab>> VOCAB = COMPONENTS.registerComponentType("vocab",
            b -> b.persistent(Vocab.CODEC).networkSynchronized(Vocab.STREAM_CODEC));
    /** What a Field Log recorded: one line per crop, its age at every look. */
    public static final Supplier<DataComponentType<String>> FIELD_LOG = COMPONENTS.registerComponentType("field_log",
            b -> b.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));

    private ModComponents() {
    }
}
