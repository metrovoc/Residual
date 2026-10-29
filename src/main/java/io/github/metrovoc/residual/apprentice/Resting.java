package io.github.metrovoc.residual.apprentice;

import net.minecraft.server.level.ServerLevel;

/** Stands where it is until it is given work. */
final class Resting extends Work {
    Resting(Apprentice a) {
        super(a);
    }

    @Override
    void tick(ServerLevel level) {
        a.status("residual.apprentice.status.resting");
        a.pause(20);
    }
}
