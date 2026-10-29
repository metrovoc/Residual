package io.github.metrovoc.residual.block;

import io.github.metrovoc.residual.Awards;
import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.item.FieldLogItem;
import io.github.metrovoc.residual.learn.Forecast;
import io.github.metrovoc.residual.learn.Growth;
import io.github.metrovoc.residual.menu.ForecasterMenu;
import io.github.metrovoc.residual.model.ModelInfo;
import io.github.metrovoc.residual.model.ModelLink;
import io.github.metrovoc.residual.model.Models;
import io.github.metrovoc.residual.registry.ModBlockEntities;
import io.github.metrovoc.residual.registry.ModComponents;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.event.level.block.CropGrowEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Watches the crops around it and writes down what they do: every {@link Growth#OBSERVE} ticks it looks at each one,
 * and a crop's line grows by a look until it has been seen ripe a few times. Only growth the game's own random ticks
 * made counts; a crop harvested, trampled or fed bone meal starts a new line. With a model whose growth matches what
 * it watched, it runs the field ahead: after each look it draws every crop's next few looks from the model and makes
 * them so.
 */
public class ForecasterBlockEntity extends BlockEntity implements MenuProvider {
    public static final int LOG = 0, MODEL = 1, SLOTS = 2;
    public static final int RADIUS = 4, SIDE = 2 * RADIUS + 1;
    /** Its first look comes a second after it loads, then one every {@link Growth#OBSERVE} ticks. */
    private static final int FIRST_LOOK = 20;
    /** What its screen shows, as container data: four map cells or sixteen flashes to a value. */
    public static final int STATE = 0, PROBLEM = 1, CROPS = 2, GROWN = 3, RATIO = 4, AHEAD = 5, SEEN = 6,
            EXPECTS = SEEN + Growth.RIPE, MAP = EXPECTS + Growth.RIPE, FLASH = MAP + (SIDE * SIDE + 3) / 4,
            DATA = FLASH + (SIDE * SIDE + 15) / 16;
    /** Why there is no model to run, in the order {@link #PROBLEM} counts them. */
    public static final List<String> PROBLEMS = List.of("residual.model.none", "residual.model.blank", "residual.model.unbound",
            "residual.model.out_of_range", "residual.model.no_server", "residual.model.server_empty", "residual.model.too_big",
            "residual.model.failed");

    public enum State { NO_CROPS, WATCHING, NO_MODEL, PEEKS, CHECKING, REFUSED, AHEAD }

    /** Forecasters in loaded chunks, told about every crop that grows by itself. */
    private static final Set<ForecasterBlockEntity> LOADED = new HashSet<>();

    /** One crop's line so far. */
    private static final class Watch {
        final StringBuilder line = new StringBuilder();
        int age;
        int ripeLooks;
        /** Its line holds looks a model drew, so it is never written down. */
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

    private final SimpleContainer items = new SimpleContainer(SLOTS) {
        @Override
        public void setChanged() {
            super.setChanged();
            ForecasterBlockEntity.this.slotsChanged();
        }
    };
    private final Map<Long, Watch> watches = new HashMap<>();
    /** Natural growth since the last look, by packed position. */
    private final Long2IntOpenHashMap grown = new Long2IntOpenHashMap();
    /** Every line it wrote down, oldest first, at most {@link FieldLogItem#CAPACITY} characters. */
    private String record = "";
    /**
     * Everything it has watched: the record, then the lines still being written. Finished lines alone would favour
     * the crops that happened to grow fastest, since those finish first.
     */
    private String watched = "";
    private int watchedGrowth;
    private float[] seen = Forecast.seen("");
    private State state = State.NO_CROPS;
    private int problem;
    @Nullable
    private Forecast.Check check;
    @Nullable
    private ModelInfo checkedModel;
    private int checkedGrowth = -1;
    private boolean busy;
    private int askedAt;
    /** Which request it waits to hear about; an answer to an older one is ignored. */
    private int request;
    private int ticks;
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
            if (i >= SEEN && i < MAP) {
                float[] rates = i < EXPECTS ? seen : check == null ? null : check.expects();
                float r = rates == null ? Float.NaN : rates[(i - SEEN) % Growth.RIPE];
                return Float.isNaN(r) ? -1 : Math.min(Short.MAX_VALUE, Math.round(r * 10_000));
            }
            return switch (i) {
                case STATE -> state.ordinal();
                case PROBLEM -> problem;
                case CROPS -> watches.size();
                case GROWN -> Math.min(Short.MAX_VALUE, watchedGrowth);
                case RATIO -> check == null || Double.isNaN(check.ratio()) ? -1 : (int) Math.min(Short.MAX_VALUE, Math.round(check.ratio() * 1000));
                case AHEAD -> state == State.AHEAD && check != null ? check.ahead() : 0;
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
        if (ticks % Growth.OBSERVE != FIRST_LOOK) {
            return;
        }
        look(level);
        if (watches.isEmpty()) {
            show(State.NO_CROPS);
            return;
        }
        ModelLink link = ModelLink.of(level, worldPosition, items.getItem(MODEL));
        if (link instanceof ModelLink.Missing m) {
            problem = Math.max(0, PROBLEMS.indexOf(m.problem()));
            check = null;
            show(items.getItem(MODEL).isEmpty() ? State.WATCHING : State.NO_MODEL);
            return;
        }
        ModelInfo info = link.info();
        if (!info.causal()) {
            show(State.PEEKS);
            return;
        }
        if (watchedGrowth < Forecast.MIN_GROWTH) {
            show(State.WATCHING);
            return;
        }
        if (busy) {
            return;
        }
        // a field still being watched keeps changing: check a model again only once it has grown a good deal more
        boolean stale = !info.equals(checkedModel) || state != State.AHEAD && watchedGrowth >= checkedGrowth + Forecast.MIN_GROWTH / 4;
        if (stale) {
            String text = watched;
            int growth = watchedGrowth;
            submit(level, link, model -> Forecast.check(model, text), result -> checked(info, growth, result));
            show(State.CHECKING);
        } else if (state == State.AHEAD && check != null) {
            ahead(level, link, check.ahead() - 1);
        }
    }

    /** Looks at every crop around it once. */
    private void look(ServerLevel level) {
        Set<Long> seen = new HashSet<>();
        flashed.clear();
        Arrays.fill(map, 0);
        for (int dy = 1; dy >= -1; dy--) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                for (int dx = -RADIUS; dx <= RADIUS; dx++) {
                    BlockPos p = worldPosition.offset(dx, dy, dz);
                    BlockState s = level.getBlockState(p);
                    // only crops that grow the way practice does, through seven ages
                    if (!(s.getBlock() instanceof CropBlock crop) || crop.getMaxAge() != Growth.RIPE) {
                        continue;
                    }
                    long key = p.asLong();
                    seen.add(key);
                    int age = crop.getAge(s), cell = (dz + RADIUS) * SIDE + dx + RADIUS;
                    if (map[cell] == 0) {
                        map[cell] = age + 1;
                    }
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
            if (!seen.contains(e.getKey())) {
                close(e.getValue());
                return true;
            }
            return false;
        });
        grown.clear();
        StringBuilder open = new StringBuilder();
        watches.values().forEach(w -> {
            if (!w.closed && kept(w)) {
                open.append(w.line).append('\n');
            }
        });
        watched(capped(record + open));
        setChanged();
    }

    /** A line is kept if it holds only what the crop did by itself, and saw it grow. */
    private static boolean kept(Watch w) {
        return !w.drawn && w.line.length() >= 2 && w.line.charAt(0) != '0' + Growth.RIPE;
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
        watchedGrowth = growth(text);
        seen = Forecast.seen(text);
        ItemStack log = items.getItem(LOG);
        if (log.getItem() instanceof FieldLogItem && !text.equals(FieldLogItem.log(log))) {
            log.set(ModComponents.FIELD_LOG.get(), text);
        }
    }

    private void checked(ModelInfo info, int growth, @Nullable Forecast.Check result) {
        if (result == null) {
            problem = PROBLEMS.indexOf("residual.model.failed");
            show(State.NO_MODEL);
            return;
        }
        boolean was = state == State.AHEAD;
        check = result;
        checkedModel = info;
        checkedGrowth = growth;
        show(result.ahead() > 0 ? State.AHEAD : State.REFUSED);
        if (!was && state == State.AHEAD && level instanceof ServerLevel server) {
            server.playSound(null, worldPosition, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.6f, 1.6f);
        }
    }

    /** Draws the next {@code steps} looks of every growing crop from the model, and makes them so. */
    private void ahead(ServerLevel level, ModelLink link, int steps) {
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
                made(level, keys, drawn);
            }
        });
    }

    private void made(ServerLevel level, List<Long> keys, String[] drawn) {
        int grew = 0;
        for (int i = 0; i < keys.size(); i++) {
            Watch w = watches.get(keys.get(i));
            BlockPos p = BlockPos.of(keys.get(i));
            BlockState s = level.getBlockState(p);
            if (w == null || w.closed || !(s.getBlock() instanceof CropBlock crop) || crop.getAge(s) != w.age) {
                continue;
            }
            int was = w.age;
            for (char c : drawn[i].toCharArray()) {
                w.look(c - '0');
            }
            w.drawn = true;
            if (w.age > was) {
                level.setBlock(p, crop.getStateForAge(w.age), Block.UPDATE_CLIENTS);
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, p.getX() + 0.5, p.getY() + 0.4, p.getZ() + 0.5, 3, 0.25, 0.2, 0.25, 0);
                flashed.set((p.getZ() - worldPosition.getZ() + RADIUS) * SIDE + p.getX() - worldPosition.getX() + RADIUS);
                grew++;
            }
            if (w.ripeLooks >= Growth.RIPE_FOR) {
                close(w);
            }
        }
        if (grew > 0) {
            for (ServerPlayer player : level.players()) {
                if (player.distanceToSqr(worldPosition.getCenter()) < 32 * 32) {
                    Awards.grant(player, "forecast/ahead");
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
        ItemStack log = items.getItem(LOG);
        if (log.getItem() instanceof FieldLogItem && !watched.equals(FieldLogItem.log(log))) {
            log.set(ModComponents.FIELD_LOG.get(), watched);
        }
        setChanged();
    }

    private void show(State s) {
        state = s;
        boolean on = s == State.AHEAD;
        BlockState here = getBlockState();
        if (level != null && here.getValue(ForecasterBlock.WORKING) != on) {
            level.setBlock(worldPosition, here.setValue(ForecasterBlock.WORKING, on), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.residual.forecaster");
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
        watchedGrowth = growth(record);
        seen = Forecast.seen(record);
    }

    /** How many ages the crops in a record grew in all: a line's last age less its first. */
    private static int growth(String record) {
        int sum = 0;
        for (String line : record.split("\n")) {
            sum += line.isEmpty() ? 0 : line.charAt(line.length() - 1) - line.charAt(0);
        }
        return sum;
    }
}
