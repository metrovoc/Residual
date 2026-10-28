package io.github.metrovoc.residual.graph;

import java.util.LinkedHashMap;
import java.util.Map;

/** Settings of one operator, stored as strings and validated against the operator's fields. */
public final class Config {
    private final Op op;
    private final LinkedHashMap<String, String> values = new LinkedHashMap<>();

    public Config(Op op) {
        this.op = op;
        for (ConfigField f : op.fields()) {
            values.put(f.key(), f.fallback());
        }
    }

    public static Config of(Op op, String... keyValues) {
        Config c = new Config(op);
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            c.set(keyValues[i], keyValues[i + 1]);
        }
        return c;
    }

    public Op op() {
        return op;
    }

    /** Sets a value if the key exists and the value is allowed; returns whether anything changed. */
    public boolean set(String key, String value) {
        ConfigField f = op.field(key);
        if (f == null || !f.choices().contains(value) || value.equals(values.get(key))) {
            return false;
        }
        values.put(key, value);
        return true;
    }

    public String get(String key) {
        return values.get(key);
    }

    public boolean flag(String key) {
        return Boolean.parseBoolean(values.get(key));
    }

    /** An integer setting; {@code vocab} resolves to the vocabulary size. */
    public int width(String key, int vocab) {
        String v = values.get(key);
        return ConfigField.VOCAB.equals(v) ? vocab : Integer.parseInt(v);
    }

    public int integer(String key) {
        return Integer.parseInt(values.get(key));
    }

    public Map<String, String> asMap() {
        return values;
    }

    public Config copy() {
        Config c = new Config(op);
        c.values.putAll(values);
        return c;
    }

    @Override
    public String toString() {
        return op.id + values;
    }
}
