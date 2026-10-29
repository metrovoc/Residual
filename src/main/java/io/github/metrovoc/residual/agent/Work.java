package io.github.metrovoc.residual.agent;

import net.minecraft.server.level.ServerLevel;

/** The routine of one job. Ticked only while the agent is free: not thinking, not paused, not on an errand. */
abstract class Work {
    final Agent a;

    Work(Agent a) {
        this.a = a;
    }

    abstract void tick(ServerLevel level);
}
