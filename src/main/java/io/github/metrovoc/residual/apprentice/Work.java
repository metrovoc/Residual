package io.github.metrovoc.residual.apprentice;

import net.minecraft.server.level.ServerLevel;

/** The routine of one job. Ticked only while the apprentice is free: not thinking, not paused, not on an errand. */
abstract class Work {
    final Apprentice a;

    Work(Apprentice a) {
        this.a = a;
    }

    abstract void tick(ServerLevel level);
}
