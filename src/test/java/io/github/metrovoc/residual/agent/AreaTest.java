package io.github.metrovoc.residual.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

/** The nine shapes, as PneumaticCraft's area widget lays them out, and areas added and taken away. */
class AreaTest {
    private static final BlockPos O = BlockPos.ZERO;

    @Test
    void aBoxIsFilledHollowOrAFrame() {
        BlockPos c = new BlockPos(2, 2, 2);
        assertEquals(27, Area.box(O, c, "filled").size());
        assertEquals(26, Area.box(O, c, "hollow").size());
        assertEquals(20, Area.box(O, c, "frame").size());
        assertFalse(Area.box(O, c, "hollow").contains(1, 1, 1));
        assertFalse(Area.box(O, c, "frame").contains(1, 1, 0));
        assertTrue(Area.box(O, c, "frame").contains(1, 0, 0));
    }

    @Test
    void aSphereHoldsWhatIsWithinItsRadius() {
        Area s = Area.sphere(O, new BlockPos(5, 0, 0), "filled");
        assertTrue(s.contains(5, 0, 0));
        assertTrue(s.contains(3, 4, 0));
        assertFalse(s.contains(5, 1, 0));
        // about four thirds of pi r cubed
        assertEquals(4.0 / 3 * Math.PI * 125, s.size(), 40);
        Area hollow = Area.sphere(O, new BlockPos(5, 0, 0), "hollow");
        assertFalse(hollow.contains(0, 0, 0));
        assertTrue(hollow.contains(5, 0, 0));
        assertTrue(hollow.size() < s.size());
    }

    @Test
    void aCylinderRunsAlongItsAxis() {
        Area c = Area.cylinder(O, new BlockPos(3, 4, 0), "filled", 'y');
        assertTrue(c.contains(0, 0, 3));
        assertTrue(c.contains(0, 4, 3));
        assertFalse(c.contains(0, 5, 0));
        assertFalse(c.contains(3, 0, 3));
        Area tube = Area.cylinder(O, new BlockPos(3, 4, 0), "tube", 'y');
        assertFalse(tube.contains(0, 1, 0));
        assertTrue(tube.contains(3, 1, 0));
        Area hollow = Area.cylinder(O, new BlockPos(3, 4, 0), "hollow", 'y');
        assertTrue(hollow.contains(0, 0, 0), "an end");
        assertFalse(hollow.contains(0, 2, 0), "the middle");
    }

    @Test
    void aGridSkipsAlongEachAxis() {
        Area g = Area.grid(O, new BlockPos(6, 0, 6), 2);
        assertEquals(16, g.size());
        assertTrue(g.contains(2, 0, 4));
        assertFalse(g.contains(1, 0, 4));
        assertEquals(1, Area.grid(O, new BlockPos(6, 0, 6), 0).size());
    }

    @Test
    void aLineIsTheBlocksItPassesThrough() {
        Area l = Area.line(O, new BlockPos(4, 0, 0));
        assertEquals(5, l.size());
        Area diagonal = Area.line(O, new BlockPos(4, 4, 4));
        assertTrue(diagonal.contains(2, 2, 2));
        assertFalse(diagonal.contains(4, 0, 0));
    }

    @Test
    void aPyramidWidensFromItsPointToItsBase() {
        Area p = Area.pyramid(O, new BlockPos(2, 2, 2), 'y', "filled");
        assertEquals(1, count(p, 0));
        assertEquals(9, count(p, 1));
        assertEquals(25, count(p, 2));
        Area hollow = Area.pyramid(O, new BlockPos(2, 2, 2), 'y', "hollow");
        assertEquals(25, count(hollow, 2), "its base is whole");
        assertEquals(8, count(hollow, 1), "the rest is what the slice before did not cover");
    }

    private static int count(Area a, int y) {
        int n = 0;
        for (var it = a.places(); it.hasNext(); ) {
            n += it.next().getY() == y ? 1 : 0;
        }
        return n;
    }

    @Test
    void randomPlacesStayBetweenTheCorners() {
        Area r = Area.random(O, new BlockPos(9, 9, 9), 50, new Random(1));
        int n = r.size();
        assertTrue(n > 30 && n <= 50, n + " places");
        assertEquals(1000, Area.random(O, new BlockPos(9, 9, 9), 5000, new Random(1)).size());
    }

    @Test
    void aTorusIsARingAroundItsCentre() {
        Area t = Area.torus(O, new BlockPos(4, 1, 0), 'y', "filled");
        assertTrue(t.contains(4, 0, 0));
        assertTrue(t.contains(0, 0, 4));
        assertFalse(t.contains(0, 0, 0), "the hole");
        assertFalse(t.contains(4, 2, 0));
    }

    @Test
    void aWallIsALineRunOutAlongItsAxis() {
        Area w = Area.wall(O, new BlockPos(4, 3, 0), 'y');
        assertEquals(20, w.size());
        assertTrue(w.contains(2, 1, 0));
        assertFalse(w.contains(2, 1, 1));
    }

    @Test
    void areasAddAndSubtract() {
        Area a = Area.box(O, new BlockPos(2, 0, 0), "filled");
        Area b = Area.box(new BlockPos(2, 0, 0), new BlockPos(4, 0, 0), "filled");
        assertEquals(5, new Area.Sum(a, b).size());
        assertEquals(2, new Area.Less(a, b).size());
        assertEquals("box(pos(0, 0, 0), pos(2, 0, 0)) - box(pos(2, 0, 0), pos(4, 0, 0))", new Area.Less(a, b).toString());
    }

    @Test
    void anAreaUpToTheMostWorksAndOneMoreIsRefused() {
        Area big = Area.box(O, new BlockPos(249, 99, 9), "filled");
        assertEquals(Area.MOST, big.volume());
        Value v = Value.Region.of(big);
        assertTrue(v instanceof Value.Region);
        long start = System.nanoTime();
        assertEquals(Area.MOST, big.size());
        System.out.printf("[areas] %.1f ms to walk through %d places%n", (System.nanoTime() - start) / 1e6, Area.MOST);
        boolean refused = false;
        try {
            Value.Region.of(Area.box(O, new BlockPos(250, 99, 9), "filled"));
        } catch (Eval.Fault f) {
            refused = true;
        }
        assertTrue(refused);
    }

    @Test
    void goingDownFindsTheSamePlacesFromTheTop() {
        Area area = Area.sphere(new BlockPos(0, 0, 0), new BlockPos(5, 0, 0), "filled");
        java.util.Set<BlockPos> up = new java.util.HashSet<>(), down = new java.util.HashSet<>();
        area.places().forEachRemaining(up::add);
        int last = Integer.MAX_VALUE;
        for (java.util.Iterator<BlockPos> it = area.downward(); it.hasNext(); ) {
            BlockPos p = it.next();
            assertTrue(p.getY() <= last);
            last = p.getY();
            down.add(p);
        }
        assertEquals(up, down);
    }
}
