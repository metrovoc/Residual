package io.github.metrovoc.residual.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.metrovoc.residual.Awards;
import io.github.metrovoc.residual.Residual;
import io.github.metrovoc.residual.ResidualConfig;
import io.github.metrovoc.residual.block.GateBlock;
import io.github.metrovoc.residual.block.RelayBlock;
import io.github.metrovoc.residual.block.RelayLinkBlock;
import io.github.metrovoc.residual.block.ServerBlockEntity;
import io.github.metrovoc.residual.item.TerminalItem;
import io.github.metrovoc.residual.learn.Benchmark;
import io.github.metrovoc.residual.model.ModelInfo;
import io.github.metrovoc.residual.net.RelaySync;
import io.github.metrovoc.residual.registry.ModBlocks;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * The relay network, kept with the world: every Model Server, relay mast, Relay Gate and Relay Link in every dimension,
 * whether or not they are loaded just now, joined as {@link RelayGraph} says. A network with a model served on it is live,
 * and keeps the ground around its parts running; one that answers fast enough with a model that reads orders is open, and
 * its gates and links work.
 */
public final class Relays extends SavedData {
    public enum Kind { SERVER, RELAY, GATE, LINK }

    /** A part of a network; {@code model} says a server holds a model, {@code pair} is the gate a gate is tuned to. */
    public record Part(GlobalPos at, Kind kind, int reach, String name, Optional<GlobalPos> pair, boolean model) {
        static final Codec<Part> CODEC = RecordCodecBuilder.create(i -> i.group(
                GlobalPos.CODEC.fieldOf("at").forGetter(Part::at),
                Codec.STRING.xmap(Kind::valueOf, Kind::name).fieldOf("kind").forGetter(Part::kind),
                Codec.INT.fieldOf("reach").forGetter(Part::reach),
                Codec.STRING.optionalFieldOf("name", "").forGetter(Part::name),
                GlobalPos.CODEC.optionalFieldOf("pair").forGetter(Part::pair),
                Codec.BOOL.optionalFieldOf("model", false).forGetter(Part::model)
        ).apply(i, Part::new));

        Part with(int reach) {
            return new Part(at, kind, reach, name, pair, model);
        }

        boolean holds(ResourceKey<Level> dimension, BlockPos where) {
            if (reach == 0 || !at.dimension().equals(dimension)) {
                return false;
            }
            long dx = at.pos().getX() - where.getX(), dz = at.pos().getZ() - where.getZ();
            return dx * dx + dz * dz <= (long) reach * reach;
        }
    }

    /** Reach of a mast for each block of it; a mast taller than {@link #TALLEST} reaches no further. */
    public static final int RANGE = 32;
    public static final int TALLEST = 32;
    public static final int SERVER_RANGE = 16;
    /** Answers a second, from a model that passes Orders, at which a network's gates open and its links carry. */
    public static final int OPEN_AT = 16;
    /** What a link carries each tick for every answer a second: at {@link #OPEN_AT}, one Port's items and one Flux plug's power. */
    public static final int ITEMS_A_TICK = 4, FE_A_TICK = 50_000;
    /** New chunks taken up in a second, each a possible load from disk. */
    private static final int HOLDS_A_SECOND = 18;
    private static final int SYNC_EVERY = 40;
    private static final BlockPos OWNER = BlockPos.ZERO;
    private static final TicketController TICKETS = new TicketController(Residual.id("relays"));

    private static final Codec<Relays> CODEC = RecordCodecBuilder.create(i -> i.group(
            Part.CODEC.listOf().fieldOf("parts").forGetter(r -> List.copyOf(r.parts.values())),
            Codec.unboundedMap(Level.RESOURCE_KEY_CODEC, Codec.LONG.listOf()).fieldOf("held").forGetter(Relays::savedHeld)
    ).apply(i, Relays::new));
    public static final SavedDataType<Relays> TYPE = new SavedDataType<>(Residual.id("relays"), Relays::new, CODEC);

    /** One network: what is on it, how much it can answer, and what its links have carried. */
    public static final class Net {
        public final int id;
        public final List<Part> parts;
        private final boolean live;
        private int capacity;
        private boolean reads;
        private long budgetAt;
        private long itemsLeft, feLeft, itemsNow, feNow;
        private long itemsPerSecond, fePerSecond;
        private int answers;

        Net(int id, List<Part> parts) {
            this.id = id;
            this.parts = parts;
            live = parts.stream().anyMatch(p -> p.kind() == Kind.SERVER && p.model());
        }

        /** Whether a Model Server on it holds a model. */
        public boolean live() {
            return live;
        }

        /** Whether its gates open and its links carry. */
        public boolean open() {
            return reads && capacity >= OPEN_AT;
        }

        public boolean reads() {
            return reads;
        }

        /** Answers a second from all its Model Servers. */
        public int capacity() {
            return capacity;
        }

        public boolean covers(ResourceKey<Level> dimension, BlockPos where) {
            for (Part p : parts) {
                if (p.holds(dimension, where)) {
                    return true;
                }
            }
            return false;
        }

        /** The most of {@code wanted} items its links can still carry this tick. */
        public long itemsRoom(long tick, long wanted) {
            refill(tick);
            return Math.min(wanted, itemsLeft);
        }

        public void itemsCarried(long n) {
            itemsLeft -= n;
            itemsNow += n;
        }

        /** The most FE its links can still carry this tick. */
        public long feRoom(long tick) {
            refill(tick);
            return feLeft;
        }

        public void feCarried(long n) {
            feLeft -= n;
            feNow += n;
        }

        private void refill(long tick) {
            if (budgetAt != tick) {
                budgetAt = tick;
                itemsLeft = (long) capacity * ITEMS_A_TICK;
                feLeft = (long) capacity * FE_A_TICK;
            }
        }

        public long itemsPerSecond() {
            return itemsPerSecond;
        }

        /** FE carried in the last second. */
        public long fePerSecond() {
            return fePerSecond;
        }

        public int answersPerSecond() {
            return answers;
        }
    }

    private final Map<GlobalPos, Part> parts = new LinkedHashMap<>();
    private final Map<ResourceKey<Level>, LongSet> held = new HashMap<>();
    private final Map<UUID, Integer> told = new HashMap<>();
    @Nullable
    private RelayGraph<GlobalPos> graph;
    private final Map<Integer, Net> nets = new HashMap<>();
    private int revision;
    private int ticks;
    private MinecraftServer server;

    private Relays() {
    }

    private Relays(List<Part> saved, Map<ResourceKey<Level>, List<Long>> holding) {
        saved.forEach(p -> parts.put(p.at(), p));
        holding.forEach((dimension, chunks) -> held.put(dimension, new LongOpenHashSet(chunks)));
    }

    private Map<ResourceKey<Level>, List<Long>> savedHeld() {
        Map<ResourceKey<Level>, List<Long>> out = new HashMap<>();
        held.forEach((dimension, chunks) -> out.put(dimension, List.copyOf(chunks)));
        return out;
    }

    public static Relays of(MinecraftServer server) {
        Relays relays = server.overworld().getDataStorage().computeIfAbsent(TYPE);
        relays.server = server;
        return relays;
    }

    public static void controllers(RegisterTicketControllersEvent event) {
        event.register(TICKETS);
    }

    public static void tick(ServerTickEvent.Post event) {
        of(event.getServer()).tick();
    }

    // ---------------------------------------------------------------- signing in and out

    private void changed() {
        graph = null;
        setDirty();
    }

    /** A part stands at {@code pos}: new, or known already with this reach. */
    public static void sign(ServerLevel level, BlockPos pos, Kind kind, int reach) {
        Relays relays = of(level.getServer());
        GlobalPos at = GlobalPos.of(level.dimension(), pos.immutable());
        Part known = relays.parts.get(at);
        if (known == null || known.kind() != kind) {
            relays.parts.put(at, new Part(at, kind, reach, "", Optional.empty(), false));
            relays.changed();
        } else if (known.reach() != reach) {
            relays.parts.put(at, known.with(reach));
            relays.changed();
        }
    }

    /** The part at {@code pos} is gone; a gate tuned to it is free again. */
    public static void leave(ServerLevel level, BlockPos pos) {
        Relays relays = of(level.getServer());
        Part gone = relays.parts.remove(GlobalPos.of(level.dimension(), pos));
        if (gone != null) {
            gone.pair().ifPresent(relays::free);
            relays.changed();
        }
    }

    private void free(GlobalPos gate) {
        Part other = parts.get(gate);
        if (other != null) {
            parts.put(gate, new Part(other.at(), other.kind(), other.reach(), other.name(), Optional.empty(), other.model()));
        }
    }

    /**
     * The mast the relay at {@code pos} stands in changed: its foot takes the reach of all of it, a block's worth for each
     * block, and the blocks above it are no parts of their own.
     */
    public static void mast(ServerLevel level, BlockPos pos) {
        Block relay = ModBlocks.RELAY.get();
        if (!level.getBlockState(pos).is(relay)) {
            return;
        }
        BlockPos foot = pos;
        while (level.getBlockState(foot.below()).is(relay)) {
            foot = foot.below();
        }
        int height = 1;
        for (BlockPos up = foot.above(); level.getBlockState(up).is(relay); up = up.above()) {
            leave(level, up);
            height++;
        }
        sign(level, foot, Kind.RELAY, RANGE * Math.min(height, TALLEST));
    }

    /** A Model Server took a model in, or gave it up. */
    public static void served(ServerLevel level, BlockPos pos, boolean model) {
        Relays relays = of(level.getServer());
        GlobalPos at = GlobalPos.of(level.dimension(), pos);
        Part known = relays.parts.get(at);
        if (known != null && known.model() != model) {
            relays.parts.put(at, new Part(at, known.kind(), known.reach(), known.name(), known.pair(), model));
            relays.changed();
        }
    }

    /** Names a gate. */
    public void rename(GlobalPos at, String name) {
        Part known = parts.get(at);
        if (known != null && !known.name().equals(name)) {
            parts.put(at, new Part(at, known.kind(), known.reach(), name, known.pair(), known.model()));
            changed();
        }
    }

    /** Tunes two gates to each other, in place of whatever they were tuned to. */
    public void tune(GlobalPos a, GlobalPos b) {
        Part first = parts.get(a), second = parts.get(b);
        if (first == null || second == null || first.kind() != Kind.GATE || second.kind() != Kind.GATE || a.equals(b)) {
            return;
        }
        first.pair().ifPresent(this::free);
        second.pair().ifPresent(this::free);
        parts.put(a, new Part(a, first.kind(), first.reach(), first.name(), Optional.of(b), first.model()));
        parts.put(b, new Part(b, second.kind(), second.reach(), second.name(), Optional.of(a), second.model()));
        changed();
    }

    /** Cuts a gate from the gate it is tuned to. */
    public void untune(GlobalPos gate) {
        Part known = parts.get(gate);
        if (known != null && known.pair().isPresent()) {
            free(known.pair().get());
            free(gate);
            changed();
        }
    }

    // ---------------------------------------------------------------- asking

    private RelayGraph<GlobalPos> graph() {
        if (graph == null) {
            List<RelayGraph.Part<GlobalPos>> all = new ArrayList<>();
            parts.values().forEach(p -> all.add(new RelayGraph.Part<>(p.at(), p.at().dimension(), p.at().pos().getX(), p.at().pos().getZ(),
                    p.reach(), p.pair().orElse(null))));
            graph = new RelayGraph<>(all);
            nets.clear();
            for (int id : graph.networks()) {
                List<Part> members = new ArrayList<>();
                graph.members(id).forEach(m -> members.add(parts.get(m.key())));
                Net net = new Net(id, members);
                measure(net);
                nets.put(id, net);
            }
            revision++;
        }
        return graph;
    }

    @Nullable
    public Part part(GlobalPos at) {
        return parts.get(at);
    }

    /** The network the part at {@code at} is on. */
    @Nullable
    public Net netOf(GlobalPos at) {
        return nets(graph().network(at));
    }

    /** The network that reaches a place, if any does. */
    @Nullable
    public Net covering(ResourceKey<Level> dimension, BlockPos where) {
        return nets(graph().covering(dimension, where.getX(), where.getZ()));
    }

    @Nullable
    private Net nets(int id) {
        graph();
        return nets.get(id);
    }

    public List<Net> all() {
        graph();
        return List.copyOf(nets.values());
    }

    /** Whether the server at {@code server} can answer something at {@code where}, directly or through its network. */
    public boolean covers(GlobalPos server, ResourceKey<Level> dimension, BlockPos where) {
        Net net = netOf(server);
        return net != null && net.covers(dimension, where);
    }

    /** Whether the server at {@code server} in this level can answer something at {@code where} in it. */
    public static boolean covers(Level level, BlockPos server, BlockPos where) {
        return level.getServer() != null && of(level.getServer()).covers(GlobalPos.of(level.dimension(), server), level.dimension(), where);
    }

    /** Whether a relay of any network stands within reach of {@code where}. */
    public static boolean relayNear(Level level, BlockPos where) {
        if (level.getServer() != null) {
            for (Part p : of(level.getServer()).parts.values()) {
                if (p.kind() == Kind.RELAY && p.holds(level.dimension(), where)) {
                    return true;
                }
            }
        }
        return false;
    }

    public int hops(GlobalPos from, GlobalPos to) {
        return graph().hops(from, to);
    }

    public int revision() {
        graph();
        return revision;
    }

    /** The served models on a network that are up, the least busy first: who a gate asks. */
    public List<ServerBlockEntity> servers(Net net) {
        List<ServerBlockEntity> out = new ArrayList<>();
        for (Part p : net.parts) {
            if (p.kind() == Kind.SERVER && p.model() && block(p.at()) instanceof ServerBlockEntity s && s.model() != null) {
                out.add(s);
            }
        }
        out.sort(java.util.Comparator.comparingInt(ServerBlockEntity::waiting));
        return out;
    }

    /** What stands at a place, if it is loaded just now. */
    @Nullable
    private net.minecraft.world.level.block.entity.BlockEntity block(GlobalPos at) {
        ServerLevel level = server.getLevel(at.dimension());
        return level != null && level.isLoaded(at.pos()) ? level.getBlockEntity(at.pos()) : null;
    }

    // ---------------------------------------------------------------- every tick

    private void tick() {
        graph();
        int tick = ticks++;
        if (tick % 20 == 0) {
            for (Net net : nets.values()) {
                measure(net);
                net.itemsPerSecond = net.itemsNow;
                net.fePerSecond = net.feNow;
                net.itemsNow = 0;
                net.feNow = 0;
            }
            states();
            tickets();
        }
        if (tick % SYNC_EVERY == 0) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (TerminalItem.held(player) != null) {
                    tell(player);
                }
            }
            told.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
        }
    }

    private void measure(Net net) {
        int capacity = 0, answers = 0;
        boolean reads = false;
        for (Part p : net.parts) {
            if (p.kind() == Kind.SERVER && p.model() && block(p.at()) instanceof ServerBlockEntity s) {
                ModelInfo model = s.model();
                if (model != null) {
                    capacity += Math.round(s.rate());
                    answers += s.answersLastSecond();
                    reads |= model.passes(Benchmark.ORDERS);
                }
            }
        }
        net.capacity = capacity;
        net.reads = reads;
        net.answers = answers;
    }

    /** Lights what is loaded as its network says, and forgets a part whose block is no longer there. */
    private void states() {
        List<GlobalPos> gone = new ArrayList<>();
        for (Net net : nets.values()) {
            for (Part p : net.parts) {
                ServerLevel level = server.getLevel(p.at().dimension());
                if (level == null || !level.isLoaded(p.at().pos())) {
                    continue;
                }
                BlockPos pos = p.at().pos();
                BlockState state = level.getBlockState(pos);
                switch (p.kind()) {
                    case RELAY -> {
                        if (!state.is(ModBlocks.RELAY.get())) {
                            gone.add(p.at());
                        } else if (state.getValue(RelayBlock.LIVE) != net.live()) {
                            for (BlockPos up = pos; level.getBlockState(up).is(ModBlocks.RELAY.get()); up = up.above()) {
                                level.setBlock(up, level.getBlockState(up).setValue(RelayBlock.LIVE, net.live()), Block.UPDATE_CLIENTS);
                            }
                        }
                    }
                    case GATE -> {
                        if (!state.is(ModBlocks.RELAY_GATE.get())) {
                            gone.add(p.at());
                        } else if (state.getValue(GateBlock.OPEN) != net.open()) {
                            level.setBlock(pos, state.setValue(GateBlock.OPEN, net.open()), Block.UPDATE_CLIENTS);
                        }
                    }
                    case LINK -> {
                        boolean working = net.open() && (net.itemsPerSecond > 0 || net.fePerSecond > 0);
                        if (!state.is(ModBlocks.RELAY_LINK.get())) {
                            gone.add(p.at());
                        } else if (state.getValue(RelayLinkBlock.WORKING) != working) {
                            level.setBlock(pos, state.setValue(RelayLinkBlock.WORKING, working), Block.UPDATE_CLIENTS);
                            if (working) {
                                level.players().stream().filter(player -> player.distanceToSqr(pos.getCenter()) < 48 * 48)
                                        .forEach(player -> Awards.grant(player, "deploy/link"));
                            }
                        }
                    }
                    case SERVER -> {
                        if (!state.is(ModBlocks.SERVER.get())) {
                            gone.add(p.at());
                        }
                    }
                }
            }
        }
        for (GlobalPos at : gone) {
            ServerLevel level = server.getLevel(at.dimension());
            if (level != null) {
                leave(level, at.pos());
            }
        }
    }

    /**
     * A live network with anything but a lone server on it keeps the chunks around its parts running, as force-loading
     * does, so a base goes on working and answering after its player has gone. What it holds is saved with the world and
     * comes back loaded.
     */
    private void tickets() {
        int radius = ResidualConfig.RELAY_HOLDS.get();
        Map<ResourceKey<Level>, LongSet> wanted = new HashMap<>();
        if (radius >= 0) {
            for (Net net : nets.values()) {
                if (net.live() && net.parts.size() > 1) {
                    for (Part p : net.parts) {
                        ChunkPos c = ChunkPos.containing(p.at().pos());
                        LongSet chunks = wanted.computeIfAbsent(p.at().dimension(), d -> new LongOpenHashSet());
                        for (int dx = -radius; dx <= radius; dx++) {
                            for (int dz = -radius; dz <= radius; dz++) {
                                chunks.add(ChunkPos.pack(c.x() + dx, c.z() + dz));
                            }
                        }
                    }
                }
            }
        }
        held.forEach((dimension, chunks) -> {
            ServerLevel level = server.getLevel(dimension);
            LongSet keep = wanted.getOrDefault(dimension, LongSet.of());
            chunks.removeIf(chunk -> {
                if (keep.contains(chunk)) {
                    return false;
                }
                if (level != null) {
                    TICKETS.forceChunk(level, OWNER, ChunkPos.getX(chunk), ChunkPos.getZ(chunk), false, false);
                }
                setDirty();
                return true;
            });
        });
        int room = HOLDS_A_SECOND;
        for (Map.Entry<ResourceKey<Level>, LongSet> e : wanted.entrySet()) {
            ServerLevel level = server.getLevel(e.getKey());
            LongSet chunks = held.computeIfAbsent(e.getKey(), d -> new LongOpenHashSet());
            for (long chunk : e.getValue()) {
                if (level != null && room > 0 && !chunks.contains(chunk)) {
                    TICKETS.forceChunk(level, OWNER, ChunkPos.getX(chunk), ChunkPos.getZ(chunk), true, false);
                    chunks.add(chunk);
                    setDirty();
                    room--;
                }
            }
        }
    }

    /** Sends a player how the network stands: its parts when they changed since last told, its numbers always. */
    public void tell(ServerPlayer player) {
        int now = revision();
        boolean full = !Integer.valueOf(now).equals(told.put(player.getUUID(), now));
        PacketDistributor.sendToPlayer(player, RelaySync.of(this, full));
    }
}
