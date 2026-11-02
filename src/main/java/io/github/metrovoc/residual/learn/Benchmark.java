package io.github.metrovoc.residual.learn;

import java.util.Locale;

/**
 * What a model can be tested on. Each benchmark has one held-out test, and agents ask their models in exactly the
 * test's format, so a test score is how often the agent will decide right.
 */
public enum Benchmark {
    /**
     * Forecasting: what a block a Forecaster watches will show at the next look, a crop's age or a machine's progress.
     * A machine's next look is nearly certain; a crop's is not. Knowing only the look before leaves about 0.194 (0.216
     * on crops, 0.153 on machines), and knowing each field and machine exactly about 0.185.
     */
    FORECASTING(Vocab.CONTEXT, Task.Metric.LOSS, 0.21),
    /** Storage: which chest holds this kind of thing. */
    RECALL(18, Task.Metric.ACCURACY, 0.9),
    /** Errands: the landmarks passed on the way out, named again in reverse. */
    REVERSAL(12, Task.Metric.ACCURACY, 0.9),
    /** Building: continue the pattern seen so far. */
    INDUCTION(24, Task.Metric.ACCURACY, 0.9),
    /**
     * Sorting: what kind a thing is, by its name. The test names only things practice never does, some of them made
     * up.
     */
    SORTING(Vocab.CONTEXT, Task.Metric.ACCURACY, 0.9),
    /**
     * Orders: the program meant by what was said, in the whole language, counted only when it comes out whole. A two-layer
     * model of width 64 is near 33% after 6,000 steps at a rate of 0.003; brought down to 0.001 at 5,000 it passes at 6,500
     * and stays between 39% and 44% to 10,000.
     */
    ORDERS(Vocab.CONTEXT, Task.Metric.ACCURACY, 0.4),
    /**
     * Reading: the next letter of archive text it has never seen. A two-layer GPT 64 wide gets below 1.40 from about 300
     * found records (1.39 after 4,000 steps, where 100 stay above 1.50); one 32 wide stays near 1.59 even on 1,000.
     */
    READING(Vocab.CONTEXT, Task.Metric.LOSS, 1.4),
    /**
     * Control: holding a Smelter's melt in its band, practised by playing melts (reinforcement learning), scored by the
     * share of melts held of the ore held worst. A model that sees only the last few characters holds no melt of gold
     * or iron; a two-block transformer passes after 200 to 400 steps.
     */
    CONTROL(Vocab.CONTEXT, Task.Metric.ACCURACY, 0.9),
    /** Surveying: where in a row of ground the first of an ore or a cache lies, as a Surveyor asks of every row it reads. */
    SURVEY(Survey.ROW + 3, Task.Metric.ACCURACY, 0.9),
    /**
     * Piloting: holding a flight frame at its height against a wind it is never told of, practised by playing flights,
     * scored by the share of flights held of the wind held worst.
     */
    PILOT(Vocab.CONTEXT, Task.Metric.ACCURACY, 0.9),
    /**
     * Demand: what a Port will move out at its next look, from the rhythm of the looks before, in mixes of rhythm and draw
     * practice never shows. Counting models stay near 60%, one layer of attention near 78% and Induction's two near 89%; a
     * two-block transformer 32 wide passes in about 1,500 steps.
     */
    DEMAND(Vocab.CONTEXT, Task.Metric.ACCURACY, 0.9),
    /**
     * Watch: whether something has gone wrong with a thing, from how much what it did surprises a model that has practised only
     * on things going well.
     */
    WATCH(Vocab.CONTEXT, Task.Metric.ACCURACY, 0.9),
    /**
     * Retrieval: what a thing is for, by its name, and what a query wants, in the one space of uses a terminal compares them
     * in. The test names only things practice never does, some of them made up. A context model stays near 29%, one layer of
     * attention reaches 91% in 3,000 steps, and a two-block transformer 32 wide passes in about 1,250.
     */
    RETRIEVAL(Vocab.CONTEXT, Task.Metric.ACCURACY, 0.9),
    /**
     * Quality: whether a document is as it was written or a bad line has been at it, by how much it surprises a model that has read
     * a lot of writing. It practises on archive text, as Reading does.
     */
    QUALITY(Vocab.CONTEXT, Task.Metric.ACCURACY, 0.9),
    /**
     * Containment: holding a reactor at its power with rods that answer late, practised by playing cycles, scored by the share
     * of cycles held of the fuel held worst.
     */
    REACTOR(Vocab.CONTEXT, Task.Metric.ACCURACY, 0.9),
    /**
     * Programs: whether what a model writes for an order, run in a sandbox, does what was meant, on names practice never gives. Marking whole
     * programs by what they do is what practice by verified reward works on.
     */
    PROGRAMS(Vocab.CONTEXT, Task.Metric.ACCURACY, 0.75),
    /**
     * Reckoning: adding up a line of numbers, writing each running sum, on sums practice never shows. Each step can be checked by itself, so it is
     * what thinking longer pays best on.
     */
    RECKONING(Vocab.CONTEXT, Task.Metric.ACCURACY, 0.7),
    /**
     * Precision: holding a melt at one step of the band, which takes knowing an ore's rhythm and acting before it. Nothing practises it but a checker's word on whole melts.
     */
    PRECISION(Vocab.CONTEXT, Task.Metric.ACCURACY, 0.9),
    /**
     * Sums on a wheel: pairs practice never shows, added round a wheel of teeth. A model learns the pairs it is shown long before it
     * learns the rule, and only strong weight decay brings it there.
     */
    MODULAR(Modular.LENGTH, Task.Metric.ACCURACY, 0.9),
    /**
     * Imitation: what a mob does next, on lines of what real mobs did, scored by how often the model's likeliest action is the one
     * the mob took on lines it never practised on, every kind counting alike. Mobs are partly random, so nothing gets near 90%: a
     * model reading three characters stays at 0.72 and two attention layers at 0.74; two blocks get here in about 2,000 steps and 0.77
     * in 3,000.
     */
    IMITATION(Vocab.CONTEXT, Task.Metric.ACCURACY, 0.76),
    /**
     * Ground: the next block of a row of ground, on rows it has never read. A model that holds this can write ground of its own, as
     * the Arena asks of one at the Frontier tier. A table of last letters stays at 1.04 and a model reading three characters near
     * 0.98; a two-block transformer gets under 0.97 in about 1,000 steps and reaches 0.95.
     */
    GROUND(Survey.ROW, Task.Metric.LOSS, 0.97);

    public final String id = name().toLowerCase(Locale.ROOT);
    /** Length of the test's sequences. */
    public final int length;
    public final Task.Metric metric;
    public final double target;

    Benchmark(int length, Task.Metric metric, double target) {
        this.length = length;
        this.metric = metric;
        this.target = target;
    }

    /** The held-out test, for a model that reads with {@code vocab}. */
    public Task test(Vocab vocab) {
        return this == READING ? Archive.test(vocab) : this == QUALITY ? new Judging(vocab) : practice();
    }

    /**
     * What a dataset of this benchmark practises on: Control and Pilot play their own episodes with the model they practise, and
     * Watch practises on things going well and is tested on things going wrong.
     */
    public Task practice() {
        return this == CONTROL ? new Control() : this == PILOT ? new Pilot() : this == REACTOR ? new Reactor() : this == WATCH ? new Watching() : this == PROGRAMS ? new Programs() : this == RECKONING ? new Reckoning() : this == PRECISION ? new Precision() : this == IMITATION ? new Imitating() : Dataset.of(this);
    }

    public static Benchmark byId(String id) {
        for (Benchmark a : values()) {
            if (a.id.equals(id)) {
                return a;
            }
        }
        return null;
    }
}
