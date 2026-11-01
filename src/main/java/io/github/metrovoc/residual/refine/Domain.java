package io.github.metrovoc.residual.refine;

import java.util.Locale;
import java.util.Map;

/** The kinds of writing a Refinery mixes, by the part of the archive each record was filed in. */
public enum Domain {
    LETTERS, TALES, KNOW_HOW, TALLIES, TOWN, SCHOOL, PROGRAMS;

    public final String id = name().toLowerCase(Locale.ROOT);
    /** The domains the archive's records are filed in; programs write the last. */
    public static final int WRITTEN = PROGRAMS.ordinal();

    private static final Map<String, Domain> FILED = Map.ofEntries(
            Map.entry("letters", LETTERS), Map.entry("journals", LETTERS), Map.entry("late", LETTERS), Map.entry("notes", LETTERS),
            Map.entry("relay-messages", LETTERS), Map.entry("invitations", LETTERS), Map.entry("toasts", LETTERS),
            Map.entry("letters-to-the-archive", LETTERS), Map.entry("relay-house-logs", LETTERS),
            Map.entry("stories", TALES), Map.entry("songs", TALES), Map.entry("fables", TALES), Map.entry("tall-tales", TALES),
            Map.entry("lullabies", TALES), Map.entry("rhymes", TALES), Map.entry("riddles", TALES), Map.entry("jokes", TALES),
            Map.entry("plays", TALES), Map.entry("dialogues", TALES),
            Map.entry("household", KNOW_HOW), Map.entry("manuals", KNOW_HOW), Map.entry("recipes", KNOW_HOW), Map.entry("remedies", KNOW_HOW),
            Map.entry("how-tos", KNOW_HOW), Map.entry("directions", KNOW_HOW), Map.entry("almanacs", KNOW_HOW),
            Map.entry("field-notes", KNOW_HOW), Map.entry("primers", KNOW_HOW),
            Map.entry("drill-records", TALLIES), Map.entry("lists", TALLIES), Map.entry("inventories", TALLIES), Map.entry("prices", TALLIES),
            Map.entry("ledgers", TALLIES), Map.entry("bills", TALLIES), Map.entry("timetables", TALLIES), Map.entry("counts", TALLIES),
            Map.entry("registers", TALLIES), Map.entry("finding-book", TALLIES), Map.entry("sums", TALLIES), Map.entry("score-cards", TALLIES),
            Map.entry("notices", TOWN), Map.entry("rules", TOWN), Map.entry("minutes", TOWN), Map.entry("petitions", TOWN),
            Map.entry("disputes", TOWN), Map.entry("agreements", TOWN), Map.entry("wanted", TOWN), Map.entry("speeches", TOWN),
            Map.entry("remembrances", TOWN), Map.entry("chronicles", TOWN),
            Map.entry("school-work", SCHOOL), Map.entry("questions", SCHOOL), Map.entry("word-lists", SCHOOL), Map.entry("lectures", SCHOOL),
            Map.entry("essays", SCHOOL), Map.entry("reviews", SCHOOL), Map.entry("interviews", SCHOOL));

    /** The domain of a record filed under {@code kind}, the archive file's name without its number. */
    public static Domain filed(String kind) {
        Domain d = FILED.get(kind);
        if (d == null) {
            throw new IllegalArgumentException("no domain for " + kind);
        }
        return d;
    }
}
