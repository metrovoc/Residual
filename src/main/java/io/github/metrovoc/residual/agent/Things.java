package io.github.metrovoc.residual.agent;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Which items a name said aloud could mean: "iron ingots", "seeds", "a pickaxe". */
public final class Things {
    private static final Map<String, String> ALIASES = Map.of("lapis", "lapis_lazuli", "steak", "cooked_beef",
            "honey", "honey_bottle", "milk", "milk_bucket", "clay balls", "clay_ball");

    /** The item names (registry paths and shown names) and tags {@code thing} stands for. */
    record Wanted(String thing, Set<String> names, List<TagKey<Item>> tags) {
        /** Exactly this kind of thing: "torch" for torches. */
        boolean exactly(ItemStack s) {
            if (s.isEmpty()) {
                return false;
            }
            Identifier id = BuiltInRegistries.ITEM.getKey(s.getItem());
            if (id != null && names.contains(id.getPath()) || names.contains(s.getHoverName().getString().toLowerCase(Locale.ROOT))) {
                return true;
            }
            for (TagKey<Item> t : tags) {
                if (s.is(t)) {
                    return true;
                }
            }
            return false;
        }

        /** A kind of this thing: "soul torch" for torches. */
        boolean loosely(ItemStack s) {
            if (s.isEmpty()) {
                return false;
            }
            Identifier id = BuiltInRegistries.ITEM.getKey(s.getItem());
            for (String n : names) {
                if (id != null && id.getPath().endsWith("_" + n)) {
                    return true;
                }
            }
            return false;
        }
    }

    private Things() {
    }

    /** What a program calls the thing in {@code stack}, as the sorting test names things too: {@code cooked salmon}. */
    public static String name(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().replace('_', ' ');
    }

    static Wanted of(String thing) {
        String said = thing.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]", "").replaceAll(" +", " ").strip();
        for (String filler : List.of("a stack of ", "some ", "the ", "a ", "an ")) {
            if (said.startsWith(filler)) {
                said = said.substring(filler.length());
            }
        }
        Set<String> names = new LinkedHashSet<>();
        for (String form : forms(said)) {
            names.add(form.replace(' ', '_'));
            names.add(form);
        }
        if (ALIASES.containsKey(said)) {
            names.add(ALIASES.get(said));
        }
        List<TagKey<Item>> tags = new ArrayList<>();
        for (String n : List.copyOf(names)) {
            for (String path : List.of(n, n + "s")) {
                Identifier id = Identifier.tryBuild("minecraft", path);
                if (id != null) {
                    tags.add(TagKey.create(Registries.ITEM, id));
                }
            }
        }
        return new Wanted(said, names, tags);
    }

    /** The name as said and every way of making its last word singular. */
    private static List<String> forms(String said) {
        List<String> out = new ArrayList<>(List.of(said));
        int space = said.lastIndexOf(' ');
        String head = said.substring(0, space + 1), last = said.substring(space + 1);
        if (last.endsWith("ies")) {
            out.add(head + last.substring(0, last.length() - 3) + "y");
        }
        if (last.endsWith("es")) {
            out.add(head + last.substring(0, last.length() - 2));
        }
        if (last.endsWith("s") && !last.endsWith("ss")) {
            out.add(head + last.substring(0, last.length() - 1));
        }
        return out;
    }
}
