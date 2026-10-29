package io.github.metrovoc.residual.registry;

import com.mojang.serialization.Codec;
import io.github.metrovoc.residual.Residual;
import io.github.metrovoc.residual.bench.MaskData;
import io.github.metrovoc.residual.model.ModelInfo;
import io.github.metrovoc.residual.model.Recovered;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
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
    /** Every record filed into an Archive Drive, in record order. */
    public static final Supplier<DataComponentType<List<Recovered>>> RECORDS = COMPONENTS.registerComponentType("records",
            b -> b.persistent(Recovered.CODEC.listOf()).networkSynchronized(Recovered.STREAM_CODEC.apply(ByteBufCodecs.list())));
    /** The Model Server an Uplink or a Terminal is tuned to. */
    public static final Supplier<DataComponentType<GlobalPos>> LINK = COMPONENTS.registerComponentType("link",
            b -> b.persistent(GlobalPos.CODEC).networkSynchronized(GlobalPos.STREAM_CODEC));
    /** Blocks marked with a Survey Marker, oldest first. */
    public static final Supplier<DataComponentType<List<BlockPos>>> MARKS = COMPONENTS.registerComponentType("marks",
            b -> b.persistent(BlockPos.CODEC.listOf()).networkSynchronized(BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list())));
    /** Which wreck a Salvaged Model is. */
    public static final Supplier<DataComponentType<String>> WRECK = COMPONENTS.registerComponentType("wreck",
            b -> b.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));

    /** The design a Photomask carries. */
    public static final Supplier<DataComponentType<MaskData>> MASK = COMPONENTS.registerComponentType("mask",
            b -> b.persistent(MaskData.CODEC).networkSynchronized(MaskData.STREAM_CODEC));
    /** False on a machine fabricated from a salvaged design, which runs one primitive at a time. */
    public static final Supplier<DataComponentType<Boolean>> FUSED = COMPONENTS.registerComponentType("fused",
            b -> b.persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL));

    private ModComponents() {
    }
}
