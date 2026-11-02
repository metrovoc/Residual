package io.github.metrovoc.residual.registry;

import com.mojang.serialization.Codec;
import io.github.metrovoc.residual.Residual;
import io.github.metrovoc.residual.bench.MaskData;
import io.github.metrovoc.residual.item.MobLogItem;
import io.github.metrovoc.residual.item.StorageDriveItem;
import io.github.metrovoc.residual.learn.Vocab;
import io.github.metrovoc.residual.model.Board;
import io.github.metrovoc.residual.model.ModelInfo;
import io.github.metrovoc.residual.model.Recovered;
import io.github.metrovoc.residual.refine.TokenSupply;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.transfer.item.ItemResource;

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
    /** What a Relay Dump holds, as the seed its text is written from. */
    public static final Supplier<DataComponentType<Long>> DUMP = COMPONENTS.registerComponentType("dump",
            b -> b.persistent(Codec.LONG).networkSynchronized(ByteBufCodecs.VAR_LONG));
    /** The refined text a Token Cell draws on. */
    public static final Supplier<DataComponentType<TokenSupply>> TOKEN_SUPPLY = COMPONENTS.registerComponentType("token_supply",
            b -> b.persistent(TokenSupply.CODEC).networkSynchronized(TokenSupply.STREAM_CODEC));
    /** The Model Server an Uplink or a Terminal is tuned to. */
    public static final Supplier<DataComponentType<GlobalPos>> LINK = COMPONENTS.registerComponentType("link",
            b -> b.persistent(GlobalPos.CODEC).networkSynchronized(GlobalPos.STREAM_CODEC));
    /** The Relay Gate a Relay Card has taken the place of, to tune the next gate it is used on to. */
    public static final Supplier<DataComponentType<GlobalPos>> GATE = COMPONENTS.registerComponentType("gate",
            b -> b.persistent(GlobalPos.CODEC).networkSynchronized(GlobalPos.STREAM_CODEC));
    /** Blocks marked with a Survey Marker, oldest first. */
    public static final Supplier<DataComponentType<List<BlockPos>>> MARKS = COMPONENTS.registerComponentType("marks",
            b -> b.persistent(BlockPos.CODEC.listOf()).networkSynchronized(BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list())));
    /** The program a Program Card carries, as a player would write it. */
    public static final Supplier<DataComponentType<String>> PROGRAM = COMPONENTS.registerComponentType("program",
            b -> b.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));
    /** Which wreck a Salvaged Model is. */
    public static final Supplier<DataComponentType<String>> WRECK = COMPONENTS.registerComponentType("wreck",
            b -> b.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));

    /** The design a Photomask carries. */
    public static final Supplier<DataComponentType<MaskData>> MASK = COMPONENTS.registerComponentType("mask",
            b -> b.persistent(MaskData.CODEC).networkSynchronized(MaskData.STREAM_CODEC));
    /** The vocabulary on a Tokenizer. */
    public static final Supplier<DataComponentType<Vocab>> VOCAB = COMPONENTS.registerComponentType("vocab",
            b -> b.persistent(Vocab.CODEC).networkSynchronized(Vocab.STREAM_CODEC));
    /** What a Field Log recorded: one line per crop, its age at every look. */
    public static final Supplier<DataComponentType<String>> FIELD_LOG = COMPONENTS.registerComponentType("field_log",
            b -> b.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));
    /** A player's choices between answers: a prompt, what was chosen and what was not, a line each. */
    public static final Supplier<DataComponentType<List<String>>> PREFERENCES = COMPONENTS.registerComponentType("preferences",
            b -> b.persistent(Codec.STRING.listOf()).networkSynchronized(ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list())));
    /** What a Mob Log holds: the kind it follows, a specimen of it, and what mobs of that kind did. */
    public static final Supplier<DataComponentType<MobLogItem.Log>> MOB_LOG = COMPONENTS.registerComponentType("mob_log",
            b -> b.persistent(MobLogItem.Log.CODEC).networkSynchronized(MobLogItem.Log.STREAM_CODEC));
    /** False on a machine fabricated from a salvaged design, which runs one primitive at a time. */
    public static final Supplier<DataComponentType<Boolean>> FUSED = COMPONENTS.registerComponentType("fused",
            b -> b.persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL));

    /** Which contents a Storage Drive holds, and how full it was when its rack last looked. */
    public static final Supplier<DataComponentType<StorageDriveItem.Label>> STORAGE_DRIVE = COMPONENTS.registerComponentType("storage_drive",
            b -> b.persistent(StorageDriveItem.Label.CODEC).networkSynchronized(StorageDriveItem.Label.STREAM_CODEC));

    /** Which Wireless Terminal this one is, among others tuned to the same network. */
    public static final Supplier<DataComponentType<UUID>> TERMINAL_ID = COMPONENTS.registerComponentType("terminal_id",
            b -> b.persistent(UUIDUtil.CODEC).networkSynchronized(UUIDUtil.STREAM_CODEC));
    /** What a Wireless Terminal or Portable Drive keeps pinned to the top of its screen's grid. */
    public static final Supplier<DataComponentType<List<ItemResource>>> PINS = COMPONENTS.registerComponentType("pins",
            b -> b.persistent(ItemResource.CODEC.listOf()).networkSynchronized(ItemResource.STREAM_CODEC.apply(ByteBufCodecs.list())));

    /** The power in a flight frame or an exosuit, in FE. */
    public static final Supplier<DataComponentType<Integer>> CHARGE = COMPONENTS.registerComponentType("charge",
            b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));
    /** What an exosuit's board measured of the model on it. */
    public static final Supplier<DataComponentType<Board>> BOARD = COMPONENTS.registerComponentType("board",
            b -> b.persistent(Board.CODEC).networkSynchronized(Board.STREAM_CODEC));
    /** Which of an exosuit's abilities its wearer turned off, a bit each. */
    public static final Supplier<DataComponentType<Integer>> ABILITIES_OFF = COMPONENTS.registerComponentType("abilities_off",
            b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));
    /** Time an Hourglass has kept, in ticks. */
    public static final Supplier<DataComponentType<Integer>> KEPT_TIME = COMPONENTS.registerComponentType("kept_time",
            b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));
    /** The Forecaster an Hourglass is bound to. */
    public static final Supplier<DataComponentType<GlobalPos>> FORECASTER = COMPONENTS.registerComponentType("forecaster",
            b -> b.persistent(GlobalPos.CODEC).networkSynchronized(GlobalPos.STREAM_CODEC));

    private ModComponents() {
    }
}
