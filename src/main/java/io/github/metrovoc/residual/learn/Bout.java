package io.github.metrovoc.residual.learn;

import java.util.Random;

/**
 * A simulated fight in an Arena: a model plays the mob, and a sparring frame comes after it. How things stand reads as a real
 * mob's lines do, so at every look the model is asked what the mob does next, and what it picks is what happens. A bout lasts
 * {@link #LOOKS} looks whatever it does, and then the mob falls.
 */
public final class Bout {
    /** Twelve seconds at four looks a second. */
    public static final int LOOKS = 48;
    /** How near the frame comes, how often it strikes, and how far a blow throws the mob. */
    private static final double REACH = 2.5, KNOCK = 1.2;
    private static final int STRIKES_EVERY = 6;
    /** How far the mob and the frame go in a look, and how far apart they can get. */
    private static final double STEP = 0.8, LEAP = 1.6, FRAME_STEP = 0.5, CLOSEST = 1, FARTHEST = 12;
    /** How far round the frame a step aside takes it, in turns. */
    private static final double AROUND = 0.06;

    private final StringBuilder line;
    private final boolean targeting;
    private double distance;
    private double turn;
    private boolean hurt;
    private boolean struck;
    private char action = Imitation.STILL;
    private int look;
    private int sinceStrike;

    public Bout(char kind, boolean targeting, Random rng) {
        line = new StringBuilder().append(kind);
        this.targeting = targeting;
        distance = 4 + 6 * rng.nextDouble();
        turn = rng.nextDouble();
    }

    public char state() {
        return Imitation.state(distance, targeting, hurt);
    }

    /** What the model reads to say what the mob does next. */
    public String prompt() {
        return Imitation.prompt(line, state());
    }

    /** The mob does {@code what}; then the frame comes on, and strikes if it can. */
    public void act(char what, Random rng) {
        line.append(state()).append(what);
        action = what;
        switch (what) {
            case Imitation.CLOSER -> distance -= STEP;
            case Imitation.AWAY -> distance += STEP;
            case Imitation.ASIDE -> turn += rng.nextBoolean() ? AROUND : -AROUND;
            case Imitation.LEAP -> distance -= LEAP;
            case Imitation.BLINK -> {
                distance = 2 + 8 * rng.nextDouble();
                turn = rng.nextDouble();
            }
            default -> {
            }
        }
        look++;
        sinceStrike++;
        hurt = false;
        struck = false;
        if (distance > REACH) {
            distance -= FRAME_STEP;
        } else if (sinceStrike >= STRIKES_EVERY) {
            sinceStrike = 0;
            struck = true;
            hurt = true;
            distance += KNOCK;
        }
        distance = Math.clamp(distance, CLOSEST, FARTHEST);
    }

    public boolean over() {
        return look >= LOOKS;
    }

    /** The whole of it so far, as a Mob Log would have it written. */
    public String line() {
        return line.toString();
    }

    public int look() {
        return look;
    }

    public double distance() {
        return distance;
    }

    /** Where it stands around the frame, in turns. */
    public double turn() {
        return turn;
    }

    /** What the mob did at the last look. */
    public char action() {
        return action;
    }

    public boolean hurt() {
        return hurt;
    }

    /** Whether the frame struck at the last look. */
    public boolean struck() {
        return struck;
    }
}
