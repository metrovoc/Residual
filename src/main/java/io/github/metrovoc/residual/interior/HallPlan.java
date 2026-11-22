package io.github.metrovoc.residual.interior;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.util.StringRepresentable;

/**
 * Where everything in a model's hall stands, in blocks from the hall's corner. Text runs across (x), a lane for each
 * token; the stream runs along (z), from the tokens to what the model predicts; each part stands across every lane.
 * A walkway runs down the left side with a console at each station.
 *
 * @param lanes how many tokens the hall shows side by side
 * @param pitch how many blocks apart the lanes are
 */
public record HallPlan(int lanes, int pitch, List<Station> stations) {
    /** Blocks of walkway before the first lane, and of margin after the last. */
    public static final int WALK = 5, MARGIN = 3;
    /** How high the walls stand above the floor. */
    public static final int HEIGHT = 12;

    public enum Type {
        /** Where a player arrives, with the controls of the run. */
        ENTRANCE(16),
        TOKENS(4),
        EMBED(6),
        POSITION(6),
        /** An empty place between two stations where a part can be put. */
        BAY(4),
        ATTENTION(10),
        FEED_FORWARD(6),
        /** Where the stream is read out as chances for the next token. */
        READOUT(9),
        /** The far wall, with the loss. */
        END(4);

        public final int depth;

        Type(int depth) {
            this.depth = depth;
        }
    }

    /**
     * @param z     where it begins along the hall
     * @param part  the part's own number, or for a bay the index a part put there would take, or -1
     * @param on    whether the part adds to the stream
     */
    public record Station(Type type, int z, int part, boolean on) {
        public int end() {
            return z + type.depth;
        }

        /** The middle of the station along the hall. */
        public double mid() {
            return z + type.depth / 2.0;
        }
    }

    /** What a console does when it is used; sneaking turns a dial the other way. */
    public enum Role implements StringRepresentable {
        EXIT, TASK, RUN, STEP, PACE, RATE, RESTART, EXAMPLE, WIDTH, POSITIONS, HEADS, NORM, ADD_ATTENTION, ADD_FEED_FORWARD, SWITCH, REMOVE;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * A console on the walkway's edge.
     *
     * @param part the part it works on, or the index a part it adds would take, or -1
     */
    public record Console(Role role, int x, int z, int part) {
    }

    /** Every console of the hall, each beside what it works on. */
    public List<Console> consoles() {
        List<Console> out = new ArrayList<>();
        for (Station s : stations) {
            Role[] roles = switch (s.type) {
                case ENTRANCE -> new Role[]{Role.EXIT, Role.TASK, Role.RUN, Role.STEP, Role.PACE, Role.RATE, Role.RESTART};
                case TOKENS -> new Role[]{Role.EXAMPLE};
                case EMBED -> new Role[]{Role.WIDTH};
                case POSITION -> new Role[]{Role.POSITIONS};
                case BAY -> new Role[]{Role.ADD_ATTENTION, Role.ADD_FEED_FORWARD};
                case ATTENTION -> new Role[]{Role.SWITCH, Role.HEADS, Role.REMOVE};
                case FEED_FORWARD -> new Role[]{Role.SWITCH, Role.REMOVE};
                case READOUT -> new Role[]{Role.NORM};
                case END -> new Role[0];
            };
            // two blocks apart, so that each one's label has room
            int z = s.z + (s.type.depth - (roles.length * 2 - 1) + 1) / 2;
            for (Role role : roles) {
                out.add(new Console(role, 0, z, s.part));
                z += 2;
            }
        }
        return out;
    }

    public static HallPlan of(Arch arch, int lanes) {
        List<Station> out = new ArrayList<>();
        int z = 0;
        z = add(out, Type.ENTRANCE, z, -1, true);
        z = add(out, Type.TOKENS, z, -1, true);
        z = add(out, Type.EMBED, z, -1, true);
        z = add(out, Type.POSITION, z, -1, arch.positions());
        for (int i = 0; i < arch.parts().size(); i++) {
            Arch.Part p = arch.parts().get(i);
            z = add(out, Type.BAY, z, i, true);
            z = add(out, p.kind() == Arch.Kind.ATTENTION ? Type.ATTENTION : Type.FEED_FORWARD, z, p.id(), p.on());
        }
        if (arch.parts().size() < Arch.MAX_PARTS) {
            z = add(out, Type.BAY, z, arch.parts().size(), true);
        }
        z = add(out, Type.READOUT, z, -1, true);
        add(out, Type.END, z, -1, true);
        return new HallPlan(lanes, lanes > 32 ? 1 : 2, List.copyOf(out));
    }

    private static int add(List<Station> out, Type type, int z, int part, boolean on) {
        out.add(new Station(type, z, part, on));
        return z + type.depth;
    }

    /** Blocks across, walls not counted. */
    public int width() {
        return WALK + lanes * pitch + MARGIN;
    }

    /** Blocks along, walls not counted. */
    public int length() {
        return stations.getLast().end();
    }

    /**
     * The middle of lane {@code t}, across the hall. The first lane is the one farthest from the walkway, so that from
     * the entrance, looking down the stream with the walkway on the right, the text reads from left to right.
     */
    public double lane(int t) {
        return WALK + (lanes - 1 - t) * pitch + pitch / 2.0;
    }

    /** The lane a point across the hall is on, or -1. */
    public int laneAt(double x) {
        double from = x - WALK;
        return from < 0 || from >= lanes * pitch ? -1 : lanes - 1 - (int) (from / pitch);
    }

    public Station first(Type type) {
        for (Station s : stations) {
            if (s.type == type) {
                return s;
            }
        }
        throw new IllegalStateException("no " + type);
    }

    /** The station of the part numbered {@code id}, or null. */
    public Station part(int id) {
        for (Station s : stations) {
            if ((s.type == Type.ATTENTION || s.type == Type.FEED_FORWARD) && s.part == id) {
                return s;
            }
        }
        return null;
    }
}
