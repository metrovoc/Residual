package io.github.metrovoc.residual.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class StorageSearchTest {
    private record Thing(String name, String mod, String modName, Collection<String> tags, String tooltip, String id,
                         long count) implements StorageSearch.Facts {
    }

    private static final List<Thing> ALL = List.of(
            new Thing("Iron Ingot", "minecraft", "Minecraft", List.of("c:ingots", "c:ingots/iron"), "", "minecraft:iron_ingot", 1200),
            new Thing("Gold Ingot", "minecraft", "Minecraft", List.of("c:ingots", "c:ingots/gold"), "", "minecraft:gold_ingot", 40),
            new Thing("Copper Ingot", "minecraft", "Minecraft", List.of("c:ingots"), "", "minecraft:copper_ingot", 64),
            new Thing("Bread", "minecraft", "Minecraft", List.of("c:foods"), "Restores hunger", "minecraft:bread", 3),
            new Thing("Weight Crystal", "residual", "Residual", List.of(), "", "residual:weight_crystal", 5000));

    private static String find(String query) {
        Predicate<StorageSearch.Facts> p = StorageSearch.parse(query);
        StringBuilder out = new StringBuilder();
        for (Thing t : ALL) {
            if (p.test(t)) {
                out.append(out.isEmpty() ? "" : ",").append(t.name());
            }
        }
        return out.toString();
    }

    @Test
    void wordsMustAllBeInTheName() {
        assertEquals("Iron Ingot,Gold Ingot,Copper Ingot,Bread,Weight Crystal", find(""));
        assertEquals("Iron Ingot,Gold Ingot,Copper Ingot", find("INGOT"));
        assertEquals("Iron Ingot", find("ingot iron"));
    }

    @Test
    void prefixesAskForTheModTagsTooltipAndId() {
        assertEquals("Weight Crystal", find("@residual"));
        assertEquals("Iron Ingot,Gold Ingot", find("#ingots/"));
        assertEquals("Bread", find("$hunger"));
        assertEquals("Copper Ingot", find("*copper"));
    }

    @Test
    void notOrAndGroupsCombine() {
        assertEquals("Gold Ingot,Copper Ingot", find("ingot -iron"));
        assertEquals("Iron Ingot,Bread", find("iron | bread"));
        assertEquals("Bread,Weight Crystal", find("-(ingot)"));
        assertEquals("Gold Ingot,Bread", find("(gold | bread) -crystal"));
    }

    @Test
    void amountsCompare() {
        assertEquals("Iron Ingot,Weight Crystal", find(">1k"));
        assertEquals("Gold Ingot,Bread", find("<64"));
        assertEquals("Copper Ingot", find("=64"));
        assertEquals("Iron Ingot,Weight Crystal", find(">=1200"));
        assertEquals("Iron Ingot,Gold Ingot,Copper Ingot,Bread,Weight Crystal", find(">abc"));
    }

    @Test
    void brokenQueriesStillFindWhatTheirWordsDo() {
        assertEquals("Iron Ingot,Gold Ingot,Copper Ingot", find("(ingot"));
        assertEquals("Iron Ingot", find("iron)"));
        assertEquals("Iron Ingot,Gold Ingot,Copper Ingot,Bread,Weight Crystal", find("-"));
    }
}
