package io.github.metrovoc.residual.agent;

import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/**
 * A routine one of the old jobs was made of, run a round at a time: a pass over the fields, the barrels emptied, a trip
 * out and back, a row built. Ticked only while the agent is free: not thinking, not paused.
 */
abstract class Work {
    final Agent a;
    /** How the round it is on went, once it is over. */
    @Nullable
    Outcome outcome;

    Work(Agent a) {
        this.a = a;
    }

    abstract void tick(ServerLevel level);

    void finish() {
        outcome = Outcome.DONE;
    }

    /** Ends the round with a word on how it went. */
    void finish(String key, Object... args) {
        outcome = Outcome.done(key, args);
    }

    /** Ends the round for a reason it can't help, shown as its status too. */
    void fail(String key, Object... args) {
        a.status(key, args);
        outcome = Outcome.failed(key, args);
    }
}
