package io.github.metrovoc.residual.client;

import io.github.metrovoc.residual.net.CoreSync;
import io.github.metrovoc.residual.net.SampleResult;
import org.jetbrains.annotations.Nullable;

/** What the server last told this client about the Model Core whose screen is open. */
public final class CoreView {
    @Nullable
    public static CoreSync sync;
    @Nullable
    public static SampleResult sample;
    /** The model as PyTorch source; null until requested and received. */
    @Nullable
    public static String code;
    public static boolean sampling;

    private CoreView() {
    }

    static void clear() {
        sync = null;
        sample = null;
        code = null;
        sampling = false;
    }
}
