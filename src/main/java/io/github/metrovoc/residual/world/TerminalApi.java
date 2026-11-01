package io.github.metrovoc.residual.world;

import io.github.metrovoc.residual.item.TerminalItem;
import io.github.metrovoc.residual.model.ModelLink;
import io.github.metrovoc.residual.net.RelaySync;
import io.github.metrovoc.residual.net.TerminalCall;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/** What a Terminal or a Relay Gate can ask of the relay network: how it stands, and to go to a gate on it. */
public final class TerminalApi {
    /** How far from a gate a player can use it, as with any block's screen. */
    private static final double GATE_REACH = 8;

    private TerminalApi() {
    }

    public static void call(ServerPlayer player, TerminalCall call) {
        MinecraftServer server = player.level().getServer();
        switch (call.kind()) {
            case MAP -> PacketDistributor.sendToPlayer(player, RelaySync.of(Relays.of(server), true));
            case GATE_JUMP -> {
                GlobalPos from = call.where().orElse(null), to = place(call.text());
                if (from != null && to != null && near(player, from)) {
                    Gates.start(player, from, to);
                }
            }
            case GATE_NAME -> {
                GlobalPos gate = call.where().orElse(null);
                if (gate != null && near(player, gate) && call.text().length() <= 32) {
                    Relays relays = Relays.of(server);
                    if (relays.part(gate) != null && relays.part(gate).kind() == Relays.Kind.GATE) {
                        relays.rename(gate, call.text().strip());
                        relays.tell(player);
                    }
                }
            }
            case JUMP -> {
                ItemStack terminal = TerminalItem.held(player);
                if (terminal == null) {
                    return;
                }
                ModelLink link = ModelLink.of(player.level(), player.blockPosition(), terminal);
                if (link instanceof ModelLink.Served) {
                    call.where().ifPresent(to -> Gates.start(player, null, to));
                } else if (link instanceof ModelLink.Missing m) {
                    player.sendOverlayMessage(Component.translatable(m.problem()));
                }
            }
        }
    }

    /** {@code dimension;x;y;z} back into a place. */
    @Nullable
    public static GlobalPos place(String text) {
        String[] parts = text.split(";");
        if (parts.length != 4) {
            return null;
        }
        try {
            ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, Identifier.parse(parts[0]));
            return GlobalPos.of(dimension, new BlockPos(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3])));
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static String place(GlobalPos at) {
        return at.dimension().identifier() + ";" + at.pos().getX() + ";" + at.pos().getY() + ";" + at.pos().getZ();
    }

    private static boolean near(ServerPlayer player, GlobalPos gate) {
        return player.level().dimension().equals(gate.dimension()) && player.distanceToSqr(gate.pos().getCenter()) < GATE_REACH * GATE_REACH;
    }
}
