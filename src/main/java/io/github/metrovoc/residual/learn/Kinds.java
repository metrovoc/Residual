package io.github.metrovoc.residual.learn;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import org.jetbrains.annotations.Nullable;

/**
 * What kind of thing something is, from its name alone: a model reads {@code cooked salmon? } and answers {@code f}.
 * A form in the name settles most kinds (an ore, a sapling, a pickaxe); for slabs, stairs and doors the material does;
 * a name with nothing to go on is other. The test asks about things practice never names: real ones held out, and
 * made-up ones, the way the things of another world would be named.
 */
public final class Kinds {
    /** The kinds, each answered with its letter. */
    public enum Kind {
        FOOD('f'), PLANTS('p'), WOOD('w'), STONE('s'), ORES('o'), TOOLS('t'), OTHER('x');

        public final char letter;
        public final String id = name().toLowerCase(Locale.ROOT);

        Kind(char letter) {
            this.letter = letter;
        }

        /** The kind answered with {@code letter}, or null. */
        @Nullable
        public static Kind of(char letter) {
            for (Kind k : values()) {
                if (k.letter == letter) {
                    return k;
                }
            }
            return null;
        }

        /** The kind a chest labelled {@code label} is for: the first word that names one, "Ores" or "ore" alike. */
        @Nullable
        public static Kind labelled(String label) {
            for (String w : label.toLowerCase(Locale.ROOT).split("[^a-z]+")) {
                for (Kind k : values()) {
                    String one = k.id.endsWith("s") ? k.id.substring(0, k.id.length() - 1) : k.id;
                    if (w.equals(one) || w.equals(one + "s")) {
                        return k;
                    }
                }
            }
            return null;
        }
    }

    public record Example(String name, Kind kind) {
    }

    /** The longest name a model is shown. */
    public static final int LONGEST = 28;
    /** How sure a model must be of a kind for an agent to go by it. */
    public static final float SURE = 0.6f;

    /** Names of one kind, every material in every form; {@code %s} in a form stands for the material. */
    private record Family(Kind kind, List<String> materials, List<String> forms) {
    }

    private static final List<String> TREES = List.of("oak", "spruce", "birch", "jungle", "acacia", "dark oak",
            "mangrove", "cherry", "pale oak");
    private static final List<Family> FAMILIES = List.of(
            new Family(Kind.FOOD, List.of("beef", "porkchop", "chicken", "mutton", "rabbit", "cod", "salmon"),
                    List.of("cooked %s")),
            new Family(Kind.PLANTS, List.of("wheat", "pumpkin", "melon", "beetroot", "torchflower"), List.of("%s seeds")),
            new Family(Kind.PLANTS, List.of("oak", "spruce", "birch", "jungle", "acacia", "dark oak", "cherry", "pale oak"),
                    List.of("%s sapling", "%s leaves")),
            new Family(Kind.PLANTS, List.of("red", "orange", "white", "pink"), List.of("%s tulip")),
            new Family(Kind.PLANTS, List.of("crimson", "warped"), List.of("%s fungus", "%s roots")),
            new Family(Kind.WOOD, TREES, List.of("%s log", "%s wood", "stripped %s log", "stripped %s wood", "%s planks",
                    "%s slab", "%s stairs", "%s fence", "%s fence gate", "%s door", "%s trapdoor", "%s button",
                    "%s pressure plate", "%s sign")),
            new Family(Kind.WOOD, List.of("crimson", "warped"), List.of("%s stem", "%s hyphae", "stripped %s stem",
                    "%s planks", "%s slab", "%s stairs", "%s fence", "%s door", "%s trapdoor", "%s button", "%s sign")),
            new Family(Kind.STONE, List.of("stone", "cobblestone", "mossy cobblestone", "granite", "polished granite",
                    "diorite", "polished diorite", "andesite", "polished andesite", "cobbled deepslate",
                    "polished deepslate", "deepslate brick", "deepslate tile", "blackstone", "polished blackstone",
                    "sandstone", "red sandstone", "stone brick", "mossy stone brick", "brick", "mud brick", "nether brick",
                    "end stone brick", "tuff", "tuff brick", "prismarine"), List.of("%s slab", "%s stairs", "%s wall")),
            new Family(Kind.ORES, List.of("iron", "gold", "copper"), List.of("%s ore", "deepslate %s ore", "raw %s",
                    "%s ingot", "%s nugget", "%s block", "raw %s block")),
            new Family(Kind.ORES, List.of("coal", "diamond", "emerald", "lapis", "redstone"),
                    List.of("%s ore", "deepslate %s ore", "%s block")),
            new Family(Kind.TOOLS, List.of("wooden", "stone", "copper", "iron", "golden", "diamond", "netherite"),
                    List.of("%s pickaxe", "%s axe", "%s shovel", "%s hoe", "%s sword")),
            new Family(Kind.TOOLS, List.of("leather", "chainmail", "copper", "iron", "golden", "diamond", "netherite"),
                    List.of("%s helmet", "%s chestplate", "%s leggings", "%s boots")));

    /** Things known only by name, which practice always shows. */
    private static final Map<Kind, List<String>> NAMED = new EnumMap<>(Map.of(
            Kind.FOOD, List.of("apple", "bread", "cookie", "cake", "carrot", "potato", "beetroot", "melon slice",
                    "sweet berries", "glow berries", "dried kelp", "honey bottle", "pumpkin pie", "baked potato",
                    "golden apple", "golden carrot", "enchanted golden apple", "mushroom stew", "rabbit stew",
                    "beetroot soup", "suspicious stew", "beef", "porkchop", "chicken", "mutton", "rabbit", "cod", "salmon",
                    "tropical fish", "chorus fruit"),
            Kind.PLANTS, List.of("wheat", "sugar cane", "cactus", "bamboo", "kelp", "vine", "lily pad", "fern",
                    "large fern", "short grass", "tall grass", "dead bush", "dandelion", "poppy", "blue orchid", "allium",
                    "azure bluet", "oxeye daisy", "cornflower", "lily of the valley", "sunflower", "lilac", "rose bush",
                    "peony", "torchflower", "pitcher plant", "nether wart", "sea pickle", "seagrass", "moss block",
                    "azalea", "flowering azalea", "cocoa beans", "mangrove propagule", "red mushroom", "brown mushroom",
                    "pumpkin", "melon", "glow lichen", "hanging roots", "spore blossom"),
            Kind.WOOD, List.of("stick", "bowl", "ladder", "bamboo planks", "bamboo mosaic", "bamboo block",
                    "stripped bamboo block"),
            Kind.STONE, List.of("stone", "cobblestone", "mossy cobblestone", "smooth stone", "granite",
                    "polished granite", "diorite", "polished diorite", "andesite", "polished andesite", "deepslate",
                    "cobbled deepslate", "polished deepslate", "deepslate bricks", "deepslate tiles", "chiseled deepslate",
                    "tuff", "polished tuff", "tuff bricks", "calcite", "blackstone", "polished blackstone", "basalt",
                    "polished basalt", "smooth basalt", "sandstone", "cut sandstone", "smooth sandstone", "red sandstone",
                    "bricks", "brick", "stone bricks", "mossy stone bricks", "cracked stone bricks",
                    "chiseled stone bricks", "mud bricks", "packed mud", "nether bricks", "red nether bricks", "end stone",
                    "end stone bricks", "netherrack", "obsidian", "crying obsidian", "prismarine", "prismarine bricks",
                    "dark prismarine", "purpur block", "dripstone block"),
            Kind.ORES, List.of("coal", "charcoal", "diamond", "emerald", "lapis lazuli", "redstone", "quartz",
                    "nether quartz ore", "nether gold ore", "amethyst shard", "netherite ingot", "netherite scrap",
                    "ancient debris", "netherite block"),
            Kind.TOOLS, List.of("bow", "crossbow", "shield", "shears", "fishing rod", "flint and steel", "trident", "mace",
                    "spyglass", "brush", "compass", "clock", "bucket", "water bucket", "lava bucket", "elytra",
                    "turtle helmet", "carrot on a stick"),
            Kind.OTHER, List.of("string", "bone", "bone meal", "feather", "leather", "gunpowder", "rotten flesh",
                    "spider eye", "ender pearl", "slime ball", "blaze rod", "blaze powder", "arrow", "paper", "book",
                    "torch", "glass", "glass pane", "white wool", "dirt", "coarse dirt", "sand", "red sand", "gravel",
                    "clay ball", "clay", "flint", "ink sac", "glowstone dust", "snowball", "ice", "rabbit hide",
                    "phantom membrane", "honeycomb", "saddle", "name tag", "lead", "map", "ghast tear", "magma cream")));

    /** Forms that settle a kind whatever the material, to make names up with. */
    private static final Map<Kind, List<String>> FORMS = new EnumMap<>(Map.of(
            Kind.FOOD, List.of("cooked %s", "%s stew", "%s soup", "%s pie", "baked %s"),
            Kind.PLANTS, List.of("%s seeds", "%s sapling", "%s leaves", "%s tulip", "%s fungus", "%s roots"),
            Kind.WOOD, List.of("%s log", "%s planks", "stripped %s log", "%s wood"),
            Kind.STONE, List.of("polished %s", "%s bricks", "cobbled %s", "chiseled %s", "%s wall"),
            Kind.ORES, List.of("%s ore", "deepslate %s ore", "raw %s", "%s ingot", "%s nugget"),
            Kind.TOOLS, List.of("%s pickaxe", "%s axe", "%s shovel", "%s hoe", "%s sword", "%s helmet", "%s chestplate",
                    "%s leggings", "%s boots"),
            Kind.OTHER, List.of("%s")));

    /** Every real name here, and its kind. */
    private static final Map<String, Kind> KNOWN = new HashMap<>();
    /** Names from families that practice shows, and the fifth of them that only the test asks about. */
    private static final Map<Kind, List<String>> PRACTISED = new EnumMap<>(Kind.class);
    private static final Map<Kind, List<String>> HELD_OUT = new EnumMap<>(Kind.class);

    static {
        NAMED.forEach((kind, names) -> names.forEach(n -> know(n, kind)));
        for (Kind k : Kind.values()) {
            PRACTISED.put(k, new ArrayList<>());
            HELD_OUT.put(k, new ArrayList<>());
        }
        for (Family f : FAMILIES) {
            for (int m = 0; m < f.materials().size(); m++) {
                for (int i = 0; i < f.forms().size(); i++) {
                    String name = f.forms().get(i).formatted(f.materials().get(m));
                    know(name, f.kind());
                    // held out on diagonals, so practice still shows every material and every form
                    ((m + i) % 5 == 2 ? HELD_OUT : PRACTISED).get(f.kind()).add(name);
                }
            }
        }
    }

    private Kinds() {
    }

    private static void know(String name, Kind kind) {
        if (KNOWN.put(name, kind) != null) {
            throw new IllegalStateException("named twice: " + name);
        }
    }

    /**
     * Something to sort and its kind, every kind as likely. {@code test} asks about names practice never shows: family
     * names held out, or made-up ones in a telling form.
     */
    public static Example example(Random rng, boolean test) {
        while (true) {
            Kind kind = Kind.values()[rng.nextInt(Kind.values().length)];
            List<String> family = (test ? HELD_OUT : PRACTISED).get(kind);
            float r = rng.nextFloat();
            String name;
            if (test) {
                name = r < 0.5f && !family.isEmpty() ? pick(rng, family) : madeUp(rng, kind);
            } else {
                name = r < 0.35f ? pick(rng, NAMED.get(kind)) : r < 0.65f && !family.isEmpty() ? pick(rng, family) : madeUp(rng, kind);
            }
            if (name.length() <= LONGEST) {
                return new Example(name, kind);
            }
        }
    }

    /** The kind of a thing named here, or null for a name it doesn't know. */
    @Nullable
    public static Kind known(String name) {
        return KNOWN.get(name);
    }

    /** What a model is shown to sort a thing called {@code name}, in the format it practised. */
    public static String prompt(String name) {
        String n = Orders.clean(name);
        return (n.length() > LONGEST ? n.substring(0, LONGEST).strip() : n) + "? ";
    }

    private static String madeUp(Random rng, Kind kind) {
        List<String> forms = FORMS.get(kind);
        return forms.get(rng.nextInt(forms.size())).formatted(Orders.madeUp(rng));
    }

    private static String pick(Random rng, List<String> names) {
        return names.get(rng.nextInt(names.size()));
    }
}
