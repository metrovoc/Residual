package io.github.metrovoc.residual.block;

import io.github.metrovoc.residual.Awards;
import io.github.metrovoc.residual.Residual;
import io.github.metrovoc.residual.ResidualConfig;
import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.item.CrystalItem;
import io.github.metrovoc.residual.item.DatasetItem;
import io.github.metrovoc.residual.item.DriveItem;
import io.github.metrovoc.residual.item.FieldLogItem;
import io.github.metrovoc.residual.learn.Archive;
import io.github.metrovoc.residual.learn.Benchmark;
import io.github.metrovoc.residual.learn.Demand;
import io.github.metrovoc.residual.learn.FieldTask;
import io.github.metrovoc.residual.learn.Task;
import io.github.metrovoc.residual.learn.Trainer;
import io.github.metrovoc.residual.learn.Vocab;
import io.github.metrovoc.residual.menu.InstrumentMenu;
import io.github.metrovoc.residual.model.Checkpoints;
import io.github.metrovoc.residual.model.ModelInfo;
import io.github.metrovoc.residual.net.ScopeAction;
import io.github.metrovoc.residual.net.ScopeReading;
import io.github.metrovoc.residual.net.ScopeSync;
import io.github.metrovoc.residual.net.StreamMarks;
import io.github.metrovoc.residual.registry.ModBlockEntities;
import io.github.metrovoc.residual.registry.ModBlocks;
import io.github.metrovoc.residual.registry.ModComponents;
import io.github.metrovoc.residual.scope.Features;
import io.github.metrovoc.residual.scope.Instrument;
import io.github.metrovoc.residual.scope.Picture;
import io.github.metrovoc.residual.scope.Records;
import io.github.metrovoc.residual.scope.Stream;
import io.github.metrovoc.residual.scope.Work;
import io.github.metrovoc.residual.tensor.Parallel;
import io.github.metrovoc.residual.train.Jobs;
import io.github.metrovoc.residual.world.Cooling;
import io.github.metrovoc.residual.world.GraphScanner;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * A Stream Scope or a Trial Rig. It works on a copy of a model: the one in its crystal, or with none, the one standing on the Model Core
 * it touches, whose machines it can then draw over. The work runs off the server thread, one piece at a time, with the threads the
 * Accelerators touching it lend, and what it comes to is sent to whoever has its screen open.
 */
public class InstrumentBlockEntity extends BlockEntity {
    public static final int MODEL = 0, DATA = 1, OUTPUT = 2, SLOTS = 3;
    /** Ticks between two looks at whether there is anything new to tell its screen. */
    private static final int SYNC_EVERY = 5;

    /** One piece of work under way, read by the server thread and written by its own. */
    private static final class Running implements Work.Watcher {
        final Instrument instrument;
        final ServerPlayer player;
        volatile boolean stop;
        volatile float fraction;
        @Nullable
        volatile Picture shown;

        Running(Instrument instrument, ServerPlayer player) {
            this.instrument = instrument;
            this.player = player;
        }

        @Override
        public boolean stopped() {
            return stop;
        }

        @Override
        public void at(float f) {
            fraction = f;
        }

        @Override
        public void show(Picture picture) {
            shown = picture;
        }
    }

    private final SimpleContainer items = new SimpleContainer(SLOTS) {
        @Override
        public void setChanged() {
            super.setChanged();
            InstrumentBlockEntity.this.setChanged();
            note = Component.empty();
            changed = true;
        }
    };
    @Nullable
    private Running running;
    private final Map<Instrument, Picture> pictures = new EnumMap<>(Instrument.class);
    /** How the last work came out, and what can be said of the model, which shows when there is nothing of the other to say. */
    private Component note = Component.empty(), modelLine = Component.empty();
    private boolean changed = true;
    /** The model last looked at, and the dictionaries fitted to it by point: fitted again only when it changes. */
    @Nullable
    private Object fittedTo;
    private Map<Integer, Features.Dictionary> dictionaries = new HashMap<>();
    private List<Component> points = List.of();
    /** Where each node of the Core's model stands, while the work looks at that. */
    @Nullable
    private List<BlockPos> machines;

    public InstrumentBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.INSTRUMENT.get(), pos, state);
    }

    public boolean rig() {
        return getBlockState().is(ModBlocks.TRIAL_RIG.get());
    }

    public SimpleContainer items() {
        return items;
    }

    public void open(Player player) {
        if (!(player instanceof ServerPlayer sp)) {
            return;
        }
        boolean rig = rig();
        sp.openMenu(new SimpleMenuProvider((id, inv, p) -> new InstrumentMenu(id, inv, worldPosition, rig, items), getBlockState().getBlock().getName()),
                buf -> {
                    buf.writeBlockPos(worldPosition);
                    buf.writeBoolean(rig);
                });
        look();
        PacketDistributor.sendToPlayer(sp, status());
        pictures.forEach((instrument, picture) -> PacketDistributor.sendToPlayer(sp, new ScopeReading(worldPosition, instrument.ordinal(), picture)));
    }

    /** A model to work on, fetched off the server thread as a copy of its own, with where its machines stand if they stand in the world. */
    private record Source(Supplier<CompiledGraph> model, Object key, @Nullable List<BlockPos> machines) {
    }

    /** The model in its crystal, or the one on a Model Core it touches; or null, having said why. */
    @Nullable
    private Source source() {
        ModelInfo info = CrystalItem.model(items.getItem(MODEL));
        if (info != null) {
            MinecraftServer server = level.getServer();
            return new Source(() -> {
                CompiledGraph saved = Checkpoints.load(server, info.id());
                return saved == null ? null : saved.copy();
            }, info.id() + "/" + info.steps(), null);
        }
        for (Direction d : Direction.values()) {
            if (level.getBlockEntity(worldPosition.relative(d)) instanceof CoreBlockEntity core) {
                CoreBlockEntity.Checked checked = core.check();
                if (checked == null) {
                    modelLine = Component.translatable("residual.scope.core_broken");
                    return null;
                }
                return new Source(checked::model, core.getBlockPos() + "/" + core.steps(), checked.scan().nodes());
            }
        }
        modelLine = Component.translatable("residual.scope.no_model");
        return null;
    }

    /** What the screen says of the model and what it can choose among, kept up to date as the slots change. */
    private void look() {
        Source s = source();
        if (s == null) {
            points = List.of();
            return;
        }
        MinecraftServer server = level.getServer();
        Jobs.EXECUTOR.execute(() -> {
            CompiledGraph model = s.model().get();
            Stream stream = model == null ? null : Stream.of(model);
            List<Component> names = new ArrayList<>();
            for (int p = 0; stream != null && p < stream.points.length; p++) {
                names.add(stream.name(stream.points[p]));
            }
            Jobs.back(server, () -> {
                points = names;
                modelLine = stream == null ? Component.translatable("residual.scope.model_lost")
                        : Component.translatable("residual.scope.model", String.format(java.util.Locale.ROOT, "%,d", model.parameterCount()), Integer.toString(stream.points.length));
                sendStatus();
            });
        });
    }

    public void act(ServerPlayer player, ScopeAction a) {
        if (a.instrument() < 0) {
            if (running != null) {
                running.stop = true;
            }
            return;
        }
        Instrument instrument = Instrument.values()[Math.floorMod(a.instrument(), Instrument.values().length)];
        if (running != null || instrument.trial != rig()) {
            return;
        }
        Source s = source();
        if (s == null) {
            changed = true;
            return;
        }
        if (!s.key().equals(fittedTo)) {
            fittedTo = s.key();
            dictionaries = new HashMap<>();
        }
        Benchmark benchmark = Benchmark.values()[Math.floorMod(a.benchmark(), Benchmark.values().length)];
        Running run = new Running(instrument, player);
        running = run;
        machines = s.machines();
        note = Component.empty();
        setWorking(true);
        ItemStack dataSlot = items.getItem(DATA).copy();
        long seed = level.getRandom().nextLong();
        Map<Integer, Features.Dictionary> kept = dictionaries;
        int threads = Cooling.compute(level, worldPosition, ResidualConfig.BASE_THREADS.get(), ResidualConfig.MAX_THREADS.get()).threads();
        MinecraftServer server = level.getServer();
        Jobs.EXECUTOR.execute(() -> {
            Work.Result result = null;
            String failure = null;
            ForkJoinPool pool = threads > 1 ? new ForkJoinPool(threads) : null;
            Work.Result[] out = new Work.Result[1];
            try {
                CompiledGraph model = s.model().get();
                // a drive's text takes a while to cut into tokens
                Work.Data data = model == null ? null : data(dataSlot, model.env.tokenizer());
                Parallel.with(pool, () -> out[0] = model == null ? new Work.Result(Picture.NONE, Component.translatable("residual.scope.model_lost"))
                        : Work.run(new Work.Request(instrument, model, benchmark, a.a(), a.b(), a.text(), seed, data, kept), run));
                result = out[0];
            } catch (RuntimeException e) {
                Residual.LOG.error("{} failed", instrument.id, e);
                failure = String.valueOf(e);
            } finally {
                if (pool != null) {
                    pool.shutdown();
                }
            }
            Work.Result done = result;
            String failed = failure;
            Jobs.back(server, () -> finished(run, done, failed));
        });
        changed = true;
    }

    /** What is in the data slot, as what a model could practise on. */
    @Nullable
    private static Work.Data data(ItemStack stack, Vocab vocab) {
        if (stack.getItem() instanceof DatasetItem dataset) {
            return new Work.Data(dataset.benchmark.practice(), dataset.benchmark, List.of());
        }
        if (stack.getItem() instanceof DriveItem) {
            var contents = DriveItem.contents(stack);
            Task text = Archive.practice(contents, vocab);
            return new Work.Data(text, Benchmark.READING, Archive.legible(contents));
        }
        if (stack.getItem() instanceof FieldLogItem) {
            String log = FieldLogItem.log(stack);
            return new Work.Data(new FieldTask(log, vocab), Demand.isLog(log) ? Benchmark.DEMAND : Benchmark.FORECASTING, List.of());
        }
        return null;
    }

    private void finished(Running run, @Nullable Work.Result result, @Nullable String failure) {
        if (running == run) {
            running = null;
        }
        if (isRemoved()) {
            return;
        }
        setWorking(false);
        changed = true;
        if (failure != null) {
            note = Component.translatable("residual.scope.failed", failure);
            return;
        }
        if (result == null) {
            return;
        }
        note = run.stop ? Component.translatable("residual.scope.stopped") : result.note();
        if (result.picture() != Picture.NONE) {
            deliver(run.instrument, result.picture());
        }
        if (!run.stop && result.award() != null && run.player.connection != null) {
            Awards.grant(run.player, result.award());
        }
        if (!run.stop && machines != null && (!result.marks().isEmpty() || !result.beams().isEmpty()) && level instanceof ServerLevel server) {
            PacketDistributor.sendToPlayersTrackingChunk(server, ChunkPos.containing(worldPosition), StreamMarks.of(result, machines, adjacentCore()));
        }
        if (!run.stop && result.output() != null) {
            keep(result.output(), run.player);
        }
        level.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.8f, 1.2f);
    }

    @Nullable
    private BlockPos adjacentCore() {
        for (Direction d : Direction.values()) {
            if (level.getBlockEntity(worldPosition.relative(d)) instanceof CoreBlockEntity core) {
                return core.getBlockPos();
            }
        }
        return null;
    }

    /** Writes the trial's model into the crystal in the output slot, if there is one, tested as a save would test it. */
    private void keep(CompiledGraph model, ServerPlayer player) {
        ItemStack crystal = items.getItem(OUTPUT);
        if (!(crystal.getItem() instanceof CrystalItem)) {
            return;
        }
        ModelInfo old = CrystalItem.model(crystal);
        UUID id = old != null ? old.id() : UUID.randomUUID();
        MinecraftServer server = level.getServer();
        Jobs.EXECUTOR.execute(() -> {
            Map<String, Float> scores = new HashMap<>();
            boolean causal = true;
            Records.Submission submission;
            byte[] encoded;
            try {
                for (Benchmark a : Benchmark.values()) {
                    Trainer.Eval e = Trainer.evaluate(model, a.test(model.env.tokenizer()), 4);
                    scores.put(a.id, (float) (a.metric == Task.Metric.LOSS ? e.loss() : e.accuracy()));
                    causal &= e.causal();
                }
                submission = Records.submission(model, scores, causal);
                encoded = Checkpoints.encode(model);
            } catch (IOException | RuntimeException e) {
                Residual.LOG.error("Keeping a model failed", e);
                return;
            }
            ModelInfo info = new ModelInfo(id, model.parameterCount(), old == null ? 0 : old.steps() + 1, scores, causal);
            Jobs.back(server, () -> {
                if (isRemoved() || items.getItem(OUTPUT) != crystal) {
                    return;
                }
                try {
                    Checkpoints.save(server, id, model, encoded);
                } catch (IOException | RuntimeException e) {
                    Residual.LOG.error("Keeping a model failed", e);
                    return;
                }
                crystal.set(ModComponents.MODEL.get(), info);
                items.setChanged();
                Records.of(server).submit(player, submission);
            });
        });
    }

    private void deliver(Instrument instrument, Picture picture) {
        pictures.put(instrument, picture);
        for (ServerPlayer player : watchers()) {
            PacketDistributor.sendToPlayer(player, new ScopeReading(worldPosition, instrument.ordinal(), picture));
        }
    }

    private List<ServerPlayer> watchers() {
        List<ServerPlayer> out = new ArrayList<>();
        if (level instanceof ServerLevel server) {
            for (ServerPlayer p : server.players()) {
                if (p.containerMenu instanceof InstrumentMenu m && m.pos.equals(worldPosition)) {
                    out.add(p);
                }
            }
        }
        return out;
    }

    private void sendStatus() {
        ScopeSync sync = status();
        for (ServerPlayer player : watchers()) {
            PacketDistributor.sendToPlayer(player, sync);
        }
    }

    private ScopeSync status() {
        return new ScopeSync(worldPosition, running != null, running == null ? 0 : running.fraction, note.getString().isEmpty() ? modelLine : note, points);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, InstrumentBlockEntity be) {
        be.tick();
    }

    private void tick() {
        if (level.getGameTime() % SYNC_EVERY != 0) {
            return;
        }
        Running run = running;
        if (run != null) {
            Picture shown = run.shown;
            if (shown != null) {
                run.shown = null;
                deliver(run.instrument, shown);
            }
            changed = true;
        }
        if (changed) {
            changed = false;
            if (run == null) {
                look();
                // work cut off by the world closing leaves the block looking busy
                setWorking(false);
            }
            sendStatus();
        }
    }

    private void setWorking(boolean on) {
        BlockState state = getBlockState();
        if (state.getValue(InstrumentBlock.WORKING) != on) {
            level.setBlock(worldPosition, state.setValue(InstrumentBlock.WORKING, on), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) {
            Containers.dropContents(level, pos, items);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (running != null) {
            running.stop = true;
        }
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        ContainerHelper.saveAllItems(out.child("items"), items.getItems());
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        in.child("items").ifPresent(c -> ContainerHelper.loadAllItems(c, items.getItems()));
    }
}
