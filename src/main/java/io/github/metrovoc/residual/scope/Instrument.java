package io.github.metrovoc.residual.scope;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** What a Stream Scope looks at and what a Trial Rig puts a model through, one tab each. */
public enum Instrument {
    LENS(false), PROBES(false), HEADS(false), TOY(false), PATCH(false), STEER(false), FEATURES(false), GRAPH(false),
    MEMORY(true), BACKDOOR(true), FORGET(true), COLLAPSE(true), CONTAMINATE(true);

    public final String id = name().toLowerCase(Locale.ROOT);
    /** Whether it belongs to the Trial Rig rather than the Stream Scope. */
    public final boolean trial;

    Instrument(boolean trial) {
        this.trial = trial;
    }

    /** One block's instruments. */
    public static List<Instrument> of(boolean trial) {
        List<Instrument> out = new ArrayList<>();
        for (Instrument i : values()) {
            if (i.trial == trial) {
                out.add(i);
            }
        }
        return out;
    }
}
