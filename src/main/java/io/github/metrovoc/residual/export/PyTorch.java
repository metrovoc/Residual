package io.github.metrovoc.residual.export;

import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.graph.Config;
import io.github.metrovoc.residual.graph.ConfigField;
import io.github.metrovoc.residual.graph.Env;
import io.github.metrovoc.residual.graph.GraphSpec;
import io.github.metrovoc.residual.graph.Op;
import io.github.metrovoc.residual.graph.Sym;
import io.github.metrovoc.residual.tensor.Tensor;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns a compiled graph into an equivalent PyTorch module, plus its weights in {@code state_dict} layout.
 * The generated file loads the weights, so a model trained in-game samples text in real PyTorch.
 */
public final class PyTorch {
    /** A tensor named and laid out as PyTorch expects it. */
    public record Weight(String name, int[] shape, float[] data) {
    }

    /** Names a node can't take: Python keywords, the generated file's own names, and nn.Module attributes. */
    private static final Set<String> RESERVED = Set.of(("and as assert async await break case class continue def del elif"
            + " else except finally for from global if import in is lambda match nonlocal not or pass raise return try type"
            + " while with yield tokens self torch nn math sys load generate gelu_tanh causal_mask split_heads merge_heads"
            + " delay add_module apply bfloat16 buffers call_super_init children compile cpu cuda double dump_patches eval"
            + " extra_repr float forward get_buffer get_extra_state get_parameter get_submodule half ipu load_state_dict"
            + " modules mtia named_buffers named_children named_modules named_parameters parameters register_backward_hook"
            + " register_buffer register_forward_hook register_forward_pre_hook register_full_backward_hook"
            + " register_full_backward_pre_hook register_load_state_dict_post_hook register_load_state_dict_pre_hook"
            + " register_module register_parameter register_state_dict_post_hook register_state_dict_pre_hook requires_grad_"
            + " set_extra_state set_submodule share_memory state_dict to to_empty train training xpu zero_grad").split(" "));

    private PyTorch() {
    }

    /** Python identifiers for every node: the player's name for it where possible. */
    public static String[] identifiers(GraphSpec spec) {
        String[] ids = new String[spec.size()];
        Set<String> used = new HashSet<>(RESERVED);
        for (int i = 0; i < spec.size(); i++) {
            GraphSpec.Node n = spec.node(i);
            String base = sanitize(n.name);
            if (base.isEmpty()) {
                base = n.op.id;
            }
            String id = base;
            int k = 2;
            while (used.contains(id)) {
                id = base + "_" + k++;
            }
            used.add(id);
            ids[i] = id;
        }
        return ids;
    }

    static String sanitize(String name) {
        if (name == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (char c : name.toLowerCase().toCharArray()) {
            if (Character.isLetterOrDigit(c) && c < 128) {
                sb.append(c);
            } else if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '_') {
                sb.append('_');
            }
        }
        while (sb.length() > 0 && sb.charAt(sb.length() - 1) == '_') {
            sb.setLength(sb.length() - 1);
        }
        if (sb.length() > 0 && Character.isDigit(sb.charAt(0))) {
            sb.insert(0, '_');
        }
        return sb.toString();
    }

    /**
     * The constructor line for a node's module, or null if it has no parameters.
     *
     * @param features size of the main input's last dimension, or -1 if not known yet
     */
    public static String declaration(String id, Op op, Config c, int features, Env env) {
        String in = features > 0 ? Integer.toString(features) : "in_features";
        // a machine that was never checked has no vocabulary yet: name the sizes instead
        String vocab = env.vocab() > 0 ? Integer.toString(env.vocab()) : "vocab_size";
        String context = env.context() > 0 ? Integer.toString(env.context()) : "context";
        return switch (op) {
            case EMBEDDING -> "self." + id + " = nn.Embedding(" + vocab + ", " + width(c, "dim", vocab) + ")";
            case POSITION -> "self." + id + " = nn.Embedding(" + context + ", " + width(c, "dim", vocab) + ")";
            case LINEAR -> "self." + id + " = nn.Linear(" + in + ", " + width(c, "out", vocab)
                    + (c.flag("bias") ? "" : ", bias=False") + ")";
            case NORM -> "self." + id + " = " + ("rms".equals(c.get("kind")) ? "RMSNorm(" : "nn.LayerNorm(") + in + ")";
            case ATTENTION -> "self." + id + " = CausalSelfAttention(" + in + ", heads=" + c.get("heads") + ")";
            case MLP -> "self." + id + " = FeedForward(" + in + ", " + hidden(c, features) + ", " + activation(c.get("fn")) + ")";
            case TRANSFORMER -> "self." + id + " = Block(" + in + ", heads=" + c.get("heads") + ", hidden=" + hidden(c, features)
                    + ", act=" + activation(c.get("fn")) + ")";
            default -> null;
        };
    }

    private static String hidden(Config c, int features) {
        return features > 0 ? Integer.toString(features * c.integer("expand")) : c.get("expand") + " * in_features";
    }

    private static String width(Config c, String key, String vocab) {
        return ConfigField.VOCAB.equals(c.get(key)) ? vocab : c.get(key);
    }

    /** The right-hand side of a node's line in {@code forward}. */
    public static String expression(String id, Op op, Config c, String[] args, int features) {
        String a = args.length > 0 ? args[0] : "";
        String b = args.length > 1 ? args[1] : "";
        return switch (op) {
            case EMBEDDING, LINEAR, NORM, ATTENTION, MLP, TRANSFORMER -> "self." + id + "(" + a + ")";
            case POSITION -> "self." + id + "(torch.arange(tokens.shape[1], device=tokens.device))";
            case ACTIVATION -> switch (c.get("fn")) {
                case "gelu" -> "F.gelu(" + a + ", approximate=\"tanh\")";
                case "tanh" -> "torch.tanh(" + a + ")";
                case "sigmoid" -> "torch.sigmoid(" + a + ")";
                case "silu" -> "F.silu(" + a + ")";
                default -> "F.relu(" + a + ")";
            };
            case ADD -> a + " + " + b;
            case MULTIPLY -> a + " * " + b;
            case MATMUL -> a + " @ " + b + (c.flag("transpose") ? ".transpose(-2, -1)" : "");
            case SCALE -> "inv_sqrt".equals(c.get("factor"))
                    ? a + " / math.sqrt(" + (features > 0 ? Integer.toString(features) : a + ".shape[-1]") + ")"
                    : a + " * " + c.get("factor");
            case CAUSAL_MASK -> "causal_mask(" + a + ")";
            case SOFTMAX -> "F.softmax(" + a + ", dim=-1)";
            case SPLIT_HEADS -> "split_heads(" + a + ", " + c.get("heads") + ")";
            case MERGE_HEADS -> "merge_heads(" + a + ")";
            case SHIFT -> "delay(" + a + ", " + c.get("steps") + ")";
            case CONCAT -> "torch.cat([" + a + ", " + b + "], dim=-1)";
        };
    }

    /** The PyTorch lines for a single machine, shown on its screen: its module (if any) and its forward step. */
    public static List<String> preview(Op op, Config c, String name, int features, Env env) {
        String id = sanitize(name);
        if (id.isEmpty() || RESERVED.contains(id)) {
            id = op.id;
        }
        String[] args = switch (op.arity) {
            case 0 -> new String[0];
            case 1 -> new String[]{op == Op.EMBEDDING ? "tokens" : "x"};
            default -> new String[]{"x", "y"};
        };
        List<String> lines = new ArrayList<>();
        String decl = declaration(id, op, c, features, env);
        if (decl != null) {
            lines.add(decl);
        }
        lines.add(id + " = " + expression(id, op, c, args, features));
        return lines;
    }

    static String activation(String fn) {
        return switch (fn) {
            case "gelu" -> "gelu_tanh";
            case "tanh" -> "torch.tanh";
            case "sigmoid" -> "torch.sigmoid";
            case "silu" -> "F.silu";
            default -> "F.relu";
        };
    }

    /** The full Python source of a model, its helpers and a small sampling script. */
    public static String module(CompiledGraph g, String vocabSymbols, String title) {
        GraphSpec spec = g.spec;
        Env env = g.env;
        String[] ids = identifiers(spec);
        EnumSet<Op> used = EnumSet.noneOf(Op.class);
        for (int node : g.order) {
            used.add(spec.node(node).op);
        }
        List<String> init = new ArrayList<>();
        List<String> forward = new ArrayList<>();
        for (int node : g.order) {
            GraphSpec.Node n = spec.node(node);
            int features = n.op.arity > 0 ? inputs(g, n)[0].dim(-1) : -1;
            String decl = declaration(ids[node], n.op, n.config, features, env);
            if (decl != null) {
                init.add(decl);
            }
            String[] args = new String[n.op.arity];
            for (int i = 0; i < args.length; i++) {
                args[i] = n.inputs[i] == GraphSpec.TOKENS ? "tokens" : ids[n.inputs[i]];
            }
            forward.add(ids[node] + " = " + expression(ids[node], n.op, n.config, args, features));
        }

        StringBuilder py = new StringBuilder();
        py.append("# Generated by Residual: a language model built block by block in Minecraft.\n");
        py.append("# Lesson: ").append(title).append(" | vocabulary: ").append(env.vocab())
                .append(" tokens | context: ").append(env.context()).append(" | parameters: ").append(g.parameterCount()).append("\n");
        py.append("#\n# Run `python model.py \"your prompt\"` next to weights.bin to sample from the trained model.\n\n");
        py.append("import math\nimport sys\nfrom pathlib import Path\n\nimport torch\nimport torch.nn as nn\nimport torch.nn.functional as F\n\n");
        py.append("VOCAB = ").append(pyString(vocabSymbols)).append("\n");
        py.append("CONTEXT = ").append(env.context()).append("\n\n");

        boolean attention = used.contains(Op.ATTENTION) || used.contains(Op.TRANSFORMER);
        if (init.stream().anyMatch(l -> l.contains("gelu_tanh"))) {
            py.append("\ndef gelu_tanh(x):\n    return F.gelu(x, approximate=\"tanh\")\n\n");
        }
        if (attention || used.contains(Op.CAUSAL_MASK)) {
            py.append("\ndef causal_mask(scores):\n    \"\"\"Hide the future: position t may only attend to positions <= t.\"\"\"\n")
                    .append("    T = scores.shape[-1]\n")
                    .append("    future = torch.triu(torch.ones(T, T, dtype=torch.bool, device=scores.device), diagonal=1)\n")
                    .append("    return scores.masked_fill(future, float(\"-inf\"))\n\n");
        }
        if (attention || used.contains(Op.SPLIT_HEADS) || used.contains(Op.MERGE_HEADS)) {
            py.append("\ndef split_heads(x, heads):\n    B, T, C = x.shape\n    return x.view(B, T, heads, C // heads).transpose(1, 2)\n\n");
            py.append("\ndef merge_heads(x):\n    B, H, T, D = x.shape\n    return x.transpose(1, 2).reshape(B, T, H * D)\n\n");
        }
        if (used.contains(Op.SHIFT)) {
            py.append("\ndef delay(x, steps):\n    \"\"\"Shift a sequence `steps` positions later in time, filling the start with zeros.\"\"\"\n")
                    .append("    return F.pad(x, (0, 0, steps, 0))[..., : x.shape[-2], :]\n\n");
        }
        if (usesRms(g)) {
            py.append("\nclass RMSNorm(nn.Module):\n    def __init__(self, dim, eps=1e-5):\n        super().__init__()\n")
                    .append("        self.eps = eps\n        self.weight = nn.Parameter(torch.ones(dim))\n\n")
                    .append("    def forward(self, x):\n        return x * torch.rsqrt(x.pow(2).mean(-1, keepdim=True) + self.eps) * self.weight\n\n");
        }
        if (attention) {
            py.append("\nclass CausalSelfAttention(nn.Module):\n    def __init__(self, dim, heads):\n        super().__init__()\n")
                    .append("        self.heads = heads\n")
                    .append("        self.q = nn.Linear(dim, dim, bias=False)\n        self.k = nn.Linear(dim, dim, bias=False)\n")
                    .append("        self.v = nn.Linear(dim, dim, bias=False)\n        self.o = nn.Linear(dim, dim, bias=False)\n\n")
                    .append("    def forward(self, x):\n")
                    .append("        q, k, v = (split_heads(f(x), self.heads) for f in (self.q, self.k, self.v))\n")
                    .append("        scores = q @ k.transpose(-2, -1) / math.sqrt(q.shape[-1])\n")
                    .append("        weights = F.softmax(causal_mask(scores), dim=-1)\n")
                    .append("        return self.o(merge_heads(weights @ v))\n\n");
        }
        if (used.contains(Op.MLP) || used.contains(Op.TRANSFORMER)) {
            py.append("\nclass FeedForward(nn.Module):\n    def __init__(self, dim, hidden, act):\n        super().__init__()\n")
                    .append("        self.w1 = nn.Linear(dim, hidden)\n        self.w2 = nn.Linear(hidden, dim)\n        self.act = act\n\n")
                    .append("    def forward(self, x):\n        return self.w2(self.act(self.w1(x)))\n\n");
        }
        if (used.contains(Op.TRANSFORMER)) {
            py.append("\nclass Block(nn.Module):\n    def __init__(self, dim, heads, hidden, act):\n        super().__init__()\n")
                    .append("        self.ln1 = nn.LayerNorm(dim)\n        self.attn = CausalSelfAttention(dim, heads)\n")
                    .append("        self.ln2 = nn.LayerNorm(dim)\n        self.mlp = FeedForward(dim, hidden, act)\n\n")
                    .append("    def forward(self, x):\n        x = x + self.attn(self.ln1(x))\n        return x + self.mlp(self.ln2(x))\n\n");
        }

        py.append("\nclass ResidualModel(nn.Module):\n    def __init__(self):\n        super().__init__()\n");
        if (init.isEmpty()) {
            py.append("        pass\n");
        }
        for (String line : init) {
            py.append("        ").append(line).append("\n");
        }
        py.append("\n    def forward(self, tokens):\n        # tokens: (batch, time) integer ids -> logits: (batch, time, vocab)\n");
        for (String line : forward) {
            py.append("        ").append(line).append("\n");
        }
        py.append("        return ").append(ids[spec.output]).append("\n\n");

        py.append("\n# (name, shape) of every tensor in weights.bin, in order (float32, little-endian)\nMANIFEST = [\n");
        for (Weight w : weights(g)) {
            py.append("    (").append(pyString(w.name())).append(", ").append(tuple(w.shape())).append("),\n");
        }
        py.append("]\n\n");
        py.append("""

                def load(path=Path(__file__).with_name("weights.bin")):
                    data = path.read_bytes()
                    state, offset = {}, 0
                    for name, shape in MANIFEST:
                        n = math.prod(shape)
                        chunk = bytearray(data[offset : offset + 4 * n])
                        state[name] = torch.frombuffer(chunk, dtype=torch.float32).reshape(shape)
                        offset += 4 * n
                    model = ResidualModel()
                    model.load_state_dict(state)
                    return model.eval()


                @torch.no_grad()
                def generate(model, prompt, count=200, temperature=0.8):
                    ids = [VOCAB.index(c) for c in prompt if c in VOCAB] or [0]
                    for _ in range(count):
                        logits = model(torch.tensor([ids[-CONTEXT:]]))[0, -1]
                        probs = F.softmax(logits / temperature, dim=-1)
                        ids.append(torch.multinomial(probs, 1).item())
                    return "".join(VOCAB[i] for i in ids)


                if __name__ == "__main__":
                    print(generate(load(), sys.argv[1] if len(sys.argv) > 1 else VOCAB[0]))
                """);
        return py.toString();
    }

    private static boolean usesRms(CompiledGraph g) {
        for (int node : g.order) {
            GraphSpec.Node n = g.spec.node(node);
            if (n.op == Op.NORM && "rms".equals(n.config.get("kind"))) {
                return true;
            }
        }
        return false;
    }

    static Sym[] inputs(CompiledGraph g, GraphSpec.Node n) {
        Sym[] in = new Sym[n.op.arity];
        for (int i = 0; i < in.length; i++) {
            in[i] = n.inputs[i] == GraphSpec.TOKENS ? Sym.tokens() : g.shapes[n.inputs[i]];
        }
        return in;
    }

    /** All parameters renamed and transposed into PyTorch's {@code state_dict} layout. */
    public static List<Weight> weights(CompiledGraph g) {
        return weights(g, false);
    }

    /** Like {@link #weights(CompiledGraph)}, but exports each parameter's gradient instead of its value. */
    public static List<Weight> weights(CompiledGraph g, boolean gradients) {
        String[] ids = identifiers(g.spec);
        List<Weight> out = new ArrayList<>();
        for (int node : g.order) {
            GraphSpec.Node n = g.spec.node(node);
            String id = ids[node];
            for (Map.Entry<String, Tensor> e : g.params(node).entrySet()) {
                String name = e.getKey();
                int[] shape = e.getValue().shape;
                float[] data = gradients ? e.getValue().grad() : e.getValue().data;
                switch (n.op) {
                    case LINEAR -> out.add(name.equals("weight") ? transposed(id + ".weight", shape, data) : plain(id + ".bias", shape, data));
                    case ATTENTION -> out.add(transposed(id + "." + name + ".weight", shape, data));
                    case MLP -> out.add(mlpWeight(id + ".", name, shape, data));
                    case TRANSFORMER -> {
                        if (name.startsWith("attn.")) {
                            out.add(transposed(id + "." + name + ".weight", shape, data));
                        } else if (name.startsWith("mlp.")) {
                            out.add(mlpWeight(id + ".mlp.", name.substring(4), shape, data));
                        } else {
                            out.add(plain(id + "." + name, shape, data));
                        }
                    }
                    default -> out.add(plain(id + "." + name, shape, data));
                }
            }
        }
        return out;
    }

    private static Weight mlpWeight(String prefix, String name, int[] shape, float[] data) {
        return switch (name) {
            case "w1" -> transposed(prefix + "w1.weight", shape, data);
            case "w2" -> transposed(prefix + "w2.weight", shape, data);
            case "b1" -> plain(prefix + "w1.bias", shape, data);
            default -> plain(prefix + "w2.bias", shape, data);
        };
    }

    private static Weight plain(String name, int[] shape, float[] data) {
        return new Weight(name, shape.clone(), data.clone());
    }

    /** Our weight matrices are [in, out] (y = x @ W); PyTorch's Linear stores [out, in]. */
    private static Weight transposed(String name, int[] shape, float[] data) {
        int r = shape[0];
        int c = shape[1];
        float[] d = new float[data.length];
        for (int i = 0; i < r; i++) {
            for (int j = 0; j < c; j++) {
                d[j * r + i] = data[i * c + j];
            }
        }
        return new Weight(name, new int[]{c, r}, d);
    }

    /** Concatenated little-endian float32 data of all weights, in manifest order. */
    public static byte[] weightBytes(List<Weight> weights) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        for (Weight w : weights) {
            ByteBuffer buf = ByteBuffer.allocate(w.data().length * 4).order(ByteOrder.LITTLE_ENDIAN);
            for (float f : w.data()) {
                buf.putFloat(f);
            }
            bytes.writeBytes(buf.array());
        }
        return bytes.toByteArray();
    }

    static String tuple(int[] shape) {
        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < shape.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(shape[i]);
        }
        if (shape.length == 1) {
            sb.append(',');
        }
        return sb.append(')').toString();
    }

    static String pyString(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\t' -> sb.append("\\t");
                case '\r' -> sb.append("\\r");
                default -> {
                    if (c < 32 || c > 126) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }
}
