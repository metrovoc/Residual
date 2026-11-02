package io.github.metrovoc.residual.scope;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.metrovoc.residual.Awards;
import io.github.metrovoc.residual.Residual;
import io.github.metrovoc.residual.bench.Design;
import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.graph.GraphSpec;
import io.github.metrovoc.residual.learn.Benchmark;
import io.github.metrovoc.residual.learn.Contamination;
import io.github.metrovoc.residual.learn.Task;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * What everyone on this server has made: for each benchmark, every player's saved designs and what they scored; for each spec, every
 * player's verified designs and what they take to lay down. Kept with the world, so friends can compare what they built.
 */
public final class Records extends SavedData {
    /** The most a board keeps. */
    private static final int MOST = 256;

    /**
     * One design's best on one benchmark.
     *
     * @param score        what the test said
     * @param fresh        what a look-alike said, the same when there can be no look-alike
     * @param contaminated the test flatters it beyond what chance explains: it stays on the board, under the rest
     * @param design       its machines, as {@code op count} pairs
     */
    public record Entry(String player, float score, float fresh, boolean contaminated, long parameters, String design, long hash) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("player").forGetter(Entry::player),
                Codec.FLOAT.fieldOf("score").forGetter(Entry::score),
                Codec.FLOAT.fieldOf("fresh").forGetter(Entry::fresh),
                Codec.BOOL.fieldOf("contaminated").forGetter(Entry::contaminated),
                Codec.LONG.fieldOf("parameters").forGetter(Entry::parameters),
                Codec.STRING.fieldOf("design").forGetter(Entry::design),
                Codec.LONG.fieldOf("hash").forGetter(Entry::hash)
        ).apply(i, Entry::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC = new StreamCodec<>() {
            @Override
            public Entry decode(RegistryFriendlyByteBuf buf) {
                return new Entry(buf.readUtf(), buf.readFloat(), buf.readFloat(), buf.readBoolean(), buf.readVarLong(), buf.readUtf(), buf.readLong());
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, Entry e) {
                buf.writeUtf(e.player);
                buf.writeFloat(e.score);
                buf.writeFloat(e.fresh);
                buf.writeBoolean(e.contaminated);
                buf.writeVarLong(e.parameters);
                buf.writeUtf(e.design);
                buf.writeLong(e.hash);
            }
        };
    }

    /** One player's verified design for one spec: what it takes to lay down, and how many parts it has. */
    public record Laid(String player, int area, int parts) {
        static final Codec<Laid> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("player").forGetter(Laid::player),
                Codec.INT.fieldOf("area").forGetter(Laid::area),
                Codec.INT.fieldOf("parts").forGetter(Laid::parts)
        ).apply(i, Laid::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, Laid> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Laid::player, ByteBufCodecs.VAR_INT, Laid::area, ByteBufCodecs.VAR_INT, Laid::parts, Laid::new);
    }

    /** What is about to go on the boards, worked out off the server thread. */
    public record Submission(long parameters, String design, long hash, Map<Benchmark, Contamination.Check> checks, Map<String, Float> scores, boolean causal) {
    }

    private record Saved(Map<String, List<Entry>> boards, Map<String, List<Laid>> designs) {
        static final Codec<Saved> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.unboundedMap(Codec.STRING, Entry.CODEC.listOf()).fieldOf("boards").forGetter(Saved::boards),
                Codec.unboundedMap(Codec.STRING, Laid.CODEC.listOf()).fieldOf("designs").forGetter(Saved::designs)
        ).apply(i, Saved::new));
    }

    private static final Codec<Records> CODEC = Saved.CODEC.xmap(Records::new, r -> new Saved(r.boards, r.designs));
    public static final SavedDataType<Records> TYPE = new SavedDataType<>(Residual.id("records"), Records::new, CODEC);

    private final Map<String, List<Entry>> boards = new HashMap<>();
    private final Map<String, List<Laid>> designs = new HashMap<>();

    private Records() {
    }

    private Records(Saved saved) {
        saved.boards.forEach((k, v) -> boards.put(k, new ArrayList<>(v)));
        saved.designs.forEach((k, v) -> designs.put(k, new ArrayList<>(v)));
    }

    public static Records of(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    /** A design's machines, as {@code op count} pairs, and a number that is the same only for the same design. */
    public static String summary(GraphSpec spec) {
        Map<String, Integer> counts = new TreeMap<>();
        for (GraphSpec.Node n : spec.nodes) {
            counts.merge(n.op.id, 1, Integer::sum);
        }
        StringBuilder out = new StringBuilder();
        counts.forEach((op, count) -> out.append(out.isEmpty() ? "" : ",").append(op).append(' ').append(count));
        return out.toString();
    }

    public static long hash(GraphSpec spec) {
        long h = spec.output;
        for (GraphSpec.Node n : spec.nodes) {
            h = 31 * h + n.op.id.hashCode();
            h = 31 * h + n.config.asMap().hashCode();
            for (int in : n.inputs) {
                h = 31 * h + in;
            }
        }
        return h;
    }

    /** Off the server thread: the look-alike's say on what a model passes, for the boards. */
    public static Submission submission(CompiledGraph model, Map<String, Float> scores, boolean causal) {
        Map<Benchmark, Contamination.Check> checks = new HashMap<>();
        for (Benchmark a : Benchmark.values()) {
            float s = scores.getOrDefault(a.id, Float.NaN);
            boolean passes = !Float.isNaN(s) && (a.metric == Task.Metric.LOSS ? s <= a.target : s >= a.target);
            if (passes) {
                checks.put(a, Contamination.check(model, a.test(model.env.tokenizer()), a));
            }
        }
        return new Submission(model.parameterCount(), summary(model.spec), hash(model.spec), checks, scores, causal);
    }

    /** Puts a saved model on the boards of what it passes. */
    public void submit(ServerPlayer player, Submission s) {
        if (!s.causal()) {
            return;
        }
        String name = player.getName().getString();
        s.checks().forEach((a, check) -> {
            List<Entry> board = boards.computeIfAbsent(a.id, k -> new ArrayList<>());
            Entry entry = new Entry(name, s.scores().getOrDefault(a.id, check.scored()), check.fresh(), check.contaminated(), s.parameters(), s.design(), s.hash());
            for (int i = 0; i < board.size(); i++) {
                Entry had = board.get(i);
                if (had.player().equals(name) && had.hash() == s.hash()) {
                    if (better(a, entry, had)) {
                        board.set(i, entry);
                    }
                    entry = null;
                    break;
                }
            }
            if (entry != null) {
                board.add(entry);
            }
            board.sort(order(a));
            while (board.size() > MOST) {
                board.removeLast();
            }
            if (!check.contaminated() && !board.isEmpty() && board.getFirst().player().equals(name) && board.stream().anyMatch(e -> !e.player().equals(name))) {
                Awards.grant(player, "evaluation/first");
            }
        });
        setDirty();
    }

    private static boolean better(Benchmark a, Entry x, Entry y) {
        return order(a).compare(x, y) < 0;
    }

    /** Clean before contaminated, then by score, then the smaller first. */
    public static Comparator<Entry> order(Benchmark a) {
        Comparator<Entry> byScore = a.metric == Task.Metric.LOSS ? Comparator.comparingDouble(Entry::score) : Comparator.comparingDouble(e -> -e.score());
        return Comparator.comparing(Entry::contaminated).thenComparing(byScore).thenComparingLong(Entry::parameters);
    }

    public List<Entry> board(Benchmark a) {
        return List.copyOf(boards.getOrDefault(a.id, List.of()));
    }

    /** Puts a verified design on its spec's list: each player's smallest. */
    public void laid(ServerPlayer player, String spec, Design design) {
        String name = player.getName().getString();
        List<Laid> list = designs.computeIfAbsent(spec, k -> new ArrayList<>());
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).player().equals(name)) {
                if (design.area() < list.get(i).area()) {
                    list.set(i, new Laid(name, design.area(), design.parts()));
                    list.sort(Comparator.comparingInt(Laid::area));
                    setDirty();
                }
                return;
            }
        }
        list.add(new Laid(name, design.area(), design.parts()));
        list.sort(Comparator.comparingInt(Laid::area));
        setDirty();
    }

    public List<Laid> designs(String spec) {
        return List.copyOf(designs.getOrDefault(spec, List.of()));
    }
}
