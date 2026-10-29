package io.github.metrovoc.residual.apprentice;

import io.github.metrovoc.residual.learn.Ability;
import java.util.Locale;
import org.jetbrains.annotations.Nullable;

/** The standing work an apprentice does, and the ability its mind is asked for while doing it. */
public enum Job {
    REST(null), FARM(Ability.REACTING), STORE(Ability.RECALL), HAUL(Ability.REVERSAL), BUILD(Ability.INDUCTION);

    public final String id = name().toLowerCase(Locale.ROOT);
    @Nullable
    public final Ability ability;

    Job(@Nullable Ability ability) {
        this.ability = ability;
    }
}
