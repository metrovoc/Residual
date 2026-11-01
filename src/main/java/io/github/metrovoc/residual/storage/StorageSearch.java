package io.github.metrovoc.residual.storage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * A Storage Terminal's search. Words a thing's name must contain, all of them, a Chinese name by its pinyin too; {@code @}
 * for its mod, {@code #} for a tag, {@code $} for its tooltip and {@code *} for its id; {@code -} before a term or a group to
 * leave those out; {@code |} between alternatives; parentheses to group; and {@code >}, {@code <}, {@code =}, {@code >=} or
 * {@code <=} with a number (and k or m for thousands or millions) for how many there are. {@code @minecraft ingot -iron | >1k}
 * finds Minecraft's ingots other than iron, and anything there is more than a thousand of.
 */
public final class StorageSearch {
    /** What a search can ask about a thing kept. */
    public interface Facts {
        String name();

        String mod();

        String modName();

        Collection<String> tags();

        String tooltip();

        String id();

        long count();
    }

    private final List<String> tokens;
    private int at;

    private StorageSearch(String query) {
        tokens = tokens(query);
    }

    /** What {@code query} asks for. A query that does not parse finds what its words do as far as they go. */
    public static Predicate<Facts> parse(String query) {
        StorageSearch s = new StorageSearch(query);
        Predicate<Facts> p = s.or();
        while (s.at < s.tokens.size()) {
            // a stray ")" ends nothing: read on past it
            s.at++;
            p = p.and(s.or());
        }
        return p;
    }

    private static List<String> tokens(String query) {
        List<String> out = new ArrayList<>();
        StringBuilder word = new StringBuilder();
        for (int i = 0; i < query.length(); i++) {
            char c = query.charAt(i);
            boolean single = c == '(' || c == ')' || c == '|';
            if (Character.isWhitespace(c) || single || c == '-' && word.isEmpty()) {
                if (!word.isEmpty()) {
                    out.add(word.toString());
                    word.setLength(0);
                }
                if (single || c == '-') {
                    out.add(String.valueOf(c));
                }
            } else {
                word.append(c);
            }
        }
        if (!word.isEmpty()) {
            out.add(word.toString());
        }
        return out;
    }

    private Predicate<Facts> or() {
        Predicate<Facts> p = and();
        while (at < tokens.size() && tokens.get(at).equals("|")) {
            at++;
            p = p.or(and());
        }
        return p;
    }

    private Predicate<Facts> and() {
        Predicate<Facts> p = f -> true;
        while (at < tokens.size() && !tokens.get(at).equals("|") && !tokens.get(at).equals(")")) {
            p = p.and(unary());
        }
        return p;
    }

    private Predicate<Facts> unary() {
        String t = tokens.get(at++);
        if (t.equals("-")) {
            return at < tokens.size() && !tokens.get(at).equals("|") && !tokens.get(at).equals(")") ? unary().negate() : f -> true;
        }
        if (t.equals("(")) {
            Predicate<Facts> p = or();
            if (at < tokens.size() && tokens.get(at).equals(")")) {
                at++;
            }
            return p;
        }
        return term(t);
    }

    private static Predicate<Facts> term(String t) {
        String rest = t.substring(1).toLowerCase(Locale.ROOT);
        return switch (t.charAt(0)) {
            case '@' -> f -> f.mod().toLowerCase(Locale.ROOT).contains(rest) || f.modName().toLowerCase(Locale.ROOT).contains(rest);
            case '#' -> f -> f.tags().stream().anyMatch(tag -> tag.toLowerCase(Locale.ROOT).contains(rest));
            case '$' -> f -> Pinyin.holds(f.tooltip(), rest);
            case '*' -> f -> f.id().toLowerCase(Locale.ROOT).contains(rest);
            case '>', '<', '=' -> amount(t);
            default -> f -> Pinyin.holds(f.name(), t);
        };
    }

    /** {@code >64}, {@code <=2k}, {@code =1}: how many there are. A comparison with no number is no test. */
    private static Predicate<Facts> amount(String t) {
        int op = t.length() > 1 && t.charAt(1) == '=' ? 2 : 1;
        String number = t.substring(op).toLowerCase(Locale.ROOT);
        long scale = number.endsWith("k") ? 1_000 : number.endsWith("m") ? 1_000_000 : 1;
        if (scale > 1) {
            number = number.substring(0, number.length() - 1);
        }
        double n;
        try {
            n = Double.parseDouble(number) * scale;
        } catch (NumberFormatException e) {
            return f -> true;
        }
        String cmp = t.substring(0, op);
        return f -> switch (cmp) {
            case ">" -> f.count() > n;
            case "<" -> f.count() < n;
            case ">=" -> f.count() >= n;
            case "<=" -> f.count() <= n;
            default -> f.count() == n;
        };
    }
}
