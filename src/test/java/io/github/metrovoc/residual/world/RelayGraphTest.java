package io.github.metrovoc.residual.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RelayGraphTest {
    private static RelayGraph.Part<String> part(String key, String dim, int x, int z, int reach) {
        return new RelayGraph.Part<>(key, dim, x, z, reach, null);
    }

    @Test
    void reachesThatTouchJoinAndCoverageHasNoGaps() {
        RelayGraph<String> g = new RelayGraph<>(List.of(
                part("server", "o", 0, 0, 16), part("a", "o", 48, 0, 32), part("b", "o", 112, 0, 32), part("c", "o", 178, 0, 32)));
        assertEquals(g.network("server"), g.network("a"));
        assertEquals(g.network("a"), g.network("b"));
        // further than the reaches touch
        assertTrue(g.network("b") != g.network("c"));
        assertEquals(g.network("a"), g.covering("o", 64, 20));
        assertEquals(-1, g.covering("o", 145, 0));
        assertEquals(-1, g.covering("n", 48, 0));
    }

    @Test
    void reachRunsLevelAtAnyHeightAndGatesAndLinksJoinWhatCoversThem() {
        RelayGraph<String> g = new RelayGraph<>(List.of(
                part("relay", "o", 0, 0, 32), part("gate", "o", 20, 20, 0), part("link", "o", 30, 30, 0)));
        assertEquals(g.network("relay"), g.network("gate"));
        // beyond its reach
        assertTrue(g.network("relay") != g.network("link"));
    }

    @Test
    void tunedGatesJoinAcrossAnyDistanceAndBetweenDimensions() {
        RelayGraph<String> g = new RelayGraph<>(List.of(
                part("server", "o", 0, 0, 16),
                new RelayGraph.Part<>("gate", "o", 10, 0, 0, "far"),
                new RelayGraph.Part<>("far", "n", 90_000, 0, 0, "gate"),
                part("relay", "n", 90_010, 0, 32)));
        assertEquals(g.network("server"), g.network("relay"));
        assertEquals(g.network("server"), g.covering("n", 90_020, 0));
        assertEquals(3, g.hops("server", "relay"));
    }

    @Test
    void hopsCountTheWayThroughTheRelays() {
        List<RelayGraph.Part<String>> chain = new ArrayList<>();
        chain.add(part("gate0", "o", 0, 0, 0));
        for (int i = 0; i < 10; i++) {
            chain.add(part("r" + i, "o", i * 64, 0, 32));
        }
        chain.add(part("gate1", "o", 576, 0, 0));
        RelayGraph<String> g = new RelayGraph<>(chain);
        assertEquals(11, g.hops("gate0", "gate1"));
        assertEquals(0, g.hops("gate0", "gate0"));
    }

    /** A thousand parts, far more than a base has, joined again whenever one of them comes or goes. */
    @Test
    void aThousandPartsJoinInAFewMilliseconds() {
        List<RelayGraph.Part<String>> parts = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            parts.add(part("p" + i, "o", i % 32 * 40, i / 32 * 40, 32));
        }
        long best = Long.MAX_VALUE;
        for (int run = 0; run < 20; run++) {
            long start = System.nanoTime();
            RelayGraph<String> g = new RelayGraph<>(parts);
            best = Math.min(best, System.nanoTime() - start);
            assertEquals(1, g.networks().size());
        }
        System.out.printf("RelayGraph: 1,000 parts joined in %.2f ms%n", best / 1e6);
        assertTrue(best < 50_000_000L, "took " + best / 1e6 + " ms");
    }
}
