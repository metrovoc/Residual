package io.github.metrovoc.residual.agent;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Random;
import net.minecraft.core.BlockPos;

/**
 * A set of places: one of nine shapes between two corners, or areas added to and taken from one another. Up to
 * {@link #MOST} blocks fit its bounding box. Nothing is kept per block except for the shapes that are sampled (line, wall,
 * random), so an area costs almost nothing to hold, and a body works through it a little at a time.
 */
public sealed interface Area {
    /** The most blocks an area's bounding box may hold. */
    int MOST = 250_000;

    int minX();

    int minY();

    int minZ();

    int maxX();

    int maxY();

    int maxZ();

    boolean contains(int x, int y, int z);

    default boolean contains(BlockPos p) {
        return contains(p.getX(), p.getY(), p.getZ());
    }

    /** How many blocks its bounding box holds. */
    default long volume() {
        return (long) (maxX() - minX() + 1) * (maxY() - minY() + 1) * (maxZ() - minZ() + 1);
    }

    /** How many places it holds, counted: as long as its bounding box takes to walk through. */
    default int size() {
        int n = 0;
        for (Iterator<BlockPos> it = places(); it.hasNext(); it.next()) {
            n++;
        }
        return n;
    }

    /** Its places from the bottom up, by layer. */
    default Iterator<BlockPos> places() {
        return walk(1);
    }

    /** Its places from the top down, by layer: nothing is kept, so a body can go through the largest of areas this way. */
    default Iterator<BlockPos> downward() {
        return walk(-1);
    }

    private Iterator<BlockPos> walk(int up) {
        return new Iterator<>() {
            private int x = minX() - 1, y = up > 0 ? minY() : maxY(), z = minZ();
            private boolean found;
            private boolean over;

            private void advance() {
                while (!found && !over) {
                    x++;
                    if (x > maxX()) {
                        x = minX();
                        z++;
                        if (z > maxZ()) {
                            z = minZ();
                            y += up;
                            if (y > maxY() || y < minY()) {
                                over = true;
                                return;
                            }
                        }
                    }
                    found = Area.this.contains(x, y, z);
                }
            }

            @Override
            public boolean hasNext() {
                advance();
                return found;
            }

            @Override
            public BlockPos next() {
                advance();
                if (!found) {
                    throw new NoSuchElementException();
                }
                found = false;
                return new BlockPos(x, y, z);
            }
        };
    }

    /** The two areas together. */
    record Sum(Area a, Area b) implements Area {
        @Override
        public int minX() {
            return Math.min(a.minX(), b.minX());
        }

        @Override
        public int minY() {
            return Math.min(a.minY(), b.minY());
        }

        @Override
        public int minZ() {
            return Math.min(a.minZ(), b.minZ());
        }

        @Override
        public int maxX() {
            return Math.max(a.maxX(), b.maxX());
        }

        @Override
        public int maxY() {
            return Math.max(a.maxY(), b.maxY());
        }

        @Override
        public int maxZ() {
            return Math.max(a.maxZ(), b.maxZ());
        }

        @Override
        public boolean contains(int x, int y, int z) {
            return a.contains(x, y, z) || b.contains(x, y, z);
        }

        @Override
        public String toString() {
            return a + " + " + b;
        }
    }

    /** The first area without the second. */
    record Less(Area a, Area b) implements Area {
        @Override
        public int minX() {
            return a.minX();
        }

        @Override
        public int minY() {
            return a.minY();
        }

        @Override
        public int minZ() {
            return a.minZ();
        }

        @Override
        public int maxX() {
            return a.maxX();
        }

        @Override
        public int maxY() {
            return a.maxY();
        }

        @Override
        public int maxZ() {
            return a.maxZ();
        }

        @Override
        public boolean contains(int x, int y, int z) {
            return a.contains(x, y, z) && !b.contains(x, y, z);
        }

        @Override
        public String toString() {
            return a + " - " + (b instanceof Sum || b instanceof Less ? "(" + b + ")" : b.toString());
        }
    }

    /** A shape by its two corners, as PneumaticCraft's area widget has them; {@code kind} and {@code axis} only where one says. */
    record Shape(String name, BlockPos p1, BlockPos p2, String kind, char axis, int number, int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                 Membership in) implements Area {
        @Override
        public boolean contains(int x, int y, int z) {
            return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ && in.test(x, y, z);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder(name).append('(').append(new Value.Place(p1).show()).append(", ").append(new Value.Place(p2).show());
            if (!kind.isEmpty()) {
                sb.append(", ").append(kind);
            }
            if (axis != 0) {
                sb.append(", ").append(axis);
            }
            if (number != 0) {
                sb.append(", ").append(number);
            }
            return sb.append(')').toString();
        }
    }

    @FunctionalInterface
    interface Membership {
        boolean test(int x, int y, int z);
    }

    private static Shape shape(String name, BlockPos a, BlockPos b, String kind, char axis, int number, Membership in) {
        return new Shape(name, a, b, kind, axis, number, Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()),
                Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()), in);
    }

    /** A shape whose bounding box is not the two corners', as a sphere's is not. */
    private static Shape shape(String name, BlockPos a, BlockPos b, String kind, char axis, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, Membership in) {
        return new Shape(name, a, b, kind, axis, 0, minX, minY, minZ, maxX, maxY, maxZ, in);
    }

    /** {@code filled}: every block between the corners; {@code hollow}: its six faces; {@code frame}: its twelve edges. */
    static Area box(BlockPos a, BlockPos b, String kind) {
        int x0 = Math.min(a.getX(), b.getX()), x1 = Math.max(a.getX(), b.getX()), y0 = Math.min(a.getY(), b.getY()), y1 = Math.max(a.getY(), b.getY()),
                z0 = Math.min(a.getZ(), b.getZ()), z1 = Math.max(a.getZ(), b.getZ());
        return shape("box", a, b, kind.equals("filled") ? "" : kind, (char) 0, 0, switch (kind) {
            case "hollow" -> (x, y, z) -> x == x0 || x == x1 || y == y0 || y == y1 || z == z0 || z == z1;
            case "frame" -> (x, y, z) -> (x == x0 || x == x1 ? 1 : 0) + (y == y0 || y == y1 ? 1 : 0) + (z == z0 || z == z1 ? 1 : 0) >= 2;
            default -> (x, y, z) -> true;
        });
    }

    /**
     * A circle round {@code a} in the plane across {@code axis}, as wide as {@code b} is from {@code a} in that plane, run out
     * along the axis as far as {@code b}. {@code tube} is only its wall, {@code hollow} the wall and both ends.
     */
    static Area cylinder(BlockPos a, BlockPos b, String kind, char axis) {
        int du = u(b, axis) - u(a, axis), dv = v(b, axis) - v(a, axis);
        double r = Math.sqrt(du * du + dv * dv);
        int ceil = (int) Math.ceil(r);
        int low = Math.min(w(a, axis), w(b, axis)), high = Math.max(w(a, axis), w(b, axis));
        double in = Math.max(0, r - 1);
        Membership m = (x, y, z) -> {
            int cu = u(x, y, z, axis) - u(a, axis), cv = v(x, y, z, axis) - v(a, axis);
            int d = cu * cu + cv * cv;
            if (d > r * r) {
                return false;
            }
            int along = w(x, y, z, axis);
            return switch (kind) {
                case "tube" -> d >= in * in;
                case "hollow" -> d >= in * in || along == low || along == high;
                default -> true;
            };
        };
        return bounded("cylinder", a, b, kind, axis, u(a, axis) - ceil, v(a, axis) - ceil, low, u(a, axis) + ceil, v(a, axis) + ceil, high, m);
    }

    /** Every {@code interval}-th block between the corners on each axis, from the low corner; only {@code a} if the corners are one or the interval is none. */
    static Area grid(BlockPos a, BlockPos b, int interval) {
        int x0 = Math.min(a.getX(), b.getX()), y0 = Math.min(a.getY(), b.getY()), z0 = Math.min(a.getZ(), b.getZ());
        boolean one = a.equals(b) || interval <= 0;
        Membership m = one ? (x, y, z) -> x == a.getX() && y == a.getY() && z == a.getZ()
                : (x, y, z) -> (x - x0) % interval == 0 && (y - y0) % interval == 0 && (z - z0) % interval == 0;
        return shape("grid", a, b, "", (char) 0, interval, m);
    }

    /** The blocks a straight line from {@code a} to {@code b} passes through. */
    static Area line(BlockPos a, BlockPos b) {
        LongOpenHashSet on = new LongOpenHashSet();
        walkLine(a.getX(), a.getY(), a.getZ(), b.getX(), b.getY(), b.getZ(), on);
        return shape("line", a, b, "", (char) 0, 0, (x, y, z) -> on.contains(BlockPos.asLong(x, y, z)));
    }

    private static void walkLine(int x0, int y0, int z0, int x1, int y1, int z1, LongOpenHashSet into) {
        double dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int samples = Math.max(1, (int) Math.ceil(length * 10));
        for (int i = 0; i <= samples; i++) {
            double t = (double) i / samples;
            into.add(BlockPos.asLong((int) Math.floor(x0 + 0.5 + dx * t), (int) Math.floor(y0 + 0.5 + dy * t), (int) Math.floor(z0 + 0.5 + dz * t)));
        }
    }

    /** Its point at {@code a}, widening a block a slice along {@code axis} up to its base at {@code b}. {@code hollow} keeps the base whole and of each other slice what the last one did not cover. */
    static Area pyramid(BlockPos a, BlockPos b, char axis, String kind) {
        int height = w(b, axis) - w(a, axis);
        int du = Math.abs(u(b, axis) - u(a, axis)), dv = Math.abs(v(b, axis) - v(a, axis));
        Membership m = (x, y, z) -> {
            if (height == 0) {
                return false;
            }
            int along = w(x, y, z, axis) - w(a, axis);
            if (Integer.signum(along) * Integer.signum(height) < 0) {
                return false;
            }
            double t = (double) Math.abs(along) / Math.abs(height);
            int cu = Math.abs(u(x, y, z, axis) - u(a, axis)), cv = Math.abs(v(x, y, z, axis) - v(a, axis));
            if (cu > du * t + 1e-9 || cv > dv * t + 1e-9) {
                return false;
            }
            if (!kind.equals("hollow") || Math.abs(along) == Math.abs(height)) {
                return true;
            }
            double before = (double) (Math.abs(along) - 1) / Math.abs(height);
            return along == 0 || cu > du * before + 1e-9 || cv > dv * before + 1e-9;
        };
        int low = Math.min(w(a, axis), w(b, axis)), high = Math.max(w(a, axis), w(b, axis));
        return bounded("pyramid", a, b, kind, axis, u(a, axis) - du, v(a, axis) - dv, low, u(a, axis) + du, v(a, axis) + dv, high, m);
    }

    /** {@code amount} places between the corners drawn at random, the same one perhaps twice; all of them if that many or more. */
    static Area random(BlockPos a, BlockPos b, int amount, Random rng) {
        int x0 = Math.min(a.getX(), b.getX()), y0 = Math.min(a.getY(), b.getY()), z0 = Math.min(a.getZ(), b.getZ());
        int w = Math.abs(a.getX() - b.getX()) + 1, h = Math.abs(a.getY() - b.getY()) + 1, d = Math.abs(a.getZ() - b.getZ()) + 1;
        if ((long) w * h * d <= amount) {
            return shape("random", a, b, "", (char) 0, amount, (x, y, z) -> true);
        }
        LongOpenHashSet on = new LongOpenHashSet();
        for (int i = 0; i < Math.max(0, amount); i++) {
            on.add(BlockPos.asLong(x0 + rng.nextInt(w), y0 + rng.nextInt(h), z0 + rng.nextInt(d)));
        }
        return shape("random", a, b, "", (char) 0, amount, (x, y, z) -> on.contains(BlockPos.asLong(x, y, z)));
    }

    /** Round {@code a}, as far as {@code b} is from it. {@code hollow} is only its shell, a block thick. */
    static Area sphere(BlockPos a, BlockPos b, String kind) {
        double r = Math.sqrt(a.distSqr(b));
        int ceil = (int) Math.ceil(r);
        double in = Math.max(0, r - 1);
        Membership m = (x, y, z) -> {
            double dx = x - a.getX(), dy = y - a.getY(), dz = z - a.getZ();
            double d = dx * dx + dy * dy + dz * dz;
            return d <= r * r && (!kind.equals("hollow") || d >= in * in);
        };
        return shape("sphere", a, b, kind.equals("filled") ? "" : kind, (char) 0, a.getX() - ceil, a.getY() - ceil, a.getZ() - ceil,
                a.getX() + ceil, a.getY() + ceil, a.getZ() + ceil, m);
    }

    /** A ring round {@code a} across {@code axis}: as wide as {@code b} is from {@code a} in that plane, as thick as it is along the axis. */
    static Area torus(BlockPos a, BlockPos b, char axis, String kind) {
        int du = u(b, axis) - u(a, axis), dv = v(b, axis) - v(a, axis);
        int major = (int) Math.round(Math.sqrt(du * du + dv * dv));
        int minor = Math.abs(w(b, axis) - w(a, axis));
        Membership m = (x, y, z) -> {
            long cu = u(x, y, z, axis) - u(a, axis), cv = v(x, y, z, axis) - v(a, axis), cw = w(x, y, z, axis) - w(a, axis);
            long sum = cu * cu + cv * cv + cw * cw + (long) major * major;
            long round = 4L * major * major * (cu * cu + cv * cv);
            long outer = sum - (long) minor * minor;
            if (outer * outer >= round) {
                return false;
            }
            if (!kind.equals("hollow")) {
                return true;
            }
            long inner = sum - (long) (minor - 1) * (minor - 1);
            return inner * inner >= round;
        };
        int reach = major + minor;
        return bounded("torus", a, b, kind, axis, u(a, axis) - reach, v(a, axis) - reach, w(a, axis) - minor, u(a, axis) + reach, v(a, axis) + reach, w(a, axis) + minor, m);
    }

    /** The line between the corners seen along {@code axis}, run out along it as far as the corners go: a flat sheet. */
    static Area wall(BlockPos a, BlockPos b, char axis) {
        LongOpenHashSet on = new LongOpenHashSet();
        double du = u(b, axis) - u(a, axis), dv = v(b, axis) - v(a, axis);
        int samples = Math.max(1, (int) Math.ceil(Math.sqrt(du * du + dv * dv) * 10));
        for (int i = 0; i <= samples; i++) {
            double t = (double) i / samples;
            on.add(flat((int) Math.floor(u(a, axis) + 0.5 + du * t), (int) Math.floor(v(a, axis) + 0.5 + dv * t)));
        }
        return shape("wall", a, b, "", axis, 0, (x, y, z) -> on.contains(flat(u(x, y, z, axis), v(x, y, z, axis))));
    }

    private static long flat(int u, int v) {
        return (long) u << 32 ^ v & 0xFFFFFFFFL;
    }

    /** A shape bounded in the axis's own terms: across it {@code u} and {@code v}, along it {@code w}. */
    private static Shape bounded(String name, BlockPos a, BlockPos b, String kind, char axis, int minU, int minV, int minW, int maxU, int maxV, int maxW, Membership m) {
        String shown = kind.equals("filled") ? "" : kind;
        return switch (axis) {
            case 'x' -> shape(name, a, b, shown, axis, minW, minU, minV, maxW, maxU, maxV, m);
            case 'y' -> shape(name, a, b, shown, axis, minU, minW, minV, maxU, maxW, maxV, m);
            default -> shape(name, a, b, shown, axis, minU, minV, minW, maxU, maxV, maxW, m);
        };
    }

    // what lies across an axis and along it: across x are y and z, across y are x and z, across z are x and y

    private static int u(int x, int y, int z, char axis) {
        return axis == 'x' ? y : x;
    }

    private static int v(int x, int y, int z, char axis) {
        return axis == 'z' ? y : z;
    }

    private static int w(int x, int y, int z, char axis) {
        return axis == 'x' ? x : axis == 'y' ? y : z;
    }

    private static int u(BlockPos p, char axis) {
        return u(p.getX(), p.getY(), p.getZ(), axis);
    }

    private static int v(BlockPos p, char axis) {
        return v(p.getX(), p.getY(), p.getZ(), axis);
    }

    private static int w(BlockPos p, char axis) {
        return w(p.getX(), p.getY(), p.getZ(), axis);
    }
}
