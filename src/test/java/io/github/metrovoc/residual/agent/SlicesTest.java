package io.github.metrovoc.residual.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

/** How the Architect cuts builds and runs into slices. */
class SlicesTest {
    @Test
    void cutsAcrossTheLongerSideFromTheFirstCorner() {
        // 4 long in x, 3 deep in z, 3 high, the first corner at the top
        Slices s = Slices.between(new BlockPos(33, -58, 8), new BlockPos(30, -60, 6));
        assertEquals(Direction.WEST, s.along());
        assertEquals(Direction.NORTH, s.across());
        assertEquals(4, s.length());
        assertEquals(3, s.width());
        assertEquals(3, s.height());
        assertEquals(new BlockPos(33, -60, 8), s.at(0, 0, 0));
        assertEquals(new BlockPos(30, -58, 6), s.at(3, 2, 2));
        assertEquals(9, s.slice(1).size());
        assertEquals(new BlockPos(30, -60, 6), s.min());
        assertEquals(new BlockPos(33, -58, 8), s.max());
    }

    @Test
    void aCopyKeepsItsShape() {
        Slices build = Slices.between(new BlockPos(30, -60, 20), new BlockPos(32, -58, 22));
        BlockPos third = new BlockPos(30, -61, 26);
        Slices copy = build.movedTo(third.above().offset(build.origin().subtract(new BlockPos(30, -60, 20))));
        assertEquals(new BlockPos(30, -60, 26), copy.at(0, 0, 0));
        assertEquals(new BlockPos(32, -58, 28), copy.max());
        assertFalse(copy.overlaps(build));
        assertTrue(build.movedTo(new BlockPos(31, -60, 22)).overlaps(build));
    }

    @Test
    void aRunIsOneBlockASliceUnlessACornerWidensIt() {
        Slices row = Slices.run(new BlockPos(40, -61, 0), new BlockPos(40, -61, 11), null);
        assertEquals(12, row.length());
        assertEquals(1, row.width() * row.height());
        assertEquals(new BlockPos(40, -60, 11), row.at(11, 0, 0));
        Slices wall = Slices.run(new BlockPos(40, -61, 0), new BlockPos(40, -61, 11), new BlockPos(42, -58, 0));
        assertEquals(Direction.SOUTH, wall.along());
        assertEquals(Direction.EAST, wall.across());
        assertEquals(3, wall.width());
        assertEquals(3, wall.height());
        assertEquals(new BlockPos(42, -58, 5), wall.at(5, 2, 2));
        assertNull(Slices.run(new BlockPos(0, 0, 0), new BlockPos(3, 0, 3), null), "not a straight line");
        assertNull(Slices.run(new BlockPos(0, 0, 0), new BlockPos(0, 0, 5), new BlockPos(1, 0, 0)), "a corner below the top");
    }
}
