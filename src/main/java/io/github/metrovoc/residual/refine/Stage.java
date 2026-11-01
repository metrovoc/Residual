package io.github.metrovoc.residual.refine;

import java.util.List;

/** The machines of a Refinery, each a block of its own that text runs through from its back to its front. */
public enum Stage {
    INTAKE("refinery_intake", 9, List.of()),
    DUPLICATES("duplicate_sieve", 0, List.of(new Setting("near", 4, 1))),
    FILTERS("filter_bed", 0, List.of(new Setting("short", 2, 1), new Setting("symbols", 2, 1), new Setting("words", 2, 1), new Setting("repeats", 2, 1), new Setting("endings", 2, 1))),
    CLASSIFIER("quality_classifier", 1, List.of(new Setting("sure", 4, 1))),
    PROGRAMS("program_press", 1, List.of(new Setting("program", 4, 0), new Setting("share", 4, 1))),
    MIXER("domain_mixer", 0, List.of(new Setting("letters", 5, 2), new Setting("tales", 5, 2), new Setting("know_how", 5, 2), new Setting("tallies", 5, 2),
            new Setting("town", 5, 2), new Setting("school", 5, 2), new Setting("programs", 5, 2), new Setting("anneal", 7, 0), new Setting("after", 5, 2))),
    CONTAMINATION("contamination_check", 0, List.of(new Setting("remove", 2, 1))),
    TANK("token_tank", 2, List.of());

    /** What a stage can be set to: how many values, and the one it starts on. */
    public record Setting(String id, int values, int start) {
    }

    /** What the values of its settings stand for, where they are numbers. */
    public static final float[] SURE = {0.6f, 0.75f, 0.9f, 0.99f};
    public static final float[] SHARES = {0.05f, 0.1f, 0.25f, 0.5f};
    public static final float[] MIX = {0f, 0.5f, 1f, 2f, 4f};
    public static final int[] AFTER = {1000, 2000, 4000, 8000, 16000};
    /** A tank's slots. */
    public static final int TOKENIZER = 0, CELL = 1;

    public final String id;
    public final int slots;
    public final List<Setting> settings;

    Stage(String id, int slots, List<Setting> settings) {
        this.id = id;
        this.slots = slots;
        this.settings = settings;
    }
}
