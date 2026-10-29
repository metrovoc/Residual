package io.github.metrovoc.residual.bench;

import io.github.metrovoc.residual.tensor.Prims;
import io.github.metrovoc.residual.tensor.Prims.Binary;
import io.github.metrovoc.residual.tensor.Prims.Unary;
import io.github.metrovoc.residual.tensor.Tape;
import io.github.metrovoc.residual.tensor.Tensor;

/** The primitive operations on a Bench, the smallest parts a design is wired from. */
public enum Prim {
    CONST("const", 0, 0, Group.SOURCE),
    SIZE("size", 1, 0, Group.SOURCE),
    ADD("add", 2, 1, Group.ARITHMETIC),
    SUB("sub", 2, 1, Group.ARITHMETIC),
    MUL("mul", 2, 1, Group.ARITHMETIC),
    DIV("div", 2, 1, Group.ARITHMETIC),
    MAX("max", 2, 1, Group.ARITHMETIC),
    NEG("neg", 1, 1, Group.FUNCTION),
    EXP("exp", 1, 2, Group.FUNCTION),
    LOG("log", 1, 2, Group.FUNCTION),
    SQRT("sqrt", 1, 2, Group.FUNCTION),
    TANH("tanh", 1, 2, Group.FUNCTION),
    SUM("sum", 1, 2, Group.AXIS),
    AMAX("amax", 1, 2, Group.AXIS),
    TRANSPOSE("transpose", 1, 1, Group.AXIS),
    UNSQUEEZE("unsqueeze", 1, 0, Group.AXIS),
    SQUEEZE("squeeze", 1, 0, Group.AXIS),
    CAUSAL("causal", 1, 1, Group.AXIS);

    /** Where a primitive sits in the Bench's palette. */
    public enum Group { SOURCE, ARITHMETIC, FUNCTION, AXIS }

    public final String id;
    public final int arity;
    /** Its share of a design's area: what fabricating it costs. */
    public final int area;
    public final Group group;

    Prim(String id, int arity, int area, Group group) {
        this.id = id;
        this.arity = arity;
        this.area = area;
        this.group = group;
    }

    public static Prim byId(String id) {
        for (Prim p : values()) {
            if (p.id.equals(id)) {
                return p;
            }
        }
        return null;
    }

    /** Runs the primitive; {@code value} is a constant's number. */
    public Tensor apply(Tape tape, Tensor[] in, float value) {
        return switch (this) {
            case CONST -> Prims.scalar(value);
            case SIZE -> Prims.size(in[0]);
            case ADD -> Prims.binary(tape, Binary.ADD, in[0], in[1]);
            case SUB -> Prims.binary(tape, Binary.SUB, in[0], in[1]);
            case MUL -> Prims.binary(tape, Binary.MUL, in[0], in[1]);
            case DIV -> Prims.binary(tape, Binary.DIV, in[0], in[1]);
            case MAX -> Prims.binary(tape, Binary.MAX, in[0], in[1]);
            case NEG -> Prims.unary(tape, Unary.NEG, in[0]);
            case EXP -> Prims.unary(tape, Unary.EXP, in[0]);
            case LOG -> Prims.unary(tape, Unary.LOG, in[0]);
            case SQRT -> Prims.unary(tape, Unary.SQRT, in[0]);
            case TANH -> Prims.unary(tape, Unary.TANH, in[0]);
            case SUM -> Prims.sum(tape, in[0]);
            case AMAX -> Prims.amax(tape, in[0]);
            case TRANSPOSE -> Prims.transpose(tape, in[0]);
            case UNSQUEEZE -> Prims.unsqueeze(tape, in[0]);
            case SQUEEZE -> Prims.squeeze(tape, in[0]);
            case CAUSAL -> Prims.causal(tape, in[0]);
        };
    }
}
