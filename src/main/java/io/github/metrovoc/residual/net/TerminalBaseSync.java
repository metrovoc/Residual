package io.github.metrovoc.residual.net;

import io.github.metrovoc.residual.Residual;
import java.util.List;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

/**
 * What a Terminal's call about the base came back with, or why it did not: how many ticks the call waited for its turn,
 * each storage network the relays reach, by one of its Storage Terminals, and each machine.
 */
public record TerminalBaseSync(int waited, String problem, List<Storage> storage, List<Machine> machines) implements CustomPacketPayload {
    public record Storage(GlobalPos terminal, long stored, long capacity, int kinds) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Storage> STREAM_CODEC = StreamCodec.composite(
                GlobalPos.STREAM_CODEC, Storage::terminal,
                ByteBufCodecs.VAR_LONG, Storage::stored,
                ByteBufCodecs.VAR_LONG, Storage::capacity,
                ByteBufCodecs.VAR_INT, Storage::kinds,
                Storage::new);
    }

    /** A machine: what it is, where, and what it is doing. {@code state} is one of {@link #IDLE}, {@link #WORKING} and {@link #WAITING}. */
    public record Machine(ItemStack icon, GlobalPos at, int state, Component doing) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Machine> STREAM_CODEC = StreamCodec.composite(
                ItemStack.OPTIONAL_STREAM_CODEC, Machine::icon,
                GlobalPos.STREAM_CODEC, Machine::at,
                ByteBufCodecs.VAR_INT, Machine::state,
                ComponentSerialization.STREAM_CODEC, Machine::doing,
                Machine::new);
    }

    public static final int IDLE = 0, WORKING = 1, WAITING = 2;
    public static final Type<TerminalBaseSync> TYPE = new Type<>(Residual.id("terminal_base"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalBaseSync> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TerminalBaseSync::waited,
            ByteBufCodecs.STRING_UTF8, TerminalBaseSync::problem,
            Storage.STREAM_CODEC.apply(ByteBufCodecs.list()), TerminalBaseSync::storage,
            Machine.STREAM_CODEC.apply(ByteBufCodecs.list()), TerminalBaseSync::machines,
            TerminalBaseSync::new);

    public static TerminalBaseSync failed(String problem) {
        return new TerminalBaseSync(0, problem, List.of(), List.of());
    }

    @Override
    public Type<TerminalBaseSync> type() {
        return TYPE;
    }
}
