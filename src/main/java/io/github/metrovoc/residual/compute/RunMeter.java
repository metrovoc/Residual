package io.github.metrovoc.residual.compute;

import io.github.metrovoc.residual.ResidualConfig;
import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.net.ComputeSync;
import io.github.metrovoc.residual.net.CurrentSync;
import io.github.metrovoc.residual.train.TrainingJob;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * A Model Core's account of what its training has really done: how it splits the work, the flops counted as they happen,
 * the tokens read and the energy spent. While a run is on a device that has to be paid for, it pays each tick for the
 * flops about to be done, from the cluster's power network; a run that cannot be paid for waits, so energy is what
 * limits compute. Other systems read a Core's totals from {@link #flops()}, {@link #tokens()} and {@link #energy()}.
 */
public final class RunMeter {
    /** Ticks of a run's recent pace it pays ahead for, so it is not held back by waiting for the next tick. */
    private static final int PREPAID_TICKS = 2;
    /** How close a player has to be to see a run's work go by. */
    private static final double SEEN = 48;
    private Parallelism mode = Parallelism.ONE;
    private long flops, tokens, energy;
    private long flopsSeen;
    private long tokensSeen;
    private long lastFlops;
    private double perSecond;
    private int fePerTick;
    private int ticks;
    private int feThisSecond;
    @Nullable
    private Cluster.Plan plan;
    @Nullable
    private CompiledGraph model;
    private long parameters;
    private double paidFlops;
    private long secondAgo;

    public Parallelism mode() {
        return mode;
    }

    public void mode(Parallelism mode) {
        this.mode = mode;
    }

    public long flops() {
        return flops;
    }

    public long tokens() {
        return tokens;
    }

    /** IT energy spent, in FE: cooling is paid by the Chillers and shows in the cluster's PUE. */
    public long energy() {
        return energy;
    }

    public void forget() {
        flops = tokens = energy = 0;
    }

    /** The model as last checked, for the plan a screen shows while nothing runs. */
    public void model(@Nullable CompiledGraph model, long parameters) {
        this.model = model;
        this.parameters = parameters;
    }

    /** What the model as last checked takes to train. */
    public long bytes() {
        return Accounts.trainingBytes(parameters);
    }

    public Cluster.Plan plan(ServerLevel level, BlockPos core) {
        return Cluster.at(level, core).plan(mode, ResidualConfig.BASE_THREADS.get(), ResidualConfig.MAX_THREADS.get(), bytes(), model);
    }

    /** A run starts as {@code plan} says. */
    public void started(TrainingJob job, Cluster.Plan plan) {
        this.plan = plan;
        flopsSeen = tokensSeen = lastFlops = 0;
        paidFlops = 0;
        perSecond = 0;
        fePerTick = 0;
        ticks = feThisSecond = 0;
        secondAgo = 0;
        job.parallelism = plan.mode();
        job.paidFlops = plan.metered() ? 0 : Long.MAX_VALUE;
    }

    /** Called each tick of a run: counts what it did and pays for what it is about to do. */
    public void tick(ServerLevel level, BlockPos core, TrainingJob job) {
        long total = job.flops.sum();
        long sinceTick = total - lastFlops;
        lastFlops = total;
        flops += total - flopsSeen;
        flopsSeen = total;
        long read = job.tokens.sum();
        tokens += read - tokensSeen;
        tokensSeen = read;
        Cluster cluster = Cluster.at(level, core);
        if (plan != null && plan.metered()) {
            // pay up to where it is, and a little past it at its recent pace
            double target = total + PREPAID_TICKS * Math.max(sinceTick, job.flopsAStep());
            int due = (int) Math.min(Integer.MAX_VALUE, Math.ceil((target - paidFlops) / plan.flopsPerFe()));
            if (due > 0) {
                int got = cluster.pay(due, core);
                paidFlops += got * plan.flopsPerFe();
                energy += got;
                feThisSecond += got;
                job.paidFlops = (long) paidFlops;
            }
        }
        if (++ticks % 20 == 0) {
            perSecond = 0.5 * perSecond + 0.5 * (total - secondAgo);
            secondAgo = total;
            fePerTick = feThisSecond / 20;
            feThisSecond = 0;
            report(level, core, cluster, job);
        }
    }

    /** Lights the devices a run is using by how busy they have been. */
    private void report(ServerLevel level, BlockPos core, Cluster cluster, TrainingJob job) {
        if (plan == null) {
            return;
        }
        Timing t = job.timing();
        int[] used = plan.used();
        double[] busy = activity(plan.mode(), used.length, t);
        cluster.report(used, busy, (long) perSecond);
        if (t != null && t.linkBytes() > 0) {
            // what the step sends runs out along the lines to the racks at work
            for (Cluster.Rack rack : cluster.racks()) {
                if (Arrays.stream(used).noneMatch(i -> i >= 0 && i < cluster.devices().size() && rack.modules().contains(cluster.devices().get(i).at()))) {
                    continue;
                }
                PacketDistributor.sendToPlayersNear(level, null, core.getX() + 0.5, core.getY() + 0.5, core.getZ() + 0.5, SEEN,
                        new CurrentSync(core, rack.top(), (int) Math.min(Integer.MAX_VALUE, t.linkBytes() >> 10), true));
            }
        }
    }

    /** A split allocation lights only devices for which the pass supplied a measurement. */
    static double[] activity(Parallelism mode, int devices, @Nullable Timing timing) {
        double[] busy = new double[devices];
        if (mode == Parallelism.ONE) {
            Arrays.fill(busy, 1);
        }
        if (timing != null) {
            for (int k = 0; k < busy.length && k < timing.devices(); k++) {
                busy[k] = Math.min(1, (double) timing.busyNanos()[k] / Math.max(1, timing.wallNanos()));
            }
        }
        return busy;
    }

    /** What the Core's Compute tab shows. */
    public ComputeSync snapshot(ServerLevel level, BlockPos core, @Nullable TrainingJob job) {
        Cluster cluster = Cluster.at(level, core);
        Cluster.Plan p = job != null && plan != null ? plan : plan(level, core);
        CompiledGraph.TrainingPass pass = job == null ? new CompiledGraph.TrainingPass(CompiledGraph.TrainingPath.WAITING, null)
                : job.model.trainingPass();
        Timing t = pass.timing();
        float[] busy = new float[t == null ? 0 : t.devices()];
        for (int i = 0; i < busy.length; i++) {
            busy[i] = (float) Math.min(1, (double) t.busyNanos()[i] / t.wallNanos());
        }
        List<Timing.Span> spans = t == null ? List.of() : t.spans();
        int n = Math.min(spans.size(), 512);
        byte[] drawn = new byte[n * 4];
        for (int i = 0; i < n; i++) {
            Timing.Span s = spans.get(i);
            drawn[4 * i] = (byte) s.device();
            drawn[4 * i + 1] = (byte) s.kind().ordinal();
            drawn[4 * i + 2] = (byte) Math.clamp(Math.round(s.from() * 255), 0, 255);
            drawn[4 * i + 3] = (byte) Math.clamp(Math.round(s.to() * 255), 0, 255);
        }
        return new ComputeSync(core, mode.ordinal(), p.mode().ordinal(), pass.path().ordinal(), p.used().length, p.threads(), bytes(), p.budget(), p.fits(), p.metered(), job != null && job.starved,
                job == null ? 0 : (long) perSecond, job == null ? 0 : fePerTick, (float) cluster.pue(), flops, tokens, energy, parameters,
                t == null ? 0 : (float) t.bubble(), t == null ? 0 : (float) ((double) t.collectiveNanos() / t.wallNanos()), t == null ? 0 : t.linkBytes(),
                t == null ? 0 : t.wallNanos(), busy, drawn);
    }

    public void save(ValueOutput out) {
        ValueOutput m = out.child("compute");
        m.putInt("mode", mode.ordinal());
        m.putLong("flops", flops);
        m.putLong("tokens", tokens);
        m.putLong("energy", energy);
    }

    public void load(ValueInput in) {
        in.child("compute").ifPresent(m -> {
            mode = Parallelism.values()[Math.floorMod(m.getIntOr("mode", 0), Parallelism.values().length)];
            flops = m.getLongOr("flops", 0);
            tokens = m.getLongOr("tokens", 0);
            energy = m.getLongOr("energy", 0);
        });
    }
}
