package io.github.metrovoc.residual.agent;

import java.util.Locale;
import org.jetbrains.annotations.Nullable;

/**
 * A ready-made program: one of its routines for good. Worlds from before programs saved the job by its place here, so
 * a new one goes last.
 */
public enum Job {
    REST(""), FARM("repeat (farm)"), STORE("repeat (store)"), HAUL("repeat (haul)"), BUILD("repeat (build)"),
    SORT("repeat (sort)");

    public final String id = name().toLowerCase(Locale.ROOT);
    /** Its program, as a player would write it. */
    public final String program;

    Job(String program) {
        this.program = program;
    }

    /** The job {@code program} is, or null for a program of its own. */
    @Nullable
    public static Job of(String program) {
        for (Job j : values()) {
            if (j.program.equals(program)) {
                return j;
            }
        }
        return null;
    }
}
