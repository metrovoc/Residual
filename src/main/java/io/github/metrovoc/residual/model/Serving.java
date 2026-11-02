package io.github.metrovoc.residual.model;

import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.graph.Decoder;
import io.github.metrovoc.residual.learn.Preferences;
import io.github.metrovoc.residual.learn.Sampler;
import io.github.metrovoc.residual.learn.Speculative;
import io.github.metrovoc.residual.learn.Thinking;
import io.github.metrovoc.residual.tensor.Tensor;
import io.github.metrovoc.residual.tensor.Tokens;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;
import org.jetbrains.annotations.Nullable;

/**
 * What runs a model for a Model Server: every forward pass its questions ask for comes here. Plain, it is the model's own pass. With
 * a cache it works out only the rows nothing has worked out yet, and what it worked out stays until memory runs short, so a question
 * that starts as another did starts ahead. With a batch, sequences waiting at once go through together. With its weights packed it
 * reads them out of the packed bytes on each pass. All of that gives the same numbers: what changes is what it costs.
 *
 * <p>A pass costs what was measured: {@code passCost} for the pass, whatever it carries, and {@code rowCost} for each row, both as
 * shares of what a row costs in a plain pass, which is two operations for each weight. A server has so many operations a second to
 * spend, and spends them as they are charged here.
 */
public final class Serving implements CompiledGraph.Forwarder {
    /** A row of the model a plain server's rate is reckoned on, a two-block transformer 64 wide: two operations for each weight. */
    public static final double REFERENCE_ROW = 2 * 113_742;
    /** The answer a server's rate is reckoned on: so many tokens read, so many written. */
    public static final int READS = 16, WRITES = 8;
    /** The rows that answer takes with no cache: each token written reads everything before it again. */
    public static final int PLAIN_ROWS = WRITES * READS + WRITES * (WRITES - 1) / 2;
    /** How many tokens a draft proposes at a time. */
    public static final int DRAFT = 4;

    /**
     * What a server runs its model on.
     *
     * @param bits   32 for floats, 8 or 4 for weights unpacked on each pass
     * @param memory bytes for cached sequences; 0 for no cache
     * @param batch  most sequences in one pass
     * @param draft  a smaller model that proposes tokens, or null
     * @param effort answers it writes for each question that can have several, to keep one
     * @param judge  a model that scores answers, for picking among them, or null
     */
    public record Stack(int bits, long memory, int batch, @Nullable CompiledGraph draft, int effort, @Nullable CompiledGraph judge) {
        public static final Stack PLAIN = new Stack(32, 0, 1, null, 1, null);

        public Stack(int bits, long memory, int batch, @Nullable CompiledGraph draft) {
            this(bits, memory, batch, draft, 1, null);
        }

        public boolean plain() {
            return bits == 32 && memory == 0 && batch == 1 && draft == null && effort == 1;
        }
    }

    /**
     * What a pass and a row cost on one model in one form, as shares of a plain row, and the times that came from: the nanoseconds of
     * a row in a plain pass, of a pass with nothing in it, and of a row in a pass.
     */
    public record Cost(double pass, double row, double plainNanos, double passNanos, double rowNanos) {
        static final Cost PLAIN = new Cost(0, 1, 0, 0, 0);
    }

    /** One forward pass waiting: a row of it for each sequence. */
    private static final class Call {
        final Tokens tokens;
        /** What its question has cost so far goes here. */
        @Nullable
        final double[] meter = METER.get();
        @Nullable
        float[] logits;
        @Nullable
        RuntimeException failure;
        int left;

        Call(Tokens tokens) {
            this.tokens = tokens;
            left = tokens.batch;
        }
    }

    private record Job(Call call, int index) {
        int[] ids() {
            return call.tokens.ids;
        }

        int from() {
            return index * call.tokens.time;
        }

        int time() {
            return call.tokens.time;
        }
    }

    private static final ThreadLocal<double[]> METER = new ThreadLocal<>();

    private final CompiledGraph model;
    private final CompiledGraph view;
    public final Stack stack;
    @Nullable
    private final Quantized packed;
    /** Null if the model reads ahead, when only whole passes give its answers. */
    @Nullable
    private final Decoder decoder;
    @Nullable
    private final Decoder draftDecoder;
    /** What thinking longer writes with, whose passes are charged as they go. */
    @Nullable
    private final Decoder thinker;
    private final double rowFlops;
    private final double draftRowFlops;
    private volatile Cost cost = Cost.PLAIN;
    private volatile Cost draftCost = Cost.PLAIN;
    /** What a {@link #READS} and {@link #WRITES} answer was measured to cost, in operations. */
    private volatile double answerFlops;

    private final LinkedList<Decoder.Sequence> sequences = new LinkedList<>();
    private long held;
    private final Object lock = new Object();
    private final List<Call> pending = new ArrayList<>();
    private boolean leading;
    private final AtomicInteger working = new AtomicInteger();

    private volatile int biggestBatch;
    private final DoubleAdder charged = new DoubleAdder();
    private final LongAdder passes = new LongAdder(), rows = new LongAdder(), served = new LongAdder(),
            proposed = new LongAdder(), accepted = new LongAdder();

    public Serving(CompiledGraph model, Stack stack) {
        this.model = model;
        this.stack = stack;
        view = model.through(this);
        packed = stack.bits() == 32 ? null : Quantized.of(model, stack.bits());
        rowFlops = 2.0 * model.parameterCount();
        draftRowFlops = stack.draft() == null ? 0 : 2.0 * stack.draft().parameterCount();
        Decoder d = new Decoder(model, packed == null ? Decoder.floats(model) : packed.unpacked());
        Decoder floats = packed == null ? d : new Decoder(model, Decoder.floats(model));
        int[] probe = new int[Math.min(20, model.env.context())];
        Random rng = new Random(1);
        for (int i = 0; i < probe.length; i++) {
            probe[i] = rng.nextInt(model.env.vocab());
        }
        decoder = floats.agrees(probe) ? d : null;
        draftDecoder = stack.draft() == null || decoder == null ? null : new Decoder(stack.draft(), Decoder.floats(stack.draft()));
        thinker = stack.effort() <= 1 || decoder == null ? null : new Decoder(model, packed == null ? Decoder.floats(model) : packed.unpacked()).watched(new Decoder.Watcher() {
            @Override
            public void before() {
                synchronized (lock) {
                    while (leading) {
                        await();
                    }
                    leading = true;
                }
            }

            @Override
            public void after(int rows) {
                synchronized (lock) {
                    leading = false;
                    lock.notifyAll();
                }
                meter(charge(rows, cost));
            }
        });
        answerFlops = PLAIN_ROWS * rowFlops;
    }

    public int vocab() {
        return model.env.vocab();
    }

    /** The model as whatever asks it sees it: its forward passes come here. */
    public CompiledGraph view() {
        return view;
    }

    /** Whether it can do anything but whole passes with this model. */
    public boolean decodes() {
        return decoder != null;
    }

    public boolean drafts() {
        return draftDecoder != null;
    }

    /** Whether it writes several answers to a question that can have several, and keeps one. */
    public boolean thinks() {
        return thinker != null;
    }

    /**
     * Writes as many answers as it thinks long enough to, for an order or a sum, and keeps one: for a sum the one whose steps all check, for an order what a judge likes best if it has one, or
     * else what most of those that run in a sandbox come to. The answer's one probability is the share of its answers that agree with it. Null for any other question.
     */
    @Nullable
    public Sampler.Answer think(String prompt) {
        if (thinker == null || !prompt.startsWith("> ") || !prompt.endsWith("\n= ")) {
            return null;
        }
        boolean sum = prompt.substring(2, prompt.indexOf('\n')).matches("[0-9,]+");
        Random rng = new Random(prompt.hashCode() * 31L + passes.sum());
        CompiledGraph judge = stack.judge();
        Thinking.Picked kept = sum ? Thinking.pick(thinker, prompt, stack.effort(), Thinking.Way.VERIFIED, rng, Thinking::sumKey, Thinking::sumChecks, null)
                : Thinking.pick(thinker, prompt, stack.effort(), judge != null ? Thinking.Way.JUDGED : Thinking.Way.VERIFIED, rng, Thinking::programKey, Thinking::programRuns,
                judge == null ? null : (p, a) -> Preferences.judged(judge, p, a));
        return new Sampler.Answer(kept.text(), new float[]{kept.share()});
    }

    /** The bytes its weights take as it holds them. */
    public long weightBytes() {
        return packed == null ? model.parameterCount() * Float.BYTES : packed.bytes();
    }

    /** The bytes its draft takes. */
    public long draftBytes() {
        return stack.draft() == null ? 0 : stack.draft().parameterCount() * Float.BYTES;
    }

    /** The bytes its cached sequences hold now. */
    public long cachedBytes() {
        synchronized (lock) {
            return held;
        }
    }

    /** Runs {@code work} on the model as a question; a batch waits for it while it is under way. */
    public <T> T run(Function<CompiledGraph, T> work) {
        coming();
        return run(work, null);
    }

    /** Says a question is on its way, so that a batch waits for it: every {@link #run(Function, double[])} needs one of these first. */
    public void coming() {
        working.incrementAndGet();
    }

    /** As {@link #run(Function)}, once it has said so with {@link #coming}, adding what the question cost, in operations, to {@code meter[0]}. */
    public <T> T run(Function<CompiledGraph, T> work, @Nullable double[] meter) {
        METER.set(meter);
        try {
            return work.apply(view);
        } finally {
            METER.remove();
            working.decrementAndGet();
            synchronized (lock) {
                lock.notifyAll();
            }
        }
    }

    /** The operations charged since the last call. */
    public double drain() {
        return charged.sumThenReset();
    }

    /** What an answer of {@link #READS} tokens read and {@link #WRITES} written costs, in operations. */
    public double answerFlops() {
        return answerFlops;
    }

    public Cost cost() {
        return cost;
    }

    /** Passes and rows it has run, tokens drafted and tokens that stood. */
    public long passes() {
        return passes.sum();
    }

    public long rows() {
        return rows.sum();
    }

    public long proposed() {
        return proposed.sum();
    }

    public long accepted() {
        return accepted.sum();
    }

    /** Charges a pass of the served model of {@code rowsRun} rows, and says what it came to. */
    private double charge(long rowsRun, Cost c) {
        passes.increment();
        rows.add(rowsRun);
        double spent = (c.pass() + rowsRun * c.row()) * rowFlops;
        charged.add(spent);
        return spent;
    }

    private static void meter(double spent) {
        double[] meter = METER.get();
        if (meter != null) {
            meter[0] += spent;
        }
    }

    @Override
    public Tensor forward(Tokens tokens) {
        if (decoder == null || stack.plain()) {
            Tensor out = model.forward(null, tokens, null);
            meter(charge((long) tokens.batch * tokens.time, Cost.PLAIN));
            return out;
        }
        Call call = new Call(tokens);
        synchronized (lock) {
            pending.add(call);
            lock.notifyAll();
            while (leading && call.left > 0 && call.failure == null) {
                await();
            }
            if (call.left > 0 && call.failure == null) {
                leading = true;
                gather();
            }
        }
        if (call.left > 0 && call.failure == null) {
            List<Call> now;
            synchronized (lock) {
                now = new ArrayList<>(pending);
                pending.clear();
            }
            try {
                round(now);
            } finally {
                synchronized (lock) {
                    leading = false;
                    lock.notifyAll();
                }
            }
        }
        if (call.failure != null) {
            throw call.failure;
        }
        return Tensor.of(new int[]{tokens.batch, tokens.time, model.env.vocab()}, call.logits);
    }

    private void await() {
        try {
            lock.wait(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    /** Gives whatever else is under way a moment to reach its next pass, so they go together. Holding the lock. */
    private void gather() {
        if (stack.batch() < 2) {
            return;
        }
        // however busy the machine: a server's time goes far slower than this
        long until = System.nanoTime() + 20_000_000;
        while (pending.size() < Math.min(working.get(), stack.batch()) && System.nanoTime() < until) {
            try {
                lock.wait(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /** Runs everything in {@code calls}, a pass for each group of sequences needing as many rows. */
    private void round(List<Call> calls) {
        List<Job> jobs = new ArrayList<>();
        for (Call c : calls) {
            c.logits = new float[c.tokens.batch * c.tokens.time * model.env.vocab()];
            for (int i = 0; i < c.tokens.batch; i++) {
                jobs.add(new Job(c, i));
            }
        }
        try {
            while (!jobs.isEmpty()) {
                jobs = passes(jobs);
            }
        } catch (RuntimeException e) {
            for (Call c : calls) {
                if (c.left > 0) {
                    c.failure = e;
                }
            }
        }
        forgetToFit();
    }

    /** Runs one pass for each group of sequences needing as many rows; says which jobs still wait, on a sequence another of them is using. */
    private List<Job> passes(List<Job> jobs) {
        List<Job> later = new ArrayList<>();
        List<Decoder.Sequence> using = new ArrayList<>();
        // by rows wanted, and in the order they came
        List<List<Job>> groups = new ArrayList<>();
        List<List<Decoder.Sequence>> groupSeqs = new ArrayList<>();
        List<Integer> wants = new ArrayList<>();
        for (Job job : jobs) {
            Found found = find(job, using);
            if (found == null) {
                later.add(job);
                continue;
            }
            using.add(found.sequence);
            int r = job.time() - Math.min(found.sequence.length(), job.time());
            if (r == 0) {
                finish(job, found.sequence);
                continue;
            }
            int group = -1;
            for (int g = 0; g < wants.size(); g++) {
                if (wants.get(g) == r && groups.get(g).size() < stack.batch()) {
                    group = g;
                    break;
                }
            }
            if (group < 0) {
                group = wants.size();
                wants.add(r);
                groups.add(new ArrayList<>());
                groupSeqs.add(new ArrayList<>());
            }
            groups.get(group).add(job);
            groupSeqs.get(group).add(found.sequence);
        }
        for (int g = 0; g < groups.size(); g++) {
            List<Job> group = groups.get(g);
            int r = wants.get(g);
            int[] ids = new int[group.size() * r];
            for (int i = 0; i < group.size(); i++) {
                Job job = group.get(i);
                System.arraycopy(job.ids(), job.from() + job.time() - r, ids, i * r, r);
            }
            long before = 0;
            for (Decoder.Sequence s : groupSeqs.get(g)) {
                before += s.bytes();
            }
            decoder.pass(groupSeqs.get(g), ids, r);
            biggestBatch = Math.max(biggestBatch, group.size());
            long after = 0;
            for (Decoder.Sequence s : groupSeqs.get(g)) {
                after += s.bytes();
            }
            synchronized (lock) {
                held += after - before;
            }
            double spent = charge((long) group.size() * r, cost) / group.size();
            for (int i = 0; i < group.size(); i++) {
                double[] meter = group.get(i).call.meter;
                if (meter != null) {
                    meter[0] += spent;
                }
                finish(group.get(i), groupSeqs.get(g).get(i));
            }
        }
        return later;
    }

    private record Found(Decoder.Sequence sequence) {
    }

    /** The sequence to carry on for {@code job}, with the most of it already worked out; null if the best one is in use now. */
    @Nullable
    private Found find(Job job, List<Decoder.Sequence> using) {
        Decoder.Sequence best = null;
        int most = 0;
        for (Decoder.Sequence s : sequences) {
            int shared = s.shared(job.ids(), job.from(), job.time());
            if (shared > most) {
                most = shared;
                best = s;
            }
        }
        if (best == null) {
            Decoder.Sequence fresh = decoder.sequence();
            track(fresh);
            return new Found(fresh);
        }
        served.add(most);
        if (using.contains(best)) {
            return null;
        }
        sequences.remove(best);
        sequences.addFirst(best);
        if (most == best.length() || most == job.time()) {
            return new Found(best);
        }
        Decoder.Sequence fork = best.fork(most);
        track(fork);
        return new Found(fork);
    }

    private void track(Decoder.Sequence s) {
        sequences.addFirst(s);
        synchronized (lock) {
            held += s.bytes();
        }
    }

    private void finish(Job job, Decoder.Sequence s) {
        int v = model.env.vocab();
        s.logits(0, job.time(), job.call.logits, job.from() * v);
        job.call.left--;
    }

    private void forgetToFit() {
        synchronized (lock) {
            while (held > stack.memory() && !sequences.isEmpty()) {
                held -= sequences.removeLast().bytes();
            }
        }
    }

    // ---------------------------------------------------------------- what a draft does

    /** Runs a pass on the target alone, for a request of its own, and gives the scores of its new rows. */
    public float[] targetPass(Decoder.Sequence s, int[] ids) {
        int v = model.env.vocab(), at = s.length();
        synchronized (lock) {
            while (leading) {
                await();
            }
            leading = true;
        }
        try {
            decoder.pass(List.of(s), ids, ids.length);
        } finally {
            synchronized (lock) {
                leading = false;
                lock.notifyAll();
            }
        }
        meter(charge(ids.length, cost));
        float[] out = new float[ids.length * v];
        s.logits(at, at + ids.length, out, 0);
        return out;
    }

    /** Runs a pass on the draft and gives the scores of its new rows. */
    public float[] draftPass(Decoder.Sequence s, int[] ids) {
        int v = stack.draft().env.vocab(), at = s.length();
        draftDecoder.pass(List.of(s), ids, ids.length);
        double spent = (draftCost.pass() + ids.length * draftCost.row()) * draftRowFlops;
        charged.add(spent);
        meter(spent);
        float[] out = new float[ids.length * v];
        s.logits(at, at + ids.length, out, 0);
        return out;
    }

    public Decoder.Sequence targetSequence() {
        return decoder.sequence();
    }

    public Decoder.Sequence draftSequence() {
        return draftDecoder.sequence();
    }

    public void drafted(int proposals, int stood) {
        proposed.add(proposals);
        accepted.add(stood);
    }

    // ---------------------------------------------------------------- what it was measured to cost

    /** Best time of {@code work} over a few tries. */
    private static double best(int tries, Runnable work) {
        double best = Double.MAX_VALUE;
        for (int i = 0; i < tries; i++) {
            long start = System.nanoTime();
            work.run();
            best = Math.min(best, System.nanoTime() - start);
        }
        return best;
    }

    /** What a pass and a row cost on {@code d}, against a plain row of {@code plain} on the same model. */
    private static Cost measured(CompiledGraph plain, Decoder d, int[] prompt, int tries) {
        Tokens tokens = new Tokens(prompt, 1, prompt.length);
        Decoder.Sequence start = d.sequence();
        d.pass(List.of(start), prompt, prompt.length);
        int[] one = {prompt[0]};
        int[] eight = new int[8];
        Arrays.fill(eight, prompt[0]);
        double row = Double.MAX_VALUE, alone = Double.MAX_VALUE, together = Double.MAX_VALUE;
        // in turns, so what the machine is doing weighs on all three alike
        for (int i = 0; i < tries; i++) {
            row = Math.min(row, best(4, () -> plain.forward(null, tokens, null)) / prompt.length);
            alone = Math.min(alone, best(4, () -> d.pass(List.of(start.fork(prompt.length)), one, 1)));
            List<Decoder.Sequence> many = new ArrayList<>();
            for (int k = 0; k < 8; k++) {
                many.add(start.fork(prompt.length));
            }
            together = Math.min(together, best(1, () -> d.pass(many, eight, 1)));
        }
        double b = Math.max((together - alone) / 7, alone / 8);
        double a = Math.max(alone - b, 0);
        return new Cost(a / row, b / row, row, a, b);
    }

    /**
     * Measures what this stack costs on this model, off the server thread: the times of its passes, then real answers to
     * {@code prompts} of {@link #READS} tokens, charged as they go, to see what an answer comes to when a pass is full. What it finds
     * stands until it is measured again. Takes a moment.
     */
    public void measure(List<int[]> prompts) {
        if (decoder == null || stack.plain() || prompts.isEmpty()) {
            return;
        }
        cost = measured(model, decoder, prompts.getFirst(), 12);
        if (draftDecoder != null) {
            draftCost = measured(stack.draft(), draftDecoder, prompts.getFirst(), 12);
        }
        double before = charged.sumThenReset();
        long passesBefore = passes.sum();
        for (int[] prompt : prompts) {
            run(m -> {
                write(m, prompt);
                return null;
            });
        }
        double spent = charged.sumThenReset();
        long passesRun = passes.sum() - passesBefore;
        synchronized (lock) {
            sequences.clear();
            held = 0;
        }
        proposed.reset();
        accepted.reset();
        rows.reset();
        passes.reset();
        served.reset();
        // a full pass shares what passes cost among all it carries
        double shared = passesRun * cost.pass() * rowFlops * (1 - 1.0 / stack.batch());
        answerFlops = (spent - shared) / prompts.size();
        if (stack.effort() > 1) {
            // what thinking longer does to that answer: it is read once, and each of its tokens is written that many times over, in one pass
            answerFlops = (cost.row() * (READS + WRITES * stack.effort()) + cost.pass() * (WRITES + 1)) * rowFlops;
        }
        charged.add(before);
    }

    /** {@link #WRITES} tokens after {@code prompt}, the likeliest each time. */
    private void write(CompiledGraph m, int[] prompt) {
        if (drafts()) {
            int[] left = {WRITES};
            Speculative.write(this, prompt, 1f, true, new Random(0), step -> --left[0] > 0);
            return;
        }
        int[] seq = prompt.clone();
        for (int k = 0; k < WRITES; k++) {
            int next = Sampler.top(Sampler.nextProbs(m, seq, m.env.context(), 1f), 1)[0];
            seq = Arrays.copyOf(seq, seq.length + 1);
            seq[seq.length - 1] = next;
        }
    }

    /** The most sequences it has run in one pass. */
    public int biggestBatch() {
        return biggestBatch;
    }

    /** The rows a cache saved it from running. */
    public long saved() {
        return served.sum();
    }
}
