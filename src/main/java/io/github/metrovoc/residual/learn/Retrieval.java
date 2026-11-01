package io.github.metrovoc.residual.learn;

import io.github.metrovoc.residual.graph.CompiledGraph;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

/**
 * What a thing is for, and what someone asking wants: a model reads {@code soul lantern? } or {@code something to light a
 * cave? } and answers with a use's letter. How likely it finds each letter is a point in the space of uses; a thing fits a
 * query as far as their two points agree, the sum over the uses of the products of their likelihoods. So a store's things are
 * placed once each, and a query only has to be placed and compared. The test names things practice never does, real ones held
 * out and made-up ones in a telling form, the way another world's things would be named.
 */
public final class Retrieval {
    /** What a thing can be wanted for, each answered with its letter; other is what a name gives nothing of. */
    public enum Use {
        LIGHT('l'), FOOD('f'), MINE('m'), CHOP('c'), DIG('d'), TILL('t'), FIGHT('k'), ARMOR('a'), FUEL('b'), BUILD('h'), PLANT('p'),
        SMELT('s'), STORE('r'), CIRCUIT('w'), TRAVEL('g'), LIQUID('q'), WRITE('n'), DYE('y'), HEAL('e'), OTHER('x');

        public final char letter;
        public final String id = name().toLowerCase(Locale.ROOT);

        Use(char letter) {
            this.letter = letter;
        }

        /** The use answered with {@code letter}, or null. */
        @Nullable
        public static Use of(char letter) {
            for (Use u : values()) {
                if (u.letter == letter) {
                    return u;
                }
            }
            return null;
        }
    }

    public record Example(String said, Use use) {
    }

    /** Names of one use, every material in every form; {@code %s} in a form stands for the material. */
    private record Family(Use use, List<String> materials, List<String> forms) {
    }

    /** The longest thing a model reads at once. */
    public static final int LONGEST = 30;
    /** How far a thing and a query must agree before a terminal shows the thing: both six parts in ten sure of one use. */
    public static final float SURE = 0.36f;

    /** How someone says they want something done, with what they want in place of {@code %s}. */
    private static final List<String> WANTING = List.of("%s", "something to %s", "something to %s with", "i need to %s", "help me %s", "a way to %s", "how do i %s");
    private static final Map<Use, List<String>> DOING = new EnumMap<>(Use.class);
    /** What someone says who wants a thing, not a deed. */
    private static final Map<Use, List<String>> ASKING = new EnumMap<>(Use.class);
    private static final Map<Use, List<String>> NAMED = new EnumMap<>(Use.class);
    /** The forms that settle a use whatever the material, to make names up with. */
    private static final Map<Use, List<String>> FORMS = new EnumMap<>(Use.class);
    private static final List<String> COLORS = List.of("white", "orange", "magenta", "light blue", "yellow", "lime", "pink", "gray",
            "cyan", "purple", "blue", "brown", "green", "red", "black");
    private static final List<String> WOODS = List.of("oak", "spruce", "birch", "jungle", "acacia", "dark oak", "mangrove", "cherry");
    private static final List<String> TOOL_METALS = List.of("wooden", "stone", "copper", "iron", "golden", "diamond", "netherite");
    private static final List<String> ARMOR_METALS = List.of("leather", "chainmail", "copper", "iron", "golden", "diamond", "netherite");
    private static final List<Family> FAMILIES = List.of(
            new Family(Use.LIGHT, COLORS, List.of("%s candle")),
            new Family(Use.FOOD, List.of("beef", "porkchop", "chicken", "mutton", "rabbit", "cod", "salmon"), List.of("cooked %s")),
            new Family(Use.MINE, TOOL_METALS, List.of("%s pickaxe")),
            new Family(Use.CHOP, TOOL_METALS, List.of("%s axe")),
            new Family(Use.DIG, TOOL_METALS, List.of("%s shovel")),
            new Family(Use.TILL, TOOL_METALS, List.of("%s hoe")),
            new Family(Use.FIGHT, TOOL_METALS, List.of("%s sword")),
            new Family(Use.ARMOR, ARMOR_METALS, List.of("%s helmet", "%s chestplate", "%s leggings", "%s boots")),
            new Family(Use.FUEL, WOODS, List.of("%s log", "stripped %s log")),
            new Family(Use.BUILD, WOODS, List.of("%s planks", "%s slab", "%s stairs", "%s fence")),
            new Family(Use.BUILD, List.of("stone", "cobblestone", "granite", "diorite", "andesite", "deepslate", "sandstone", "brick", "tuff"),
                    List.of("%s slab", "%s stairs", "%s wall")),
            new Family(Use.PLANT, List.of("wheat", "pumpkin", "melon", "beetroot", "torchflower"), List.of("%s seeds")),
            new Family(Use.PLANT, WOODS, List.of("%s sapling")),
            new Family(Use.SMELT, List.of("iron", "gold", "copper"), List.of("raw %s", "%s ore", "deepslate %s ore")),
            new Family(Use.STORE, COLORS, List.of("%s shulker box", "%s bundle")),
            new Family(Use.CIRCUIT, WOODS, List.of("%s button", "%s pressure plate")),
            new Family(Use.TRAVEL, WOODS, List.of("%s boat")),
            new Family(Use.WRITE, WOODS, List.of("%s sign")),
            new Family(Use.DYE, COLORS, List.of("%s dye")));

    /** Every real name here, and what it is for. */
    private static final Map<String, Set<Use>> KNOWN = new HashMap<>();
    private static final Map<Use, List<String>> PRACTISED = new EnumMap<>(Use.class);
    private static final Map<Use, List<String>> HELD_OUT = new EnumMap<>(Use.class);

    static {
        doing(Use.LIGHT, "light", "light a cave", "light the mine", "light up a room", "see in the dark", "brighten a hall", "light my way", "keep mobs away");
        asking(Use.LIGHT, "a light", "a lamp", "something that glows", "light for the cave", "i cant see");
        doing(Use.FOOD, "eat", "feed myself", "cook dinner", "stop being hungry", "fill my stomach");
        asking(Use.FOOD, "food", "a meal", "food for the road", "dinner", "im hungry", "im starving", "lunch");
        doing(Use.MINE, "mine", "mine stone", "mine ore", "break rock", "dig for ore", "get through stone", "mine diamonds");
        asking(Use.MINE, "a pick", "a tool for mining", "mining gear");
        doing(Use.CHOP, "chop", "cut down a tree", "chop wood", "fell the oak", "chop logs", "get some logs", "cut wood");
        asking(Use.CHOP, "a tool for trees", "a tool for chopping", "lumber gear");
        doing(Use.DIG, "dig", "dig dirt", "shovel sand", "clear gravel", "dig a path", "move the snow");
        asking(Use.DIG, "a tool for digging", "a spade", "dirt tool");
        doing(Use.TILL, "till", "till the soil", "make farmland", "work the land", "turn the soil");
        asking(Use.TILL, "a tool for the field", "a farming tool", "soil tool");
        doing(Use.FIGHT, "fight", "fight a zombie", "kill mobs", "defend myself", "hit harder", "hunt", "fight skeletons", "kill the creeper");
        asking(Use.FIGHT, "a weapon", "weapons", "something to fight with", "a blade");
        doing(Use.ARMOR, "protect my head", "take less damage", "keep me safe", "stop the arrows", "survive a fight", "cover my legs");
        asking(Use.ARMOR, "armor", "armor for the fight", "something to wear", "protection", "clothes");
        doing(Use.FUEL, "burn", "burn in a furnace", "keep the furnace going", "feed the fire", "fuel a furnace", "run a furnace", "keep warm");
        asking(Use.FUEL, "fuel", "fuel for a furnace", "something to burn", "coal for the fire");
        doing(Use.BUILD, "build", "build a wall", "build a house", "floor my house", "roof a house", "make a tower", "build a base");
        asking(Use.BUILD, "blocks to build with", "walls for a base", "a roof", "building blocks", "a floor", "something to build with");
        doing(Use.PLANT, "plant", "plant a farm", "grow crops", "grow a tree", "start a garden", "plant a forest", "sow a field");
        asking(Use.PLANT, "something to plant", "seeds for the field", "crops", "plants");
        doing(Use.SMELT, "smelt", "smelt into ingots", "make metal", "get some ingots", "melt something", "make iron");
        asking(Use.SMELT, "ore to smelt", "something to melt", "ore", "metal to melt");
        doing(Use.STORE, "store", "store my items", "keep things", "carry more things", "put away stuff", "hold my items");
        asking(Use.STORE, "somewhere to put stuff", "a box for my things", "more room", "storage", "a container");
        doing(Use.CIRCUIT, "wire", "wire a circuit", "send a signal", "open a door by itself", "trigger a piston", "power a door");
        asking(Use.CIRCUIT, "a redstone machine", "a switch", "redstone parts", "a circuit");
        doing(Use.TRAVEL, "travel", "cross the sea", "ride across the land", "travel fast", "get around quickly", "fly over the hills", "get home");
        asking(Use.TRAVEL, "a way home", "a ride", "a vehicle", "transport");
        doing(Use.LIQUID, "carry water", "put out a fire", "move lava", "fill a pool", "hold liquid");
        asking(Use.LIQUID, "something to hold liquid", "a pail", "water carrier");
        doing(Use.WRITE, "write", "write a note", "keep notes", "draw a map", "leave a message", "write a letter");
        asking(Use.WRITE, "something to write on", "stationery", "writing things");
        doing(Use.DYE, "dye", "paint it red", "dye the wool", "color my armor", "make it blue", "change the color");
        asking(Use.DYE, "something to color with", "paint", "a dye");
        doing(Use.HEAL, "heal my wounds", "cure me", "get my health back", "stay alive", "heal");
        asking(Use.HEAL, "im dying", "medicine", "a cure", "healing");
        doing(Use.OTHER, "be a teapot", "sing a song", "fold the moon");
        asking(Use.OTHER, "nothing at all", "whatever", "the weather", "my old friend");

        named(Use.LIGHT, "torch", "soul torch", "lantern", "soul lantern", "sea lantern", "glowstone", "shroomlight", "redstone lamp",
                "jack o lantern", "end rod", "campfire", "ochre froglight", "verdant froglight", "pearlescent froglight", "candle");
        named(Use.FOOD, "apple", "bread", "cookie", "cake", "carrot", "potato", "baked potato", "beetroot", "melon slice", "sweet berries",
                "glow berries", "dried kelp", "honey bottle", "pumpkin pie", "golden apple", "golden carrot", "mushroom stew", "rabbit stew",
                "beetroot soup", "suspicious stew", "beef", "porkchop", "chicken", "mutton", "rabbit", "cod", "salmon", "tropical fish");
        named(Use.FIGHT, "bow", "crossbow", "trident", "mace", "arrow", "tnt");
        named(Use.ARMOR, "shield", "turtle helmet");
        named(Use.FUEL, "coal", "charcoal", "coal block", "blaze rod", "lava bucket", "dried kelp block", "stick");
        named(Use.BUILD, "stone", "cobblestone", "bricks", "stone bricks", "mud bricks", "deepslate bricks", "smooth stone", "glass", "glass pane",
                "white wool", "terracotta", "white concrete", "quartz block", "prismarine", "purpur block", "nether bricks", "obsidian");
        named(Use.PLANT, "potato", "carrot", "sugar cane", "bamboo", "cactus", "cocoa beans", "nether wart", "kelp");
        named(Use.SMELT, "nether gold ore", "ancient debris", "sand", "clay ball", "cobblestone");
        named(Use.STORE, "chest", "barrel", "ender chest", "trapped chest", "hopper", "shulker box", "bundle");
        named(Use.CIRCUIT, "redstone", "redstone torch", "repeater", "comparator", "observer", "piston", "sticky piston", "lever", "stone button",
                "daylight detector", "tripwire hook", "target", "dropper", "dispenser");
        named(Use.TRAVEL, "minecart", "rail", "powered rail", "saddle", "elytra", "ender pearl", "firework rocket");
        named(Use.LIQUID, "bucket", "water bucket", "lava bucket", "cauldron", "glass bottle", "sponge");
        named(Use.WRITE, "book", "writable book", "paper", "map", "ink sac", "feather", "name tag");
        named(Use.DYE, "ink sac", "bone meal", "lapis lazuli", "cocoa beans");
        named(Use.HEAL, "golden apple", "enchanted golden apple", "potion", "splash potion", "milk bucket", "totem of undying", "honey bottle", "golden carrot");
        named(Use.OTHER, "string", "bone", "gunpowder", "rotten flesh", "spider eye", "slime ball", "blaze powder", "ghast tear", "magma cream",
                "flint", "leather", "phantom membrane", "rabbit hide", "honeycomb", "snowball", "dirt", "gravel", "clay", "ice", "lead");

        forms(Use.LIGHT, "%s lantern", "%s torch", "%s lamp", "%s candle");
        forms(Use.FOOD, "cooked %s", "%s stew", "%s soup", "%s pie", "baked %s");
        forms(Use.MINE, "%s pickaxe");
        forms(Use.CHOP, "%s axe");
        forms(Use.DIG, "%s shovel");
        forms(Use.TILL, "%s hoe");
        forms(Use.FIGHT, "%s sword", "%s bow");
        forms(Use.ARMOR, "%s helmet", "%s chestplate", "%s leggings", "%s boots", "%s shield");
        forms(Use.FUEL, "%s log", "%s coal");
        forms(Use.BUILD, "%s bricks", "polished %s", "%s slab", "%s stairs", "%s wall", "%s planks");
        forms(Use.PLANT, "%s seeds", "%s sapling");
        forms(Use.SMELT, "raw %s", "%s ore", "deepslate %s ore");
        forms(Use.STORE, "%s chest", "%s barrel", "%s shulker box");
        forms(Use.CIRCUIT, "%s button", "%s pressure plate", "%s lever");
        forms(Use.TRAVEL, "%s boat", "%s minecart", "%s rail");
        forms(Use.LIQUID, "%s bucket");
        forms(Use.WRITE, "%s sign", "%s book");
        forms(Use.DYE, "%s dye");
        forms(Use.HEAL, "%s potion");
        forms(Use.OTHER, "%s");

        for (Use u : Use.values()) {
            PRACTISED.put(u, new ArrayList<>());
            HELD_OUT.put(u, new ArrayList<>());
        }
        NAMED.forEach((use, names) -> names.forEach(n -> KNOWN.computeIfAbsent(n, k -> new HashSet<>()).add(use)));
        for (Family f : FAMILIES) {
            for (int m = 0; m < f.materials().size(); m++) {
                for (int i = 0; i < f.forms().size(); i++) {
                    String name = f.forms().get(i).formatted(f.materials().get(m));
                    KNOWN.computeIfAbsent(name, k -> new HashSet<>()).add(f.use());
                    // held out on diagonals, so practice still shows every material and every form
                    ((m + i) % 5 == 2 ? HELD_OUT : PRACTISED).get(f.use()).add(name);
                }
            }
        }
        // a name practice shows anywhere, under another use or as a thing of its own, is not held out from any
        Set<String> shown = new HashSet<>();
        PRACTISED.values().forEach(shown::addAll);
        NAMED.values().forEach(shown::addAll);
        HELD_OUT.values().forEach(names -> names.removeAll(shown));
    }

    private Retrieval() {
    }

    private static void doing(Use use, String... things) {
        DOING.put(use, List.of(things));
    }

    private static void asking(Use use, String... things) {
        ASKING.put(use, List.of(things));
    }

    private static void named(Use use, String... things) {
        NAMED.put(use, List.of(things));
    }

    private static void forms(Use use, String... forms) {
        FORMS.put(use, List.of(forms));
    }

    /**
     * Something said and what it is for, every use as likely: a thing named or a query. {@code test} names things practice
     * never shows, family names held out or made-up ones in a telling form; a thing with two uses is practised under either, as
     * often, so a model's likelihoods split between them.
     */
    public static Example example(Random rng, boolean test) {
        while (true) {
            Use use = Use.values()[rng.nextInt(Use.values().length)];
            String said = rng.nextFloat() < 0.4f ? query(rng, use) : thing(rng, use, test);
            if (said.length() <= LONGEST) {
                return new Example(said, use);
            }
        }
    }

    private static String query(Random rng, Use use) {
        return rng.nextBoolean() ? pick(rng, WANTING).formatted(pick(rng, DOING.get(use))) : pick(rng, ASKING.get(use));
    }

    private static String thing(Random rng, Use use, boolean test) {
        List<String> family = (test ? HELD_OUT : PRACTISED).get(use);
        float r = rng.nextFloat();
        if (test) {
            return r < 0.5f && !family.isEmpty() ? pick(rng, family) : madeUp(rng, use);
        }
        List<String> named = NAMED.getOrDefault(use, List.of());
        return r < 0.4f && !named.isEmpty() ? pick(rng, named) : r < 0.7f && !family.isEmpty() ? pick(rng, family) : madeUp(rng, use);
    }

    /** What a thing called this is for, or nothing for a name it does not know. */
    public static Set<Use> known(String name) {
        return KNOWN.getOrDefault(name, Set.of());
    }

    /** What a model is shown of a thing or a query, in the format it practised. */
    public static String prompt(String said) {
        String s = Orders.clean(said);
        return (s.length() > LONGEST ? s.substring(0, LONGEST).strip() : s) + "? ";
    }

    /**
     * Where a model places {@code said} in the space of uses: how likely it finds each use's letter. What holds nothing it can
     * read has no place, and so fits nothing.
     */
    public static float[] place(CompiledGraph model, String said) {
        float[] at = new float[Use.values().length];
        if (Orders.clean(said).isEmpty()) {
            return at;
        }
        Vocab vocab = model.env.tokenizer();
        float[] p = Sampler.nextProbs(model, vocab.spell(prompt(said)), Vocab.CONTEXT, 1f);
        for (Use u : Use.values()) {
            at[u.ordinal()] = p[vocab.id(u.letter)];
        }
        return at;
    }

    /** How far a thing placed at {@code thing} is what a query placed at {@code query} wants: what they agree on, other left out. */
    public static float fit(float[] query, float[] thing) {
        float sum = 0;
        for (Use u : Use.values()) {
            if (u != Use.OTHER) {
                sum += query[u.ordinal()] * thing[u.ordinal()];
            }
        }
        return sum;
    }

    private static String madeUp(Random rng, Use use) {
        return pick(rng, FORMS.get(use)).formatted(Orders.madeUp(rng));
    }

    private static String pick(Random rng, List<String> names) {
        return names.get(rng.nextInt(names.size()));
    }
}
