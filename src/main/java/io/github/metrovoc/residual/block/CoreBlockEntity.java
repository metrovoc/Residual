package io.github.metrovoc.residual.block;

import io.github.metrovoc.residual.Residual;
import io.github.metrovoc.residual.ResidualConfig;
import io.github.metrovoc.residual.export.PyTorch;
import io.github.metrovoc.residual.graph.CompileException;
import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.graph.Env;
import io.github.metrovoc.residual.graph.GraphSpec;
import io.github.metrovoc.residual.graph.Sym;
import io.github.metrovoc.residual.learn.Curriculum;
import io.github.metrovoc.residual.learn.Sampler;
import io.github.metrovoc.residual.learn.Task;
import io.github.metrovoc.residual.menu.CoreMenu;
import io.github.metrovoc.residual.net.CodeText;
import io.github.metrovoc.residual.net.CoreSync;
import io.github.metrovoc.residual.net.ExportChunk;
import io.github.metrovoc.residual.net.SampleResult;
import io.github.metrovoc.residual.progress.Rewards;
import io.github.metrovoc.residual.registry.ModAttachments;
import io.github.metrovoc.residual.registry.ModBlockEntities;
import io.github.metrovoc.residual.tensor.Tensor;
import io.github.metrovoc.residual.train.Jobs;
import io.github.metrovoc.residual.train.TrainingJob;
import io.github.metrovoc.residual.world.CaseBuilder;
import io.github.metrovoc.residual.world.CaseLayout;
import io.github.metrovoc.residual.world.GraphScanner;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * The Model Core's state: the lesson, the optimiser settings, the learning curve and the running training job.
 * Only the server has it; open screens receive it as {@link CoreSync}.
 */
public class CoreBlockEntity extends BlockEntity {
    public static final float[] LEARNING_RATES = {1e-4f, 3e-4f, 1e-3f, 3e-3f, 1e-2f, 3e-2f, 0.1f, 0.3f, 1.5f};
    public static final int[] BATCH_SIZES = {8, 16, 32, 64};
    public static final float[] CLIPS = {0f, 0.5f, 1f, 5f};
    private static final int HISTORY = 400;

    private String lesson = Curriculum.ALL.getFirst().id();
    private int learningRate;
    private int batchSize;
    private int clip;
    private TrainingJob.Speed speed = TrainingJob.Speed.NORMAL;
    /** Steps trained on this lesson, and since the weights were last reset (case budgets count these). */
    private int steps;
    private int runSteps;
    private final List<TrainingJob.Point> history = new ArrayList<>();
    private float trainLoss = Float.NaN;
    private float valLoss = Float.NaN;
    private float accuracy = Float.NaN;
    private float gradNorm = Float.NaN;
    private boolean causal = true;
    private String message = "residual.status.ready";
    private List<String> args = List.of();
    private List<CoreSync.Issue> issues = List.of();
    private List<CoreSync.Node> nodes = List.of();
    private long parameters;
    private int threads;

    @Nullable
    private TrainingJob job;
    @Nullable
    private UUID owner;
    private int jobSteps;
    private int jobRunSteps;
    private boolean dirty;
    private long lastSync;

    public CoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CORE.get(), pos, state);
        applyDefaults(Curriculum.ALL.getFirst());
    }

    public Curriculum.Lesson lesson() {
        Curriculum.Lesson l = Curriculum.byId(lesson);
        return l != null ? l : Curriculum.ALL.getFirst();
    }

    public boolean running() {
        return job != null;
    }

    private void applyDefaults(Curriculum.Lesson l) {
        Curriculum.Settings s = l.settings();
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

    // ---------------------------------------------------------------- screen actions (server thread)

    public void open(Player player) {
        if (player instanceof ServerPlayer sp && level instanceof ServerLevel) {
            if (job == null) {
                check();
            }
            sp.openMenu(new SimpleMenuProvider((id, inv, p) -> new CoreMenu(id, worldPosition), getBlockState().getBlock().getName()),
                    worldPosition);
            PacketDistributor.sendToPlayer(sp, snapshot());
        }
    }

    public void select(ServerPlayer player, int index) {
        if (job != null || index < 0 || index >= Curriculum.ALL.size()) {
            return;
        }
        Curriculum.Lesson l = Curriculum.ALL.get(index);
        if (!Curriculum.unlocked(l, player.getData(ModAttachments.PROGRESS).done()) && !player.isCreative()) {
            say("residual.status.locked");
            return;
        }
        lesson = l.id();
        applyDefaults(l);
        clearCurve();
        say("residual.status.ready");
        check();
        setChanged();
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
        Curriculum.Lesson l = lesson();
        if (!Curriculum.unlocked(l, player.getData(ModAttachments.PROGRESS).done()) && !player.isCreative()) {
            say("residual.status.locked");
            return;
        }
        int remaining = 0;
        if (l.budget() > 0) {
            remaining = l.budget() - runSteps;
            if (remaining <= 0) {
                say("residual.status.budget_used", Integer.toString(l.budget()));
                return;
            }
        }
        Checked checked = check();
        if (checked == null) {
            return;
        }
        threads = threadCount();
        job = new TrainingJob(server, worldPosition, checked.scan.footprint(), checked.scan.nodes(), checked.model, checked.task,
                server.getRandom().nextLong(), threads, remaining, speed, LEARNING_RATES[learningRate], BATCH_SIZES[batchSize], CLIPS[clip]);
        jobSteps = steps;
        jobRunSteps = runSteps;
        owner = player.getUUID();
        Jobs.start(job);
        setRunning(true);
        say("residual.status.training");
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
        trainLoss = valLoss = accuracy = gradNorm = Float.NaN;
        causal = true;
    }

    /** Builds the current case's broken machine in front of the Core. */
    public void buildCase() {
        Curriculum.Lesson l = lesson();
        if (job != null || l.brokenMachine() == null || !(level instanceof ServerLevel server)) {
            return;
        }
        GraphSpec spec = l.brokenMachine().get();
        CaseLayout layout = CaseLayout.of(spec);
        BlockPos blocked = CaseBuilder.obstacle(server, worldPosition, layout);
        if (blocked != null) {
            say("residual.status.no_room", Integer.toString(layout.length - 1), Integer.toString(layout.width),
                    Integer.toString(layout.height), blocked.toShortString());
            return;
        }
        CaseBuilder.build(server, worldPosition, spec, layout);
        level.playSound(null, worldPosition, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.7f, 1.4f);
        clearCurve();
        say("residual.status.built");
        check();
    }

    /** Runs the model on a prompt, or on a fresh puzzle, and sends the player what it wrote. */
    public void sample(ServerPlayer player, String prompt, int temperature) {
        Checked checked = check();
        if (checked == null) {
            PacketDistributor.sendToPlayer(player, new SampleResult("", "", List.of(), List.of(),
                    "The model doesn't check out yet: see the Inspect tab."));
            return;
        }
        long seed = level.getRandom().nextLong();
        MinecraftServer server = player.level().getServer();
        Jobs.EXECUTOR.execute(() -> {
            SampleResult result;
            try {
                Sampler.Demo d = Sampler.demo(checked.model(), checked.task(), prompt, temperature / 100f, new Random(seed));
                result = new SampleResult(d.prompt(), d.text(), d.labels(), d.probs(), d.note());
            } catch (RuntimeException e) {
                Residual.LOG.error("Sampling failed", e);
                result = new SampleResult("", "", List.of(), List.of(), "Sampling failed: " + e);
            }
            SampleResult reply = result;
            server.execute(() -> PacketDistributor.sendToPlayer(player, reply));
        });
    }

    /** Sends the player the model as PyTorch source, or an empty text if it doesn't check out. */
    public void code(ServerPlayer player) {
        Checked checked = check();
        PacketDistributor.sendToPlayer(player, new CodeText(checked == null ? ""
                : PyTorch.module(checked.model(), checked.task().vocab.symbols, lesson)));
    }

    /** Sends the player the model's source and trained weights to save next to their game. */
    public void export(ServerPlayer player) {
        Checked checked = check();
        if (checked == null) {
            say("residual.status.export_broken");
            return;
        }
        String code = PyTorch.module(checked.model(), checked.task().vocab.symbols, lesson);
        byte[] data = PyTorch.weightBytes(PyTorch.weights(checked.model()));
        String folder = lesson + "-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        int total = Math.max(1, (data.length + ExportChunk.CHUNK - 1) / ExportChunk.CHUNK);
        for (int i = 0; i < total; i++) {
            byte[] piece = Arrays.copyOfRange(data, i * ExportChunk.CHUNK, Math.min(data.length, (i + 1) * ExportChunk.CHUNK));
            PacketDistributor.sendToPlayer(player, new ExportChunk(folder, i == 0 ? code : "", i, total, piece));
        }
    }

    private int threadCount() {
        int base = ResidualConfig.BASE_THREADS.get() + GraphScanner.accelerators(level, worldPosition);
        return Math.max(1, Math.min(ResidualConfig.MAX_THREADS.get(), base));
    }

    // ---------------------------------------------------------------- checking the model

    /** A model that checked out, ready to train or sample. */
    public record Checked(GraphScanner.Scan scan, CompiledGraph model, Task task) {
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
        Curriculum.Lesson l = lesson();
        Task task = l.makeTask();
        Env env = new Env(task.vocab.size(), task.context);
        GraphScanner.Scan scan = GraphScanner.scan(level, worldPosition);
        threads = threadCount();
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
            for (int node : model.order) {
                GraphSpec.Node n = scan.spec().node(node);
                if (task.forbidden.contains(n.op)) {
                    found.add(new CoreSync.Issue(scan.nodes().get(node), "This lesson doesn't allow the " + n.op.title
                            + ": find another way to build what it does."));
                }
            }
        } catch (CompileException e) {
            shapes = e.shapes;
            for (CompileException.Issue i : e.issues) {
                found.add(new CoreSync.Issue(i.node() >= 0 ? scan.nodes().get(i.node()) : null, i.message()));
            }
        }
        publish(scan, model, shapes, env, found);
        return found.isEmpty() && model != null ? new Checked(scan, model, task) : null;
    }

    private Map<String, Tensor> paramsAt(BlockPos pos) {
        return level.getBlockEntity(pos) instanceof OperatorBlockEntity be ? be.params() : Map.of();
    }

    /** Records what a check found, on the Core and on each machine. */
    private void publish(GraphScanner.Scan scan, @Nullable CompiledGraph model, @Nullable Sym[] shapes, Env env, List<CoreSync.Issue> found) {
        issues = List.copyOf(found);
        Map<BlockPos, String> errors = new HashMap<>();
        for (CoreSync.Issue i : found) {
            if (i.pos() != null) {
                errors.merge(i.pos(), i.message(), (a, b) -> a + " " + b);
            }
        }
        List<CoreSync.Node> rows = new ArrayList<>();
        long total = 0;
        for (int i = 0; i < scan.nodes().size(); i++) {
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
            String error = errors.getOrDefault(p, "");
            if (error.isEmpty() && model != null && !active) {
                error = "Its output never reaches the Model Core, so it has no effect on the model.";
            }
            be.setStatus(new OperatorBlockEntity.Status(shape != null ? shape.toString() : "", error, features, env.vocab(),
                    env.context(), count, be.status().gradNorm(), active));
            rows.add(new CoreSync.Node(p, n.op.id, n.name, shape != null ? shape.toString() : "", count, be.status().gradNorm(), active));
        }
        nodes = rows;
        parameters = total;
        if (!found.isEmpty()) {
            say("residual.status.issues", Integer.toString(found.size()));
        } else if (job == null && message.equals("residual.status.issues")) {
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
                touch();
            }
        } else if (getBlockState().getValue(CoreBlock.RUNNING)) {
            setRunning(false);
        }
        if (dirty && System.currentTimeMillis() - lastSync > 250) {
            sync();
        }
    }

    private void addPoint(TrainingJob.Point p) {
        history.add(p);
        if (history.size() > HISTORY) {
            // keep the whole curve by thinning it out, oldest half first
            List<TrainingJob.Point> thinned = new ArrayList<>();
            for (int i = 0; i < history.size(); i++) {
                if (i >= history.size() / 2 || i % 2 == 0) {
                    thinned.add(history.get(i));
                }
            }
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
        if (u.eval != null) {
            valLoss = (float) u.eval.loss();
            accuracy = (float) u.eval.accuracy();
            causal = u.eval.causal();
            if (!causal && u.outcome == null && running.task.passes(u.eval.loss(), u.eval.accuracy())) {
                say("residual.status.leak");
            }
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
    }

    private void finish(TrainingJob running, TrainingJob.Update u) {
        Curriculum.Lesson l = lesson();
        switch (u.outcome) {
            case PASSED -> {
                say("residual.status.passed");
                ServerPlayer player = owner == null ? null : ((ServerLevel) level).getServer().getPlayerList().getPlayer(owner);
                if (player != null) {
                    Rewards.complete(player, l);
                }
                level.playSound(null, worldPosition, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.8f, 1.2f);
                ((ServerLevel) level).sendParticles(ParticleTypes.END_ROD, worldPosition.getX() + 0.5, worldPosition.getY() + 1.1,
                        worldPosition.getZ() + 0.5, 40, 0.4, 0.3, 0.4, 0.05);
            }
            case BUDGET -> say("residual.status.budget", Integer.toString(l.budget()));
            case DIVERGED -> {
                if ("NaN".equals(u.detail)) {
                    say("residual.status.nan");
                } else {
                    say("residual.status.diverged", u.detail, String.format("%.2f", Math.log(running.task.vocab.size())));
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
        return new CoreSync(worldPosition, lesson, learningRate, batchSize, clip, speed.ordinal(), job != null, steps, runSteps,
                trainLoss, valLoss, accuracy, causal, gradNorm, message, args, List.copyOf(history), issues, nodes,
                job != null ? job.threads : threads, parameters);
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
    public void setRemoved() {
        super.setRemoved();
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
        out.putString("lesson", lesson);
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
        lesson = in.getStringOr("lesson", lesson);
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
