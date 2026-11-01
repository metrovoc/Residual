package io.github.metrovoc.residual.agent;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Values in a save: each as a program would write it, read back without asking the world anything. Numbers, text, flags,
 * positions and lists of them come back as they were, and anything else as nothing.
 */
public final class Saved {
    private static final Codec<Map<String, String>> TEXTS = Codec.unboundedMap(Codec.STRING, Codec.STRING);

    private Saved() {
    }

    static Value read(String text) {
        Expr e = Program.expression(text);
        return e == null ? Value.NONE : value(e);
    }

    private static Value value(Expr e) {
        return switch (e) {
            case Expr.Lit l -> l.value();
            case Expr.Unary u when u.op().equals("-") -> Value.of(-Eval.num(value(u.e())));
            case Expr.Items l -> {
                List<Value> out = new ArrayList<>();
                l.items().forEach(x -> out.add(value(x)));
                yield new Value.Many(List.copyOf(out));
            }
            case Expr.Call c when c.fn().equals("pos") && c.args().size() == 3 -> new Value.Place(new BlockPos(
                    (int) Eval.num(value(c.args().get(0))), (int) Eval.num(value(c.args().get(1))), (int) Eval.num(value(c.args().get(2)))));
            case Expr.Words w -> Value.of(w.words());
            default -> Value.NONE;
        };
    }

    /** A body's own variables. */
    public static void write(ValueOutput out, Map<String, Value> vars) {
        if (!vars.isEmpty()) {
            Map<String, String> texts = new HashMap<>();
            vars.forEach((name, v) -> texts.put(name, v.source()));
            out.store("vars", TEXTS, texts);
        }
    }

    public static void read(ValueInput in, Map<String, Value> vars) {
        vars.clear();
        in.read("vars", TEXTS).ifPresent(texts -> texts.forEach((name, text) -> vars.put(name, read(text))));
    }
}
