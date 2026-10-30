package io.github.metrovoc.residual.block;

import io.github.metrovoc.residual.Awards;
import io.github.metrovoc.residual.Residual;
import io.github.metrovoc.residual.ResidualConfig;
import io.github.metrovoc.residual.graph.CompileException;
import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.graph.Env;
import io.github.metrovoc.residual.graph.GraphSpec;
import io.github.metrovoc.residual.graph.Sym;
import io.github.metrovoc.residual.item.CrystalItem;
import io.github.metrovoc.residual.item.DatasetItem;
import io.github.metrovoc.residual.item.DriveItem;
import io.github.metrovoc.residual.item.FieldLogItem;
import io.github.metrovoc.residual.item.TokenizerItem;
import io.github.metrovoc.residual.item.WreckItem;
import io.github.metrovoc.residual.learn.Benchmark;
import io.github.metrovoc.residual.learn.Archive;
import io.github.metrovoc.residual.learn.Dataset;
import io.github.metrovoc.residual.learn.FieldTask;
import io.github.metrovoc.residual.learn.Mixture;
import io.github.metrovoc.residual.learn.Sampler;
import io.github.metrovoc.residual.learn.Task;
import io.github.metrovoc.residual.learn.TextTask;
import io.github.metrovoc.residual.learn.Trainer;
import io.github.metrovoc.residual.learn.Vocab;
import io.github.metrovoc.residual.learn.Wrecks;
import io.github.metrovoc.residual.menu.CoreMenu;
import io.github.metrovoc.residual.model.Checkpoints;
import io.github.metrovoc.residual.model.ModelInfo;
import io.github.metrovoc.residual.net.CoreSync;
import io.github.metrovoc.residual.net.WaveSync;
import io.github.metrovoc.residual.net.SampleResult;
import io.github.metrovoc.residual.registry.ModBlockEntities;
import io.github.metrovoc.residual.registry.ModComponents;
import io.github.metrovoc.residual.tensor.Tensor;
import io.github.metrovoc.residual.train.Jobs;
import io.github.metrovoc.residual.train.TrainingJob;
import io.github.metrovoc.residual.world.CaseBuilder;
import io.github.metrovoc.residual.world.CaseLayout;
import io.github.metrovoc.residual.world.Cooling;
import io.github.metrovoc.residual.world.GraphScanner;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
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
 * The Model Core's state: the data it trains on, the optimiser settings, the learning curve, the latest test scores
 * and the running training job. Only the server has it; open screens receive it as {@link CoreSync}.
 */
public class CoreBlockEntity extends BlockEntity {
    public static final float[] LEARNING_RATES = {1e-4f, 3e-4f, 1e-3f, 3e-3f, 1e-2f, 3e-2f, 0.1f, 0.3f, 1.5f};
    public static final int[] BATCH_SIZES = {8, 16, 32, 64};
    public static final float[] CLIPS = {0f, 0.5f, 1f, 5f};
    private static final int HISTORY = 400;
    /** Batches per benchmark when every test is run: twice what training checks with, for a steadier score. */
    private static final int BATTERY = 8;
    /** How often a training model sends a pair of waves through its blocks, and the Core's own place in them. */
    private static final long WAVE_MS = 2000;
    private static final int LOSS = Integer.MIN_VALUE;

    private final SimpleContainer items = new SimpleContainer(CoreMenu.SLOTS) {
        @Override
        public void setChanged() {
            super.setChanged();
            slotsChanged();
        }
    };
    private int learningRate;
    private int batchSize;
    private int clip;
    private TrainingJob.Speed speed = TrainingJob.Speed.NORMAL;
    /** Steps trained, and since the weights were last reset or a wreck rebuilt (a wreck's budget counts these). */
    private int steps;
    private int runSteps;
    private final List<TrainingJob.Point> history = new ArrayList<>();
    private float trainLoss = Float.NaN;
    private float valLoss = Float.NaN;
    private float accuracy = Float.NaN;
    private float gradNorm = Float.NaN;
    private boolean causal = true;
    private final Map<Benchmark, CoreSync.Score> tests = new EnumMap<>(Benchmark.class);
    private String message = "residual.status.ready";
    private List<String> args = List.of();
    private List<CoreSync.Issue> issues = List.of();
    private List<CoreSync.Node> nodes = List.of();
    private long parameters;
    private Cooling.Compute compute = new Cooling.Compute(1, 0);

    @Nullable
    private TrainingJob job;
    @Nullable
    private UUID owner;
    private int jobSteps;
    private int jobRunSteps;
    /** The wreck in the data slots when they last changed. */
    @Nullable
    private String wreck;
    /** A full test run or a save is under way off the server thread. */
    private boolean busy;
    private boolean dirty;
    private long lastSync;
    private long lastWave;
    private int ticks;
    /** A block the last check read has changed since, so the model gets checked again. */
    private boolean stale;
    /** The practice the slots hold, kept until they change: a drive's text takes a while to cut into tokens. */
    @Nullable
    private Mixture practice;
    private boolean practiceKnown;

    public CoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CORE.get(), pos, state);
        applySettings(Wrecks.Settings.DEFAULT);
    }

    public boolean running() {
        return job != null;
    }

    private void applySettings(Wrecks.Settings s) {
        learningRate = nearest(LEARNING_RATES, s.learningRate());
        batchSize = nearest(BATCH_SIZES, s.batchSize());
        clip = nearest(CLIPS, s.clip());
    }

    private static int nearest(float[] choices, float value) {
        int best = 0;
        for (int i = 1; i < choices.length; i++) {
            if (Math.abs(choices[i] - value) < Math.abs(choices[best] - value)) {
                best = i;
            }
        }
        return best;
    }

    private static int nearest(int[] choices, int value) {
        int best = 0;
        for (int i = 1; i < choices.length; i++) {
            if (Math.abs(choices[i] - value) < Math.abs(choices[best] - value)) {
                best = i;
            }
        }
        return best;
    }

    // ---------------------------------------------------------------- data

    /** The vocabulary models built here read and write with. */
    public Vocab tokenizer() {
        return TokenizerItem.vocab(items.getItem(CoreMenu.TOKENIZER));
    }

    /** What the loaded data practises, or null if nothing loaded can be practised on. */
    @Nullable
    public Mixture mixture() {
        if (!practiceKnown) {
            practice = practice();
            practiceKnown = true;
        }
        return practice;
    }

    @Nullable
    private Mixture practice() {
        Vocab vocab = tokenizer();
        List<Mixture.Source> sources = new ArrayList<>();
        for (int i = 0; i < CoreMenu.DATA_SLOTS; i++) {
            ItemStack stack = items.getItem(i);
            if (stack.getItem() instanceof DatasetItem dataset) {
                sources.add(new Mixture.Source(dataset.benchmark, Dataset.of(dataset.benchmark)));
            } else if (stack.getItem() instanceof DriveItem) {
                TextTask text = Archive.practice(DriveItem.contents(stack), vocab);
                if (!text.isEmpty()) {
                    sources.add(new Mixture.Source(Benchmark.READING, text));
                }
            } else if (stack.getItem() instanceof FieldLogItem) {
                FieldTask field = new FieldTask(FieldLogItem.log(stack), vocab);
                if (!field.isEmpty()) {
                    sources.add(new Mixture.Source(Benchmark.FORECASTING, field));
                }
            } else if (WreckItem.wreck(stack) instanceof Wrecks.Wreck w) {
                sources.add(new Mixture.Source(w.benchmark(), w.practice()));
            }
        }
        return sources.isEmpty() ? null : new Mixture(sources, vocab);
    }

    /** The wreck loaded in the data slots, if any. */
    @Nullable
    public Wrecks.Wreck wreck() {
        for (int i = 0; i < CoreMenu.DATA_SLOTS; i++) {
            Wrecks.Wreck w = WreckItem.wreck(items.getItem(i));
            if (w != null) {
                return w;
            }
        }
        return null;
    }

    private void slotsChanged() {
        practiceKnown = false;
        if (level == null || level.isClientSide()) {
            return;
        }
        Wrecks.Wreck w = wreck();
        String id = w == null ? null : w.id();
        boolean newWreck = !Objects.equals(id, wreck);
        wreck = id;
        if (job != null) {
            Mixture m = mixture();
            // a new tokenizer reshapes the model's first and last layers
            if (newWreck || !tokenizer().equals(job.model.env.tokenizer())) {
                job.requestStop(TrainingJob.Outcome.CHANGED);
            } else if (m == null) {
                job.requestStop(TrainingJob.Outcome.STOPPED);
            } else {
                job.data = m;
            }
        } else {
            stale = true;
            if (newWreck && w != null) {
                // a wreck comes with the settings it was trained with, faults and all
                applySettings(w.settings());
            }
        }
        touch();
    }

    // ---------------------------------------------------------------- screen actions (server thread)

    public void open(Player player) {
        if (player instanceof ServerPlayer sp && level instanceof ServerLevel) {
            if (job == null) {
                check();
            }
            sp.openMenu(new SimpleMenuProvider((id, inv, p) -> new CoreMenu(id, inv, worldPosition, items),
                    getBlockState().getBlock().getName()), worldPosition);
            PacketDistributor.sendToPlayer(sp, snapshot());
        }
    }

    public void setLearningRate(int i) {
        learningRate = Math.floorMod(i, LEARNING_RATES.length);
        if (job != null) {
            job.learningRate = LEARNING_RATES[learningRate];
        }
        touch();
    }

    public void setBatchSize(int i) {
        batchSize = Math.floorMod(i, BATCH_SIZES.length);
        if (job != null) {
            job.batchSize = BATCH_SIZES[batchSize];
        }
        touch();
    }

    public void setClip(int i) {
        clip = Math.floorMod(i, CLIPS.length);
        if (job != null) {
            job.clip = CLIPS[clip];
        }
        touch();
    }

    public void setSpeed(int i) {
        speed = TrainingJob.Speed.values()[Math.floorMod(i, TrainingJob.Speed.values().length)];
        if (job != null) {
            job.speed = speed;
        }
        touch();
    }

    public void start(ServerPlayer player) {
        if (job != null || !(level instanceof ServerLevel server)) {
            return;
        }
        Mixture data = mixture();
        if (data == null) {
            say("residual.status.no_data");
            return;
        }
        Wrecks.Wreck w = wreck();
        int remaining = 0;
        if (w != null) {
            remaining = Wrecks.BUDGET - runSteps;
            if (remaining <= 0) {
                say("residual.status.budget_used", Integer.toString(Wrecks.BUDGET));
                return;
            }
        }
        Checked checked = check();
        if (checked == null) {
            return;
        }
        compute = compute();
        job = new TrainingJob(server, worldPosition, checked.scan, checked.model, data, w, server.getRandom().nextLong(),
                compute.threads(), remaining, speed, LEARNING_RATES[learningRate], BATCH_SIZES[batchSize], CLIPS[clip]);
        jobSteps = steps;
        jobRunSteps = runSteps;
        owner = player.getUUID();
        Jobs.start(job);
        setRunning(true);
        Cooling.working(level, worldPosition, true);
        say(w != null ? "residual.status.repairing" : "residual.status.training");
    }

    public void stop() {
        if (job != null) {
            job.requestStop(TrainingJob.Outcome.STOPPED);
        }
    }

    /** Forgets everything the model learned, so the next run starts from random weights. */
    public void reset() {
        if (job != null || level == null) {
            return;
        }
        for (BlockPos p : GraphScanner.scan(level, worldPosition).nodes()) {
            if (level.getBlockEntity(p) instanceof OperatorBlockEntity be) {
                be.resetWeights();
            }
        }
        clearCurve();
        say("residual.status.reset");
        check();
    }

    private void clearCurve() {
        steps = 0;
        runSteps = 0;
        history.clear();
        tests.clear();
        trainLoss = valLoss = accuracy = gradNorm = Float.NaN;
        causal = true;
    }

    /** Rebuilds the loaded wreck's machine in front of the Core, with the settings it was trained with. */
    public void buildWreck() {
        Wrecks.Wreck w = wreck();
        if (job != null || w == null || !(level instanceof ServerLevel server)) {
            return;
        }
        GraphSpec spec = w.machine().get();
        CaseLayout layout = CaseLayout.of(spec);
        BlockPos blocked = CaseBuilder.obstacle(server, worldPosition, layout);
        if (blocked != null) {
            say("residual.status.no_room", Integer.toString(layout.length - 1), Integer.toString(layout.width),
                    Integer.toString(layout.height), blocked.toShortString());
            return;
        }
        CaseBuilder.build(server, worldPosition, spec, layout);
        level.playSound(null, worldPosition, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.7f, 1.4f);
        applySettings(w.settings());
        clearCurve();
        say("residual.status.built");
        check();
    }

    /** Runs the model on a prompt, or on a fresh example of one benchmark, and sends the player what it wrote. */
    public void sample(ServerPlayer player, String prompt, int value) {
        Checked checked = check();
        if (checked == null) {
            PacketDistributor.sendToPlayer(player, new SampleResult("", "", List.of(), List.of(),
                    Component.translatable("residual.sample.unchecked")));
            return;
        }
        Benchmark benchmark = Benchmark.values()[Math.floorMod(value / 1000, Benchmark.values().length)];
        float temperature = Math.floorMod(value, 1000) / 100f;
        long seed = level.getRandom().nextLong();
        MinecraftServer server = player.level().getServer();
        AbstractContainerMenu menu = player.containerMenu;
        Jobs.EXECUTOR.execute(() -> {
            SampleResult result;
            try {
                Sampler.Demo d = Sampler.demo(checked.model(), benchmark, prompt, temperature, new Random(seed));
                result = new SampleResult(d.prompt(), d.text(), d.labels(), d.probs(), d.note());
            } catch (RuntimeException e) {
                Residual.LOG.error("Sampling failed", e);
                result = new SampleResult("", "", List.of(), List.of(), Component.translatable("residual.sample.failed", e.toString()));
            }
            SampleResult reply = result;
            Jobs.back(server, () -> {
                if (player.containerMenu == menu) {
                    PacketDistributor.sendToPlayer(player, reply);
                }
            });
        });
    }

    /** Runs every benchmark's test on the model as it stands. */
    public void runTests() {
        battery(null);
    }

    /** Runs every test, then saves the model with its scores into the crystal in the crystal slot. */
    public void save(ServerPlayer player) {
        if (!(items.getItem(CoreMenu.CRYSTAL).getItem() instanceof CrystalItem)) {
            say("residual.status.no_crystal");
            return;
        }
        battery(player);
    }

    private void battery(@Nullable ServerPlayer saver) {
        if (busy || !(level instanceof ServerLevel server)) {
            return;
        }
        Checked checked = check();
        if (checked == null) {
            return;
        }
        busy = true;
        ItemStack target = items.getItem(CoreMenu.CRYSTAL);
        ModelInfo old = CrystalItem.model(target);
        // saving over a crystal keeps its model's name
        UUID id = old != null ? old.id() : UUID.randomUUID();
        int at = steps;
        long count = parameters;
        MinecraftServer mc = server.getServer();
        say(saver != null ? "residual.status.saving" : "residual.status.testing");
        Jobs.EXECUTOR.execute(() -> {
            Map<Benchmark, Trainer.Eval> results = new EnumMap<>(Benchmark.class);
            String failure = null;
            byte[] checkpoint = null;
            try {
                for (Benchmark a : Benchmark.values()) {
                    results.put(a, Trainer.evaluate(checked.model(), a.test(checked.model().env.tokenizer()), BATTERY));
                }
                if (saver != null) {
                    checkpoint = Checkpoints.encode(checked.model());
                }
            } catch (IOException | RuntimeException e) {
                Residual.LOG.error("Testing or saving a model failed", e);
                failure = String.valueOf(e);
            }
            String failed = failure;
            byte[] encoded = checkpoint;
            Jobs.back(mc, () -> tested(results, failed, saver, target, new ModelInfo(id, count, at, scores(results), causal(results)),
                    checked.model(), encoded));
        });
    }

    private static Map<String, Float> scores(Map<Benchmark, Trainer.Eval> results) {
        Map<String, Float> out = new HashMap<>();
        results.forEach((a, e) -> out.put(a.id, (float) (a.metric == Task.Metric.LOSS ? e.loss() : e.accuracy())));
        return out;
    }

    private static boolean causal(Map<Benchmark, Trainer.Eval> results) {
        return results.values().stream().allMatch(Trainer.Eval::causal);
    }

    private void tested(Map<Benchmark, Trainer.Eval> results, @Nullable String failure, @Nullable ServerPlayer saver,
                        ItemStack target, ModelInfo info, CompiledGraph model, @Nullable byte[] checkpoint) {
        busy = false;
        if (isRemoved() || !(level instanceof ServerLevel server)) {
            return;
        }
        results.forEach((a, e) -> tests.put(a, new CoreSync.Score((float) e.loss(), (float) e.accuracy(), info.steps())));
        if (failure != null) {
            say("residual.status.crashed", failure);
            return;
        }
        if (saver == null) {
            say(info.causal() ? "residual.status.tested" : "residual.status.leak");
            return;
        }
        ItemStack crystal = items.getItem(CoreMenu.CRYSTAL);
        if (crystal != target || !(crystal.getItem() instanceof CrystalItem)) {
            say("residual.status.no_crystal");
            return;
        }
        try {
            Checkpoints.save(server.getServer(), info.id(), model, checkpoint);
        } catch (IOException | RuntimeException e) {
            Residual.LOG.error("Saving a model failed", e);
            say("residual.status.crashed", String.valueOf(e));
            return;
        }
        crystal.set(ModComponents.MODEL.get(), info);
        items.setChanged();
        level.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1f, 1.2f);
        say("residual.status.saved", info.tag());
        Awards.grant(saver, "model/saved");
        int passed = 0;
        for (Benchmark a : Benchmark.values()) {
            if (info.passes(a)) {
                Awards.grant(saver, "benchmark/" + a.id);
                passed++;
            }
        }
        if (passed == Benchmark.values().length) {
            Awards.grant(saver, "model/complete");
        }
    }

    private Cooling.Compute compute() {
        return Cooling.compute(level, worldPosition, ResidualConfig.BASE_THREADS.get(), ResidualConfig.MAX_THREADS.get());
    }

    // ---------------------------------------------------------------- checking the model

    /** A model that checked out, ready to train, test or sample. */
    public record Checked(GraphScanner.Scan scan, CompiledGraph model) {
    }

    /**
     * Reads the model out of the world and checks it, updating every machine's status and the issue list.
     * Returns null (with the issues recorded) if it can't run.
     */
    @Nullable
    public Checked check() {
        if (level == null) {
            return null;
        }
        Env env = new Env(tokenizer(), Vocab.CONTEXT);
        GraphScanner.Scan scan = GraphScanner.scan(level, worldPosition);
        if (job == null) {
            compute = compute();
        }
        List<CoreSync.Issue> found = new ArrayList<>();
        if (scan.problem() != null) {
            found.add(new CoreSync.Issue(null, scan.problem()));
            publish(scan, null, null, env, found);
            return null;
        }
        CompiledGraph model = null;
        Sym[] shapes;
        try {
            model = CompiledGraph.compile(scan.spec(), env, node -> paramsAt(scan.nodes().get(node)), level.getRandom().nextLong());
            shapes = model.shapes;
        } catch (CompileException e) {
            shapes = e.shapes;
            for (CompileException.Issue i : e.issues) {
                found.add(new CoreSync.Issue(i.node() >= 0 ? scan.nodes().get(i.node()) : null, i.message()));
            }
        }
        publish(scan, model, shapes, env, found);
        return found.isEmpty() && model != null ? new Checked(scan, model) : null;
    }

    private Map<String, Tensor> paramsAt(BlockPos pos) {
        return level.getBlockEntity(pos) instanceof OperatorBlockEntity be ? be.params() : Map.of();
    }

    /** Records what a check found, on the Core and on each machine. */
    private void publish(GraphScanner.Scan scan, @Nullable CompiledGraph model, @Nullable Sym[] shapes, Env env, List<CoreSync.Issue> found) {
        Jobs.watch(this, level, scan.footprint(), () -> stale = true);
        issues = List.copyOf(found);
        Map<BlockPos, Component> errors = new HashMap<>();
        for (CoreSync.Issue i : found) {
            if (i.pos() != null) {
                errors.merge(i.pos(), i.message(), (a, b) -> a.copy().append(" ").append(b));
            }
        }
        List<CoreSync.Node> rows = new ArrayList<>();
        long total = 0;
        for (int i : scan.spec().readingOrder()) {
            BlockPos p = scan.nodes().get(i);
            if (!(level.getBlockEntity(p) instanceof OperatorBlockEntity be)) {
                continue;
            }
            GraphSpec.Node n = scan.spec().node(i);
            Sym shape = shapes != null ? shapes[i] : null;
            int features = -1;
            if (n.op.arity > 0 && n.inputs[0] >= 0 && shapes != null && shapes[n.inputs[0]] != null) {
                features = shapes[n.inputs[0]].dim(-1);
            }
            boolean active = model != null && model.uses(i);
            long count = 0;
            if (model != null && active) {
                for (Tensor t : model.params(i).values()) {
                    count += t.size();
                }
            }
            total += count;
            Component error = errors.get(p);
            if (error == null && model != null && !active) {
                error = Component.translatable("residual.machine.unused");
            }
            be.setStatus(new OperatorBlockEntity.Status(shape != null ? shape.toString() : "", error, features, env.vocab(),
                    env.context(), count, be.status().gradNorm(), active));
            rows.add(new CoreSync.Node(p, n.op.id, n.name, shape != null ? shape.toString() : "", count, be.status().gradNorm(), active));
        }
        nodes = rows;
        parameters = total;
        if (!found.isEmpty()) {
            say(found.size() == 1 ? "residual.status.issue" : "residual.status.issues", Integer.toString(found.size()));
        } else if (job == null && message.startsWith("residual.status.issue")) {
            say("residual.status.ready");
        }
        touch();
    }

    // ---------------------------------------------------------------- training updates

    public static void serverTick(Level level, BlockPos pos, BlockState state, CoreBlockEntity be) {
        be.tick();
    }

    /** Takes in whatever the training thread produced; also called while the server stops. */
    public void tick() {
        if (ticks++ == 0 && job == null) {
            // accelerators left smoking by a Core that was unloaded mid-run
            Cooling.working(level, worldPosition, false);
        }
        if (job != null) {
            for (TrainingJob.Point p : job.drainPoints()) {
                addPoint(new TrainingJob.Point(jobSteps + p.step(), p.train(), p.val(), p.accuracy()));
            }
            boolean finished = job.finished();
            TrainingJob.Update u = job.poll();
            if (u != null) {
                apply(job, u);
            }
            if (finished && job != null) {
                Jobs.forget(job);
                job = null;
                setRunning(false);
                Cooling.working(level, worldPosition, false);
                touch();
            } else if (job != null && ticks % 100 == 0) {
                Cooling.working(level, worldPosition, true);
            }
        } else if (getBlockState().getValue(CoreBlock.RUNNING)) {
            setRunning(false);
        }
        if (stale && job == null) {
            stale = false;
            check();
        }
        if (dirty && System.currentTimeMillis() - lastSync > 250) {
            sync();
        }
    }

    private void addPoint(TrainingJob.Point p) {
        history.add(p);
        if (history.size() > HISTORY) {
            // keep the whole curve by thinning it out evenly by steps, so its start stays as detailed as its end
            float gap = (history.getLast().step() - history.getFirst().step()) / (HISTORY / 2f);
            List<TrainingJob.Point> thinned = new ArrayList<>();
            thinned.add(history.getFirst());
            for (int i = 1; i < history.size() - 1; i++) {
                if (history.get(i).step() - thinned.getLast().step() >= gap) {
                    thinned.add(history.get(i));
                }
            }
            thinned.add(history.getLast());
            history.clear();
            history.addAll(thinned);
        }
        touch();
    }

    private void apply(TrainingJob running, TrainingJob.Update u) {
        steps = jobSteps + u.steps;
        runSteps = jobRunSteps + u.steps;
        trainLoss = u.trainLoss;
        gradNorm = u.gradNorm;
        if (!u.tests.isEmpty()) {
            double loss = 0, acc = 0;
            int accs = 0;
            boolean allCausal = true;
            for (Map.Entry<Benchmark, Trainer.Eval> e : u.tests.entrySet()) {
                Trainer.Eval ev = e.getValue();
                tests.put(e.getKey(), new CoreSync.Score((float) ev.loss(), (float) ev.accuracy(), steps));
                loss += ev.loss();
                if (e.getKey().metric == io.github.metrovoc.residual.learn.Task.Metric.ACCURACY) {
                    acc += ev.accuracy();
                    accs++;
                }
                allCausal &= ev.causal();
            }
            valLoss = (float) (loss / u.tests.size());
            accuracy = accs == 0 ? Float.NaN : (float) (acc / accs);
            if (!allCausal && causal && u.outcome == null) {
                say("residual.status.leak");
            }
            causal = allCausal;
        }
        if (u.params != null) {
            commit(running, u);
        }
        if (u.outcome != null) {
            finish(running, u);
        }
        setChanged();
        touch();
    }

    private void commit(TrainingJob running, TrainingJob.Update u) {
        Map<BlockPos, Float> norms = new HashMap<>();
        for (int node : running.model.order) {
            BlockPos p = running.nodes.get(node);
            if (!(level.getBlockEntity(p) instanceof OperatorBlockEntity be) || be.op() != running.model.spec.node(node).op) {
                continue;
            }
            Map<String, Tensor> params = u.params.get(node);
            if (params != null) {
                be.commit(params);
            }
            float norm = u.gradNorms.getOrDefault(node, -1f);
            be.show(u.outputs.get(node), u.weights.get(node), norm);
            norms.put(p, norm);
        }
        List<CoreSync.Node> rows = new ArrayList<>(nodes.size());
        for (CoreSync.Node n : nodes) {
            rows.add(new CoreSync.Node(n.pos(), n.op(), n.name(), n.shape(), n.parameters(), norms.getOrDefault(n.pos(), n.gradNorm()), n.active()));
        }
        nodes = rows;
        long now = System.currentTimeMillis();
        if (now - lastWave >= WAVE_MS && !u.activity.isEmpty()) {
            lastWave = now;
            PacketDistributor.sendToPlayersTrackingChunk((ServerLevel) level, ChunkPos.containing(worldPosition), waves(running, u));
        }
    }

    /**
     * Lights the model's blocks in the order its nodes run: the cables from the Core's front carry the tokens, every
     * other block carries one node's output, and the Core takes in the loss. Brightness follows the size of what each
     * block carries on a log scale, two decades for activations and four for gradients, which fade faster.
     */
    private WaveSync waves(TrainingJob running, TrainingJob.Update u) {
        int[] order = running.model.order;
        int[] place = new int[running.model.spec.size()];
        for (int i = 0; i < order.length; i++) {
            place[order[i]] = i + 1;
        }
        int slots = order.length + 2;
        Map<Long, Integer> lit = new LinkedHashMap<>();
        for (int node : order) {
            lit.put(running.nodes.get(node).asLong(), node);
        }
        running.carries.forEach((pos, node) -> {
            if (node == GraphSpec.TOKENS || place[node] > 0) {
                lit.putIfAbsent(pos, node);
            }
        });
        lit.put(worldPosition.asLong(), LOSS);
        float topA = Collections.max(u.activity.values()), topG = Collections.max(u.gradient.values());
        int n = lit.size(), i = 0;
        long[] blocks = new long[n];
        int[] slot = new int[n];
        byte[] forward = new byte[n], backward = new byte[n];
        for (Map.Entry<Long, Integer> e : lit.entrySet()) {
            int node = e.getValue();
            blocks[i] = e.getKey();
            slot[i] = node == GraphSpec.TOKENS ? 0 : node == LOSS ? slots - 1 : place[node];
            forward[i] = node < 0 ? (byte) 255 : bright(u.activity.getOrDefault(node, 0f), topA, 2);
            backward[i] = node == LOSS ? (byte) 255 : node < 0 ? 0 : bright(u.gradient.getOrDefault(node, 0f), topG, 4);
            i++;
        }
        return new WaveSync(worldPosition, slots, blocks, slot, forward, backward);
    }

    private static byte bright(float value, float top, int decades) {
        if (!(value > 0) || !(top > 0)) {
            return 0;
        }
        double b = 1 + Math.log10(value / top) / decades;
        return (byte) Math.round(255 * Math.max(0.06, Math.min(1, b)));
    }

    private void finish(TrainingJob running, TrainingJob.Update u) {
        switch (u.outcome) {
            case PASSED -> {
                Wrecks.Wreck w = running.wreck;
                say("residual.status.repaired", "residual.wreck." + w.id() + ".title");
                ServerPlayer player = owner == null ? null : ((ServerLevel) level).getServer().getPlayerList().getPlayer(owner);
                if (player != null) {
                    player.giveExperienceLevels(3);
                    Awards.grant(player, "wreck/" + w.id());
                }
                level.playSound(null, worldPosition, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.8f, 1.2f);
                ((ServerLevel) level).sendParticles(ParticleTypes.END_ROD, worldPosition.getX() + 0.5, worldPosition.getY() + 1.1,
                        worldPosition.getZ() + 0.5, 40, 0.4, 0.3, 0.4, 0.05);
            }
            case BUDGET -> say("residual.status.budget_used", Integer.toString(Wrecks.BUDGET));
            case DIVERGED -> {
                if ("NaN".equals(u.detail)) {
                    say("residual.status.nan");
                } else {
                    say("residual.status.diverged", u.detail, String.format(Locale.ROOT, "%.2f", Math.log(running.model.env.vocab())));
                }
                level.playSound(null, worldPosition, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.6f, 0.8f);
            }
            case STOPPED -> say("residual.status.stopped");
            case CHANGED -> say("residual.status.changed");
            case CRASHED -> say("residual.status.crashed", u.detail);
        }
    }

    private void setRunning(boolean on) {
        BlockState state = getBlockState();
        if (level != null && state.getValue(CoreBlock.RUNNING) != on) {
            level.setBlock(worldPosition, state.setValue(CoreBlock.RUNNING, on), Block.UPDATE_CLIENTS);
        }
    }

    private void say(String key, String... values) {
        message = key;
        args = List.of(values);
        touch();
    }

    private void touch() {
        dirty = true;
        setChanged();
    }

    // ---------------------------------------------------------------- sync

    public CoreSync snapshot() {
        Mixture m = mixture();
        List<CoreSync.Score> scores = new ArrayList<>();
        List<Boolean> practising = new ArrayList<>();
        for (Benchmark a : Benchmark.values()) {
            scores.add(tests.getOrDefault(a, CoreSync.Score.NONE));
            practising.add(m != null && m.sources.stream().anyMatch(s -> s.benchmark() == a));
        }
        int budgetLeft = wreck() == null ? -1 : Math.max(0, Wrecks.BUDGET - runSteps);
        return new CoreSync(worldPosition, learningRate, batchSize, clip, speed.ordinal(), job != null, steps, runSteps,
                trainLoss, valLoss, accuracy, causal, gradNorm, message, args, List.copyOf(history), issues, nodes,
                job != null ? job.threads : compute.threads(), compute.hot(), parameters, scores, practising, budgetLeft, busy);
    }

    private void sync() {
        dirty = false;
        lastSync = System.currentTimeMillis();
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        CoreSync msg = null;
        for (ServerPlayer p : server.players()) {
            if (p.containerMenu instanceof CoreMenu m && m.pos.equals(worldPosition)) {
                if (msg == null) {
                    msg = snapshot();
                }
                PacketDistributor.sendToPlayer(p, msg);
            }
        }
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) {
            Containers.dropContents(level, pos, items);
            if (job != null) {
                Cooling.working(level, pos, false);
            }
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level instanceof ServerLevel) {
            Jobs.unwatch(this);
        }
        if (job != null) {
            job.requestStop(TrainingJob.Outcome.STOPPED);
            Jobs.forget(job);
            job = null;
        }
    }

    // ---------------------------------------------------------------- persistence

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        ContainerHelper.saveAllItems(out, items.getItems());
        out.putInt("learning_rate", learningRate);
        out.putInt("batch_size", batchSize);
        out.putInt("clip", clip);
        out.putInt("speed", speed.ordinal());
        out.putInt("steps", steps);
        out.putInt("run_steps", runSteps);
        out.putFloat("train_loss", trainLoss);
        out.putFloat("val_loss", valLoss);
        out.putFloat("accuracy", accuracy);
        out.putBoolean("causal", causal);
        out.putString("message", message);
        out.putString("args", String.join("\u0001", args));
        ValueOutput t = out.child("tests");
        tests.forEach((a, s) -> {
            ValueOutput one = t.child(a.id);
            one.putFloat("loss", s.loss());
            one.putFloat("accuracy", s.accuracy());
            one.putInt("step", s.step());
        });
        int[] at = new int[history.size()];
        int[] train = new int[at.length];
        int[] val = new int[at.length];
        int[] acc = new int[at.length];
        for (int i = 0; i < at.length; i++) {
            TrainingJob.Point p = history.get(i);
            at[i] = p.step();
            train[i] = Float.floatToRawIntBits(p.train());
            val[i] = Float.floatToRawIntBits(p.val());
            acc[i] = Float.floatToRawIntBits(p.accuracy());
        }
        ValueOutput h = out.child("history");
        h.putIntArray("step", at);
        h.putIntArray("train", train);
        h.putIntArray("val", val);
        h.putIntArray("accuracy", acc);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        items.getItems().clear();
        ContainerHelper.loadAllItems(in, items.getItems());
        Wrecks.Wreck w = wreck();
        wreck = w == null ? null : w.id();
        learningRate = Math.floorMod(in.getIntOr("learning_rate", learningRate), LEARNING_RATES.length);
        batchSize = Math.floorMod(in.getIntOr("batch_size", batchSize), BATCH_SIZES.length);
        clip = Math.floorMod(in.getIntOr("clip", clip), CLIPS.length);
        speed = TrainingJob.Speed.values()[Math.floorMod(in.getIntOr("speed", 1), TrainingJob.Speed.values().length)];
        steps = in.getIntOr("steps", 0);
        runSteps = in.getIntOr("run_steps", 0);
        trainLoss = in.getFloatOr("train_loss", Float.NaN);
        valLoss = in.getFloatOr("val_loss", Float.NaN);
        accuracy = in.getFloatOr("accuracy", Float.NaN);
        causal = in.getBooleanOr("causal", true);
        message = in.getStringOr("message", message);
        String joined = in.getStringOr("args", "");
        args = joined.isEmpty() ? List.of() : List.of(joined.split("\u0001", -1));
        tests.clear();
        in.child("tests").ifPresent(t -> {
            for (Benchmark a : Benchmark.values()) {
                t.child(a.id).ifPresent(one -> tests.put(a, new CoreSync.Score(one.getFloatOr("loss", Float.NaN),
                        one.getFloatOr("accuracy", Float.NaN), one.getIntOr("step", -1))));
            }
        });
        history.clear();
        in.child("history").ifPresent(h -> {
            int[] at = h.getIntArray("step").orElse(new int[0]);
            int[] train = h.getIntArray("train").orElse(new int[0]);
            int[] val = h.getIntArray("val").orElse(new int[0]);
            int[] acc = h.getIntArray("accuracy").orElse(new int[0]);
            int n = Math.min(Math.min(at.length, train.length), Math.min(val.length, acc.length));
            for (int i = 0; i < n; i++) {
                history.add(new TrainingJob.Point(at[i], Float.intBitsToFloat(train[i]), Float.intBitsToFloat(val[i]),
                        Float.intBitsToFloat(acc[i])));
            }
        });
    }
}
