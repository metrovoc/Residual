package io.github.metrovoc.residual.block;

import io.github.metrovoc.residual.Awards;
import io.github.metrovoc.residual.Residual;
import io.github.metrovoc.residual.ResidualConfig;
import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.graph.Decoder;
import io.github.metrovoc.residual.item.CrystalItem;
import io.github.metrovoc.residual.item.DatasetItem;
import io.github.metrovoc.residual.item.DriveItem;
import io.github.metrovoc.residual.item.LinkItem;
import io.github.metrovoc.residual.item.PreferenceLogItem;
import io.github.metrovoc.residual.learn.Archive;
import io.github.metrovoc.residual.learn.Benchmark;
import io.github.metrovoc.residual.learn.Distill;
import io.github.metrovoc.residual.learn.Grpo;
import io.github.metrovoc.residual.learn.Lora;
import io.github.metrovoc.residual.learn.Mixture;
import io.github.metrovoc.residual.learn.Orders;
import io.github.metrovoc.residual.learn.Preferences;
import io.github.metrovoc.residual.learn.Programs;
import io.github.metrovoc.residual.learn.Task;
import io.github.metrovoc.residual.learn.TextTask;
import io.github.metrovoc.residual.learn.Trainer;
import io.github.metrovoc.residual.learn.Tuning;
import io.github.metrovoc.residual.learn.Vocab;
import io.github.metrovoc.residual.menu.TunerMenu;
import io.github.metrovoc.residual.model.Checkpoints;
import io.github.metrovoc.residual.model.ModelInfo;
import io.github.metrovoc.residual.model.ModelLink;
import io.github.metrovoc.residual.net.TunerSync;
import io.github.metrovoc.residual.registry.ModBlockEntities;
import io.github.metrovoc.residual.registry.ModComponents;
import io.github.metrovoc.residual.registry.ModItems;
import io.github.metrovoc.residual.tensor.Parallel;
import io.github.metrovoc.residual.tensor.Tensor;
import io.github.metrovoc.residual.tensor.Tokens;
import io.github.metrovoc.residual.train.Jobs;
import io.github.metrovoc.residual.world.Cooling;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

/**
 * Takes a model that has already been trained and tunes it. It needs no machines laid out: a saved model goes in, what it learns from goes in beside it, and what
 * comes out is a new checkpoint. It changes nothing in the model it started from. It runs on a thread of its own, with as many threads as a Model Core has with
 * the Accelerators touching it, and what it does each step is one of the {@link Method}s. Lines of them can be joined with hoppers: a Tuner set to start by itself
 * starts when its base and a blank checkpoint are in.
 */
public class TunerBlockEntity extends BlockEntity implements MenuProvider, WorldlyContainer {
    public static final int BASE = 0, AID = 1, AID2 = 2, OUT = 3, SLOTS = 4;
    public static final int[] BUDGETS = {100, 250, 500, 1000, 2000, 4000};
    /** Choices below this many are too few to learn anything from. */
    public static final int LEAST_CHOICES = 4;
    private static final int POINTS = 48;

    /** What it does, with what it needs beside the base. */
    public enum Method {
        /** Practice at a falling rate on a dataset or a drive, or both mixed: fine-tuning, and with both, mid-training. */
        FINE_TUNE,
        /** As fine-tuning, but only thin matrices beside the model's own learn, and are added in at the end. */
        ADAPTER,
        /** Two tunings of one model put together: it takes no time. */
        MERGE,
        /** The player's choices between two answers. */
        CHOOSE,
        /** A judge learned from those choices. */
        JUDGE,
        /** Tuning against a judge. */
        AGAINST_JUDGE,
        /** Rewards from a checker that runs what is written. */
        VERIFIED,
        /** Practice on what a served model makes of each token. */
        DISTILL;

        public final String id = name().toLowerCase(java.util.Locale.ROOT);
    }

    /** What a method's third button sets, with what each choice is called. */
    public static final Map<Method, List<String>> OPTIONS = Map.of(
            Method.ADAPTER, List.of("4", "8", "16", "2"),
            Method.AGAINST_JUDGE, List.of("0.2", "0.05", "0"),
            Method.VERIFIED, List.of("result", "runs", "parses", "melts", "sums"),
            Method.DISTILL, List.of("2", "1", "4"));

    private final SimpleContainer items = new SimpleContainer(SLOTS) {
        @Override
        public void setChanged() {
            super.setChanged();
            TunerBlockEntity.this.setChanged();
        }
    };
    private Method method = Method.FINE_TUNE;
    private int budget = 3;
    private int option;
    private boolean auto;
    @Nullable
    private volatile Job job;
    private String problem = "";
    private String lastSaved = "";
    private int ticks;
    /** Two answers waiting for a choice, once they have been written. */
    @Nullable
    private volatile Choice choice;
    private boolean writing;
    private boolean starting;
    private String asked = "";

    /** What was asked and two answers to it. */
    public record Choice(String prompt, String first, String second) {
    }

    /** One run, on its own thread. Everything it publishes is read on the server thread. */
    private static final class Job {
        final Method method;
        final int budget;
        final ArrayDeque<Float> rewards = new ArrayDeque<>(), truths = new ArrayDeque<>();
        final CompiledGraph model;
        volatile boolean stop;
        volatile int steps;
        volatile float loss = Float.NaN, reward = Float.NaN, truth = Float.NaN;
        volatile String said = "", wrote = "";
        volatile boolean[] marks = new boolean[0];
        volatile boolean finished;
        volatile String failure = "";

        Job(Method method, int budget, CompiledGraph model) {
            this.method = method;
            this.budget = budget;
            this.model = model;
        }
    }

    public TunerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TUNER.get(), pos, state);
    }

    public SimpleContainer items() {
        return items;
    }

    public Method method() {
        return method;
    }

    public boolean running() {
        return job != null;
    }

    /** Buttons: 0 runs or stops it, 1 sets it to start by itself, 2 asks for two answers, 3 and 4 choose one of them, 5 neither; from 10, a method, from 30 a budget, from 50 an option. */
    public void button(int id, ServerPlayer player) {
        if (id == 0) {
            if (job != null) {
                job.stop = true;
            } else {
                start((ServerLevel) level, player);
            }
        } else if (id == 1) {
            auto = !auto;
        } else if (id == 2) {
            ask("");
        } else if (id >= 3 && id <= 5) {
            choose(id - 3);
        } else if (id >= 10 && id < 10 + Method.values().length) {
            if (job == null) {
                method = Method.values()[id - 10];
                option = 0;
                problem = "";
                choice = null;
            }
        } else if (id >= 30 && id < 30 + BUDGETS.length) {
            budget = id - 30;
        } else if (id >= 50 && id < 60 && OPTIONS.containsKey(method)) {
            option = Math.min(id - 50, OPTIONS.get(method).size() - 1);
        }
        setChanged();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, TunerBlockEntity be) {
        be.tick((ServerLevel) level);
    }

    private void tick(ServerLevel level) {
        ticks++;
        Job j = job;
        boolean on = j != null;
        if (getBlockState().getValue(TunerBlock.WORKING) != on) {
            level.setBlock(worldPosition, getBlockState().setValue(TunerBlock.WORKING, on), Block.UPDATE_CLIENTS);
        }
        if (j != null && !isUsable(items.getItem(BASE))) {
            j.stop = true;
        }
        if (j == null && auto && ticks % 20 == 0 && !problemWith(level).isPresent()) {
            start(level, null);
        }
        // the attempts at a question, as they were marked: a row across its top, green for those that passed and smoke for those that didn't
        if (j != null && ticks % 20 == 0) {
            boolean[] marks = j.marks;
            for (int i = 0; i < marks.length; i++) {
                level.sendParticles(marks[i] ? ParticleTypes.HAPPY_VILLAGER : ParticleTypes.SMOKE, worldPosition.getX() + (i + 0.5) / marks.length, worldPosition.getY() + 1.1, worldPosition.getZ() + 0.5,
                        1, 0, 0, 0, 0);
            }
        }
        // with its log beside it, it always has two answers ready to choose from
        if (j == null && method == Method.CHOOSE && choice == null && !writing && ticks % 20 == 0 && items.getItem(AID).getItem() instanceof PreferenceLogItem) {
            ask(asked);
        }
    }

    private static boolean isUsable(ItemStack stack) {
        return CrystalItem.model(stack) != null;
    }

    private static final Optional<String> FINE = Optional.empty();

    /** What is wrong with starting now, a translation key; nothing if it can start. */
    private Optional<String> problemWith(ServerLevel level) {
        if (CrystalItem.model(items.getItem(BASE)) == null) {
            return Optional.of("residual.tuner.no_base");
        }
        ItemStack out = items.getItem(OUT);
        if (!(out.getItem() instanceof CrystalItem) || CrystalItem.model(out) != null) {
            return Optional.of("residual.tuner.no_out");
        }
        ItemStack aid = items.getItem(AID), aid2 = items.getItem(AID2);
        return switch (method) {
            case FINE_TUNE, ADAPTER -> practice(aid, aid2, Vocab.WORLD) == null ? Optional.of("residual.tuner.no_data") : FINE;
            case MERGE -> CrystalItem.model(aid) == null ? Optional.of("residual.tuner.no_other") : FINE;
            case CHOOSE, JUDGE -> PreferenceLogItem.pairs(aid).size() < LEAST_CHOICES ? Optional.of("residual.tuner.no_choices") : FINE;
            case AGAINST_JUDGE -> CrystalItem.model(aid) == null ? Optional.of("residual.tuner.no_judge") : FINE;
            case VERIFIED -> FINE;
            case DISTILL -> {
                ModelLink link = aid.getItem() instanceof LinkItem ? ModelLink.of(level, worldPosition, aid) : null;
                yield link instanceof ModelLink.Served ? FINE : Optional.of("residual.tuner.no_teacher");
            }
        };
    }

    /** What a dataset or a drive in the two slots practises, mixed; null if they hold neither. */
    @Nullable
    private static Task practice(ItemStack a, ItemStack b, Vocab vocab) {
        List<Mixture.Source> sources = new ArrayList<>();
        for (ItemStack stack : List.of(a, b)) {
            if (stack.getItem() instanceof DatasetItem dataset) {
                sources.add(new Mixture.Source(dataset.benchmark, dataset.benchmark.practice()));
            } else if (stack.getItem() instanceof DriveItem) {
                TextTask text = Archive.practice(DriveItem.contents(stack), vocab);
                if (!text.isEmpty()) {
                    sources.add(new Mixture.Source(Benchmark.READING, text));
                }
            }
        }
        return sources.isEmpty() ? null : new Mixture(sources, vocab);
    }

    private void start(ServerLevel level, @Nullable ServerPlayer player) {
        var why = problemWith(level);
        problem = why.orElse("");
        if (why.isPresent() || starting) {
            return;
        }
        starting = true;
        MinecraftServer server = level.getServer();
        ModelInfo base = CrystalItem.model(items.getItem(BASE));
        Method m = method;
        int steps = BUDGETS[budget];
        int opt = option;
        ItemStack aid = items.getItem(AID).copy(), aid2 = items.getItem(AID2).copy();
        ModelLink teacher = m == Method.DISTILL ? ModelLink.of(level, worldPosition, items.getItem(AID)) : null;
        int threads = Cooling.compute(level, worldPosition, ResidualConfig.BASE_THREADS.get(), ResidualConfig.MAX_THREADS.get()).threads();
        Jobs.EXECUTOR.execute(() -> {
            CompiledGraph original = Checkpoints.load(server, base.id());
            if (original == null) {
                Jobs.back(server, () -> {
                    starting = false;
                    problem = "residual.model.failed";
                });
                return;
            }
            CompiledGraph model = Tuning.copy(original);
            Job j = new Job(m, steps, model);
            Jobs.back(server, () -> {
                starting = false;
                job = j;
            });
            ForkJoinPool pool = threads > 1 ? new ForkJoinPool(threads) : null;
            try {
                Parallel.with(pool, () -> run(j, server, base, opt, aid, aid2, teacher, player));
            } catch (RuntimeException e) {
                Residual.LOG.error("Tuning failed", e);
                j.failure = String.valueOf(e);
                Jobs.back(server, () -> {
                    if (job == j) {
                        job = null;
                        problem = "residual.tuner.failed";
                    }
                });
            } finally {
                if (pool != null) {
                    pool.shutdown();
                }
            }
        });
    }

    /** What the job does, on its own thread. */
    private void run(Job j, MinecraftServer server, ModelInfo base, int opt, ItemStack aid, ItemStack aid2, @Nullable ModelLink teacher, @Nullable ServerPlayer player) {
        CompiledGraph model = j.model;
        CompiledGraph out = model;
        long seed = ticks;
        Lora adapter = null;
        Tuning.Method method = null;
        switch (j.method) {
            case FINE_TUNE -> method = new Tuning.FineTune(model, practice(aid, aid2, model.env.tokenizer()), 3e-4f, j.budget, seed);
            case ADAPTER -> {
                adapter = new Lora(model, practice(aid, aid2, model.env.tokenizer()), Integer.parseInt(OPTIONS.get(Method.ADAPTER).get(opt)), 3e-3f, seed);
                method = adapter;
            }
            case MERGE -> {
                CompiledGraph other = Checkpoints.load(server, CrystalItem.model(aid).id());
                ModelInfo from = CrystalItem.model(aid2);
                CompiledGraph origin = from == null ? null : Checkpoints.load(server, from.id());
                if (other == null || !sameShape(model, other) || from != null && (origin == null || !sameShape(model, origin))) {
                    throw new IllegalStateException("those models have different machines");
                }
                out = Lora.merge(model, other, origin);
            }
            case CHOOSE -> method = new Preferences.Direct(model, PreferenceLogItem.pairs(aid), 3e-4f, seed);
            case JUDGE -> method = new Preferences.Judge(model, PreferenceLogItem.pairs(aid), 1e-3f, seed);
            case AGAINST_JUDGE -> {
                CompiledGraph judge = Checkpoints.load(server, CrystalItem.model(aid).id());
                if (judge == null) {
                    throw new IllegalStateException("no judge");
                }
                method = new Preferences.Ppo(model, judge, 3e-4f, Float.parseFloat(OPTIONS.get(Method.AGAINST_JUDGE).get(opt)), seed);
            }
            case VERIFIED -> method = verified(model, OPTIONS.get(Method.VERIFIED).get(opt), seed);
            case DISTILL -> method = distil(j, model, teacher, aid2, Float.parseFloat(OPTIONS.get(Method.DISTILL).get(opt)), seed);
        }
        if (method != null) {
            while (!j.stop && j.steps < j.budget) {
                Tuning.Report r = method.step();
                j.steps++;
                if (!Float.isFinite(r.loss())) {
                    throw new IllegalStateException("it diverged");
                }
                publish(j, r);
            }
        }
        if (adapter != null) {
            adapter.fold();
        }
        // whatever it came to, it is tested on everything as a saved model is
        Map<String, Float> scores = new HashMap<>();
        boolean causal = true;
        for (Benchmark a : Benchmark.values()) {
            Task test = a.test(out.env.tokenizer());
            Trainer.Eval e = Trainer.evaluate(out, test, 16);
            scores.put(a.id, (float) test.score(e.loss(), e.accuracy()));
            causal &= e.causal();
        }
        ModelInfo info = new ModelInfo(UUID.randomUUID(), out.parameterCount(), base.steps() + j.steps, scores, causal);
        byte[] encoded;
        try {
            encoded = Checkpoints.encode(out);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        CompiledGraph result = out;
        Jobs.back(server, () -> finished(j, server, info, result, encoded, player, base));
    }

    private static boolean sameShape(CompiledGraph a, CompiledGraph b) {
        List<Tensor> x = a.parameters(), y = b.parameters();
        if (x.size() != y.size()) {
            return false;
        }
        for (int i = 0; i < x.size(); i++) {
            if (!x.get(i).sameShape(y.get(i))) {
                return false;
            }
        }
        return true;
    }

    private Tuning.Method verified(CompiledGraph model, String what, long seed) {
        return switch (what) {
            case "runs" -> new Grpo(model, Programs.Checker.RUNS, 3e-4f, seed);
            case "parses" -> new Grpo(model, Programs.Checker.PARSES, 3e-4f, seed);
            case "melts" -> new Grpo.Melts(model, 2e-4f, seed);
            case "sums" -> new Grpo(model, Grpo.sums(), 3e-4f, seed);
            default -> new Grpo(model, Programs.Checker.RESULT, 3e-4f, seed);
        };
    }

    /** A served model is the teacher: every batch waits its turn at its server, and costs what it costs anyone. */
    private Tuning.Method distil(Job j, CompiledGraph model, @Nullable ModelLink teacher, ItemStack data, float temperature, long seed) {
        ModelInfo info = teacher == null ? null : teacher.info();
        Task task = practice(data, ItemStack.EMPTY, model.env.tokenizer());
        if (task == null && info != null) {
            List<Mixture.Source> sources = new ArrayList<>();
            for (Benchmark a : Benchmark.values()) {
                if (info.passes(a) && a != Benchmark.READING) {
                    sources.add(new Mixture.Source(a, a.practice()));
                }
            }
            task = sources.isEmpty() ? null : new Mixture(sources, model.env.tokenizer());
        }
        if (task == null) {
            throw new IllegalStateException("nothing to practise on");
        }
        ServerLevel level = (ServerLevel) this.level;
        return new Distill(model, tokens -> teacherScores(level, teacher, tokens, j), task, temperature, 3e-3f, seed);
    }

    /** What the served model makes of {@code tokens}, however long that takes: a server turning questions away is only a wait. */
    private static float[] teacherScores(ServerLevel level, @Nullable ModelLink teacher, Tokens tokens, Job j) {
        try {
            while (!j.stop) {
                CompletableFuture<float[]> scores = new CompletableFuture<>();
                Jobs.back(level.getServer(), () -> {
                    if (teacher instanceof ModelLink.Served served) {
                        served.server().submit(m -> m.forward(null, tokens, null).data, r -> scores.complete(r));
                    } else {
                        scores.complete(null);
                    }
                });
                float[] got = scores.get(120, TimeUnit.SECONDS);
                if (got != null) {
                    return got;
                }
                Thread.sleep(500);
            }
        } catch (java.util.concurrent.TimeoutException | java.util.concurrent.ExecutionException | InterruptedException e) {
            throw new IllegalStateException("its server did not answer", e);
        }
        throw new IllegalStateException("stopped");
    }

    private void publish(Job j, Tuning.Report r) {
        j.loss = r.loss();
        j.reward = (float) r.reward();
        j.truth = (float) r.truth();
        if (!r.said().isEmpty()) {
            j.said = r.said();
            j.wrote = r.wrote();
            j.marks = r.attempts();
        }
        synchronized (j) {
            j.rewards.addLast(Float.isNaN(j.reward) ? j.loss : j.reward);
            j.truths.addLast(j.truth);
            while (j.rewards.size() > POINTS) {
                j.rewards.removeFirst();
                j.truths.removeFirst();
            }
        }
    }

    private void finished(Job j, MinecraftServer server, ModelInfo info, CompiledGraph model, byte[] encoded, @Nullable ServerPlayer player, ModelInfo base) {
        if (job == j) {
            job = null;
        }
        if (isRemoved() || !(level instanceof ServerLevel here)) {
            return;
        }
        ItemStack out = items.getItem(OUT);
        if (!(out.getItem() instanceof CrystalItem) || CrystalItem.model(out) != null) {
            problem = "residual.tuner.no_out";
            return;
        }
        try {
            Checkpoints.save(server, info.id(), model, encoded);
        } catch (java.io.IOException | RuntimeException e) {
            Residual.LOG.error("Saving a tuned model failed", e);
            problem = "residual.model.failed";
            return;
        }
        out.set(ModComponents.MODEL.get(), info);
        items.setChanged();
        problem = "";
        lastSaved = info.tag();
        here.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1f, 1.2f);
        award(here, j, info, base, player);
    }

    /** Whoever is near has seen what it came to. */
    private void award(ServerLevel level, Job j, ModelInfo info, ModelInfo base, @Nullable ServerPlayer starter) {
        List<String> earned = new ArrayList<>();
        earned.add("tune/" + j.method.id);
        boolean whole = true;
        for (Benchmark a : Benchmark.values()) {
            if (info.passes(a)) {
                earned.add("benchmark/" + a.id);
            } else {
                whole = false;
            }
        }
        if (whole) {
            earned.add("model/complete");
        }
        if (j.method == Method.VERIFIED && !Float.isNaN(j.reward) && j.reward >= 0.9f && j.truth <= 0.5f) {
            earned.add("tune/hacked");
        }
        if (j.method == Method.DISTILL && info.parameters() <= ResidualConfig.ONBOARD_PARAMETERS.get()
                && Arrays.stream(Benchmark.values()).anyMatch(a -> info.passes(a) && !base.passes(a))) {
            earned.add("tune/carried");
        }
        for (ServerPlayer player : level.players()) {
            if (player == starter || player.distanceToSqr(worldPosition.getCenter()) < 32 * 32) {
                earned.forEach(path -> Awards.grant(player, path));
            }
        }
    }

    // ---------------------------------------------------------------- choosing

    /** Has its base write two different answers to {@code said}, or if that is empty to an order of its own making. */
    public void ask(String said) {
        ModelInfo base = CrystalItem.model(items.getItem(BASE));
        if (writing || base == null || !(level instanceof ServerLevel here)) {
            return;
        }
        asked = said;
        writing = true;
        MinecraftServer server = here.getServer();
        Random rng = new Random(ticks * 31L + PreferenceLogItem.pairs(items.getItem(AID)).size());
        String order = said.isBlank() ? Orders.example(rng, false).said() : said;
        Jobs.EXECUTOR.execute(() -> {
            CompiledGraph model = Checkpoints.load(server, base.id());
            Choice c = null;
            if (model != null) {
                Decoder d = new Decoder(model, Decoder.floats(model));
                String prompt = Orders.prompt(order);
                String first = Programs.write(d, prompt, 1, 0f, rng).getFirst().text(), second = first;
                for (int tries = 0; tries < 16 && second.equals(first); tries++) {
                    second = Programs.write(d, prompt, 1, 1.5f, rng).getFirst().text();
                }
                if (!second.equals(first)) {
                    c = rng.nextBoolean() ? new Choice(prompt, first, second) : new Choice(prompt, second, first);
                }
            }
            Choice made = c;
            Jobs.back(server, () -> {
                writing = false;
                choice = made;
            });
        });
    }

    /** {@code which}: 0 the first answer, 1 the second, 2 neither. */
    private void choose(int which) {
        Choice c = choice;
        ItemStack log = items.getItem(AID);
        if (c == null) {
            return;
        }
        if (which < 2 && log.getItem() instanceof PreferenceLogItem) {
            PreferenceLogItem.add(log, new Preferences.Pair(c.prompt(), which == 0 ? c.first() : c.second(), which == 0 ? c.second() : c.first()));
            items.setChanged();
        }
        choice = null;
        ask(asked);
    }

    // ---------------------------------------------------------------- what it shows

    public TunerSync sync(int menu) {
        Job j = job;
        List<Float> rewards = new ArrayList<>(), truths = new ArrayList<>();
        if (j != null) {
            synchronized (j) {
                rewards.addAll(j.rewards);
                truths.addAll(j.truths);
            }
        }
        boolean[] marks = j == null ? new boolean[0] : j.marks;
        int passed = 0;
        for (boolean b : marks) {
            passed += b ? 1 : 0;
        }
        Choice c = choice;
        int choices = PreferenceLogItem.pairs(items.getItem(AID)).size();
        return new TunerSync(menu, method.ordinal(), budget, option, auto, j != null, j == null ? 0 : j.steps, BUDGETS[budget], j == null ? Float.NaN : j.loss, j == null ? Float.NaN : j.reward,
                j == null ? Float.NaN : j.truth, j == null ? "" : j.said, j == null ? "" : j.wrote, marks.length, passed, rewards, truths, problem, lastSaved, c == null ? "" : c.prompt(),
                c == null ? "" : c.first(), c == null ? "" : c.second(), choices);
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new TunerMenu(id, inventory, worldPosition, items);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        Job j = job;
        if (j != null) {
            j.stop = true;
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
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        ContainerHelper.saveAllItems(out, items.getItems());
        out.putInt("method", method.ordinal());
        out.putInt("budget", budget);
        out.putInt("option", option);
        out.putBoolean("auto", auto);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        ContainerHelper.loadAllItems(in, items.getItems());
        method = Method.values()[Math.floorMod(in.getIntOr("method", 0), Method.values().length)];
        budget = Math.floorMod(in.getIntOr("budget", 3), BUDGETS.length);
        option = in.getIntOr("option", 0);
        auto = in.getBooleanOr("auto", false);
    }

    // ---------------------------------------------------------------- hoppers: a base and what goes with it in from above and the sides, what is finished out below

    @Override
    public int[] getSlotsForFace(Direction side) {
        return side == Direction.DOWN ? new int[]{OUT} : new int[]{BASE, AID, AID2, OUT};
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return slot == OUT ? stack.getItem() instanceof CrystalItem && CrystalItem.model(stack) == null
                : slot == BASE ? isUsable(stack) && items.getItem(BASE).isEmpty() : items.getItem(slot).isEmpty();
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot == OUT && CrystalItem.model(stack) != null;
    }

    @Override
    public int getContainerSize() {
        return items.getContainerSize();
    }

    @Override
    public boolean isEmpty() {
        return items.isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.getItem(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        return items.removeItem(slot, count);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return items.removeItemNoUpdate(slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.setItem(slot, stack);
    }

    @Override
    public boolean stillValid(Player player) {
        return items.stillValid(player);
    }

    @Override
    public void clearContent() {
        items.clearContent();
    }
}
