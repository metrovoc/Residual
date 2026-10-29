package io.github.metrovoc.residual.learn;

import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * The language of orders: many ways of saying a few things. A mind reads {@code > what was said} and writes
 * {@code = what was meant}; an apprentice acts only on what its mind wrote.
 */
public final class Orders {
    /** Everything an order can mean. Only {@code fetch} takes something after it. */
    public enum Meaning { FARM, STORE, FETCH, COME, STAY, BUILD, HAUL, HOME }

    public record Example(String said, String meant) {
    }

    /** What a mind's answer means, or null if it means nothing an apprentice knows. */
    public record Command(Meaning meaning, String thing) {
    }

    /** The longest order a mind is shown. */
    public static final int LONGEST = 40;

    private static final List<String> FETCH = List.of(
            "bring me %s", "bring me some %s", "bring me the %s", "fetch %s", "fetch me %s", "fetch me some %s",
            "fetch the %s", "get me %s", "get me some %s", "go get %s", "go get some %s", "go and get the %s",
            "go and fetch %s", "i need %s", "i need some %s", "i need the %s", "i want %s", "i want some %s",
            "can you bring me %s", "can you get me some %s", "could you fetch %s", "could you bring the %s",
            "please bring %s", "please get me %s", "%s please", "some %s please", "find me some %s", "find the %s",
            "we need %s", "we need more %s", "hand me the %s", "hand me some %s", "grab me %s", "grab some %s",
            "get the %s from the chest", "bring the %s over here", "carry some %s to me", "bring %s here",
            "go find %s", "i could use some %s", "bring me a stack of %s", "go fetch the %s for me",
            "run and get some %s", "fetch %s from storage", "i am out of %s", "we are out of %s", "get %s",
            "bring %s");
    private static final List<List<String>> PLAIN = List.of(
            List.of("farm", "go farm", "go and farm", "tend the fields", "tend the crops", "look after the crops",
                    "look after the fields", "work the fields", "work the farm", "see to the wheat", "see to the crops",
                    "harvest the field", "harvest the crops", "keep the fields", "mind the crops", "back to the fields",
                    "go to the fields", "go work the farm", "take care of the farm", "do the farming", "go harvest",
                    "time to farm", "the crops need you", "watch the fields"),
            List.of("store", "store everything", "store it all", "put this away", "put these away", "put the things away",
                    "put everything away", "sort the chests", "sort the storage", "sort the stores", "tidy the stores",
                    "tidy the chests", "tidy up", "clean up the chests", "go sort things", "put your things in the chests",
                    "empty the barrel", "file everything", "put it all in the chests", "stock the shelves",
                    "sort it out", "do the sorting"),
            List.of(),
            List.of("come", "come here", "come to me", "come with me", "come along", "follow me", "follow", "over here",
                    "here please", "walk with me", "keep up with me", "stay close", "this way", "come on", "with me",
                    "to me", "come over here", "come find me"),
            List.of("stay", "stay here", "stay put", "wait", "wait here", "wait for me", "stop", "stop there", "hold on",
                    "hold still", "stand still", "do not move", "dont move", "halt", "rest", "take a rest", "sit tight",
                    "wait right there", "stay where you are"),
            List.of("build", "keep building", "carry on building", "go on building", "finish the wall", "finish the row",
                    "continue the pattern", "continue the wall", "build the rest", "build the rest of the row",
                    "go on with the wall", "lay the next blocks", "follow the pattern", "keep the pattern going",
                    "build on", "extend the wall", "add to the wall", "back to building"),
            List.of("haul", "go haul", "haul the goods", "run the errand", "do the errand", "do the run", "make the run",
                    "make the trip", "go on your errand", "bring the goods back", "bring back the load", "fetch the load",
                    "go to the pickup", "carry the goods home", "start hauling", "do your rounds", "go collect"),
            List.of("home", "go home", "go back home", "return home", "head home", "back to your post",
                    "go back to your post", "back home", "go back", "go to your home", "return to your post",
                    "off you go home", "back to base"));
    private static final List<String> NAMES = List.of("pip", "clank", "moss", "lantern", "bolt", "tinker", "wick",
            "pebble", "copper", "apprentice");
    private static final List<String> BEFORE = List.of("", "", "", "", "please ", "now ", "ok ", "hey ", "%s, ",
            "%s ", "right, ", "and ", "quick, ", "listen, ");
    private static final List<String> AFTER = List.of("", "", "", "", " please", " now", " thanks", " thank you",
            ", please", " quickly", " for me", "!", ".", " right now", " when you can");
    /** Things to fetch. Every fifth one never appears in practice, only in the test. */
    static final List<String> THINGS = List.of(
            "bread", "wheat", "wheat seeds", "seeds", "carrots", "potatoes", "baked potatoes", "beetroots",
            "beetroot seeds", "apples", "golden apples", "melon slices", "pumpkins", "pumpkin pie", "sugar",
            "sugar cane", "eggs", "milk", "cake", "cookies", "honey", "honeycomb", "sweet berries", "mushrooms",
            "mushroom stew", "rabbit stew", "cod", "salmon", "cooked cod", "cooked salmon", "beef", "steak",
            "porkchops", "chicken", "mutton", "kelp", "dried kelp", "coal", "charcoal", "iron ingots",
            "iron nuggets", "gold ingots", "gold nuggets", "copper ingots", "raw iron", "raw copper", "raw gold",
            "diamonds", "emeralds", "redstone", "lapis", "quartz", "amethyst shards", "flint", "clay", "clay balls",
            "bricks", "glass", "glass panes", "sand", "red sand", "gravel", "dirt", "cobblestone", "stone",
            "deepslate", "cobbled deepslate", "andesite", "granite", "diorite", "oak logs", "birch logs",
            "spruce logs", "jungle logs", "acacia logs", "dark oak logs", "oak planks", "spruce planks",
            "birch planks", "sticks", "torches", "ladders", "chests", "barrels", "rails", "buckets",
            "water buckets", "shears", "pickaxe", "axe", "shovel", "hoe", "sword", "bow", "arrows", "string", "wool",
            "white wool", "leather", "feathers", "bones", "bone meal", "paper", "books", "ink sacs", "slime balls",
            "gunpowder", "snowballs", "ice", "packed ice", "lanterns", "candles", "saddles", "leads", "name tags",
            "flowers", "dandelions", "poppies", "oak saplings", "cactus", "vines", "lily pads");

    private Orders() {
    }

    private static final String[] SYLLABLES = {"ba", "be", "bo", "da", "di", "do", "fa", "fi", "ga", "go", "ka", "ki",
            "ko", "la", "li", "lo", "ma", "mi", "mo", "na", "ni", "no", "pa", "pi", "po", "ra", "ri", "ro", "sa", "si",
            "so", "ta", "ti", "to", "va", "vi", "wa", "wi", "za", "zo"};

    /**
     * One order in a random wording, short enough to be shown whole. {@code test} draws the things to fetch from the
     * ones practice never shows; practice also asks for made-up things, so a mind learns to copy any name it hears.
     */
    public static Example example(Random rng, boolean test) {
        while (true) {
            Meaning m = rng.nextFloat() < 0.35f ? Meaning.FETCH : Meaning.values()[rng.nextInt(Meaning.values().length)];
            String core;
            String meant;
            if (m == Meaning.FETCH) {
                String thing = !test && rng.nextFloat() < 0.4f ? madeUp(rng) : thing(rng, test);
                core = FETCH.get(rng.nextInt(FETCH.size())).formatted(thing);
                meant = "fetch " + thing;
            } else {
                List<String> ways = PLAIN.get(m.ordinal());
                core = ways.get(rng.nextInt(ways.size()));
                meant = m.name().toLowerCase(Locale.ROOT);
            }
            String said = BEFORE.get(rng.nextInt(BEFORE.size())).formatted(NAMES.get(rng.nextInt(NAMES.size())))
                    + core + AFTER.get(rng.nextInt(AFTER.size()));
            if (said.length() <= LONGEST) {
                return new Example(said, meant);
            }
        }
    }

    private static String thing(Random rng, boolean test) {
        while (true) {
            int i = rng.nextInt(THINGS.size());
            if ((i % 5 == 4) == test) {
                return THINGS.get(i);
            }
        }
    }

    /** A word that names nothing, like "kodari" or "mipo sanis". */
    private static String madeUp(Random rng) {
        StringBuilder sb = new StringBuilder();
        int words = rng.nextFloat() < 0.25f ? 2 : 1;
        for (int w = 0; w < words; w++) {
            if (w > 0) {
                sb.append(' ');
            }
            int n = 2 + rng.nextInt(2);
            for (int i = 0; i < n; i++) {
                sb.append(SYLLABLES[rng.nextInt(SYLLABLES.length)]);
            }
            if (rng.nextFloat() < 0.3f) {
                sb.append(rng.nextBoolean() ? 's' : 'n');
            }
        }
        return sb.toString();
    }

    /** What a mind is shown for an order, in the format it practised. */
    public static String prompt(String said) {
        return "> " + clean(said) + "\n= ";
    }

    /** Lower case, only characters minds know, single spaces, at most {@link #LONGEST} characters. */
    public static String clean(String said) {
        StringBuilder sb = new StringBuilder();
        for (char c : said.toLowerCase(Locale.ROOT).toCharArray()) {
            if (Character.isWhitespace(c)) {
                c = ' ';
            }
            if (Vocab.WORLD.has(c) && c != '\n' && c != '>' && c != '=' && c != '|' && !(c == ' ' && (sb.isEmpty()
                    || sb.charAt(sb.length() - 1) == ' '))) {
                sb.append(c);
            }
        }
        String s = sb.toString().strip();
        return s.length() > LONGEST ? s.substring(0, LONGEST).strip() : s;
    }

    /** Reads what a mind wrote after {@code =}. */
    public static Command parse(String meant) {
        String s = meant.strip();
        for (Meaning m : Meaning.values()) {
            String word = m.name().toLowerCase(Locale.ROOT);
            if (m == Meaning.FETCH) {
                if (s.startsWith("fetch ") && s.length() > 6) {
                    return new Command(m, s.substring(6).strip());
                }
            } else if (s.equals(word)) {
                return new Command(m, "");
            }
        }
        return null;
    }
}
