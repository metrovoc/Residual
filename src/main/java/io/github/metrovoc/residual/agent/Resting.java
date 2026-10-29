package io.github.metrovoc.residual.agent;

import net.minecraft.server.level.ServerLevel;

/** Stands where it is until it is given work. */
final class Resting extends Work {
    Resting(Agent a) {
        super(a);
    }

    @Override
    void tick(ServerLevel level) {
        a.status("residual.agent.status.resting");
        a.pause(20);
    }
}
