package io.github.metrovoc.residual.refine;

import io.github.metrovoc.residual.learn.Archive;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * What a fallen mast's box held: everything its relay passed on, as it came down the line. Records whole, and the same records
 * again and again, because a message was sent until it was heard; some spoiled by bad lines; the carrier between messages; and
 * the lines each keeper put round what passed through. It is written from its seed, so a dump always holds the same text.
 */
public final class Dump {
    /** Records a dump holds clean; the rest of it comes along with them. */
    public static final int RECORDS = 80;
    private static final String[] HEADS = {"Passed on by the relay at %s, for whoever keeps the next lamp.", "From the keeper at %s. Pass it on as it stands.",
            "Sent down the line from %s. Send it again if it is not heard."};
    private static final String[] TAILS = {"End of message. Keep your lamp lit.", "That is all of it. Pass it on before the next bell.",
            "End. Answer if you have it. We will send it again if you do not."};
    private static final String[] PLACES = {"Northpoint", "Crow Hill", "Stone Ring", "Kettle Ridge", "Lowmarsh", "Saltreach", "Copperhold", "Three Wells", "Ambrook", "Vell"};

    /** What happened to one piece of a dump, kept so a test can say which stage caught what. */
    public enum Fate { CLEAN, COPIED, RESENT, GARBLED, SCRAMBLED, CARRIER }

    public record Piece(Doc doc, Fate fate) {
    }

    private Dump() {
    }

    /** A dump's pieces in the order they lie in it. */
    public static List<Piece> read(long seed) {
        Random rng = new Random(seed);
        List<Archive.Record> all = new ArrayList<>();
        for (Archive.Record r : Archive.records()) {
            if (!Archive.heldOut(r.id())) {
                all.add(r);
            }
        }
        List<Piece> out = new ArrayList<>();
        // spoiled copies are of other records than the clean ones: nothing of them comes through to be kept
        for (int i = 0; i < RECORDS; i++) {
            Archive.Record r = all.get(rng.nextInt(all.size()));
            Domain domain = Domain.filed(r.filed());
            String clean = wrap(r.text(), rng);
            out.add(new Piece(new Doc(clean, domain), Fate.CLEAN));
            // sent until it was heard
            int again = rng.nextInt(4);
            for (int k = 0; k < again; k++) {
                out.add(rng.nextBoolean() ? new Piece(new Doc(clean, domain), Fate.COPIED)
                        : new Piece(new Doc(wrap(Dirt.nudge(r.text(), 0.03, rng), rng), domain), Fate.RESENT));
            }
            if (rng.nextFloat() < 0.5f) {
                Archive.Record other = all.get(rng.nextInt(all.size()));
                double rate = Dirt.GARBLE_LEAST + (Dirt.GARBLE_MOST - Dirt.GARBLE_LEAST) * rng.nextDouble();
                out.add(new Piece(new Doc(wrap(Dirt.garble(other.text(), rate, rng), rng), Domain.filed(other.filed())), Fate.GARBLED));
            }
            if (rng.nextFloat() < 0.5f) {
                Archive.Record other = all.get(rng.nextInt(all.size()));
                out.add(new Piece(new Doc(wrap(Dirt.scramble(other.text(), rng), rng), Domain.filed(other.filed())), Fate.SCRAMBLED));
            }
            if (rng.nextFloat() < 0.3f) {
                out.add(new Piece(new Doc(Dirt.carrier(200 + rng.nextInt(1000), rng), domain), Fate.CARRIER));
            }
        }
        Collections.shuffle(out, rng);
        return out;
    }

    /** A dump's text as it lies, each piece a stretch of its own. */
    public static List<String> texts(long seed) {
        List<String> out = new ArrayList<>();
        read(seed).forEach(p -> out.addAll(p.doc().parts()));
        return out;
    }

    /** The lines a keeper put round a message. */
    private static String wrap(String text, Random rng) {
        return HEADS[rng.nextInt(HEADS.length)].formatted(PLACES[rng.nextInt(PLACES.length)]) + "\n" + text + "\n" + TAILS[rng.nextInt(TAILS.length)];
    }
}
