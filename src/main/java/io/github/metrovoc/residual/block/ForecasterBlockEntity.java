package io.github.metrovoc.residual.block;

import io.github.metrovoc.residual.Awards;
import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.item.FieldLogItem;
import io.github.metrovoc.residual.learn.Forecast;
import io.github.metrovoc.residual.learn.Forecast.Kind;
import io.github.metrovoc.residual.learn.Growth;
import io.github.metrovoc.residual.menu.ForecasterMenu;
import io.github.metrovoc.residual.model.ModelInfo;
import io.github.metrovoc.residual.model.ModelLink;
import io.github.metrovoc.residual.model.Models;
import io.github.metrovoc.residual.registry.ModBlockEntities;
import io.github.metrovoc.residual.registry.ModBlocks;
import io.github.metrovoc.residual.registry.ModComponents;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.event.level.block.CropGrowEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Watches the blocks around it that make progress and writes down what they do, a line per block. It looks at each
 * crop every {@link Growth#OBSERVE} ticks, and only growth the game's own random ticks made counts; a crop harvested,
 * trampled or fed bone meal starts a new line. It looks at each furnace, smoker, blast furnace and brewing stand eight
 * times an item. With a model whose forecasts match what a kind of block did, it runs those blocks ahead: crops grow
 * to the ages the model draws, and machines work through the progress the model draws.
 */
public class ForecasterBlockEntity extends BlockEntity implements MenuProvider {
    public static final int LOG = 0, MODEL = 1, SLOTS = 2;
    public static final int RADIUS = 4, SIDE = 2 * RADIUS + 1;
    /** Its first look at crops comes a second after it loads, then one every {@link Growth#OBSERVE} ticks. */
    private static final int FIRST_LOOK = 20;
    /** How often it looks for machines come and gone, and writes down what it watched. */
    private static final int SURVEY = 20;
    /** A brewing stand's brew, in ticks. */
    private static final int BREW = 400;
    /** A Fabricator making nothing is looked at as if it made something of middling size. */
    private static final int FABRICATING = 100;
    /** Container data per kind of block: how many it watches, progress seen, the model's fit, and its phase. */
    public static final int COUNT = 0, GROWN = 1, RATIO = 2, AHEAD = 3, PHASE = 4, PER_KIND = 5;
    /** What its screen shows, as container data: four map cells or sixteen flashes to a value. */
    public static final int STATE = 0, PROBLEM = 1, KINDS = 2, SEEN = KINDS + PER_KIND * 2, EXPECTS = SEEN + Growth.RIPE + Growth.EIGHTHS,
            MAP = EXPECTS + Growth.RIPE + Growth.EIGHTHS, FLASH = MAP + (SIDE * SIDE + 3) / 4, DATA = FLASH + (SIDE * SIDE + 15) / 16;
    /** Map cells: nothing, a crop's age plus one, an idle machine, a working one. */
    public static final int IDLE_MACHINE = Growth.RIPE + 2, WORKING_MACHINE = IDLE_MACHINE + 1;
    /** Why there is no model to run, in the order {@link #PROBLEM} counts them. */
    public static final List<String> PROBLEMS = List.of("residual.model.none", "residual.model.blank", "residual.model.unbound",
            "residual.model.out_of_range", "residual.model.no_server", "residual.model.server_empty", "residual.model.too_big",
            "residual.model.failed");

    public enum State { NOTHING, WATCHING, NO_MODEL, PEEKS, READY }

    /** Where a model stands with one kind of block. */
    public enum Phase { NONE, WATCHING, CHECKING, REFUSED, AHEAD }

    /** Forecasters in loaded chunks, told about every crop that grows by itself. */
    private static final Set<ForecasterBlockEntity> LOADED = new HashSet<>();

    /** One crop's line so far. */
    private static final class Watch {
        final StringBuilder line = new StringBuilder();
        int age;
        int ripeLooks;
        /** Its line holds looks a model drew or an Hourglass hurried, so it is never written down. */
        boolean drawn;
        boolean closed;

        Watch(int age) {
            look(age);
        }

        void look(int now) {
            age = now;
            line.append((char) ('0' + now));
            if (now >= Growth.RIPE) {
                ripeLooks++;
            }
        }
    }

    /** A machine's line waiting for a forecast, as it stood after its {@code looks}th look. */
    private record Due(String line, int looks) {
    }

    /** One machine's line so far, its own clock, and the ticks a model's forecast has earned it. */
    private static final class Machine {
        final StringBuilder line = new StringBuilder();
        int every;
        int next;
        /** Looks taken so far, so a forecast that comes back after a newer look is not written into the line. */
        int looks;
        /** Its line holds looks a model drew or an Hourglass hurried, so it is never written down. */
        boolean drawn;
        int budget;
        int pace;

        Machine(int every, int next) {
            this.every = every;
            this.next = next;
        }

        char last() {
            return line.isEmpty() ? Growth.IDLE : line.charAt(line.length() - 1);
        }

        boolean ended() {
            int n = line.length();
            return n >= 2 && line.charAt(n - 1) == Growth.IDLE && line.charAt(n - 2) == Growth.IDLE;
        }
    }

    private final SimpleContainer items = new SimpleContainer(SLOTS) {
        @Override
        public void setChanged() {
            super.setChanged();
            ForecasterBlockEntity.this.slotsChanged();
        }
    };
    private final Map<Long, Watch> watches = new HashMap<>();
    private final Map<Long, Machine> machines = new HashMap<>();
    /** Natural growth since the last look, by packed position. */
    private final Long2IntOpenHashMap grown = new Long2IntOpenHashMap();
    /** Every line it wrote down, oldest first, at most {@link FieldLogItem#CAPACITY} characters. */
    private String record = "";
    /**
     * Everything it has watched: the record, then the lines still being written. Finished lines alone would favour
     * the crops that happened to grow fastest, since those finish first.
     */
    private String watched = "";
    private final int[] watchedGrowth = new int[Kind.values().length];
    private final float[][] seen = {Forecast.seen("", Kind.CROPS), Forecast.seen("", Kind.MACHINES)};
    private final Phase[] phases = {Phase.NONE, Phase.NONE};
    private State state = State.NOTHING;
    private int problem;
    @Nullable
    private Forecast.Check check;
    @Nullable
    private ModelInfo checkedModel;
    private final int[] checkedGrowth = {-1, -1};
    private boolean busy;
    private int askedAt;
    /** Which request it waits to hear about; an answer to an older one is ignored. */
    private int request;
    private int ticks;
    /** Crops due to be run ahead, and machines waiting for a forecast. */
    private boolean cropsDue;
    private final Map<Long, Due> due = new LinkedHashMap<>();
    @Nullable
    private ModelLink link;
    private final BitSet flashed = new BitSet(SIDE * SIDE);
    private final int[] map = new int[SIDE * SIDE];
    private final Random rng = new Random();

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            if (i >= FLASH) {
                int bits = 0;
                for (int b = 0; b < 16; b++) {
                    bits |= flashed.get((i - FLASH) * 16 + b) ? 1 << b : 0;
                }
                return bits;
            }
            if (i >= MAP) {
                int packed = 0;
                for (int c = 0; c < 4 && (i - MAP) * 4 + c < map.length; c++) {
                    packed |= map[(i - MAP) * 4 + c] << (c * 4);
                }
                return packed;
            }
            if (i >= SEEN) {
                boolean expects = i >= EXPECTS;
                int at = i - (expects ? EXPECTS : SEEN);
                Kind kind = at < Growth.RIPE ? Kind.CROPS : Kind.MACHINES;
                int place = kind == Kind.CROPS ? at : at - Growth.RIPE;
                float[] rates = !expects ? seen[kind.ordinal()] : check == null ? null : check.of(kind).expects();
                float r = rates == null ? Float.NaN : rates[place];
                return Float.isNaN(r) ? -1 : Math.clamp(Math.round(r * 10_000), Short.MIN_VALUE + 1, Short.MAX_VALUE);
            }
            if (i >= KINDS) {
                Kind kind = Kind.values()[(i - KINDS) / PER_KIND];
                int k = kind.ordinal();
                return switch ((i - KINDS) % PER_KIND) {
                    case COUNT -> kind == Kind.CROPS ? watches.size() : machines.size();
                    case GROWN -> Math.clamp(watchedGrowth[k], 0, Short.MAX_VALUE);
                    case RATIO -> {
                        double r = check == null ? Double.NaN : check.of(kind).ratio();
                        yield Double.isNaN(r) ? -1 : (int) Math.min(Short.MAX_VALUE, Math.round(r * 1000));
                    }
                    case AHEAD -> phases[k] == Phase.AHEAD && check != null ? check.of(kind).ahead(most()) : 0;
                    default -> phases[k].ordinal();
                };
            }
            return switch (i) {
                case STATE -> state.ordinal();
                case PROBLEM -> problem;
                default -> 0;
            };
        }

        @Override
        public void set(int i, int value) {
        }

        @Override
        public int getCount() {
            return DATA;
        }
    };

    public ForecasterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FORECASTER.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ForecasterBlockEntity be) {
        be.tick((ServerLevel) level);
    }

    /** A crop grew on a random tick: tell the Forecasters watching it. */
    public static void grew(CropGrowEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            for (ForecasterBlockEntity f : LOADED) {
                if (f.level == level && f.covers(event.getPos())) {
                    f.grown.addTo(event.getPos().asLong(), 1);
                }
            }
        }
    }

    /** An Hourglass ran the block at {@code p} this tick: the lines watching it no longer hold what it did by itself. */
    public static void hurried(ServerLevel level, BlockPos p) {
        for (ForecasterBlockEntity f : LOADED) {
            if (f.level == level && f.covers(p)) {
                Watch w = f.watches.get(p.asLong());
                if (w != null) {
                    w.drawn = true;
                }
                Machine m = f.machines.get(p.asLong());
                if (m != null) {
                    m.drawn = true;
                }
            }
        }
    }

    private boolean covers(BlockPos p) {
        return Math.abs(p.getX() - worldPosition.getX()) <= RADIUS && Math.abs(p.getZ() - worldPosition.getZ()) <= RADIUS
                && Math.abs(p.getY() - worldPosition.getY()) <= 1;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide()) {
            LOADED.add(this);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        LOADED.remove(this);
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        LOADED.remove(this);
    }

    private void tick(ServerLevel level) {
        ticks++;
        if (busy && ticks - askedAt > Models.PATIENCE) {
            busy = false;
            request++;
        }
        boolean crops = ticks % Growth.OBSERVE == FIRST_LOOK;
        if (crops) {
            lookAtCrops(level);
        }
        if (ticks % SURVEY == 0) {
            survey(level);
        }
        lookAtMachines(level);
        run(level);
        if (crops || ticks % SURVEY == 0) {
            if (crops || ticks % (SURVEY * 5) == 0) {
                watched(capped(record + open()));
            }
            decide(level);
        }
        if (!busy && link != null && !(link instanceof ModelLink.Missing)) {
            work(level, link);
        }
    }

    /** Settles what it can do with the model it holds, and with each kind of block. */
    private void decide(ServerLevel level) {
        boolean anything = !watches.isEmpty() || !machines.isEmpty();
        link = anything ? ModelLink.of(level, worldPosition, items.getItem(MODEL)) : null;
        ModelInfo info = link == null ? null : link.info();
        boolean usable = info != null && info.causal();
        if (!usable) {
            check = null;
            due.clear();
        }
        for (Kind kind : Kind.values()) {
            int k = kind.ordinal();
            if (kind == Kind.CROPS ? watches.isEmpty() : machines.isEmpty()) {
                phases[k] = Phase.NONE;
            } else if (!usable || phases[k] == Phase.NONE) {
                phases[k] = Phase.WATCHING;
            }
            // otherwise it keeps what the last check said until the model changes, or that a check is under way
        }
        if (!anything) {
            show(State.NOTHING);
        } else if (link instanceof ModelLink.Missing m) {
            problem = Math.max(0, PROBLEMS.indexOf(m.problem()));
            show(items.getItem(MODEL).isEmpty() ? State.WATCHING : State.NO_MODEL);
        } else {
            show(usable ? State.READY : State.PEEKS);
        }
    }

    /** Hands the model its next job: a check that is due, crops to run ahead, then machines waiting for a forecast. */
    private void work(ServerLevel level, ModelLink link) {
        ModelInfo info = link.info();
        if (state != State.READY || info == null) {
            return;
        }
        if (stale(info)) {
            String text = watched;
            int[] growth = watchedGrowth.clone();
            for (Kind kind : Kind.values()) {
                if (growth[kind.ordinal()] >= Forecast.MIN_GROWTH) {
                    phases[kind.ordinal()] = Phase.CHECKING;
                }
            }
            submit(level, link, model -> Forecast.check(model, text), result -> checked(info, growth, result));
        } else if (cropsDue) {
            cropsDue = false;
            if (phases[Kind.CROPS.ordinal()] == Phase.AHEAD && check != null) {
                aheadCrops(level, link, check.crops().ahead(most()) - 1);
            }
        } else if (!due.isEmpty()) {
            aheadMachines(level, link);
        }
    }

    /**
     * A model is checked again when it changes, and a kind of block it was not trusted with is checked again each time
     * those blocks have moved a good deal more: they keep changing while they are watched.
     */
    private boolean stale(ModelInfo info) {
        boolean ready = false;
        for (Kind kind : Kind.values()) {
            int k = kind.ordinal();
            if (watchedGrowth[k] < Forecast.MIN_GROWTH) {
                continue;
            }
            ready = true;
            if (phases[k] != Phase.AHEAD && watchedGrowth[k] >= checkedGrowth[k] + Forecast.MIN_GROWTH / 4) {
                return true;
            }
        }
        return ready && !info.equals(checkedModel);
    }

    /** Looks at every crop around it once. */
    private void lookAtCrops(ServerLevel level) {
        Set<Long> seenHere = new HashSet<>();
        flashed.clear();
        for (int dy = 1; dy >= -1; dy--) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                for (int dx = -RADIUS; dx <= RADIUS; dx++) {
                    BlockPos p = worldPosition.offset(dx, dy, dz);
                    BlockState s = level.getBlockState(p);
                    // only plants that grow the way practice does, through seven ages
                    int age = age(s);
                    if (age < 0) {
                        continue;
                    }
                    long key = p.asLong();
                    seenHere.add(key);
                    Watch w = watches.get(key);
                    if (w == null || w.age + grown.get(key) != age) {
                        if (w != null) {
                            close(w);
                        }
                        watches.put(key, new Watch(age));
                    } else if (!w.closed) {
                        w.look(age);
                        if (w.ripeLooks >= Growth.RIPE_FOR) {
                            close(w);
                        }
                    }
                }
            }
        }
        watches.entrySet().removeIf(e -> {
            if (!seenHere.contains(e.getKey())) {
                close(e.getValue());
                return true;
            }
            return false;
        });
        grown.clear();
        cropsDue = true;
        setChanged();
    }

    /** Finds the machines around it, and draws them and the crops on its map. */
    private void survey(ServerLevel level) {
        Arrays.fill(map, 0);
        Set<Long> here = new HashSet<>();
        for (int dy = 1; dy >= -1; dy--) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                for (int dx = -RADIUS; dx <= RADIUS; dx++) {
                    BlockPos p = worldPosition.offset(dx, dy, dz);
                    int cell = (dz + RADIUS) * SIDE + dx + RADIUS;
                    BlockState s = level.getBlockState(p);
                    if (age(s) >= 0) {
                        if (map[cell] == 0) {
                            map[cell] = age(s) + 1;
                        }
                        continue;
                    }
                    if (!level.isLoaded(p) || !(level.getBlockEntity(p) instanceof BlockEntity be) || cook(be) < 0) {
                        continue;
                    }
                    long key = p.asLong();
                    here.add(key);
                    flashed.clear(cell);
                    Machine m = machines.computeIfAbsent(key, k -> new Machine(Growth.every(cook(be)), ticks + 1));
                    map[cell] = Math.max(map[cell], m.last() != Growth.IDLE || timer(be) > 0 ? WORKING_MACHINE : IDLE_MACHINE);
                }
            }
        }
        machines.keySet().removeIf(key -> {
            if (!here.contains(key)) {
                close(machines.get(key));
                due.remove(key);
                return true;
            }
            return false;
        });
    }

    /** Looks at every machine whose look is due: its progress through the item it is making. */
    private void lookAtMachines(ServerLevel level) {
        machines.forEach((key, m) -> {
            if (ticks < m.next) {
                return;
            }
            if (!(level.getBlockEntity(BlockPos.of(key)) instanceof BlockEntity be) || cook(be) < 0) {
                m.next = ticks + m.every;
                return;
            }
            int every = Growth.every(cook(be));
            if (every != m.every) {
                // a Fabricator set to make something else: its looks keep to eighths of the new item
                close(m);
                m.line.setLength(0);
                m.drawn = false;
                m.every = every;
            }
            m.next = ticks + m.every;
            m.line.append(Growth.progress(timer(be), cook(be)));
            m.looks++;
            if (m.ended()) {
                close(m);
                m.line.setLength(0);
                m.drawn = false;
                m.line.append(Growth.IDLE);
            }
            if (phases[Kind.MACHINES.ordinal()] == Phase.AHEAD && check != null) {
                due.put(key, new Due(m.line.toString(), m.looks));
            }
        });
    }

    /** Spends the ticks each machine's forecast earned it, a few a tick, so it runs that much faster. */
    private void run(ServerLevel level) {
        machines.forEach((key, m) -> {
            if (m.budget <= 0) {
                return;
            }
            BlockPos p = BlockPos.of(key);
            BlockEntity be = level.getBlockEntity(p);
            int spend = Math.min(m.budget, m.pace);
            m.budget -= spend;
            if (be == null || cook(be) < 0) {
                m.budget = 0;
                return;
            }
            for (int i = 0; i < spend; i++) {
                tickOnce(level, p, be);
            }
            if (ticks % 4 == 0) {
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX() + 0.5, p.getY() + 1.05, p.getZ() + 0.5, 1, 0.3, 0.05, 0.3, 0);
            }
            flashed.set((p.getZ() - worldPosition.getZ() + RADIUS) * SIDE + p.getX() - worldPosition.getX() + RADIUS);
        });
    }

    /** The fastest it runs blocks ahead: Forecaster II four times as fast as the Forecaster. */
    public int most() {
        return getBlockState().is(ModBlocks.FORECASTER_II.get()) ? Forecast.MOST_II : Forecast.MOST;
    }

    /** Whether its model is trusted to run blocks of this kind ahead: it passed its last check on them. */
    public boolean trusts(Kind kind) {
        return phases[kind.ordinal()] == Phase.AHEAD;
    }

    /** The kind of block a Forecaster could run ahead at {@code p}: a machine it watches, a crop of seven ages, or null. */
    @Nullable
    public static Kind kindOf(ServerLevel level, BlockPos p) {
        BlockEntity be = level.getBlockEntity(p);
        if (be != null && cook(be) >= 0) {
            return Kind.MACHINES;
        }
        return age(level.getBlockState(p)) >= 0 ? Kind.CROPS : null;
    }

    @SuppressWarnings("unchecked")
    public static void tickOnce(ServerLevel level, BlockPos p, BlockEntity be) {
        BlockState s = level.getBlockState(p);
        BlockEntityTicker<BlockEntity> ticker = (BlockEntityTicker<BlockEntity>) s.getTicker(level, be.getType());
        if (ticker != null) {
            ticker.tick(level, p, s, be);
        }
    }

    /** Ticks an item takes in a machine it can watch, or -1 for a block that is not one. */
    static int cook(BlockEntity be) {
        if (be instanceof AbstractFurnaceBlockEntity furnace) {
            int total = furnace.dataAccess.get(AbstractFurnaceBlockEntity.DATA_COOKING_TOTAL_TIME);
            return total > 0 ? total : be.getType() == BlockEntityType.FURNACE ? 200 : 100;
        }
        if (be instanceof FabricatorBlockEntity fabricator) {
            return fabricator.total() > 0 ? fabricator.total() : FABRICATING;
        }
        return be instanceof BrewingStandBlockEntity ? BREW : -1;
    }

    /** The age of a plant that grows through seven ages on random ticks, a crop or a stem; -1 for anything else. */
    static int age(BlockState s) {
        if (s.getBlock() instanceof CropBlock crop) {
            return crop.getMaxAge() == Growth.RIPE ? crop.getAge(s) : -1;
        }
        return s.getBlock() instanceof StemBlock ? s.getValue(StemBlock.AGE) : -1;
    }

    /** {@code s} grown to {@code age}. */
    private static BlockState aged(BlockState s, int age) {
        return s.getBlock() instanceof CropBlock crop ? crop.getStateForAge(age) : s.setValue(StemBlock.AGE, age);
    }

    /** How many ticks a machine is into the item it is making. */
    static int timer(BlockEntity be) {
        if (be instanceof AbstractFurnaceBlockEntity furnace) {
            return furnace.dataAccess.get(AbstractFurnaceBlockEntity.DATA_COOKING_PROGRESS);
        }
        if (be instanceof BrewingStandBlockEntity stand) {
            int left = stand.dataAccess.get(BrewingStandBlockEntity.DATA_BREW_TIME);
            return left > 0 ? BREW - left : 0;
        }
        return be instanceof FabricatorBlockEntity fabricator ? fabricator.progress() : 0;
    }

    /** The lines it is still writing, crops' then machines', that hold only what the blocks did by themselves. */
    private String open() {
        StringBuilder open = new StringBuilder();
        watches.values().forEach(w -> {
            if (!w.closed && kept(w)) {
                open.append(w.line).append('\n');
            }
        });
        machines.values().forEach(m -> {
            if (kept(m)) {
                open.append(m.line).append('\n');
            }
        });
        return open.toString();
    }

    /** A line is kept if it holds only what the crop did by itself, and saw it grow. */
    private static boolean kept(Watch w) {
        return !w.drawn && w.line.length() >= 2 && w.line.charAt(0) != '0' + Growth.RIPE;
    }

    /** A machine's line is kept if it ran at its own pace and worked at all. */
    private static boolean kept(Machine m) {
        return !m.drawn && m.line.chars().anyMatch(c -> c != Growth.IDLE);
    }

    /** Writes a crop's line down for good. */
    private void close(Watch w) {
        if (w.closed) {
            return;
        }
        w.closed = true;
        if (kept(w)) {
            record = capped(record + w.line + "\n");
        }
    }

    private void close(Machine m) {
        if (kept(m)) {
            record = capped(record + m.line + "\n");
        }
    }

    /** The newest lines of {@code text} that fit on a Field Log. */
    private static String capped(String text) {
        int cut = 0;
        while (text.length() - cut > FieldLogItem.CAPACITY) {
            cut = text.indexOf('\n', cut) + 1;
        }
        return text.substring(cut);
    }

    private void watched(String text) {
        watched = text;
        for (Kind kind : Kind.values()) {
            watchedGrowth[kind.ordinal()] = Forecast.grown(text, kind);
            seen[kind.ordinal()] = Forecast.seen(text, kind);
        }
        ItemStack log = items.getItem(LOG);
        if (log.getItem() instanceof FieldLogItem && !text.equals(FieldLogItem.log(log))) {
            log.set(ModComponents.FIELD_LOG.get(), text);
        }
    }

    private void checked(ModelInfo info, int[] growth, @Nullable Forecast.Check result) {
        if (result == null) {
            problem = PROBLEMS.indexOf("residual.model.failed");
            check = null;
            show(State.NO_MODEL);
            return;
        }
        boolean was = phases[0] == Phase.AHEAD || phases[1] == Phase.AHEAD;
        check = result;
        checkedModel = info;
        for (Kind kind : Kind.values()) {
            int k = kind.ordinal();
            checkedGrowth[k] = growth[k];
            if (growth[k] >= Forecast.MIN_GROWTH) {
                phases[k] = result.of(kind).ahead(most()) > 0 ? Phase.AHEAD : Phase.REFUSED;
            }
        }
        boolean now = phases[0] == Phase.AHEAD || phases[1] == Phase.AHEAD;
        if (!now) {
            due.clear();
        }
        lit(now);
        if (!was && now && level instanceof ServerLevel server) {
            server.playSound(null, worldPosition, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.6f, 1.6f);
        }
    }

    /** Draws the next {@code steps} looks of every growing crop from the model, and makes them so. */
    private void aheadCrops(ServerLevel level, ModelLink link, int steps) {
        List<Long> keys = new ArrayList<>();
        List<String> lines = new ArrayList<>();
        watches.forEach((key, w) -> {
            if (!w.closed && w.age < Growth.RIPE) {
                keys.add(key);
                lines.add(w.line.toString());
            }
        });
        if (keys.isEmpty()) {
            return;
        }
        long seed = rng.nextLong();
        String field = watched;
        submit(level, link, model -> Forecast.ahead(model, field, lines, steps, new Random(seed)), drawn -> {
            if (drawn != null) {
                grow(level, keys, drawn);
            }
        });
    }

    private void grow(ServerLevel level, List<Long> keys, String[] drawn) {
        int grew = 0;
        for (int i = 0; i < keys.size(); i++) {
            Watch w = watches.get(keys.get(i));
            BlockPos p = BlockPos.of(keys.get(i));
            BlockState s = level.getBlockState(p);
            if (w == null || w.closed || age(s) != w.age) {
                continue;
            }
            int was = w.age;
            for (char c : drawn[i].toCharArray()) {
                w.look(c - '0');
            }
            w.drawn = true;
            if (w.age > was) {
                level.setBlock(p, aged(s, w.age), Block.UPDATE_CLIENTS);
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, p.getX() + 0.5, p.getY() + 0.4, p.getZ() + 0.5, 3, 0.25, 0.2, 0.25, 0);
                flashed.set((p.getZ() - worldPosition.getZ() + RADIUS) * SIDE + p.getX() - worldPosition.getX() + RADIUS);
                grew++;
            }
            if (w.ripeLooks >= Growth.RIPE_FOR) {
                close(w);
            }
        }
        if (grew > 0) {
            ahead(level, check == null ? 0 : check.crops().ahead(most()));
        }
    }

    /**
     * Draws the next looks of every machine waiting for a forecast, and lets each run on through the progress drawn:
     * a forecaster at ×4 draws three looks for every one the world takes.
     */
    private void aheadMachines(ServerLevel level, ModelLink link) {
        if (check == null) {
            due.clear();
            return;
        }
        int steps = check.machines().ahead(most()) - 1;
        List<Long> keys = new ArrayList<>(due.keySet());
        List<Due> sent = new ArrayList<>(due.values());
        due.clear();
        if (steps <= 0) {
            return;
        }
        List<String> lines = sent.stream().map(Due::line).toList();
        long seed = rng.nextLong();
        String field = watched;
        submit(level, link, model -> Forecast.ahead(model, field, lines, steps, new Random(seed)), drawn -> {
            if (drawn == null) {
                return;
            }
            boolean any = false;
            for (int i = 0; i < keys.size(); i++) {
                Machine m = machines.get(keys.get(i));
                String line = sent.get(i).line();
                if (m == null || line.isEmpty()) {
                    continue;
                }
                if (!m.drawn) {
                    // what it did by itself so far is written down; from here on its line holds the model's looks
                    close(m);
                    m.line.setLength(0);
                    m.line.append(line.charAt(line.length() - 1));
                    m.drawn = true;
                }
                if (m.looks == sent.get(i).looks()) {
                    // the model reads on from its own looks, so its context keeps the pace it was trained on
                    m.line.append(drawn[i]);
                }
                int eighths = Forecast.made(line.charAt(line.length() - 1), drawn[i]);
                m.budget += eighths * m.every;
                m.pace = steps;
                any |= eighths > 0;
            }
            if (any) {
                ahead(level, steps + 1);
            }
        });
    }

    /** Players nearby have seen a Forecaster run blocks ahead. */
    private void ahead(ServerLevel level, int factor) {
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(worldPosition.getCenter()) < 32 * 32) {
                Awards.grant(player, "forecast/ahead");
                if (factor >= 8) {
                    Awards.grant(player, "forecast/deep");
                }
            }
        }
    }

    private <T> void submit(ServerLevel level, ModelLink link, Function<CompiledGraph, T> work, Consumer<@Nullable T> done) {
        busy = true;
        askedAt = ticks;
        int id = ++request;
        Consumer<@Nullable T> reply = result -> {
            if (id == request && !isRemoved()) {
                busy = false;
                done.accept(result);
            }
        };
        switch (link) {
            case ModelLink.Served s -> s.server().submit(work, reply);
            case ModelLink.Onboard o -> Models.submit(level.getServer(), o.info().id(), work, reply);
            case ModelLink.Missing m -> busy = false;
        }
    }

    private void slotsChanged() {
        request++;
        busy = false;
        check = null;
        checkedModel = null;
        due.clear();
        for (Kind kind : Kind.values()) {
            if (phases[kind.ordinal()] != Phase.NONE) {
                phases[kind.ordinal()] = Phase.WATCHING;
            }
        }
        lit(false);
        ItemStack log = items.getItem(LOG);
        if (log.getItem() instanceof FieldLogItem && !watched.equals(FieldLogItem.log(log))) {
            log.set(ModComponents.FIELD_LOG.get(), watched);
        }
        setChanged();
    }

    private void show(State s) {
        state = s;
        lit(s == State.READY && (phases[0] == Phase.AHEAD || phases[1] == Phase.AHEAD));
    }

    private void lit(boolean on) {
        BlockState here = getBlockState();
        if (level != null && here.getValue(ForecasterBlock.WORKING) != on) {
            level.setBlock(worldPosition, here.setValue(ForecasterBlock.WORKING, on), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new ForecasterMenu(id, inventory, worldPosition, items, data);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) {
            Containers.dropContents(level, pos, items);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        ContainerHelper.saveAllItems(out.child("items"), items.getItems());
        out.putString("record", record);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        in.child("items").ifPresent(c -> ContainerHelper.loadAllItems(c, items.getItems()));
        record = in.getStringOr("record", "");
        watched = record;
        for (Kind kind : Kind.values()) {
            watchedGrowth[kind.ordinal()] = Forecast.grown(record, kind);
            seen[kind.ordinal()] = Forecast.seen(record, kind);
        }
    }
}
