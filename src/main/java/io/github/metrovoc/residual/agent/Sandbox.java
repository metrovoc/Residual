package io.github.metrovoc.residual.agent;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Runs a program with nothing real to touch: a small made-up place with a store, a field, a player and marks, and an agent that does in it what the program says, a step at a
 * time, for {@link #STEPS} steps at most. What the place looks like afterwards is what the program did, so two programs that come to the same thing are the same however they
 * are written. Nothing here ever reaches a world.
 *
 * <p>It reads a program as it is written, by words, not by what {@link Program} makes of it. It knows the steps orders are written in: anything else counts as a step that
 * leaves that much written on the record, so a program with more in it than it knows of still runs.
 */
public final class Sandbox {
    /** The most steps a run takes; what goes on for good stops here. */
    public static final int STEPS = 64;
    /** What an agent can carry, by count. */
    private static final int FULL = 64;
    /** What a harvest brings in, and how long a harvested field takes to ripen again. */
    private static final int CROP = 8, REGROWS = 12;

    /**
     * The place a program runs in. {@code stock} is what the store holds, and things nobody mentioned are not in it.
     *
     * @param ripeAt the step the field is ripe from
     * @param nightAt the step night comes
     */
    public record Scene(Map<String, Integer> stock, Map<String, Integer> carried, Map<String, Integer> dropped, int ripeAt, int nightAt) {
    }

    /** How a run went: whether a step failed (and so ended it), whether it ran out of steps, and what it left, as one line two runs can be compared by. */
    public record Result(boolean failed, boolean ranOut, String left) {
    }

    /** A step as written: its first word and the rest, what it runs in a block, and what it runs otherwise. */
    private record Step(String word, String rest, List<Step> body, List<Step> otherwise) {
    }

    private final Scene scene;
    private final Map<String, Integer> store;
    private final Map<String, Integer> hands;
    private final Map<String, Integer> ground;
    private final Map<String, Integer> given = new TreeMap<>();
    private final Map<String, Integer> planted = new TreeMap<>();
    private final Map<String, Integer> placed = new TreeMap<>();
    private final Map<String, Integer> jobs = new TreeMap<>();
    private final List<String> said = new ArrayList<>();
    private String at = "home";
    private boolean following;
    private boolean stayed;
    private int tilled;
    private int waited;
    private int steps;
    private int time;
    private int ripeAt;
    private boolean failed;

    private Sandbox(Scene scene) {
        this.scene = scene;
        store = new TreeMap<>(scene.stock());
        hands = new TreeMap<>(scene.carried());
        ground = new TreeMap<>(scene.dropped());
        ripeAt = scene.ripeAt();
    }

    /** Runs {@code program}, as a program writes itself, in a fresh copy of {@code scene}. */
    public static Result run(String program, Scene scene) {
        Sandbox s = new Sandbox(scene);
        s.run(read(program));
        return new Result(s.failed, s.steps >= STEPS, s.left());
    }

    /** Whether {@code written} comes to what {@code meant} comes to in {@code scene}, failing nowhere it doesn't fail. */
    public static boolean same(String written, String meant, Scene scene) {
        Result a = run(written, scene), b = run(meant, scene);
        return a.failed() == b.failed() && a.left().equals(b.left());
    }

    /** The things {@code program} names, to fill a store with. */
    public static Set<String> named(String program) {
        Set<String> things = new LinkedHashSet<>();
        named(read(program), things);
        return things;
    }

    private static void named(List<Step> steps, Set<String> things) {
        for (Step s : steps) {
            switch (s.word()) {
                case "repeat" -> named(s.body(), things);
                case "if", "until", "wait until" -> {
                    String[] check = s.rest().split(" ", 3);
                    int at = check[0].equals("not") ? 1 : 0;
                    if ((check[at].equals("have") || check[at].equals("stocked")) && check.length > at + 1) {
                        things.add(thing(String.join(" ", List.of(check).subList(at + 1, check.length))));
                    }
                    named(s.body(), things);
                    named(s.otherwise(), things);
                }
                case "take", "put", "give", "fetch", "plant", "mine" -> {
                    if (!thing(s.rest()).isEmpty()) {
                        things.add(thing(s.rest()));
                    }
                }
                case "place" -> things.add(thing(s.rest().substring(0, Math.max(0, s.rest().lastIndexOf(' ')))));
                default -> {
                }
            }
        }
    }

    // ---------------------------------------------------------------- reading

    private static List<Step> read(String text) {
        List<Step> out = new ArrayList<>();
        int depth = 0, from = 0;
        for (int i = 0; i <= text.length(); i++) {
            char c = i < text.length() ? text.charAt(i) : ',';
            depth += c == '(' ? 1 : c == ')' ? -1 : 0;
            if (c == ',' && depth == 0) {
                String one = text.substring(from, i).strip();
                if (!one.isEmpty()) {
                    out.add(step(one));
                }
                from = i + 1;
            }
        }
        return out;
    }

    private static Step step(String text) {
        int open = text.indexOf('(');
        String head = (open < 0 ? text : text.substring(0, open)).strip();
        if (head.startsWith("wait until ")) {
            return new Step("wait until", head.substring(11), List.of(), List.of());
        }
        int space = head.indexOf(' ');
        String word = space < 0 ? head : head.substring(0, space), rest = space < 0 ? "" : head.substring(space + 1).strip();
        if (open < 0) {
            return new Step(word, rest, List.of(), List.of());
        }
        int close = closing(text, open);
        List<Step> body = read(text.substring(open + 1, close));
        String after = text.substring(close + 1).strip();
        List<Step> otherwise = List.of();
        if (after.startsWith("else")) {
            int next = after.indexOf('(');
            otherwise = next < 0 ? List.of() : read(after.substring(next + 1, closing(after, next)));
        }
        return new Step(word, rest, body, otherwise);
    }

    private static int closing(String text, int open) {
        int depth = 0;
        for (int i = open; i < text.length(); i++) {
            depth += text.charAt(i) == '(' ? 1 : text.charAt(i) == ')' ? -1 : 0;
            if (depth == 0) {
                return i;
            }
        }
        return text.length() - 1;
    }

    /** How many a step with this after its word asks for, 0 if it says nothing. */
    private static int count(String rest) {
        int space = rest.indexOf(' ');
        String first = space < 0 ? rest : rest.substring(0, space);
        return !first.isEmpty() && first.chars().allMatch(Character::isDigit) && first.length() < 6 ? Integer.parseInt(first) : 0;
    }

    /** What a step with this after its word names. */
    private static String thing(String rest) {
        return count(rest) > 0 && rest.indexOf(' ') > 0 ? rest.substring(rest.indexOf(' ') + 1).strip() : rest;
    }

    // ---------------------------------------------------------------- running

    private boolean stopped() {
        return failed || stayed || steps >= STEPS;
    }

    private void run(List<Step> list) {
        for (Step step : list) {
            if (stopped()) {
                return;
            }
            step(step);
        }
    }

    private void step(Step s) {
        String rest = s.rest();
        switch (s.word()) {
            case "repeat" -> {
                int times = count(rest);
                for (int round = 0; (times == 0 || round < times) && !stopped(); round++) {
                    run(s.body());
                    // a round with nothing in it still takes a step
                    tick();
                }
            }
            case "if" -> run(holds(rest) ? s.body() : s.otherwise());
            case "until" -> {
                while (!holds(rest) && !stopped()) {
                    run(s.body());
                    tick();
                }
            }
            case "wait until" -> {
                while (!holds(rest) && !stopped()) {
                    tick();
                }
            }
            case "come" -> {
                at = "player";
                tick();
            }
            case "home" -> {
                at = "home";
                following = false;
                tick();
            }
            case "go" -> {
                at = "mark " + rest;
                following = false;
                tick();
            }
            case "stay" -> {
                stayed = true;
                tick();
            }
            case "follow" -> {
                at = "player";
                following = true;
                tick();
            }
            case "take" -> take(thing(rest), count(rest));
            case "put" -> put(rest);
            case "give" -> give(thing(rest), count(rest));
            case "fetch" -> {
                // taking, coming and giving, as an agent does it
                take(thing(rest), count(rest));
                if (!failed) {
                    at = "player";
                    tick();
                    give(thing(rest), count(rest));
                }
            }
            case "pickup" -> {
                ground.forEach((k, n) -> hands.merge(k, n, Integer::sum));
                ground.clear();
                tick();
            }
            case "harvest" -> {
                if (time >= ripeAt) {
                    hands.merge("wheat", CROP, Integer::sum);
                    ripeAt = time + REGROWS;
                }
                tick();
            }
            case "plant" -> {
                planted.merge(rest, 1, Integer::sum);
                tick();
            }
            case "till" -> {
                tilled++;
                tick();
            }
            case "mine" -> {
                hands.merge(thing(rest), Math.max(1, count(rest)), Integer::sum);
                tick();
            }
            case "place" -> {
                int last = rest.lastIndexOf(' ');
                String what = last < 0 ? rest : rest.substring(0, last);
                if (hands.getOrDefault(what, 0) < 1) {
                    failed = true;
                } else {
                    hands.merge(what, -1, Integer::sum);
                    placed.merge(rest, 1, Integer::sum);
                }
                tick();
            }
            case "wait" -> {
                waited += count(rest);
                time += count(rest);
                steps++;
            }
            case "say" -> {
                said.add(rest);
                tick();
            }
            default -> {
                jobs.merge((s.word() + " " + rest).strip(), 1, Integer::sum);
                tick();
            }
        }
    }

    private void tick() {
        steps++;
        time++;
    }

    private void take(String thing, int count) {
        int has = store.getOrDefault(thing, 0);
        if (has < 1) {
            failed = true;
        } else {
            int n = count == 0 ? Math.min(has, 64) : Math.min(has, count);
            store.merge(thing, -n, Integer::sum);
            hands.merge(thing, n, Integer::sum);
        }
        tick();
    }

    private void put(String thing) {
        if (thing.isEmpty()) {
            hands.forEach((k, n) -> store.merge(k, n, Integer::sum));
            hands.clear();
        } else {
            int n = hands.getOrDefault(thing, 0);
            if (n < 1) {
                failed = true;
            } else {
                store.merge(thing, n, Integer::sum);
                hands.remove(thing);
            }
        }
        tick();
    }

    private void give(String thing, int count) {
        int has = hands.getOrDefault(thing, 0);
        if (has < 1) {
            failed = true;
        } else {
            int n = count == 0 ? has : Math.min(has, count);
            hands.merge(thing, -n, Integer::sum);
            given.merge(thing, n, Integer::sum);
        }
        tick();
    }

    /** Whether a check as written holds. */
    private boolean holds(String check) {
        boolean not = check.startsWith("not ");
        String rest = not ? check.substring(4) : check;
        int space = rest.indexOf(' ');
        String sense = space < 0 ? rest : rest.substring(0, space), arg = space < 0 ? "" : rest.substring(space + 1).strip();
        boolean yes = switch (sense) {
            case "have" -> hands.getOrDefault(thing(arg), 0) >= Math.max(1, count(arg));
            case "stocked" -> store.getOrDefault(thing(arg), 0) >= Math.max(1, count(arg));
            case "ripe" -> time >= ripeAt;
            case "full" -> hands.values().stream().mapToInt(Integer::intValue).sum() >= FULL;
            case "night" -> time >= scene.nightAt();
            case "near" -> at.equals("player");
            default -> false;
        };
        return yes != not;
    }

    private String left() {
        store.values().removeIf(n -> n == 0);
        hands.values().removeIf(n -> n == 0);
        return "at " + at + (following ? " following" : "") + (stayed ? " staying" : "") + " store " + store + " hands " + hands + " ground " + ground + " given " + given
                + " planted " + planted + " placed " + placed + " tilled " + tilled + " waited " + waited + " said " + said + " jobs " + jobs;
    }
}
