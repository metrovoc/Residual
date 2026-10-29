package io.github.metrovoc.residual.learn;

import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * The language of orders: many ways of saying what an agent can do. A model reads {@code > what was said} and writes
 * {@code = the program meant}, in the language agents run ({@code fetch bread}, {@code harvest, put wheat},
 * {@code wait until night, home}); an agent runs only what its model wrote.
 */
public final class Orders {
    public record Example(String said, String meant) {
    }

    /** The longest order a model is shown. */
    public static final int LONGEST = 40;
    /** The longest program a model writes for an order. */
    public static final int WRITTEN = 40;
    /** An order and its program fit one window together, so the whole order is in view to the last letter written. */
    private static final int FITS = Vocab.CONTEXT - "> \n= \n".length() + 1;

    /** One step said a way, and the step meant. In both, %1$s stands for a thing or words, %2$s for a number. */
    private record Way(String said, String meant) {
    }

    private enum Arg { NONE, THING, SEED, BLOCK, WORDS, SECONDS, MARK, COUNT }

    /** Ways of saying one kind of step, and what each needs filled in. */
    private record Step(Arg arg, boolean endless, List<Way> ways) {
    }

    private static List<Way> ways(String meant, String... said) {
        return java.util.Arrays.stream(said).map(s -> new Way(s, meant)).toList();
    }

    private static final Step FARM = new Step(Arg.NONE, true, ways("repeat (farm)",
            "farm", "go farm", "go and farm", "tend the fields", "tend the crops", "look after the crops",
            "look after the fields", "work the fields", "work the farm", "see to the crops", "keep the fields",
            "back to the fields", "go to the fields", "go work the farm", "take care of the farm", "do the farming",
            "time to farm", "the crops need you", "watch the fields", "mind the farm"));
    private static final Step STORE = new Step(Arg.NONE, true, ways("repeat (store)",
            "store", "store everything", "tidy the stores", "tidy the chests", "tidy up", "clean up the chests",
            "empty the barrels", "empty the barrel", "file everything", "stock the shelves", "keep the stores tidy",
            "mind the stores", "keep the storeroom tidy", "keep things where they were"));
    private static final Step SORT = new Step(Arg.NONE, true, ways("repeat (sort)",
            "sort", "sort everything", "sort the chests", "sort the storage", "sort the stores", "go sort things",
            "sort it out", "do the sorting", "sort things by kind", "sort the barrels", "keep things sorted",
            "sort the loot", "sort by kind", "put each thing with its kind", "file things by kind"));
    private static final Step BUILD = new Step(Arg.NONE, true, ways("repeat (build)",
            "build", "keep building", "carry on building", "go on building", "finish the wall", "finish the row",
            "continue the pattern", "continue the wall", "build the rest", "build the rest of the row",
            "go on with the wall", "lay the next blocks", "follow the pattern", "keep the pattern going", "build on",
            "extend the wall", "add to the wall", "back to building"));
    private static final Step HAUL = new Step(Arg.NONE, true, ways("repeat (haul)",
            "haul", "go haul", "haul the goods", "run the errand", "do the errand", "do the run", "make the run",
            "make the trip", "go on your errand", "bring the goods back", "bring back the load", "fetch the load",
            "go to the pickup", "carry the goods home", "start hauling", "do your rounds", "go collect"));
    private static final Step STAY = new Step(Arg.NONE, true, ways("stay",
            "stay", "stay here", "stay put", "wait", "wait here", "wait for me", "stop", "stop there", "hold on",
            "hold still", "stand still", "do not move", "dont move", "halt", "rest", "take a rest", "sit tight",
            "wait right there", "stay where you are"));
    private static final Step FOLLOW = new Step(Arg.NONE, false, ways("follow",
            "follow me", "follow", "come with me", "come along", "walk with me", "keep up with me", "stay close",
            "with me", "stick with me", "tag along", "keep close", "on my heels"));
    private static final Step COME = new Step(Arg.NONE, false, ways("come",
            "come", "come here", "come to me", "over here", "here please", "this way", "come on", "to me",
            "come over here", "come find me", "get over here", "come back here", "i need you here"));
    private static final Step HOME = new Step(Arg.NONE, false, ways("home",
            "home", "go home", "go back home", "return home", "head home", "back to your post", "go back to your post",
            "back home", "go back", "go to your home", "return to your post", "off you go home", "back to base"));
    private static final Step HARVEST = new Step(Arg.NONE, false, ways("harvest",
            "harvest", "harvest the field", "harvest the crops", "harvest the wheat", "go harvest", "reap the field",
            "reap the wheat", "pick the crops", "bring in the harvest", "gather the crops", "cut the wheat",
            "pick what is ripe", "harvest what is ripe"));
    private static final Step TILL = new Step(Arg.NONE, false, ways("till",
            "till", "till the soil", "till the ground", "hoe the ground", "hoe the soil", "make farmland",
            "plough the field", "plow the field", "turn the soil", "dig the field", "get the soil ready",
            "make a field"));
    private static final Step PICKUP = new Step(Arg.NONE, false, ways("pickup",
            "pick up", "pick it up", "pick that up", "pick up the items", "pick up the drops", "collect the drops",
            "gather the loot", "grab what is on the ground", "clean up the floor", "pick up everything",
            "collect what is lying around", "get the stuff on the ground"));
    private static final Step PUT = new Step(Arg.NONE, false, ways("put",
            "put this away", "put these away", "put the things away", "put everything away", "put your things away",
            "put it in the chest", "put it all in the chest", "unload", "unload your things", "empty your hands",
            "drop off what you carry", "drop it off", "store what you carry", "put what you have away"));
    private static final Step PUT_THING = new Step(Arg.THING, false, ways("put %1$s",
            "put the %1$s away", "put away the %1$s", "store the %1$s", "put the %1$s in the chest",
            "put your %1$s away", "drop off the %1$s", "unload the %1$s", "stash the %1$s"));
    private static final Step FETCH = new Step(Arg.THING, false, ways("fetch %1$s",
            "bring me %1$s", "bring me some %1$s", "bring me the %1$s", "fetch %1$s", "fetch me %1$s",
            "fetch me some %1$s", "fetch the %1$s", "get me %1$s", "get me some %1$s", "go get %1$s",
            "go get some %1$s", "go and get the %1$s", "go and fetch %1$s", "i need %1$s", "i need some %1$s",
            "i need the %1$s", "i want %1$s", "i want some %1$s", "can you bring me %1$s", "can you get me some %1$s",
            "could you fetch %1$s", "could you bring the %1$s", "please bring %1$s", "please get me %1$s",
            "%1$s please", "some %1$s please", "find me some %1$s", "find the %1$s", "we need %1$s",
            "we need more %1$s", "hand me the %1$s", "hand me some %1$s", "grab me %1$s", "grab some %1$s",
            "get the %1$s from the chest", "bring the %1$s over here", "carry some %1$s to me", "bring %1$s here",
            "go find %1$s", "i could use some %1$s", "go fetch the %1$s for me", "run and get some %1$s",
            "fetch %1$s from storage", "i am out of %1$s", "we are out of %1$s", "get %1$s", "bring %1$s"));
    private static final Step FETCH_SOME = new Step(Arg.COUNT, false, ways("fetch %2$s %1$s",
            "bring me %2$s %1$s", "fetch me %2$s %1$s", "get me %2$s %1$s", "i need %2$s %1$s", "grab me %2$s %1$s",
            "%2$s %1$s please", "can you bring me %2$s %1$s", "go get %2$s %1$s", "fetch %2$s %1$s",
            "i want %2$s %1$s", "hand me %2$s %1$s"));
    private static final Step PLANT = new Step(Arg.SEED, false, ways("plant %1$s",
            "plant %1$s", "plant the %1$s", "plant some %1$s", "sow the %1$s", "sow %1$s", "sow some %1$s",
            "put the %1$s in the ground", "plant the field with %1$s", "get the %1$s planted", "plant out the %1$s"));
    private static final Step MINE = new Step(Arg.BLOCK, false, ways("mine %1$s",
            "mine some %1$s", "mine the %1$s", "mine %1$s", "dig up some %1$s", "dig out the %1$s", "break the %1$s",
            "break some %1$s", "go mine %1$s", "quarry some %1$s"));
    private static final Step WAIT = new Step(Arg.SECONDS, false, ways("wait %2$s",
            "wait %2$s seconds", "wait for %2$s seconds", "hold on %2$s seconds", "pause for %2$s seconds",
            "give it %2$s seconds", "wait %2$s"));
    private static final Step GO = new Step(Arg.MARK, false, ways("go %2$s",
            "go to mark %2$s", "go to waymark %2$s", "walk to mark %2$s", "head to mark %2$s", "move to mark %2$s",
            "go over to mark %2$s", "stand on mark %2$s", "go stand at mark %2$s"));
    private static final Step SAY = new Step(Arg.WORDS, false, ways("say %1$s",
            "say %1$s", "say %1$s out loud", "tell everyone %1$s", "shout %1$s", "announce %1$s", "call out %1$s"));

    /** Every single step, the commonest first in the draw: fetching. */
    private static final List<Step> SINGLE = List.of(FETCH, FETCH_SOME, FARM, STORE, SORT, BUILD, HAUL, STAY, FOLLOW, COME,
            HOME, HARVEST, TILL, PICKUP, PUT, PUT_THING, PLANT, MINE, WAIT, GO, SAY);
    /** Steps that end, which may be put one after another. */
    private static final List<Step> FINITE = List.of(FETCH, FETCH_SOME, COME, HOME, HARVEST, TILL, PICKUP, PUT,
            PUT_THING, PLANT, MINE, WAIT, GO, SAY);
    private static final List<String> THEN = List.of("%s and %s", "%s then %s", "%s, then %s", "first %s, then %s",
            "%s and then %s", "%s, and %s");

    /** Orders that check or wait before they act. */
    private static final List<Way> RIPE = List.of(
            new Way("if the crops are ripe, harvest them", "if ripe (harvest)"),
            new Way("harvest if anything is ripe", "if ripe (harvest)"),
            new Way("if anything is ripe, harvest it", "if ripe (harvest)"),
            new Way("if the wheat is ready, harvest it", "if ripe (harvest)"),
            new Way("harvest the crops if they are ripe", "if ripe (harvest)"),
            new Way("when the crops are ripe, harvest them", "wait until ripe, harvest"),
            new Way("harvest when the wheat is ready", "wait until ripe, harvest"),
            new Way("wait for the crops, then harvest", "wait until ripe, harvest"),
            new Way("keep harvesting", "repeat (harvest)"),
            new Way("harvest all day", "repeat (harvest)"),
            new Way("keep picking the crops", "repeat (harvest)"),
            new Way("harvest until your hands are full", "until full (harvest)"),
            new Way("harvest till you are full", "until full (harvest)"));
    private static final List<Way> NIGHT = List.of(
            new Way("when it gets dark, go home", "wait until night, home"),
            new Way("go home at night", "wait until night, home"),
            new Way("come home when night falls", "wait until night, home"),
            new Way("at nightfall go home", "wait until night, home"),
            new Way("when night comes, go home", "wait until night, home"),
            new Way("go home when it is dark", "wait until night, home"),
            new Way("come to me at night", "wait until night, come"),
            new Way("when it gets dark, come to me", "wait until night, come"),
            new Way("find me when it is dark", "wait until night, come"));
    private static final List<Way> PICKING = List.of(
            new Way("keep picking things up", "repeat (pickup)"),
            new Way("keep collecting the drops", "repeat (pickup)"),
            new Way("pick things up until you are full", "until full (pickup)"),
            new Way("collect drops till your hands are full", "until full (pickup)"));
    private static final List<String> IF_STOCKED = List.of("bring me %s if there is any", "if we have %s, bring me some",
            "if there is %s, fetch it", "fetch %s if we have any", "if there is any %s, bring it",
            "get me %s if we have some");

    private static final List<String> NAMES = List.of("pip", "clank", "moss", "lantern", "bolt", "tinker", "wick",
            "pebble", "copper", "agent");
    private static final List<String> BEFORE = List.of("", "", "", "", "please ", "now ", "ok ", "hey ", "%s, ",
            "%s ", "right, ", "and ", "quick, ", "listen, ");
    private static final List<String> AFTER = List.of("", "", "", "", " please", " now", " thanks", " thank you",
            ", please", " quickly", " for me", "!", ".", " right now", " when you can");
    /** Things to fetch and put away. Every fifth one never appears in practice, only in the test. */
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
    /** Things to plant, held out the same way. */
    static final List<String> SEEDS = List.of("wheat seeds", "seeds", "carrots", "potatoes", "beetroot seeds",
            "melon seeds", "pumpkin seeds", "torchflower seeds", "wheat", "potato");
    /** Blocks to mine, held out the same way. */
    static final List<String> BLOCKS = List.of("stone", "cobblestone", "dirt", "sand", "gravel", "clay", "coal ore",
            "iron ore", "copper ore", "gold ore", "deepslate", "andesite", "granite", "diorite", "oak logs",
            "birch logs", "spruce logs", "logs", "netherrack", "snow", "ice", "glass", "terracotta", "sandstone",
            "obsidian");
    /** Words to say, held out the same way. */
    static final List<String> WORDS = List.of("hello", "good morning", "dinner is ready", "all done", "hi there",
            "on my way", "time to eat", "welcome home", "well done", "nice work", "good night", "the wheat is in",
            "help", "hurry up", "over here", "back soon", "look out", "thank you", "watch this", "happy birthday");
    /** Every fifth name of each list, held out of practice wherever else it appears. */
    private static final Set<String> HELD_OUT = Stream.of(THINGS, SEEDS, BLOCKS, WORDS)
            .flatMap(names -> IntStream.range(0, names.size()).filter(i -> i % 5 == 4).mapToObj(names::get))
            .collect(Collectors.toUnmodifiableSet());
    private static final String[] NUMBERS = {"zero", "one", "two", "three", "four", "five", "six", "seven", "eight",
            "nine", "ten", "eleven", "twelve"};

    private static final String[] SYLLABLES = {"ba", "be", "bo", "da", "di", "do", "fa", "fi", "ga", "go", "ka", "ki",
            "ko", "la", "li", "lo", "ma", "mi", "mo", "na", "ni", "no", "pa", "pi", "po", "ra", "ri", "ro", "sa", "si",
            "so", "ta", "ti", "to", "va", "vi", "wa", "wi", "za", "zo"};

    private Orders() {
    }

    /**
     * One order in a random wording, short enough to be shown whole, and the program it means. {@code test} draws
     * things, seeds, blocks and words from the ones practice never shows; practice also asks for made-up ones, so a
     * model learns to copy any name it hears.
     */
    public static Example example(Random rng, boolean test) {
        while (true) {
            Way core = core(rng, test);
            String said = BEFORE.get(rng.nextInt(BEFORE.size())).formatted(NAMES.get(rng.nextInt(NAMES.size())))
                    + core.said() + AFTER.get(rng.nextInt(AFTER.size()));
            if (said.length() <= LONGEST && core.meant().length() <= WRITTEN && said.length() + core.meant().length() <= FITS) {
                return new Example(said, core.meant());
            }
        }
    }

    /** What is said and meant, before anything is put round it. */
    private static Way core(Random rng, boolean test) {
        float r = rng.nextFloat();
        if (r < 0.62f) {
            Step s = rng.nextFloat() < 0.3f ? (rng.nextBoolean() ? FETCH : FETCH_SOME) : SINGLE.get(rng.nextInt(SINGLE.size()));
            return fill(rng, s, test);
        }
        if (r < 0.82f) {
            Step a = FINITE.get(rng.nextInt(FINITE.size()));
            Step b = FINITE.get(rng.nextInt(FINITE.size()));
            Way first = fill(rng, a, test), second = fill(rng, b, test);
            return new Way(THEN.get(rng.nextInt(THEN.size())).formatted(first.said(), second.said()), first.meant() + ", " + second.meant());
        }
        return switch (rng.nextInt(4)) {
            case 0 -> RIPE.get(rng.nextInt(RIPE.size()));
            case 1 -> NIGHT.get(rng.nextInt(NIGHT.size()));
            case 2 -> PICKING.get(rng.nextInt(PICKING.size()));
            default -> {
                String thing = name(rng, THINGS, test);
                yield new Way(IF_STOCKED.get(rng.nextInt(IF_STOCKED.size())).formatted(thing),
                        "if stocked %s (fetch %s)".formatted(thing, thing));
            }
        };
    }

    /** One way of saying {@code s}, its blanks filled. */
    private static Way fill(Random rng, Step s, boolean test) {
        Way w = s.ways().get(rng.nextInt(s.ways().size()));
        String word = switch (s.arg()) {
            case THING, COUNT -> name(rng, THINGS, test);
            case SEED -> name(rng, SEEDS, test);
            case BLOCK -> name(rng, BLOCKS, test);
            case WORDS -> name(rng, WORDS, test);
            default -> "";
        };
        int n = switch (s.arg()) {
            case SECONDS -> 1 + rng.nextInt(rng.nextBoolean() ? 12 : 60);
            case MARK -> 1 + rng.nextInt(8);
            case COUNT -> 2 + rng.nextInt(rng.nextBoolean() ? 11 : 63);
            default -> 0;
        };
        // small numbers are as often said in words as in digits
        String said = n > 0 && n < NUMBERS.length && rng.nextBoolean() ? NUMBERS[n] : String.valueOf(n);
        return new Way(w.said().formatted(word, said), w.meant().formatted(word, n));
    }

    /** A name from {@code names}, a held-out one for the test; practice also makes names up. */
    private static String name(Random rng, List<String> names, boolean test) {
        if (!test && rng.nextFloat() < 0.4f) {
            return madeUp(rng);
        }
        while (true) {
            String n = names.get(rng.nextInt(names.size()));
            if (HELD_OUT.contains(n) == test) {
                return n;
            }
        }
    }

    /** A word that names nothing, like "kodari" or "mipo sanis". */
    static String madeUp(Random rng) {
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

    /** What a model is shown for an order, in the format it practised. */
    public static String prompt(String said) {
        return "> " + clean(said) + "\n= ";
    }

    /** Lower case, only characters models know, single spaces, at most {@link #LONGEST} characters. */
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
}
