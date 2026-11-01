package io.github.metrovoc.residual.bench;

import java.util.Locale;
import org.jetbrains.annotations.Nullable;

/**
 * A machine that joins a Model Core by touching it, directly or through accelerators and other units, and opens a part
 * of its recipe. The Fabricator makes each from a photomask of its spec.
 */
public enum Unit {
    SCHEDULER("schedule"), BF16_UNIT("bf16"), FP8_UNIT("fp8"), FP4_UNIT("fp4"), ORTHOGONALIZER("orthogonalize"), PACKER("document_mask");

    public final String id = name().toLowerCase(Locale.ROOT);
    public final String spec;

    Unit(String spec) {
        this.spec = spec;
    }

    /** The unit a design of {@code spec} makes, or null. */
    @Nullable
    public static Unit of(Spec spec) {
        for (Unit u : values()) {
            if (u.spec.equals(spec.id())) {
                return u;
            }
        }
        return null;
    }
}
