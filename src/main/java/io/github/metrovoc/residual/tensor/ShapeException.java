package io.github.metrovoc.residual.tensor;

import net.minecraft.network.chat.Component;

/** A shape or value mismatch with a message written for players, not programmers. */
public final class ShapeException extends RuntimeException {
    public final Component message;

    /** The message is the translation {@code key} with {@code args}: components, or anything else as its string. */
    public ShapeException(String key, Object... args) {
        super(key);
        Object[] shown = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            shown[i] = args[i] instanceof Component c ? c : String.valueOf(args[i]);
        }
        this.message = Component.translatable(key, shown);
    }
}
