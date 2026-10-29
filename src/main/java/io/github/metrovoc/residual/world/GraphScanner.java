package io.github.metrovoc.residual.world;

import io.github.metrovoc.residual.block.AcceleratorBlock;
import io.github.metrovoc.residual.block.CableBlock;
import io.github.metrovoc.residual.block.CoreBlock;
import io.github.metrovoc.residual.block.OperatorBlock;
import io.github.metrovoc.residual.block.OperatorBlockEntity;
import io.github.metrovoc.residual.block.Port;
import io.github.metrovoc.residual.graph.GraphSpec;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Reads the model wired to a Model Core out of the world. Ports touch either a cable network or, face to face,
 * another machine's port; every network must have exactly one driver (an output, or the Core's token face).
 */
public final class GraphScanner {
    public static final int MAX_MACHINES = 256;
    public static final int MAX_CABLES = 8192;
    public static final int MAX_ACCELERATORS = 64;

    /**
     * The model found in the world: node {@code i} of {@link #spec} is the machine at {@code nodes.get(i)}.
     *
     * @param footprint every block the model depends on, as packed positions
     * @param problem   why the scan gave up early, or null
     */
    public record Scan(GraphSpec spec, List<BlockPos> nodes, Set<Long> footprint, @Nullable String problem) {
        public int indexOf(BlockPos pos) {
            return nodes.indexOf(pos);
        }
    }

    /** A port face touching a network. */
    private record Attachment(BlockPos pos, Direction face, Port port) {
    }

    private final Level level;
    private final BlockPos core;
    private final Map<BlockPos, Integer> index = new HashMap<>();
    private final List<BlockPos> nodes = new ArrayList<>();
    private final List<List<Attachment>> nets = new ArrayList<>();
    private final Map<Long, Integer> portNet = new HashMap<>();
    private final Set<BlockPos> cables = new HashSet<>();
    private final Set<Long> footprint = new HashSet<>();
    private final Deque<BlockPos> queue = new ArrayDeque<>();
    private String problem;

    private GraphScanner(Level level, BlockPos core) {
        this.level = level;
        this.core = core;
    }

    public static Scan scan(Level level, BlockPos core) {
        return new GraphScanner(level, core).run();
    }

    private Scan run() {
        footprint.add(core.asLong());
        explore(core);
        while (!queue.isEmpty() && problem == null) {
            explore(queue.poll());
        }

        GraphSpec spec = new GraphSpec();
        for (BlockPos p : nodes) {
            OperatorBlockEntity be = (OperatorBlockEntity) level.getBlockEntity(p);
            spec.addUnwired(be.name(), be.op(), be.config().copy(), p.asLong());
        }
        for (int i = 0; i < nodes.size(); i++) {
            BlockPos p = nodes.get(i);
            GraphSpec.Node node = spec.node(i);
            Direction facing = level.getBlockState(p).getValue(OperatorBlock.FACING);
            if (node.op.arity >= 1) {
                node.inputs[0] = source(portNet.get(key(p, facing.getOpposite())));
            }
            if (node.op.arity == 2) {
                int side = GraphSpec.MISSING;
                for (Direction d : Direction.values()) {
                    if (d.getAxis() != facing.getAxis()) {
                        side = merge(side, source(portNet.get(key(p, d))));
                    }
                }
                node.inputs[1] = side;
            }
        }
        Direction front = level.getBlockState(core).getValue(CoreBlock.FACING);
        int out = GraphSpec.MISSING;
        for (Direction d : Direction.values()) {
            if (d != front) {
                out = merge(out, source(portNet.get(key(core, d))));
            }
        }
        spec.output = out;
        return new Scan(spec, List.copyOf(nodes), footprint, problem);
    }

    /** Combines two sources reaching the same input: one wins over nothing, two different ones conflict. */
    private static int merge(int a, int b) {
        if (a == GraphSpec.MISSING) {
            return b;
        }
        if (b == GraphSpec.MISSING || a == b) {
            return a;
        }
        return GraphSpec.CONFLICT;
    }

    /** The node driving a network: TOKENS for our Core's front, MISSING if undriven, CONFLICT if contested. */
    private int source(@Nullable Integer net) {
        if (net == null || net < 0) {
            return GraphSpec.MISSING;
        }
        int found = GraphSpec.MISSING;
        int drivers = 0;
        for (Attachment a : nets.get(net)) {
            if (!a.port.drives()) {
                continue;
            }
            drivers++;
            if (a.port == Port.TOKENS) {
                found = a.pos.equals(core) ? GraphSpec.TOKENS : GraphSpec.CONFLICT;
            } else {
                Integer node = index.get(a.pos);
                found = node == null ? GraphSpec.MISSING : node;
            }
        }
        return drivers > 1 ? GraphSpec.CONFLICT : found;
    }

    private void explore(BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        for (Direction d : Direction.values()) {
            Port p = Port.at(state, d);
            if (p != Port.NONE) {
                resolve(pos, d, p);
            }
        }
    }

    /** Finds what the port at {@code pos}/{@code face} connects to. */
    private void resolve(BlockPos pos, Direction face, Port port) {
        long key = key(pos, face);
        if (portNet.containsKey(key)) {
            return;
        }
        BlockPos n = pos.relative(face);
        if (!level.isLoaded(n)) {
            portNet.put(key, -1);
            return;
        }
        BlockState ns = level.getBlockState(n);
        if (ns.getBlock() instanceof CableBlock) {
            flood(n);
            return;
        }
        Port other = Port.at(ns, face.getOpposite());
        if (other == Port.NONE) {
            portNet.put(key, -1);
            return;
        }
        int net = nets.size();
        nets.add(List.of(new Attachment(pos, face, port), new Attachment(n, face.getOpposite(), other)));
        portNet.put(key, net);
        portNet.put(key(n, face.getOpposite()), net);
        visit(n, ns);
    }

    /** Collects a whole cable network and every port touching it. */
    private void flood(BlockPos start) {
        int net = nets.size();
        List<Attachment> attached = new ArrayList<>();
        nets.add(attached);
        Deque<BlockPos> todo = new ArrayDeque<>();
        todo.add(start);
        cables.add(start);
        while (!todo.isEmpty()) {
            BlockPos c = todo.poll();
            footprint.add(c.asLong());
            CableBlock cable = (CableBlock) level.getBlockState(c).getBlock();
            for (Direction d : Direction.values()) {
                BlockPos n = c.relative(d);
                if (!level.isLoaded(n)) {
                    continue;
                }
                BlockState ns = level.getBlockState(n);
                if (!cable.attaches(ns, d)) {
                    continue;
                }
                if (ns.getBlock() instanceof CableBlock) {
                    if (cables.add(n)) {
                        if (cables.size() > MAX_CABLES) {
                            problem = "This model uses more than " + MAX_CABLES + " cables.";
                            return;
                        }
                        todo.add(n);
                    }
                } else {
                    Direction face = d.getOpposite();
                    attached.add(new Attachment(n, face, Port.at(ns, face)));
                    portNet.put(key(n, face), net);
                    visit(n, ns);
                }
            }
        }
    }

    private void visit(BlockPos pos, BlockState state) {
        footprint.add(pos.asLong());
        if (!(state.getBlock() instanceof OperatorBlock) || index.containsKey(pos)
                || !(level.getBlockEntity(pos) instanceof OperatorBlockEntity)) {
            return;
        }
        if (nodes.size() >= MAX_MACHINES) {
            problem = "This model has more than " + MAX_MACHINES + " machines.";
            return;
        }
        index.put(pos, nodes.size());
        nodes.add(pos);
        queue.add(pos);
    }

    private static long key(BlockPos pos, Direction face) {
        return pos.asLong() * 8 + face.ordinal();
    }

    /** A port touching a cable network. */
    public record Touch(BlockPos pos, Port port) {
    }

    /** Every port touching the cable network that contains {@code cable}, for the Tensor Probe. */
    public static List<Touch> network(Level level, BlockPos cable) {
        GraphScanner s = new GraphScanner(level, cable);
        s.flood(cable);
        List<Touch> out = new ArrayList<>();
        for (Attachment a : s.nets.get(0)) {
            out.add(new Touch(a.pos, a.port));
        }
        return out;
    }

    /** Accelerators touching the Core, directly or through each other. */
    public static Set<BlockPos> accelerators(Level level, BlockPos core) {
        Set<BlockPos> seen = new HashSet<>();
        Deque<BlockPos> todo = new ArrayDeque<>();
        todo.add(core);
        while (!todo.isEmpty() && seen.size() < MAX_ACCELERATORS) {
            BlockPos p = todo.poll();
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (level.isLoaded(n) && level.getBlockState(n).getBlock() instanceof AcceleratorBlock && seen.add(n)) {
                    todo.add(n);
                }
            }
        }
        return seen;
    }
}
