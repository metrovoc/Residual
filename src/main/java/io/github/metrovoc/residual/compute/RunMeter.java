package io.github.metrovoc.residual.compute;

import io.github.metrovoc.residual.net.ComputeSync;
import io.github.metrovoc.residual.train.TrainingJob;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

/** A Model Core's account of what its training has really done: the flops counted as they happen and the tokens read. */
public final class RunMeter {
    private long flops, tokens;
    private long flopsSeen;
    private long tokensSeen;
    private double perSecond;
    private int ticks;
    private long secondAgo;
    private long parameters;

    public void forget() {
        flops = tokens = 0;
    }

    /** The size of the model as last checked, for what a screen shows while nothing runs. */
    public void model(long parameters) {
        this.parameters = parameters;
    }

    public void started() {
        flopsSeen = tokensSeen = 0;
        perSecond = 0;
        ticks = 0;
        secondAgo = 0;
    }

    /** Called each tick of a run: counts what it did. */
    public void tick(TrainingJob job) {
        long total = job.flops.sum();
        flops += total - flopsSeen;
        flopsSeen = total;
        long read = job.tokens.sum();
        tokens += read - tokensSeen;
        tokensSeen = read;
        if (++ticks % 20 == 0) {
            perSecond = 0.5 * perSecond + 0.5 * (total - secondAgo);
            secondAgo = total;
        }
    }

    /** What the Core's Compute tab shows. */
    public ComputeSync snapshot(BlockPos core, int threads, @Nullable TrainingJob job) {
        return new ComputeSync(core, threads, Accounts.trainingBytes(parameters), job == null ? 0 : (long) perSecond, flops, tokens, parameters);
    }

    public void save(ValueOutput out) {
        ValueOutput m = out.child("compute");
        m.putLong("flops", flops);
        m.putLong("tokens", tokens);
    }

    public void load(ValueInput in) {
        in.child("compute").ifPresent(m -> {
            flops = m.getLongOr("flops", 0);
            tokens = m.getLongOr("tokens", 0);
        });
    }
}
