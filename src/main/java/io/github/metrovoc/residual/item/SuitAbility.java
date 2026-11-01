package io.github.metrovoc.residual.item;

import java.util.EnumSet;
import java.util.Locale;

/**
 * What an exosuit can do, cheapest first, with the compute each draws and the power it spends a tick while it runs (the
 * flight only while its wearer flies, vein mining by the block instead). A two-block transformer 32 wide carries the two
 * cheapest, one 48 wide vein mining, one 64 wide the flight, and only one 96 wide all five, which fits on the board at 4 bits
 * alone (systems.md).
 */
public enum SuitAbility {
    NIGHT_VISION(16_000, 16),
    STEP_ASSIST(16_000, 16),
    REACH(32_000, 32),
    VEIN_MINING(64_000, 0),
    FLIGHT(112_000, 320);

    public final String id = name().toLowerCase(Locale.ROOT);
    public final long compute;
    public final int power;

    SuitAbility(long compute, int power) {
        this.compute = compute;
        this.power = power;
    }

    /**
     * What runs on {@code compute}: those not turned off in {@code off}, a bit each, cheapest first as far as it goes. Vein
     * mining also takes a model that passes Survey in the form it runs in.
     */
    public static EnumSet<SuitAbility> running(long compute, int off, boolean survey) {
        EnumSet<SuitAbility> out = EnumSet.noneOf(SuitAbility.class);
        for (SuitAbility a : values()) {
            if ((off >> a.ordinal() & 1) == 0 && a.compute <= compute && (a != VEIN_MINING || survey)) {
                out.add(a);
                compute -= a.compute;
            }
        }
        return out;
    }
}
