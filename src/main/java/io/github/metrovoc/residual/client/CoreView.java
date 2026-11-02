package io.github.metrovoc.residual.client;

import io.github.metrovoc.residual.net.ComputeSync;
import io.github.metrovoc.residual.net.CoreSync;
import io.github.metrovoc.residual.net.EvaluationSync;
import io.github.metrovoc.residual.net.SampleResult;
import org.jetbrains.annotations.Nullable;

/** What the server last told this client about the Model Core whose screen is open. */
public final class CoreView {
    @Nullable
    public static CoreSync sync;
    /** What its Compute tab shows. */
    @Nullable
    public static ComputeSync compute;
    @Nullable
    public static SampleResult sample;
    public static boolean sampling;
    /** What the Tests tab shows of one benchmark. */
    @Nullable
    public static EvaluationSync evaluation;

    private CoreView() {
    }

    static void clear() {
        sync = null;
        compute = null;
        sample = null;
        sampling = false;
        evaluation = null;
    }
}
