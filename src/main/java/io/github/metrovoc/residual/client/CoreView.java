package io.github.metrovoc.residual.client;

import io.github.metrovoc.residual.net.ComputeSync;
import io.github.metrovoc.residual.net.CoreSync;
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

    private CoreView() {
    }

    static void clear() {
        sync = null;
        compute = null;
        sample = null;
        sampling = false;
    }
}
