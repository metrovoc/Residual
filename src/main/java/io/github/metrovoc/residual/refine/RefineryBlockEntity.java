package io.github.metrovoc.residual.refine;

import io.github.metrovoc.residual.Awards;
import io.github.metrovoc.residual.Residual;
import io.github.metrovoc.residual.ResidualConfig;
import io.github.metrovoc.residual.energy.PowerBlockEntity;
import io.github.metrovoc.residual.energy.PowerDeviceBlock;
import io.github.metrovoc.residual.item.DriveItem;
import io.github.metrovoc.residual.item.DumpItem;
import io.github.metrovoc.residual.item.TokenCellItem;
import io.github.metrovoc.residual.item.TokenizerItem;
import io.github.metrovoc.residual.learn.Archive;
import io.github.metrovoc.residual.learn.Benchmark;
import io.github.metrovoc.residual.learn.Vocab;
import io.github.metrovoc.residual.model.ModelInfo;
import io.github.metrovoc.residual.model.ModelLink;
import io.github.metrovoc.residual.refine.RefinerySync.Row;
import io.github.metrovoc.residual.registry.ModBlockEntities;
import io.github.metrovoc.residual.registry.ModComponents;
import io.github.metrovoc.residual.train.Jobs;
import io.github.metrovoc.residual.world.RuinKinds;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

/**
 * A Refinery machine at work. The Intake keeps the pass: it walks the line of machines from its front, and hands each what the one
 * before it kept. Each machine runs its stage off the server thread and takes at least as long as its pace says, drawing power all
 * the while; what it leaves is the next one's text. The Intake keeps every cut, so a measure trains a small reader on each and
 * every machine can say what it did to held-out loss.
 */
public class RefineryBlockEntity extends PowerBlockEntity implements MenuProvider {
    /** FE a tick while it works, and what it can keep. */
    public static final int RATE = 40, CAPACITY = 16_000, IN_RATE = 2_000;
    /** How far apart two machines of a line can stand, with nothing solid between. */
    public static final int REACH = 12;
    /** Characters a machine takes through in a tick, and the fewest ticks any work takes. */
    private static final int PACE = 5_000, LEAST = 60;
    /** What one tank holds. */
    public static final long TANK_HOLDS = 1_000_000;
    /** Screen buttons: a setting and its value, and the Intake's two actions. */
    public static final int SETTING_STEP = 32, START = 1000, MEASURE = 1001;
    /** What its screen shows, as container data: its state, the work done and to do, and its settings after them. */
    public static final int STATE = 0, WORKED = 1, NEEDED = 2, SETTINGS = 3;
    /** How much a stage's domain sample holds, for the stream to show. */
    private static final int SAMPLE = 120;
    /** Docs asked of a model in one go: one answer of a Model Server. */
    private static final int CHUNK = 64;
    /** How near a player has to be to earn what a machine earns or learn what a tank is told. */
    private static final int HEARD = 48;
    /** A move in held-out loss smaller than this is nothing, whatever the seeds say. */
    private static final double NOISE = 0.002;
    /** Tokens in tanks that earn the milestone for them. */
    private static final long MILLION = 1_000_000;

    public enum State { IDLE, WORKING, NO_POWER, MEASURING }

    private final Stage stage;
    private final SimpleContainer items;
    private final int[] settings;
    private State state = State.IDLE;
    private List<Row> rows = List.of();
    /** What a measure found of it. */
    private List<Row> held = List.of();
    private int worked, needed;
    private boolean busy;
    @Nullable
    private Outcome outcome;
    /** The pass it is at work for, the text that pass gave it, and when the pass last looked in. */
    @Nullable
    private Pass owner;
    @Nullable
    private Supply taken;
    private long lookedIn;
    /** What the stream leaving it looks like: how wide, of what, and where to. */
    private boolean flowing;
    private int lanes;
    private float[] shares = new float[Domain.values().length];
    private String[] samples = new String[Domain.values().length];
    /** What it took out falls from it: the start of that, and the share of the text that stayed. */
    private String dropped = "";
    private float kept = 1;
    @Nullable
    private BlockPos next;
    /** What it did to held-out loss, floated above it; NaN before a measure. */
    private float delta = Float.NaN;
    private boolean within;
    /** A tank: what it holds, what it shows of that, its supply and the characters it holds of it. */
    @Nullable
    private TokenSupply supply;
    private float fill, fillFrom, fillTo;
    /** A mixer: each domain's share of the text that last came through. */
    private float[] textShares = new float[Domain.values().length];
    /** A classifier: how many documents its model settled in its last pass. */
    private int judged;
    /** A tank: the tokenizer it last counted with. */
    @Nullable
    private Vocab counted;
    private int pourTicks;
    /** The Intake: the pass under way, the cuts of the last one and the machines they came from. */
    @Nullable
    private Pass pass;
    private final List<Supply> cuts = new ArrayList<>();
    private final List<BlockPos> cutAt = new ArrayList<>();
    private volatile boolean measuring;
    private int measuredCuts;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case STATE -> state.ordinal();
                case WORKED -> Math.min(worked, Short.MAX_VALUE);
                case NEEDED -> Math.min(needed, Short.MAX_VALUE);
                default -> settings[i - SETTINGS];
            };
        }

        @Override
        public void set(int i, int value) {
        }

        @Override
        public int getCount() {
            return SETTINGS + settings.length;
        }
    };

    /** What a stage made of what came in. */
    private record Outcome(Supply out, List<Row> rows, @Nullable TokenSupply stored, @Nullable String earns, String[] samples, String dropped, float kept, List<String> told) {
        Outcome(Supply out, List<Row> rows) {
            this(out, rows, null, null);
        }

        Outcome(Supply out, List<Row> rows, @Nullable TokenSupply stored, @Nullable String earns) {
            this(out, rows, stored, earns, new String[0], "", 1, List.of());
        }

        /** With what the world shows of it, worked out off the server thread with the rest. */
        Outcome shown(Supply in) {
            long before = Doc.chars(in.docs()), after = Doc.chars(out.docs());
            return new Outcome(out, rows, stored, earns, RefineryBlockEntity.samples(out), RefineryBlockEntity.dropped(in, out), before == 0 ? 1 : Math.min(1f, (float) after / before), told);
        }

        Outcome telling(List<String> places) {
            return new Outcome(out, rows, stored, earns, samples, dropped, kept, places);
        }
    }

    /** One pass of text down the line. */
    private static final class Pass {
        final List<BlockPos> line;
        Supply text = Supply.plain(List.of());
        long raw;
        int at;
        /** Ticks the tanks still fill for, with the whole line flowing. */
        int pouring = -1;

        Pass(List<BlockPos> line) {
            this.line = line;
        }
    }

    public RefineryBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.REFINERY.get(), pos, state, CAPACITY, IN_RATE, 0);
        stage = ((RefineryBlock) state.getBlock()).stage;
        items = new SimpleContainer(Math.max(1, stage.slots)) {
            @Override
            public void setChanged() {
                super.setChanged();
                RefineryBlockEntity.this.setChanged();
                slotsChanged();
            }
        };
        settings = stage.settings.stream().mapToInt(Stage.Setting::start).toArray();
        Arrays.fill(samples, "");
    }

    @Override
    public Role role() {
        return Role.MACHINE;
    }

    public Stage stage() {
        return stage;
    }

    public SimpleContainer items() {
        return items;
    }

    public ContainerData data() {
        return data;
    }

    // ---------------------------------------------------------------- the world's view of it

    public boolean flowing() {
        return flowing;
    }

    public int lanes() {
        return lanes;
    }

    public float[] shares() {
        return shares;
    }

    public String[] samples() {
        return samples;
    }

    public String dropped() {
        return dropped;
    }

    public float kept() {
        return kept;
    }

    @Nullable
    public BlockPos next() {
        return next;
    }

    public float delta() {
        return delta;
    }

    public boolean withinNoise() {
        return within;
    }

    public float fill() {
        return fill;
    }

    public int judged() {
        return judged;
    }

    /** What a tank holds, if it holds anything. */
    @Nullable
    public TokenSupply supply() {
        return supply;
    }

    private void show() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private void working(boolean on) {
        BlockState here = getBlockState();
        if (level != null && here.getValue(PowerDeviceBlock.WORKING) != on) {
            level.setBlock(worldPosition, here.setValue(PowerDeviceBlock.WORKING, on), Block.UPDATE_CLIENTS);
        }
    }

    /** What its display shows now. */
    public List<Row> rows() {
        List<Row> out = new ArrayList<>(rows.isEmpty() ? idleRows() : rows);
        if (stage == Stage.INTAKE) {
            out.addAll(0, intakeRows());
            if (pass != null && pass.pouring < 0 && level != null) {
                out.addFirst(Row.of("residual.refinery.intake.at", "").args(level.getBlockState(pass.line.get(pass.at)).getBlock().getDescriptionId()).tone(RefinerySync.LIT));
            }
            if (measuring) {
                out.addFirst(Row.of("residual.refinery.intake.measured", measuredCuts + " / " + cuts.size()).tone(RefinerySync.LIT));
            }
        }
        out.addAll(held);
        return out;
    }

    private List<Row> intakeRows() {
        int drives = 0, dumps = 0;
        for (int i = 0; i < items.getContainerSize(); i++) {
            drives += items.getItem(i).getItem() instanceof DriveItem ? 1 : 0;
            dumps += items.getItem(i).getItem() instanceof DumpItem ? 1 : 0;
        }
        return List.of(Row.of("residual.refinery.intake.holds", drives + " · " + dumps).hint("residual.refinery.intake.holds_hint"));
    }

    private List<Row> idleRows() {
        return switch (stage) {
            case MIXER -> mixRows(new float[Domain.values().length]);
            case TANK -> List.of(Row.of("residual.refinery.tank.empty", "").tone(RefinerySync.DIM));
            case INTAKE -> List.of();
            default -> List.of(Row.of("residual.refinery.waiting", "").tone(RefinerySync.DIM).hint("residual.refinery." + stage.id + ".hint"));
        };
    }

    // ---------------------------------------------------------------- the line

    /** The machines text runs through from this one, itself first. Past a tank only another tank can follow. */
    private List<BlockPos> walk(ServerLevel level) {
        List<BlockPos> out = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        BlockPos at = worldPosition;
        while (seen.add(at)) {
            out.add(at);
            BlockPos after = seek(level, at);
            Stage here = ((RefineryBlock) level.getBlockState(at).getBlock()).stage;
            if (after == null || here == Stage.TANK && ((RefineryBlock) level.getBlockState(after).getBlock()).stage != Stage.TANK) {
                break;
            }
            at = after;
        }
        return out;
    }

    /** The machine in front of the one at {@code at}, with nothing solid between; no one feeds an Intake. */
    @Nullable
    private static BlockPos seek(ServerLevel level, BlockPos at) {
        Direction facing = level.getBlockState(at).getValue(PowerDeviceBlock.FACING);
        for (int i = 1; i <= REACH; i++) {
            BlockPos p = at.relative(facing, i);
            BlockState there = level.getBlockState(p);
            if (there.getBlock() instanceof RefineryBlock block) {
                return block.stage == Stage.INTAKE ? null : p;
            }
            if (!there.getCollisionShape(level, p).isEmpty()) {
                return null;
            }
        }
        return null;
    }

    @Nullable
    private RefineryBlockEntity machine(ServerLevel level, BlockPos at) {
        return level.getBlockEntity(at) instanceof RefineryBlockEntity be ? be : null;
    }

    /** A screen button: a setting and its value, or one of the Intake's actions. */
    public void press(int id) {
        if (level instanceof ServerLevel server) {
            if (id == START && stage == Stage.INTAKE && pass == null && !measuring) {
                begin(server);
            } else if (id == MEASURE && stage == Stage.INTAKE && pass == null && !measuring) {
                if (cuts.isEmpty()) {
                    rows = List.of(Row.of("residual.refinery.intake.refine_first", "").tone(RefinerySync.BAD));
                } else {
                    measure(server);
                }
            } else if (id >= 0 && id / SETTING_STEP < settings.length) {
                Stage.Setting s = stage.settings.get(id / SETTING_STEP);
                settings[id / SETTING_STEP] = Math.floorMod(id % SETTING_STEP, s.values());
                setChanged();
                if (stage == Stage.MIXER && !rows.isEmpty()) {
                    // what the mix would come to, from the share of the text each domain had
                    rows = mixRows(drawn());
                }
            }
        }
    }

    // ---------------------------------------------------------------- a pass

    private void begin(ServerLevel level) {
        if (items.isEmpty()) {
            rows = List.of(Row.of("residual.refinery.intake.nothing", "").tone(RefinerySync.BAD));
            return;
        }
        List<BlockPos> line = walk(level);
        pass = new Pass(line);
        cuts.clear();
        cutAt.clear();
        rows = List.of(Row.of("residual.refinery.intake.reading", "").tone(RefinerySync.LIT));
        for (BlockPos p : line) {
            RefineryBlockEntity m = machine(level, p);
            if (m != null) {
                m.held = List.of();
                m.delta = Float.NaN;
                m.next = seek(level, p);
                if (m != this) {
                    m.rows = List.of();
                }
                m.flowing = false;
                m.show();
            }
        }
    }

    /** The Intake moves the pass along. */
    private void runPass(ServerLevel level) {
        Pass p = pass;
        if (p.pouring >= 0) {
            if (p.pouring-- == 0) {
                endPass(level);
            } else {
                for (BlockPos at : p.line) {
                    RefineryBlockEntity m = machine(level, at);
                    if (m != null && m.stage == Stage.TANK) {
                        m.pour(p.pouring);
                    }
                }
            }
            return;
        }
        RefineryBlockEntity m = machine(level, p.line.get(p.at));
        if (m == null) {
            rows = List.of(Row.of("residual.refinery.intake.broken", "").tone(RefinerySync.BAD));
            endPass(level);
            return;
        }
        if (m.busy && m.owner != p) {
            // another Intake's pass has it for now
            return;
        }
        m.lookedIn = level.getGameTime();
        if (!m.busy) {
            m.start(level, p);
        } else if (m.outcome != null && m.worked >= m.needed) {
            m.finish(level, p);
            if (m.stage == Stage.TANK) {
                pourTo(level, p, m.supply);
            } else if (++p.at >= p.line.size()) {
                // a line with no tank keeps nothing: its stream shows a moment and is gone
                p.pouring = LEAST;
            }
        }
    }

    /** Every tank of the line draws on the supply the first stored, and fills with its share of it. */
    private void pourTo(ServerLevel level, Pass p, @Nullable TokenSupply stored) {
        p.pouring = stored == null ? 0 : Math.max(LEAST, (int) (stored.characters() / PACE));
        long left = stored == null ? 0 : stored.characters();
        RefineryBlockEntity first = machine(level, p.line.get(p.at));
        for (int i = p.at; i < p.line.size(); i++) {
            RefineryBlockEntity tank = machine(level, p.line.get(i));
            if (tank != null) {
                tank.supply = stored;
                tank.rows = first.rows;
                tank.fillFrom = tank.fill;
                tank.fillTo = Math.min(1f, (float) left / TANK_HOLDS);
                tank.pourTicks = p.pouring;
                left = Math.max(0, left - TANK_HOLDS);
                tank.next = i + 1 < p.line.size() ? p.line.get(i + 1) : null;
                tank.flowing = tank.next != null;
                tank.lanes = Math.max(1, first.lanes);
                tank.shares = first.shares;
                tank.samples = first.samples;
                if (stored != null) {
                    tank.fillCell();
                }
                tank.show();
            }
        }
        if (stored != null) {
            award(level, "refinery/tokens");
            if (stored.tokens() >= MILLION) {
                award(level, "refinery/million");
            }
        }
    }

    /** Whoever is near a machine when it does something earns what it earns. */
    private void award(ServerLevel level, String id) {
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(worldPosition.getCenter()) < HEARD * HEARD) {
                Awards.grant(player, id);
            }
        }
    }

    private void endPass(ServerLevel level) {
        for (BlockPos at : pass.line) {
            RefineryBlockEntity m = machine(level, at);
            if (m != null) {
                m.flowing = false;
                if (m.owner == pass) {
                    m.busy = false;
                    m.state = State.IDLE;
                    m.working(false);
                }
                m.show();
            }
        }
        pass = null;
    }

    private void start(ServerLevel level, Pass p) {
        busy = true;
        owner = p;
        outcome = null;
        worked = 0;
        Supply in = p.text;
        taken = in;
        needed = Math.max(LEAST, (int) Math.min(Integer.MAX_VALUE, Doc.chars(in.docs()) / PACE));
        int[] set = settings.clone();
        switch (stage) {
            case INTAKE -> off(level, in, readInput(level));
            case DUPLICATES -> off(level, in, () -> sieve(in, set[0]));
            case FILTERS -> off(level, in, () -> filter(in, set));
            case CLASSIFIER -> classify(level, in, set[0]);
            case PROGRAMS -> {
                List<String> pages = pages();
                long seed = level.getRandom().nextLong();
                off(level, in, () -> press(in, Programs.Program.values()[set[0]], Stage.SHARES[set[1]], pages, seed));
            }
            case MIXER -> off(level, in, () -> mix(in, set));
            case CONTAMINATION -> off(level, in, () -> check(in, set[0] == 1));
            case TANK -> {
                long holds = (p.line.size() - p.at) * TANK_HOLDS;
                needed = Math.max(LEAST, (int) (Math.min(holds, Doc.chars(in.docs())) / PACE));
                Vocab vocab = TokenizerItem.vocab(items.getItem(Stage.TOKENIZER));
                MinecraftServer server = level.getServer();
                // a line's tanks keep one supply, filled again by each pass, so a cell drawn from them goes on drawing from them
                UUID id = supply != null ? supply.id() : UUID.randomUUID();
                off(level, in, () -> store(server, in, holds, vocab, id));
            }
        }
    }

    /** Runs a stage off the server thread and leaves its outcome to be picked up. */
    private void off(ServerLevel level, Supply in, Supplier<Outcome> work) {
        MinecraftServer server = level.getServer();
        Jobs.EXECUTOR.execute(() -> {
            Outcome done;
            try {
                done = work.get().shown(in);
            } catch (RuntimeException e) {
                Residual.LOG.error("A Refinery stage failed", e);
                done = new Outcome(in, List.of(Row.of("residual.refinery.failed", "").tone(RefinerySync.BAD))).shown(in);
            }
            Outcome o = done;
            Jobs.back(server, () -> {
                if (taken == in) {
                    outcome = o;
                }
            });
        });
    }

    private void finish(ServerLevel level, Pass p) {
        busy = false;
        taken = null;
        state = State.IDLE;
        working(false);
        Outcome o = outcome;
        outcome = null;
        rows = o.rows();
        supply = o.stored() != null ? o.stored() : supply;
        p.text = o.out();
        if (stage == Stage.INTAKE) {
            p.raw = Doc.chars(o.out().docs());
        }
        if (stage != Stage.TANK) {
            RefineryBlockEntity intake = machine(level, p.line.getFirst());
            intake.cuts.add(o.out());
            intake.cutAt.add(worldPosition);
        }
        if (o.earns() != null) {
            award(level, o.earns());
        }
        // whoever stands by while text comes into the tanks learns of the places it tells of
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(worldPosition.getCenter()) < HEARD * HEARD) {
                o.told().forEach(word -> RuinKinds.learn(player, word));
            }
        }
        // the stream it sends on, as wide as what is left of what came in
        long left = Doc.chars(o.out().docs());
        lanes = p.raw == 0 ? 1 : Math.clamp(Math.round(9f * left / p.raw), 1, 12);
        shares = o.out().shares();
        samples = o.samples();
        dropped = o.dropped();
        kept = o.kept();
        flowing = next != null && left > 0;
        level.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.5f, 1.2f);
        show();
    }

    /** The start of the first thing a stage took out, to fall from the stream. */
    private static String dropped(Supply in, Supply out) {
        Set<Doc> kept = new HashSet<>(out.docs());
        for (Doc d : in.docs()) {
            if (!kept.contains(d) && !d.parts().isEmpty()) {
                String p = d.parts().getFirst();
                return p.substring(0, Math.min(p.length(), SAMPLE / 2)).replace('\n', ' ');
            }
        }
        return "";
    }

    /** A little of each domain's writing for the stream to carry, on one line. */
    private static String[] samples(Supply s) {
        String[] out = new String[Domain.values().length];
        Arrays.fill(out, "");
        Random rng = new Random(s.docs().size());
        for (int tries = 0; tries < 400 && !s.docs().isEmpty(); tries++) {
            Doc d = s.docs().get(rng.nextInt(s.docs().size()));
            int k = d.domain().ordinal();
            if (out[k].length() < SAMPLE && !d.parts().isEmpty()) {
                String part = d.parts().get(rng.nextInt(d.parts().size()));
                int from = part.length() <= SAMPLE ? 0 : rng.nextInt(part.length() - SAMPLE);
                out[k] = (out[k] + " " + part.substring(from, Math.min(part.length(), from + SAMPLE))).replace('\n', ' ').strip();
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- the stages

    /** What the Intake's slots hold, read off the server thread; each copy of a record is a doc of its own. */
    private Supplier<Outcome> readInput(ServerLevel level) {
        List<Map<Integer, BitSet>> drives = new ArrayList<>();
        List<Long> dumps = new ArrayList<>();
        for (int i = 0; i < items.getContainerSize(); i++) {
            ItemStack stack = items.getItem(i);
            if (stack.getItem() instanceof DriveItem) {
                drives.add(DriveItem.contents(stack));
            } else if (stack.getItem() instanceof DumpItem) {
                dumps.add(DumpItem.seed(stack, level.getRandom()));
            }
        }
        return () -> {
            List<Doc> docs = new ArrayList<>();
            for (Map<Integer, BitSet> drive : drives) {
                drive.forEach((id, lost) -> {
                    Archive.Record r = Archive.get(id);
                    if (r != null && !Archive.heldOut(id)) {
                        List<String> legible = r.legible(lost);
                        if (!legible.isEmpty()) {
                            docs.add(new Doc(legible, Domain.filed(r.filed())));
                        }
                    }
                });
            }
            for (long seed : dumps) {
                Dump.read(seed).forEach(p -> docs.add(p.doc()));
            }
            Supply out = Supply.plain(docs);
            return new Outcome(out, List.of(Row.of("residual.refinery.intake.docs", count(docs.size())),
                    Row.of("residual.refinery.out", chars(Doc.chars(docs)))));
        };
    }

    private static Outcome sieve(Supply in, int threshold) {
        Duplicates.Result r = Duplicates.run(in.docs(), threshold);
        long before = Doc.chars(in.docs()), after = Doc.chars(r.kept());
        return new Outcome(in.withDocs(r.kept()), List.of(inRow(before), outRow(before, after),
                Row.of("residual.refinery.duplicate_sieve.exact", count(r.exactDocs()) + " · " + chars(r.exactChars())).hint("residual.refinery.duplicate_sieve.exact_hint"),
                Row.of("residual.refinery.duplicate_sieve.near", count(r.nearDocs()) + " · " + chars(r.nearChars()))
                        .hint("residual.refinery.duplicate_sieve.near_hint", percent(Duplicates.chance(threshold, Duplicates.THRESHOLDS[threshold])),
                                count(r.compared()), histogram(r.similar())),
                Row.of("residual.refinery.duplicate_sieve.lines", count(r.lines()) + " · " + chars(r.lineChars())).hint("residual.refinery.duplicate_sieve.lines_hint")));
    }

    private static Outcome filter(Supply in, int[] set) {
        int on = 0;
        for (int i = 0; i < set.length; i++) {
            on |= set[i] << i;
        }
        Filters.Result r = Filters.run(in.docs(), on);
        long before = Doc.chars(in.docs()), after = Doc.chars(r.kept());
        List<Row> out = new ArrayList<>(List.of(inRow(before), outRow(before, after)));
        for (Filters.Rule rule : Filters.Rule.values()) {
            int k = rule.ordinal();
            out.add(Row.of("residual.refinery.filter_bed." + rule.id, set[k] == 0 ? "–" : count(r.docs()[k]) + " · " + chars(r.chars()[k]))
                    .tone(set[k] == 0 ? RefinerySync.DIM : RefinerySync.PLAIN)
                    .hint(r.example()[k].isEmpty() ? "residual.refinery.filter_bed." + rule.id + "_hint" : "residual.refinery.filter_bed.threw", r.example()[k].replace('\n', ' ')));
        }
        return new Outcome(in.withDocs(r.kept()), out);
    }

    /** Asks the model a chunk of docs at a time, in turn; what it cannot say goes to the rule, off the server thread. */
    private void classify(ServerLevel level, Supply in, int sure) {
        ItemStack slot = items.getItem(0);
        ModelLink link = slot.isEmpty() ? null : ModelLink.of(level, worldPosition, slot);
        ModelInfo info = link == null ? null : link.info();
        boolean usable = info != null && info.passes(Benchmark.QUALITY);
        float threshold = Stage.SURE[sure];
        float[] seen = new float[in.docs().size()];
        Arrays.fill(seen, -1);
        String model = info == null ? "" : info.tag();
        boolean known = info != null;
        if (!usable) {
            off(level, in, () -> sort(in, seen, threshold, known, false, model));
            return;
        }
        askModel(level, link, in, seen, new Quality.Reader[1], 0, threshold, model);
    }

    private void askModel(ServerLevel level, ModelLink link, Supply in, float[] seen, Quality.Reader[] reader, int from, float threshold, String model) {
        if (taken != in) {
            return;
        }
        List<Doc> docs = in.docs();
        if (from >= docs.size() || isRemoved()) {
            off(level, in, () -> sort(in, seen, threshold, true, true, model));
            int sure = 0;
            for (float p : seen) {
                sure += p >= threshold || p >= 0 && p <= 1 - threshold ? 1 : 0;
            }
            judged = sure;
            return;
        }
        List<Doc> chunk = docs.subList(from, Math.min(docs.size(), from + CHUNK));
        link.submit(level, m -> {
            // the first answer finds where this model tells good from bad, on documents it knows the answer to
            Quality.Reader knows = reader[0] != null ? reader[0] : Quality.calibrate(m);
            return new Asked(knows, Quality.read(m, knows, chunk));
        }, answer -> {
            if (answer != null) {
                reader[0] = answer.reader();
                System.arraycopy(answer.good(), 0, seen, from, answer.good().length);
            }
            worked = Math.max(worked, needed * from / Math.max(1, docs.size()));
            askModel(level, link, in, seen, reader, from + CHUNK, threshold, model);
        });
    }

    private record Asked(Quality.Reader reader, float[] good) {
    }

    /** Goes by what the model was sure of, and by the rule for the rest. */
    private static Outcome sort(Supply in, float[] seen, float threshold, boolean known, boolean usable, String model) {
        int keptByModel = 0, droppedByModel = 0, keptByRule = 0, droppedByRule = 0;
        long droppedChars = 0;
        String example = "";
        List<Doc> kept = new ArrayList<>(in.docs().size());
        for (int i = 0; i < seen.length; i++) {
            Doc d = in.docs().get(i);
            boolean good;
            if (seen[i] >= threshold) {
                good = true;
                keptByModel++;
            } else if (seen[i] >= 0 && seen[i] <= 1 - threshold) {
                good = false;
                droppedByModel++;
            } else {
                good = Quality.ruleGood(d);
                if (good) {
                    keptByRule++;
                } else {
                    droppedByRule++;
                }
            }
            if (good) {
                kept.add(d);
            } else {
                droppedChars += d.chars();
                if (example.isEmpty() && !d.parts().isEmpty()) {
                    String p = d.parts().getFirst();
                    example = p.substring(0, Math.min(p.length(), 120)).replace('\n', ' ');
                }
            }
        }
        long before = Doc.chars(in.docs()), after = before - droppedChars;
        Row who = usable ? Row.of("residual.refinery.quality_classifier.model", model).tone(RefinerySync.LIT).hint("residual.refinery.quality_classifier.model_hint", percent(threshold))
                : known ? Row.of("residual.refinery.quality_classifier.fails", model).tone(RefinerySync.BAD).hint("residual.refinery.quality_classifier.fails_hint")
                : Row.of("residual.refinery.quality_classifier.no_model", "").tone(RefinerySync.DIM).hint("residual.refinery.quality_classifier.no_model_hint");
        return new Outcome(in.withDocs(kept), List.of(inRow(before), outRow(before, after), who,
                Row.of("residual.refinery.quality_classifier.by_model", count(keptByModel) + " · " + count(droppedByModel)).hint("residual.refinery.quality_classifier.by_model_hint"),
                Row.of("residual.refinery.quality_classifier.by_rule", count(keptByRule) + " · " + count(droppedByRule))
                        .hint("residual.refinery.quality_classifier.by_rule_hint", percent(Quality.UNKNOWN)),
                Row.of("residual.refinery.quality_classifier.threw", example.isEmpty() ? "–" : "").tone(RefinerySync.DIM)
                        .hint("residual.refinery.quality_classifier.threw_hint", example)));
    }

    /** A book's pages, written or still being written. */
    private List<String> pages() {
        ItemStack book = items.getItem(0);
        if (book.has(DataComponents.WRITTEN_BOOK_CONTENT)) {
            return book.get(DataComponents.WRITTEN_BOOK_CONTENT).pages().stream().map(p -> p.raw().getString()).toList();
        }
        if (book.has(DataComponents.WRITABLE_BOOK_CONTENT)) {
            return book.get(DataComponents.WRITABLE_BOOK_CONTENT).pages().stream().map(p -> p.raw()).toList();
        }
        return List.of();
    }

    private static Outcome press(Supply in, Programs.Program program, float share, List<String> pages, long seed) {
        long before = Doc.chars(in.docs());
        List<Doc> written = Programs.write(program, (long) (before * share), pages, seed);
        List<Doc> all = new ArrayList<>(in.docs());
        all.addAll(written);
        String sample = written.isEmpty() ? "" : written.getFirst().parts().getFirst();
        return new Outcome(in.withDocs(all), List.of(inRow(before),
                Row.of("residual.refinery.program_press.wrote", chars(Doc.chars(written))).tone(written.isEmpty() ? RefinerySync.BAD : RefinerySync.LIT)
                        .hint(written.isEmpty() ? "residual.refinery.program_press.no_book" : "residual.refinery.program_press.sample", sample.substring(0, Math.min(sample.length(), 160)).replace('\n', ' ')),
                Row.of("residual.refinery.out", chars(Doc.chars(all)))));
    }

    private Outcome mix(Supply in, int[] set) {
        float[] mix = new float[Domain.values().length];
        for (int d = 0; d < mix.length; d++) {
            mix[d] = Stage.MIX[set[d]];
        }
        Supply out = new Supply(in.docs(), mix, set[Domain.values().length] - 1, Stage.AFTER[set[Domain.values().length + 1]]);
        textShares = Supply.plain(in.docs()).shares();
        return new Outcome(out, mixRows(out.shares()));
    }

    /** The share of practice each domain would get, of the text that last came through, as the mixer is set now. */
    private float[] drawn() {
        float[] out = new float[textShares.length];
        float total = 0;
        for (int d = 0; d < out.length; d++) {
            out[d] = textShares[d] * Stage.MIX[settings[d]];
            total += out[d];
        }
        for (int d = 0; d < out.length; d++) {
            out[d] = total == 0 ? 0 : out[d] / total;
        }
        return out;
    }

    private List<Row> mixRows(float[] drawn) {
        List<Row> out = new ArrayList<>();
        for (Domain d : Domain.values()) {
            int k = d.ordinal();
            out.add(Row.of("residual.refinery.domain." + d.id, percent(drawn[k]) + " · ×" + mixName(Stage.MIX[settings[k]])).bar(drawn[k])
                    .hint("residual.refinery.domain_mixer.mix_hint").setting(k));
        }
        return out;
    }

    private static String mixName(float m) {
        return m == (int) m ? Integer.toString((int) m) : String.format(Locale.ROOT, "%.1f", m);
    }

    private static Outcome check(Supply in, boolean remove) {
        Contamination.Result r = Contamination.run(in.docs(), remove);
        long before = Doc.chars(in.docs()), after = Doc.chars(r.kept());
        List<Row> out = new ArrayList<>(List.of(inRow(before), outRow(before, after),
                Row.of(remove ? "residual.refinery.contamination_check.removed" : "residual.refinery.contamination_check.left", count(r.lines()) + " · " + chars(r.chars()))
                        .tone(r.lines() > 0 ? RefinerySync.BAD : RefinerySync.GOOD).hint("residual.refinery.contamination_check.lines_hint")));
        List<Contamination.Finding> findings = new ArrayList<>(r.findings());
        findings.sort((a, b) -> Integer.compare(b.found(), a.found()));
        for (Contamination.Finding f : findings) {
            Row row = Row.of("residual.benchmark." + f.benchmark().id, f.played() ? "–" : count(f.found()) + " / " + count(f.items()));
            out.add((f.played() ? row.tone(RefinerySync.DIM).hint("residual.refinery.contamination_check.played")
                    : f.found() > 0 ? row.tone(RefinerySync.BAD).hint("residual.refinery.contamination_check.found", f.example().replace('\n', ' '))
                    : row.tone(RefinerySync.DIM).hint("residual.refinery.contamination_check.none")));
        }
        return new Outcome(in.withDocs(r.kept()), out, null, r.lines() > 0 ? "refinery/caught" : null);
    }

    /** Stores what is left, up to what the line's tanks hold, and counts it as the first tank's tokenizer cuts it. */
    private static Outcome store(MinecraftServer server, Supply text, long holds, Vocab vocab, UUID id) {
        List<Doc> kept = new ArrayList<>();
        long chars = 0, spilled = 0;
        for (Doc d : text.docs()) {
            if (chars + d.chars() <= holds) {
                kept.add(d);
                chars += d.chars();
            } else {
                spilled += d.chars();
            }
        }
        Supply out = text.withDocs(kept);
        try {
            Supplies.save(server, id, out);
        } catch (IOException e) {
            Residual.LOG.error("Could not store refined text", e);
            return new Outcome(Supply.plain(List.of()), List.of(Row.of("residual.refinery.failed", "").tone(RefinerySync.BAD)));
        }
        long tokens = 0;
        for (Doc d : kept) {
            for (String part : d.parts()) {
                tokens += vocab.encode(part).length;
            }
        }
        TokenSupply supply = new TokenSupply(id, chars, tokens, kept.size());
        // what the text tells of, by the words a shard's record would use
        List<String> told = new ArrayList<>();
        for (RuinKinds.Kind kind : RuinKinds.all()) {
            boolean tells = false;
            for (int i = 0; i < kept.size() && !tells; i++) {
                for (String part : kept.get(i).parts()) {
                    tells |= kind.toldIn(part);
                }
            }
            if (tells) {
                told.add(kind.words().getFirst());
            }
        }
        List<Row> rows = new ArrayList<>(tankRows(supply, spilled, holds));
        rows.add(Row.of("residual.refinery.tank.told", told.size() + " / " + RuinKinds.all().size()).hint("residual.refinery.tank.told_hint"));
        return new Outcome(out, rows, supply, null).telling(told);
    }

    private static List<Row> tankRows(TokenSupply s, long spilled, long holds) {
        List<Row> out = new ArrayList<>();
        out.add(Row.of("residual.refinery.tank.tokens", count(s.tokens())).tone(RefinerySync.LIT).hint("residual.refinery.tank.tokens_hint", String.format(Locale.ROOT, "%.2f", s.tokens() == 0 ? 0.0 : (double) s.characters() / s.tokens())));
        out.add(Row.of("residual.refinery.tank.characters", count(s.characters())).bar((float) s.characters() / holds).hint("residual.refinery.tank.holds_hint", count(holds)));
        out.add(Row.of("residual.refinery.tank.docs", count(s.docs())));
        if (spilled > 0) {
            out.add(Row.of("residual.refinery.tank.spilled", chars(spilled)).tone(RefinerySync.BAD).hint("residual.refinery.tank.spilled_hint"));
        }
        return out;
    }

    /** A tank rises toward what it holds as the last of the text comes in. */
    private void pour(int left) {
        float was = fill;
        fill = left <= 0 ? fillTo : fillTo - (fillTo - fillFrom) * Math.min(1f, left / (float) Math.max(1, pourTicks));
        if (Math.abs(fill - was) > 0.01f || left == 0) {
            show();
        }
    }

    private void slotsChanged() {
        if (level instanceof ServerLevel server && stage == Stage.TANK && supply != null) {
            fillCell();
            Vocab vocab = TokenizerItem.vocab(items.getItem(Stage.TOKENIZER));
            if (!vocab.equals(counted)) {
                counted = vocab;
                recount(server, vocab);
            }
        }
    }

    /** Counts what the tank holds again, as another tokenizer cuts it. */
    private void recount(ServerLevel level, Vocab vocab) {
        TokenSupply was = supply;
        MinecraftServer server = level.getServer();
        Jobs.EXECUTOR.execute(() -> {
            long tokens = 0;
            try {
                for (Doc d : Supplies.load(server, was.id()).docs()) {
                    for (String part : d.parts()) {
                        tokens += vocab.encode(part).length;
                    }
                }
            } catch (IOException e) {
                return;
            }
            TokenSupply now = new TokenSupply(was.id(), was.characters(), tokens, was.docs());
            Jobs.back(server, () -> {
                if (was.equals(supply)) {
                    supply = now;
                    rows = tankRows(now, 0, Math.max(TANK_HOLDS, was.characters()));
                    fillCell();
                    setChanged();
                }
            });
        });
    }

    /** A cell in a tank's cell slot draws on what the tank holds. */
    private void fillCell() {
        ItemStack cell = items.getItem(Stage.CELL);
        if (cell.getItem() instanceof TokenCellItem && !supply.equals(TokenCellItem.supply(cell))) {
            cell.set(ModComponents.TOKEN_SUPPLY.get(), supply);
        }
    }

    // ---------------------------------------------------------------- what the rows say

    static String count(long n) {
        return String.format(Locale.ROOT, "%,d", n);
    }

    /** A number of characters, short enough for a row. */
    static String chars(long n) {
        if (n >= 1_000_000) {
            return String.format(Locale.ROOT, "%.2fM", n / 1e6);
        }
        return n >= 10_000 ? String.format(Locale.ROOT, "%.0fK", n / 1e3) : count(n);
    }

    static String percent(double f) {
        return String.format(Locale.ROOT, "%.0f%%", 100 * f);
    }

    private static String histogram(int[] h) {
        StringBuilder out = new StringBuilder();
        for (int v : h) {
            out.append(out.isEmpty() ? "" : " ").append(v);
        }
        return out.toString();
    }

    private static Row inRow(long chars) {
        return Row.of("residual.refinery.in", chars(chars));
    }

    private static Row outRow(long before, long after) {
        return Row.of("residual.refinery.out", chars(after)).bar(before == 0 ? 0 : (float) after / before).tone(after < before ? RefinerySync.LIT : RefinerySync.PLAIN)
                .hint("residual.refinery.kept", before == 0 ? "–" : percent((double) after / before));
    }

    // ---------------------------------------------------------------- measuring

    /** Trains the small reader on every cut, from the same seeds each, off the server thread, and shows each machine what it did. */
    private void measure(ServerLevel level) {
        measuring = true;
        measuredCuts = 0;
        state = State.MEASURING;
        working(true);
        List<Supply> todo = List.copyOf(cuts);
        List<BlockPos> where = List.copyOf(cutAt);
        for (BlockPos at : where) {
            RefineryBlockEntity m = machine(level, at);
            if (m != null) {
                m.flowing = m.next != null;
                m.show();
            }
        }
        MinecraftServer server = level.getServer();
        int threads = ResidualConfig.BASE_THREADS.get();
        Jobs.EXECUTOR.execute(() -> {
            ForkJoinPool pool = threads > 1 ? new ForkJoinPool(threads) : null;
            Assay.Score[] last = null, first = null;
            boolean annealed = false;
            try {
                for (int i = 0; i < todo.size() && !isRemoved(); i++) {
                    Assay.Score[] scores = new Assay.Score[Assay.SEEDS];
                    Supply cut = todo.get(i);
                    // annealing says nothing on a reader that has hardly started: it gets a measure of its own, longer
                    Supply plain = new Supply(cut.docs(), cut.mix(), Supply.NO_ANNEAL, 0);
                    for (int s = 0; s < Assay.SEEDS; s++) {
                        scores[s] = Assay.run(plain, s + 1, pool);
                    }
                    Assay.Score[] before = last;
                    BlockPos at = where.get(i);
                    Jobs.back(server, () -> {
                        measuredCuts++;
                        RefineryBlockEntity m = machine(level, at);
                        if (m != null) {
                            m.measured(before, scores);
                        }
                    });
                    if (cut.anneal() >= 0 && !annealed && scores[0] != null) {
                        annealed = true;
                        Assay.Score[] without = new Assay.Score[Assay.SEEDS], with = new Assay.Score[Assay.SEEDS];
                        for (int s = 0; s < Assay.SEEDS; s++) {
                            without[s] = Assay.run(plain, s + 1, pool, Assay.LONG_STEPS);
                            with[s] = Assay.run(cut, s + 1, pool, Assay.LONG_STEPS);
                        }
                        Jobs.back(server, () -> {
                            RefineryBlockEntity m = machine(level, at);
                            if (m != null) {
                                m.annealMeasured(without, with, cut.anneal());
                            }
                        });
                    }
                    last = scores[0] == null ? last : scores;
                    first = first == null ? last : first;
                }
            } finally {
                if (pool != null) {
                    pool.shutdown();
                }
                Assay.Score[] from = first, to = last;
                Jobs.back(server, () -> measuredAll(level, where, from, to));
            }
        });
    }

    private void measuredAll(ServerLevel level, List<BlockPos> where, Assay.Score @Nullable [] from, Assay.Score @Nullable [] to) {
        measuring = false;
        state = State.IDLE;
        working(false);
        for (BlockPos at : walk(level)) {
            RefineryBlockEntity m = machine(level, at);
            if (m != null && m.stage == Stage.TANK && from != null && to != null) {
                m.held = List.of(Row.of("residual.refinery.tank.whole", String.format(Locale.ROOT, "%.3f → %.3f", mean(from, Assay.Score::loss), mean(to, Assay.Score::loss)))
                        .tone(RefinerySync.LIT).hint("residual.refinery.held_hint"));
                m.setChanged();
            }
        }
        for (BlockPos at : where) {
            RefineryBlockEntity m = machine(level, at);
            if (m != null) {
                m.flowing = false;
                m.show();
            }
        }
        award(level, "refinery/measured");
    }

    /** What annealing did, from the same seeds over a longer run: on its own domain, and on everything. */
    private void annealMeasured(Assay.Score[] without, Assay.Score[] with, int domain) {
        List<Row> out = new ArrayList<>(held);
        out.add(Row.of("residual.refinery.domain_mixer.annealed", String.format(Locale.ROOT, "%.3f → %.3f", mean(without, s -> s.byDomain()[domain]), mean(with, s -> s.byDomain()[domain])))
                .args("residual.refinery.domain." + Domain.values()[domain].id).tone(RefinerySync.LIT).hint("residual.refinery.domain_mixer.annealed_hint", Integer.toString(Assay.LONG_STEPS)));
        out.add(Row.of("residual.refinery.domain_mixer.annealed_all", String.format(Locale.ROOT, "%.3f → %.3f", mean(without, Assay.Score::loss), mean(with, Assay.Score::loss))).tone(RefinerySync.DIM));
        held = out;
        setChanged();
    }

    /** What a measure found: this machine's text against the text it took in, from the same seeds. */
    private void measured(Assay.Score @Nullable [] before, Assay.Score[] after) {
        List<Row> out = new ArrayList<>();
        if (after[0] == null) {
            held = List.of(Row.of("residual.refinery.too_little", "").tone(RefinerySync.BAD));
            setChanged();
            return;
        }
        double now = mean(after, Assay.Score::loss);
        if (before == null) {
            out.add(Row.of("residual.refinery.held_raw", String.format(Locale.ROOT, "%.3f", now)).hint("residual.refinery.held_hint"));
            delta = Float.NaN;
        } else {
            double was = mean(before, Assay.Score::loss);
            double[] diffs = new double[after.length];
            for (int i = 0; i < diffs.length; i++) {
                diffs[i] = after[i].loss() - before[i].loss();
            }
            double d = now - was, spread = spread(diffs);
            within = Math.abs(d) <= Math.max(NOISE, 2 * spread);
            delta = (float) d;
            out.add(Row.of("residual.refinery.held", String.format(Locale.ROOT, "%.3f → %.3f", was, now))
                    .tone(within ? RefinerySync.DIM : d < 0 ? RefinerySync.GOOD : RefinerySync.BAD)
                    .hint(within ? "residual.refinery.held_within" : "residual.refinery.held_moved", String.format(Locale.ROOT, "%+.3f", d), String.format(Locale.ROOT, "%.3f", spread)));
            if (stage == Stage.MIXER) {
                for (int k = 0; k < Domain.WRITTEN; k++) {
                    int dom = k;
                    out.add(Row.of("residual.refinery.domain_held", String.format(Locale.ROOT, "%.3f → %.3f", mean(before, s -> s.byDomain()[dom]), mean(after, s -> s.byDomain()[dom])))
                            .args("residual.refinery.domain." + Domain.values()[k].id).tone(RefinerySync.DIM));
                }
            }
            if (stage == Stage.PROGRAMS) {
                out.add(Row.of("residual.refinery.program_press.orders", percent(mean(before, Assay.Score::orders)) + " → " + percent(mean(after, Assay.Score::orders))).tone(RefinerySync.DIM));
                out.add(Row.of("residual.refinery.program_press.sorting", percent(mean(before, Assay.Score::sorting)) + " → " + percent(mean(after, Assay.Score::sorting))).tone(RefinerySync.DIM));
                int tallies = Domain.TALLIES.ordinal();
                out.add(Row.of("residual.refinery.domain_held", String.format(Locale.ROOT, "%.3f → %.3f", mean(before, s -> s.byDomain()[tallies]), mean(after, s -> s.byDomain()[tallies])))
                        .args("residual.refinery.domain." + Domain.TALLIES.id).tone(RefinerySync.DIM));
            }
        }
        held = out;
        show();
    }

    private static double mean(Assay.Score[] scores, java.util.function.ToDoubleFunction<Assay.Score> of) {
        double sum = 0;
        for (Assay.Score s : scores) {
            sum += of.applyAsDouble(s);
        }
        return sum / scores.length;
    }

    /** The standard error of a mean of {@code diffs}. */
    private static double spread(double[] diffs) {
        double mean = Arrays.stream(diffs).average().orElse(0), sum = 0;
        for (double d : diffs) {
            sum += (d - mean) * (d - mean);
        }
        return Math.sqrt(sum / Math.max(1, diffs.length - 1) / diffs.length);
    }

    // ---------------------------------------------------------------- ticking

    @Override
    protected void tick(ServerLevel level) {
        super.tick(level);
        if (pass != null) {
            runPass(level);
        }
        if (busy && level.getGameTime() - lookedIn > 2) {
            // the Intake that set it to work is gone
            busy = false;
            owner = null;
            taken = null;
            outcome = null;
            state = State.IDLE;
        }
        if (busy) {
            int stored = energy().getAmountAsInt();
            boolean powered = stored >= RATE;
            if (powered && worked < needed) {
                energy().set(stored - RATE);
                worked++;
            }
            state = powered ? State.WORKING : State.NO_POWER;
            working(powered);
        } else if (pass == null && !measuring && getBlockState().getValue(PowerDeviceBlock.WORKING)) {
            // a world saved in the middle of a pass comes back with none under way
            working(false);
        }
    }

    // ---------------------------------------------------------------- menu and saving

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new RefineryMenu(id, inventory, worldPosition, stage, items, data);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null && stage.slots > 0) {
            Containers.dropContents(level, pos, items);
        }
        if (pass != null && level instanceof ServerLevel server) {
            pass.line.remove(worldPosition);
            endPass(server);
        }
    }

    private void writeView(ValueOutput out) {
        out.putBoolean("flowing", flowing);
        out.putInt("lanes", lanes);
        out.putIntArray("shares", toInts(shares));
        out.putString("samples", String.join("\u0001", samples));
        out.putString("dropped", dropped);
        out.putFloat("kept", kept);
        if (next != null) {
            out.putLong("next", next.asLong());
        }
        out.putFloat("delta", delta);
        out.putBoolean("within", within);
        out.putFloat("fill", fill);
    }

    private static int[] toInts(float[] v) {
        int[] out = new int[v.length];
        for (int i = 0; i < v.length; i++) {
            out[i] = Float.floatToRawIntBits(v[i]);
        }
        return out;
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        if (stage.slots > 0) {
            ContainerHelper.saveAllItems(out.child("items"), items.getItems());
        }
        out.putIntArray("settings", settings);
        out.store("rows", Row.CODEC.listOf(), rows);
        out.store("held", Row.CODEC.listOf(), held);
        if (supply != null) {
            out.store("supply", TokenSupply.CODEC, supply);
        }
        writeView(out);
        // no pass survives a save
        out.putBoolean("flowing", false);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        in.child("items").ifPresent(c -> ContainerHelper.loadAllItems(c, items.getItems()));
        in.getIntArray("settings").filter(s -> s.length == settings.length).ifPresent(s -> System.arraycopy(s, 0, settings, 0, s.length));
        rows = in.read("rows", Row.CODEC.listOf()).orElse(rows);
        held = in.read("held", Row.CODEC.listOf()).orElse(held);
        supply = in.read("supply", TokenSupply.CODEC).orElse(supply);
        flowing = in.getBooleanOr("flowing", false);
        lanes = in.getIntOr("lanes", 0);
        int[] s = in.getIntArray("shares").orElse(new int[0]);
        for (int i = 0; i < Math.min(s.length, shares.length); i++) {
            shares[i] = Float.intBitsToFloat(s[i]);
        }
        String[] joined = in.getStringOr("samples", "").split("\u0001", -1);
        for (int i = 0; i < Math.min(joined.length, samples.length); i++) {
            samples[i] = joined[i];
        }
        dropped = in.getStringOr("dropped", "");
        kept = in.getFloatOr("kept", 1);
        long at = in.getLongOr("next", Long.MIN_VALUE);
        next = at == Long.MIN_VALUE ? null : BlockPos.of(at);
        delta = in.getFloatOr("delta", Float.NaN);
        within = in.getBooleanOr("within", false);
        fill = in.getFloatOr("fill", 0);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
        writeView(out);
        return out.buildResult();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
