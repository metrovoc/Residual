package io.github.metrovoc.residual.tensor;

import java.util.Arrays;

/**
 * A dense row-major float tensor with an optional gradient buffer.
 * Shapes are small (rank 1-4); the last dimension is the feature dimension.
 */
public final class Tensor {
    public final int[] shape;
    public final float[] data;
    public final boolean requiresGrad;
    private float[] grad;

    public Tensor(int[] shape, float[] data, boolean requiresGrad) {
        if (numel(shape) != data.length) {
            throw new IllegalArgumentException("shape " + Arrays.toString(shape) + " does not match " + data.length + " values");
        }
        this.shape = shape;
        this.data = data;
        this.requiresGrad = requiresGrad;
    }

    public static Tensor zeros(boolean requiresGrad, int... shape) {
        return new Tensor(shape.clone(), new float[numel(shape)], requiresGrad);
    }

    public static Tensor of(int[] shape, float... data) {
        return new Tensor(shape.clone(), data, false);
    }

    public static int numel(int[] shape) {
        int n = 1;
        for (int d : shape) {
            n *= d;
        }
        return n;
    }

    public int size() {
        return data.length;
    }

    public int rank() {
        return shape.length;
    }

    /** Size of dimension {@code i}; negative indices count from the end. */
    public int dim(int i) {
        return shape[i < 0 ? shape.length + i : i];
    }

    /** Product of all dimensions except the last. */
    public int rows() {
        return data.length / Math.max(1, dim(-1));
    }

    public boolean hasGrad() {
        return grad != null;
    }

    public float[] grad() {
        if (grad == null) {
            grad = new float[data.length];
        }
        return grad;
    }

    public void zeroGrad() {
        if (grad != null) {
            Arrays.fill(grad, 0f);
        }
    }

    public Tensor copy(boolean requiresGrad) {
        return new Tensor(shape.clone(), data.clone(), requiresGrad);
    }

    public boolean sameShape(Tensor other) {
        return Arrays.equals(shape, other.shape);
    }

    public float item() {
        return data[0];
    }

    public String shapeString() {
        return shapeString(shape);
    }

    public static String shapeString(int[] shape) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < shape.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(shape[i]);
        }
        return sb.append(']').toString();
    }

    @Override
    public String toString() {
        return "Tensor" + shapeString();
    }
}
