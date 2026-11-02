package io.github.metrovoc.residual.block;

import io.github.metrovoc.residual.Awards;
import io.github.metrovoc.residual.ResidualConfig;
import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.item.CrystalItem;
import io.github.metrovoc.residual.learn.Benchmark;
import io.github.metrovoc.residual.learn.Sampler;
import io.github.metrovoc.residual.learn.Task;
import io.github.metrovoc.residual.learn.Thinking;
import io.github.metrovoc.residual.learn.Trainer;
import io.github.metrovoc.residual.menu.ServerMenu;
import io.github.metrovoc.residual.model.Checkpoints;
import io.github.metrovoc.residual.model.ModelInfo;
import io.github.metrovoc.residual.model.Models;
import io.github.metrovoc.residual.model.Quantized;
import io.github.metrovoc.residual.model.Serving;
import io.github.metrovoc.residual.net.ServerSync;
import io.github.metrovoc.residual.registry.ModBlockEntities;
import io.github.metrovoc.residual.registry.ModItems;
import io.github.metrovoc.residual.tensor.Tokens;
import io.github.metrovoc.residual.train.Jobs;
import io.github.metrovoc.residual.world.Cooling;
import io.github.metrovoc.residual.world.Relays;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
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
 * Serves the model in its crystal to everything in reach of its relay network. What a server can do is a number of operations a second,
 * more with every Accelerator touching it, and every question costs what the model really computed for it: a question waits for those
 * operations to be there, and past so many waiting, questions are turned away. What it serves with decides what a question costs: a
 * KV Cache, a batch, a form for its weights and a draft ({@link Serving}), all of them measured on the model it holds.
 */
public class ServerBlockEntity extends BlockEntity implements MenuProvider {
    public static final int MODEL = 0, CACHE = 1, PACKER = 2, DRAFT = 3, JUDGE = 4, SLOTS = 5;
    /** Questions waiting beyond this many are turned away. */
    public static final int QUEUE = 64;
    /** What it holds without a card, and what each KV Cache adds. */
    public static final long MEMORY = 1 << 20, CARD = 1 << 18;
    public static final int[] BATCHES = {1, 2, 4, 8, 16, 32};
    public static final int[] FORMS = {32, 8, 4};
    public static final int[] EFFORTS = {1, 2, 4, 8, 16, 32, 64};
    private static final int NOTICE_EVERY = 1200;

    private final SimpleContainer items = new SimpleContainer(SLOTS) {
        @Override
        public void setChanged() {
            super.setChanged();
            ServerBlockEntity.this.setChanged();
            if (level != null && !level.isClientSide()) {
                stackChanged();
            }
        }
    };
    private final ArrayDeque<Request<?>> queue = new ArrayDeque<>();
    /** Answers worked out and not yet due. */
    private final ArrayDeque<Done> done = new ArrayDeque<>();
    private Cooling.Compute compute = new Cooling.Compute(1, 0);
    private int bits = 32;
    private int batch = 1;
    private int effort = 1;
    /** What everything but the crystal's own scores hang on: what it runs and what that was measured to be. */
    @Nullable
    private volatile Serving serving;
    @Nullable
    private ModelInfo form;
    private String problem = "";
    private String shown = "";
    /** The tick it has worked through so far, as if it worked at its own speed: an answer is due when it gets there. */
    private double busyUntil;
    private double spentThisSecond;
    private float load;
    private final Map<String, Float> drawn = new HashMap<>();
    private int inFlight;
    private int answered;
    /** Turns taken in the second before this one, and in this one so far. */
    private int lastSecond, thisSecond;
    private int turnedAway;
    private int turnedAwayNow;
    private int quiet;
    private int ticks;
    private int revision;
    private int noticeAt = -NOTICE_EVERY;
    private boolean dirtyStack;

    /** Something waiting its turn: work for the model, or with none only the turn itself. */
    private record Request<T>(@Nullable Function<CompiledGraph, T> work, Consumer<@Nullable T> reply, String who) {
    }

    private record Done(double due, Runnable reply) {
    }

    public ServerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SERVER.get(), pos, state);
    }

    public SimpleContainer items() {
        return items;
    }

    /** The model it serves, its scores as it was measured in the form it serves it in. */
    @Nullable
    public ModelInfo model() {
        ModelInfo info = CrystalItem.model(crystal());
        return info == null || form == null || !form.id().equals(info.id()) || form.steps() != info.steps() ? info : form;
    }

    public ItemStack crystal() {
        return items.getItem(MODEL);
    }

    /** Puts {@code stack} in and hands back what was there. */
    public ItemStack swap(ItemStack stack) {
        ItemStack old = crystal();
        items.setItem(MODEL, stack);
        return old;
    }

    public int bits() {
        return bits;
    }

    public int batch() {
        return batch;
    }

    public void setBits(int value) {
        bits = items.getItem(PACKER).isEmpty() ? 32 : value == 8 || value == 4 ? value : 32;
        stackChanged();
        setChanged();
    }

    public int effort() {
        return effort;
    }

    public void setEffort(int value) {
        effort = Math.clamp(value, 1, EFFORTS[EFFORTS.length - 1]);
        stackChanged();
        setChanged();
    }

    public void setBatch(int value) {
        batch = Math.clamp(value, 1, BATCHES[BATCHES.length - 1]);
        stackChanged();
        setChanged();
    }

    /** Operations a second, with every accelerator it has. */
    public double flops() {
        return ResidualConfig.SERVER_ANSWERS.get() * compute.threads() * Serving.PLAIN_ROWS * Serving.REFERENCE_ROW;
    }

    /** Answers per second: answers of a set length, as its model and its stack were measured to cost. */
    public float rate() {
        Serving s = serving;
        return s == null ? 0 : (float) (flops() / s.answerFlops());
    }

    /** The share of its operations in use over the last second. */
    public float load() {
        return load;
    }

    public int waiting() {
        return queue.size();
    }

    /** Whether new questions are being turned away. */
    public boolean full() {
        return queue.size() >= QUEUE;
    }

    /** What keeps it from serving, a translation key; empty if nothing does. */
    public String problem() {
        return problem;
    }

    public void ask(String prompt, int max, char stop, Consumer<Sampler.@Nullable Answer> reply) {
        submit(m -> Sampler.answer(m, prompt, max, stop), reply);
    }

    public GlobalPos where() {
        return GlobalPos.of(level.dimension(), worldPosition);
    }

    public int answersLastSecond() {
        return lastSecond;
    }

    /**
     * Takes one answer's worth of what it can do, with nothing for the model to work out: a call from a Terminal, a step of a jump. {@code reply} gets true on the server thread when the
     * turn comes, or false if it is turned away.
     */
    public void call(Consumer<Boolean> reply) {
        if (model() == null || !problem.isEmpty() || full()) {
            turnedAway += full() ? 1 : 0;
            reply.accept(false);
            return;
        }
        queue.add(new Request<Boolean>(null, turn -> reply.accept(turn != null), who()));
    }

    /** Runs {@code work} on the model when its turn comes; {@code reply} gets the result (or null) on the server thread. */
    public <T> void submit(Function<CompiledGraph, T> work, Consumer<@Nullable T> reply) {
        if (model() == null || !problem.isEmpty()) {
            reply.accept(null);
            return;
        }
        if (full()) {
            turnedAway++;
            turnedAwayNow++;
            reply.accept(null);
            return;
        }
        queue.add(new Request<>(work, reply, who()));
    }

    private static final StackWalker WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

    /** Who is asking: what the server's screen says draws on it. */
    private static String who() {
        String caller = WALKER.walk(frames -> frames.map(f -> f.getDeclaringClass().getSimpleName())
                .filter(c -> !c.equals("ServerBlockEntity") && !c.equals("ModelLink") && !c.contains("$"))
                .findFirst().orElse(""));
        return switch (caller) {
            case "Agent", "Sorting", "Running", "Storing", "Farming", "Hauling", "Building", "Copying", "Acts", "Laying" -> "agents";
            case "Network", "Agents", "TerminalApi", "Gates" -> "terminals";
            case "ForecasterBlockEntity", "Bursts", "HourglassItem" -> "forecasters";
            case "ReaderBlockEntity" -> "readers";
            case "SmelterBlockEntity" -> "smelters";
            case "PortBlockEntity", "Meaning", "TerminalMenu" -> "storage";
            case "WatchPostBlockEntity" -> "watch";
            case "SurveyorBlockEntity" -> "surveyors";
            case "TunerBlockEntity", "Tuning" -> "tuners";
            default -> "other";
        };
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ServerBlockEntity be) {
        be.tick((ServerLevel) level);
    }

    private void stackChanged() {
        dirtyStack = true;
    }

    private void tick(ServerLevel level) {
        if (ticks++ % 100 == 0) {
            compute = Cooling.compute(level, worldPosition, 1, ResidualConfig.MAX_THREADS.get());
            Cooling.working(level, worldPosition, quiet < 100);
        }
        if (ticks % 20 == 0) {
            lastSecond = thisSecond;
            thisSecond = 0;
        }
        if (dirtyStack) {
            dirtyStack = false;
            build(level);
        }
        while (!done.isEmpty() && done.peek().due() <= ticks) {
            done.poll().reply().run();
        }
        Serving s = serving;
        while (s != null && !queue.isEmpty() && busyUntil <= ticks && inFlight < Math.max(2 * compute.threads(), batch)) {
            run(level.getServer(), s, queue.poll());
        }
        if (ticks % 20 == 0) {
            load = (float) (spentThisSecond / flops());
            spentThisSecond = 0;
            drawn.replaceAll((who, spent) -> spent * 0.9f);
            drawn.values().removeIf(spent -> spent < 1f);
            if (turnedAwayNow > 0) {
                notice(level);
            }
            turnedAwayNow = 0;
            award(level);
        }
        quiet = queue.isEmpty() && inFlight == 0 && done.isEmpty() ? quiet + 1 : 0;
        boolean answering = quiet < 20;
        boolean strained = turnedAwayNow > 0 || queue.size() > QUEUE / 2 || load > 0.95f;
        boolean loaded = !crystal().isEmpty();
        BlockState state = getBlockState();
        if (state.getValue(ServerBlock.SERVING) != answering || state.getValue(ServerBlock.STRAINED) != strained || state.getValue(ServerBlock.LOADED) != loaded) {
            level.setBlock(worldPosition, state.setValue(ServerBlock.SERVING, answering).setValue(ServerBlock.STRAINED, strained).setValue(ServerBlock.LOADED, loaded), Block.UPDATE_CLIENTS);
        }
    }

    /** Whoever is close has seen what this stack does, once it has done it. */
    private void award(ServerLevel level) {
        Serving s = serving;
        List<String> earned = new ArrayList<>();
        if (s != null) {
            if (s.saved() > 0 && s.stack.memory() > 0) {
                earned.add("serve/cache");
            }
            if (s.biggestBatch() >= 8) {
                earned.add("serve/batch");
            }
            if (s.proposed() >= 100 && s.accepted() * 2 >= s.proposed()) {
                earned.add("serve/draft");
            }
            if (s.thinks() && answered > 0) {
                earned.add("reason/effort");
            }
            ModelInfo plain = CrystalItem.model(crystal());
            if (s.stack.bits() == 4 && form != null && plain != null && Arrays.stream(Benchmark.values()).filter(plain::passes).findAny().isPresent()
                    && Arrays.stream(Benchmark.values()).filter(plain::passes).allMatch(form::passes)) {
                earned.add("serve/packed");
            }
        }
        if (turnedAway > 0) {
            earned.add("serve/turned_away");
        }
        if (!earned.isEmpty()) {
            for (ServerPlayer player : level.players()) {
                if (player.distanceToSqr(worldPosition.getCenter()) < 32 * 32) {
                    earned.forEach(path -> Awards.grant(player, path));
                }
            }
        }
    }

    /** Tells whoever is close that questions are being turned away, now and then. */
    private void notice(ServerLevel level) {
        if (ticks - noticeAt < NOTICE_EVERY) {
            return;
        }
        noticeAt = ticks;
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(worldPosition.getCenter()) < 64 * 64) {
                player.sendSystemMessage(Component.translatable("residual.server.overloaded", worldPosition.toShortString()));
            }
        }
    }

    /** Puts the stack together again off the server thread, with what its slots now hold. */
    private void build(ServerLevel level) {
        revision++;
        flush();
        serving = null;
        form = null;
        problem = "";
        ModelInfo info = CrystalItem.model(crystal());
        Relays.served(level, worldPosition, info != null);
        if (info == null) {
            shown = "";
            return;
        }
        MinecraftServer server = level.getServer();
        int cards = items.getItem(CACHE).getCount();
        int wanted = items.getItem(PACKER).isEmpty() ? 32 : bits;
        ModelInfo draftInfo = CrystalItem.model(items.getItem(DRAFT)), judgeInfo = CrystalItem.model(items.getItem(JUDGE));
        int asked = cards > 0 ? batch : 1, thought = cards > 0 ? effort : 1;
        int at = revision;
        Jobs.EXECUTOR.execute(() -> {
            CompiledGraph model = Checkpoints.load(server, info.id());
            if (model == null) {
                Jobs.back(server, () -> built(at, null, "residual.model.failed", null));
                return;
            }
            CompiledGraph draft = draftInfo == null || cards == 0 ? null : Checkpoints.load(server, draftInfo.id());
            if (draft != null && !model.env.tokenizer().equals(draft.env.tokenizer())) {
                draft = null;
            }
            long weights = wanted == 32 ? model.parameterCount() * Float.BYTES : Quantized.of(model, wanted).bytes();
            CompiledGraph judge = judgeInfo == null || thought == 1 ? null : Checkpoints.load(server, judgeInfo.id());
            long room = MEMORY + cards * CARD - weights - (draft == null ? 0 : draft.parameterCount() * Float.BYTES) - (judge == null ? 0 : judge.parameterCount() * Float.BYTES);
            if (room < 0) {
                Jobs.back(server, () -> built(at, null, "residual.server.no_room", null));
                return;
            }
            Serving s = new Serving(model, new Serving.Stack(wanted, cards > 0 ? room : 0, asked, draft, thought, judge));
            s.measure(prompts(info, model));
            Jobs.back(server, () -> built(at, s, "", null));
            if (wanted != 32 || thought > 1) {
                ModelInfo scored = scored(info, wanted == 32 ? model : Quantized.of(model, wanted).restore(), thought, judge);
                Jobs.back(server, () -> built(at, s, "", scored));
            }
        });
    }

    private void built(int at, @Nullable Serving s, String why, @Nullable ModelInfo scored) {
        if (at != revision) {
            return;
        }
        serving = s;
        problem = why;
        form = scored;
    }

    /** Questions for measuring on: the start of the first test it passes, or of any. */
    private static List<int[]> prompts(ModelInfo info, CompiledGraph model) {
        Benchmark from = Benchmark.READING;
        for (Benchmark a : Benchmark.values()) {
            if (info.passes(a) && a != Benchmark.CONTROL && a != Benchmark.PILOT) {
                from = a;
                break;
            }
        }
        List<int[]> out = new ArrayList<>();
        Tokens tokens = from.test(model.env.tokenizer()).batch(new Random(7), 4, true).input();
        for (int i = 0; i < tokens.batch; i++) {
            int[] prompt = new int[Math.min(Serving.READS, tokens.time)];
            System.arraycopy(tokens.ids, i * tokens.time, prompt, 0, prompt.length);
            out.add(prompt);
        }
        return out;
    }

    /**
     * {@code info} with what it passes tested again on {@code model}, as a form of it; and at more than one answer a question, what can be thought longer about tested at that many
     * whether it passed before or not, since that is what thinking longer buys.
     */
    private static ModelInfo scored(ModelInfo info, CompiledGraph model, int effort, @Nullable CompiledGraph judge) {
        Map<String, Float> scores = new LinkedHashMap<>(info.scores());
        for (Benchmark a : Benchmark.values()) {
            if (effort > 1 && a == Benchmark.PROGRAMS) {
                scores.put(a.id, (float) Thinking.programs(model, 128, effort, judge != null ? Thinking.Way.JUDGED : Thinking.Way.VERIFIED, 5151, judge));
            } else if (effort > 1 && a == Benchmark.RECKONING) {
                scores.put(a.id, (float) Thinking.sums(model, 128, effort, Thinking.Way.VERIFIED, 909, null));
            } else if (info.passes(a)) {
                Task test = a.test(model.env.tokenizer());
                Trainer.Eval eval = Trainer.evaluate(model, test, 8);
                scores.put(a.id, (float) test.score(eval.loss(), eval.accuracy()));
            }
        }
        return new ModelInfo(info.id(), info.parameters(), info.steps(), scores, info.causal());
    }

    private <T> void run(MinecraftServer server, Serving s, Request<T> r) {
        thisSecond++;
        inFlight++;
        int at = revision;
        if (r.work() == null) {
            @SuppressWarnings("unchecked")
            T turn = (T) Boolean.TRUE;
            finished(at, r, turn, s.answerFlops());
            return;
        }
        s.coming();
        Jobs.EXECUTOR.execute(() -> {
            double[] meter = new double[1];
            T out = Models.evaluate(s.view(), model -> s.run(r.work(), meter));
            Jobs.back(server, () -> finished(at, r, out, meter[0]));
        });
    }

    private <T> void finished(int at, Request<T> r, @Nullable T out, double cost) {
        inFlight--;
        double perTick = flops() / 20;
        busyUntil = Math.max(busyUntil, ticks) + cost / perTick;
        spentThisSecond += cost;
        drawn.merge(r.who(), (float) cost, Float::sum);
        // whatever it comes to, an answer doesn't keep its asker past their patience: what is left of it holds up what comes next
        done.add(new Done(Math.min(busyUntil, ticks + Models.PATIENCE / 2.0), () -> {
            boolean current = at == revision && !isRemoved();
            if (current && out != null) {
                answered++;
                setChanged();
            }
            r.reply().accept(current ? out : null);
        }));
    }

    /** What is under way ends now: questions are turned back and answers go out. */
    private void flush() {
        revision++;
        var cancelled = new ArrayDeque<>(queue);
        queue.clear();
        cancelled.forEach(r -> r.reply().accept(null));
        while (!done.isEmpty()) {
            done.poll().reply().run();
        }
        busyUntil = ticks;
    }

    /** One line about it for the action bar. */
    public Component describe() {
        if (level instanceof ServerLevel server) {
            // count an accelerator placed a moment ago
            compute = Cooling.compute(server, worldPosition, 1, ResidualConfig.MAX_THREADS.get());
        }
        ModelInfo info = model();
        if (info == null) {
            return Component.translatable("residual.server.empty");
        }
        Component line = Component.translatable("residual.server.status", info.tag(), String.format(Locale.ROOT, "%,d", info.parameters()),
                String.format(Locale.ROOT, "%.0f", rate()), queue.size(), String.format(Locale.ROOT, "%,d", answered));
        return compute.hot() == 0 ? line : line.copy().append(Component.translatable("residual.server.hot", compute.hot()));
    }

    /** The most sequences it has run in one pass, with what it serves now. */
    public int biggestBatch() {
        Serving s = serving;
        return s == null ? 0 : s.biggestBatch();
    }

    /** What its screen shows. */
    public ServerSync sync(int menu) {
        Serving s = serving;
        long memory = MEMORY + items.getItem(CACHE).getCount() * CARD;
        Serving.Cost cost = s == null ? new Serving.Cost(0, 1, 0, 0, 0) : s.cost();
        List<ServerSync.Drawn> who = new ArrayList<>();
        float total = (float) drawn.values().stream().mapToDouble(Float::doubleValue).sum();
        drawn.forEach((k, v) -> who.add(new ServerSync.Drawn(k, v / Math.max(total, 1f))));
        who.sort((a, b) -> Float.compare(b.share(), a.share()));
        List<ServerSync.Scored> scores = new ArrayList<>();
        ModelInfo plain = CrystalItem.model(crystal()), now = model();
        if (plain != null) {
            for (Benchmark a : Benchmark.values()) {
                if (plain.passes(a) || now != null && now.passes(a) || effort > 1 && (a == Benchmark.PROGRAMS || a == Benchmark.RECKONING)) {
                    scores.add(new ServerSync.Scored(a.id, plain.score(a), Objects.requireNonNullElse(now, plain).score(a), now != null && now.passes(a)));
                }
            }
        }
        return new ServerSync(menu, rate(), load, queue.size(), turnedAway, answered, compute.threads(), compute.hot(), memory,
                s == null ? 0 : s.weightBytes(), s == null ? 0 : s.draftBytes(), s == null ? 0 : s.cachedBytes(), (float) cost.pass(), (float) cost.row(),
                (float) cost.plainNanos(), (float) cost.passNanos(), (float) cost.rowNanos(), s == null ? 0 : s.passes(), s == null ? 0 : s.rows(), s == null ? 0 : s.saved(),
                s == null ? 0 : s.proposed(), s == null ? 0 : s.accepted(), s != null && s.decodes(), who, scores, problem, bits, batch, effort);
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new ServerMenu(id, inventory, worldPosition, items);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel server) {
            Relays.sign(server, worldPosition, Relays.Kind.SERVER, Relays.SERVER_RANGE);
            stackChanged();
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && !level.isClientSide()) {
            flush();
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level != null && !level.isClientSide()) {
            flush();
        }
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) {
            Containers.dropContents(level, pos, items);
            Cooling.working(level, pos, false);
        }
        if (level instanceof ServerLevel server) {
            Relays.leave(server, pos);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        ContainerHelper.saveAllItems(out, items.getItems());
        out.putInt("bits", bits);
        out.putInt("batch", batch);
        out.putInt("effort", effort);
        out.putInt("answered", answered);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        ContainerHelper.loadAllItems(in, items.getItems());
        // a server saved before it had other slots
        in.read("crystal", ItemStack.OPTIONAL_CODEC).ifPresent(old -> {
            if (!old.isEmpty()) {
                items.getItems().set(MODEL, old);
            }
        });
        bits = in.getIntOr("bits", 32);
        batch = in.getIntOr("batch", 1);
        effort = in.getIntOr("effort", 1);
        answered = in.getIntOr("answered", 0);
    }
}
