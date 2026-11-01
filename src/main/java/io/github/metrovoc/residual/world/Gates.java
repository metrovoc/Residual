package io.github.metrovoc.residual.world;

import io.github.metrovoc.residual.Awards;
import io.github.metrovoc.residual.block.GateBlock;
import io.github.metrovoc.residual.block.ServerBlockEntity;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Sending players between gates. A jump is a request that travels the network: it takes a turn at a Model Server for every
 * step from relay to relay along its way, a tick each at best, waiting behind whatever else asks, so a long way takes a
 * moment and a busy server longer. From a Terminal, with no gate underfoot, it takes {@link #FROM_AFAR} turns more. Moving
 * away calls it off.
 */
public final class Gates {
    /** Turns a jump takes on top of its steps when it starts from a Terminal instead of a gate. */
    public static final int FROM_AFAR = 8;
    /** The shortest a jump takes, in ticks, from a gate and from afar: about a warp plate's and a warp stone's. */
    private static final int AT_GATE = 15, AFAR = 32;
    /** How far a player may drift from where a jump began. */
    private static final double DRIFT = 1.5;
    private static final Map<UUID, Jump> JUMPS = new HashMap<>();

    private static final class Jump {
        final GlobalPos to;
        final Vec3 from;
        int left;
        int ticks;
        boolean asking;

        Jump(GlobalPos to, Vec3 from, int left, int ticks) {
            this.to = to;
            this.from = from;
            this.left = left;
            this.ticks = ticks;
        }
    }

    private Gates() {
    }

    /** Starts {@code player} toward the gate {@code to}, from the gate {@code from} or, with none, a Terminal in reach of it. */
    public static void start(ServerPlayer player, @Nullable GlobalPos from, GlobalPos to) {
        Relays relays = Relays.of(player.level().getServer());
        Relays.Part gate = relays.part(to);
        if (gate == null || gate.kind() != Relays.Kind.GATE || JUMPS.containsKey(player.getUUID()) || to.equals(from)) {
            return;
        }
        Relays.Net net = relays.netOf(to);
        if (net == null || !net.open()) {
            player.sendOverlayMessage(GateBlock.closed(net));
            return;
        }
        int steps;
        if (from != null) {
            if (relays.netOf(from) != net) {
                return;
            }
            steps = Math.max(1, relays.hops(from, to));
        } else {
            if (!net.covers(player.level().dimension(), player.blockPosition())) {
                player.sendOverlayMessage(Component.translatable("residual.model.out_of_range"));
                return;
            }
            steps = FROM_AFAR + Math.max(1, nearest(relays, net, player, to));
        }
        JUMPS.put(player.getUUID(), new Jump(to, player.position(), steps, from != null ? AT_GATE : AFAR));
        player.level().playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8f, 1.6f);
        player.closeContainer();
    }

    /** The steps from the part that reaches the player to the gate. */
    private static int nearest(Relays relays, Relays.Net net, ServerPlayer player, GlobalPos to) {
        int best = Integer.MAX_VALUE;
        for (Relays.Part p : net.parts) {
            if (p.holds(player.level().dimension(), player.blockPosition())) {
                best = Math.min(best, relays.hops(p.at(), to));
            }
        }
        return best == Integer.MAX_VALUE ? 1 : best;
    }

    /** Takes the jump's next turn at the least busy server on its network; false if there is none to ask. */
    private static boolean ask(MinecraftServer server, ServerPlayer player, Jump jump) {
        Relays relays = Relays.of(server);
        Relays.Net net = relays.netOf(jump.to);
        if (net == null || !net.open()) {
            player.sendOverlayMessage(GateBlock.closed(net));
            return false;
        }
        List<ServerBlockEntity> servers = relays.servers(net);
        if (servers.isEmpty()) {
            player.sendOverlayMessage(Component.translatable("residual.relay_gate.busy"));
            return false;
        }
        jump.asking = true;
        UUID id = player.getUUID();
        servers.getFirst().call(turn -> {
            if (JUMPS.get(id) != jump) {
                return;
            }
            jump.asking = false;
            if (turn) {
                jump.left--;
            } else {
                JUMPS.remove(id);
                ServerPlayer now = server.getPlayerList().getPlayer(id);
                if (now != null) {
                    now.sendOverlayMessage(Component.translatable("residual.relay_gate.busy"));
                }
            }
        });
        return true;
    }

    private static void arrive(MinecraftServer server, ServerPlayer player, GlobalPos to) {
        ServerLevel there = server.getLevel(to.dimension());
        if (there == null) {
            return;
        }
        // on top of the pad, wherever the chunk has got to
        there.getChunk(to.pos());
        Vec3 feet = new Vec3(to.pos().getX() + 0.5, to.pos().getY() + 0.25, to.pos().getZ() + 0.5);
        BlockPos above = to.pos().above();
        if (!there.getBlockState(above).getCollisionShape(there, above).isEmpty()
                || !there.getBlockState(above.above()).getCollisionShape(there, above.above()).isEmpty()) {
            player.sendOverlayMessage(Component.translatable("residual.relay_gate.blocked"));
            return;
        }
        ServerLevel here = player.level();
        here.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1, player.getZ(), 40, 0.25, 0.8, 0.25, 0.04);
        here.playSound(null, player.blockPosition(), SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.PLAYERS, 0.8f, 1.2f);
        player.stopRiding();
        player.teleport(new TeleportTransition(there, feet, Vec3.ZERO, player.getYRot(), player.getXRot(), TeleportTransition.DO_NOTHING));
        player.resetFallDistance();
        there.sendParticles(ParticleTypes.END_ROD, feet.x, feet.y + 1, feet.z, 40, 0.25, 0.8, 0.25, 0.04);
        there.playSound(null, to.pos(), SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.PLAYERS, 0.8f, 1.2f);
        Awards.grant(player, "deploy/gate");
    }

    /** Moves every jump on a step: its next turn, or if it has had them all, its end. Whoever walked away is left behind. */
    public static void tick(ServerTickEvent.Post event) {
        if (JUMPS.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        JUMPS.entrySet().removeIf(e -> {
            ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
            Jump jump = e.getValue();
            if (player == null || !player.isAlive() || player.position().distanceToSqr(jump.from) > DRIFT * DRIFT) {
                if (player != null) {
                    player.sendOverlayMessage(Component.translatable("residual.relay_gate.called_off"));
                }
                return true;
            }
            player.level().sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 1, player.getZ(), 2, 0.3, 0.6, 0.3, 0.02);
            jump.ticks--;
            if (jump.left > 0) {
                player.sendOverlayMessage(Component.translatable("residual.relay_gate.jumping", jump.left));
                return !jump.asking && !ask(server, player, jump);
            }
            if (jump.ticks <= 0) {
                arrive(server, player, jump.to);
                return true;
            }
            return false;
        });
    }
}
