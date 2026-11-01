package io.github.metrovoc.residual.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The pieces of programs, laid out for the palette beside an agent's or a Port's program: everything the language can say,
 * to click together instead of typing. A step goes on as the next step, a word on the end of what is being written, and
 * what plainly comes next comes by itself ({@link Program#append}).
 */
public final class Pieces {
    /** A piece: what its chip says, what it adds to a program and how, and what it does, as a translation key. */
    public record Piece(String label, String text, Program.Join join, String help) {
    }

    /** Pieces of one kind under a heading, by a translation key. */
    public record Group(String title, int kind, List<Piece> pieces) {
    }

    /** One of the palette's pages. */
    public record Page(String title, List<Group> groups) {
    }

    /** What a group's chips are coloured by. */
    public static final int STEP = 0, BLOCK = 1, CHECK = 2, VALUE = 3;

    /** What is on each page of steps, and in what order; a verb not listed goes under the last. */
    private static final Map<String, List<Program.Verb>> STEPS = new LinkedHashMap<>();

    static {
        STEPS.put("going", List.of(Program.Verb.COME, Program.Verb.HOME, Program.Verb.GO, Program.Verb.STAY, Program.Verb.FOLLOW, Program.Verb.FORWARD,
                Program.Verb.BACK, Program.Verb.UP, Program.Verb.DOWN, Program.Verb.TURN, Program.Verb.TELEPORT));
        STEPS.put("carrying", List.of(Program.Verb.TAKE, Program.Verb.PUT, Program.Verb.GIVE, Program.Verb.FETCH, Program.Verb.PICKUP, Program.Verb.SEND,
                Program.Verb.PUMP, Program.Verb.CHARGE, Program.Verb.DROP, Program.Verb.FLING, Program.Verb.VOID, Program.Verb.DRAIN));
        STEPS.put("world", List.of(Program.Verb.HARVEST, Program.Verb.PLANT, Program.Verb.TILL, Program.Verb.MINE, Program.Verb.DIG, Program.Verb.PLACE,
                Program.Verb.USE, Program.Verb.ATTACK, Program.Verb.EXTRUDE, Program.Verb.RETRACT, Program.Verb.EMIT, Program.Verb.PULSE, Program.Verb.SIGN));
        STEPS.put("hands", List.of(Program.Verb.SELECT, Program.Verb.TRANSFER, Program.Verb.EQUIP, Program.Verb.REFUEL, Program.Verb.LIFT, Program.Verb.LOWER));
        STEPS.put("logistics", List.of(Program.Verb.CRAFT, Program.Verb.SUPPLY, Program.Verb.PROVIDE, Program.Verb.PUSH, Program.Verb.ACCEPT, Program.Verb.REQUEST, Program.Verb.DELIVER));
        STEPS.put("routines", List.of(Program.Verb.FARM, Program.Verb.STORE, Program.Verb.SORT, Program.Verb.HAUL, Program.Verb.BUILD, Program.Verb.COPY, Program.Verb.MOVE));
        STEPS.put("saying", List.of(Program.Verb.WAIT, Program.Verb.SAY, Program.Verb.NOTE, Program.Verb.SOUND, Program.Verb.PARTICLE, Program.Verb.RENAME, Program.Verb.RUN, Program.Verb.RETIRE));
    }

    public static final List<Page> PAGES = List.of(steps(), flow(), places(), senses(), working());

    private Pieces() {
    }

    private static Piece word(String word, String help) {
        return new Piece(word, word + " ", Program.Join.WORD, "residual.palette." + help);
    }

    private static Piece on(String word, String help) {
        return new Piece(word, word, Program.Join.ON, "residual.palette." + help);
    }

    /** A verb, with a number where one reads plainly; a thing is named by clicking the program with it, or from the things page. */
    private static Piece verb(Program.Verb v) {
        String rest = v == Program.Verb.WAIT ? " 5" : v == Program.Verb.GO ? " 1" : v == Program.Verb.SAY ? " hello" : v == Program.Verb.TURN ? " left" : v.shape.isEmpty() ? "" : " ";
        return new Piece(v.word, (v.word + rest).stripTrailing() + (rest.equals(" ") ? " " : ""), Program.Join.STEP, "residual.palette." + v.word);
    }

    private static Page steps() {
        List<Group> groups = new ArrayList<>();
        STEPS.forEach((name, verbs) -> {
            List<Piece> pieces = new ArrayList<>();
            verbs.forEach(v -> pieces.add(verb(v)));
            groups.add(new Group("residual.palette.group." + name, STEP, pieces));
        });
        groups.add(new Group("residual.palette.group.clauses", VALUE, List.of(
                word("from", "from"), word("to", "to"), word("each", "each"), word("at", "at"), word("or", "or_thing"), word("except", "except"), word("where", "where"),
                word("sneak", "sneak"), word("speed", "speed"), word("pitch", "pitch"), word("yaw", "yaw"), word("for", "for"))));
        return new Page("residual.palette.page.steps", groups);
    }

    private static Page flow() {
        return new Page("residual.palette.page.flow", List.of(
                new Group("residual.palette.blocks", BLOCK, List.of(
                        new Piece("repeat (…)", "repeat (", Program.Join.STEP, "residual.palette.repeat"),
                        new Piece("repeat 3 (…)", "repeat 3 (", Program.Join.STEP, "residual.palette.repeat_times"),
                        new Piece("if … (…)", "if ", Program.Join.STEP, "residual.palette.if"),
                        new Piece("else (…)", " else (", Program.Join.ON, "residual.palette.else"),
                        new Piece("while … (…)", "while ", Program.Join.STEP, "residual.palette.while"),
                        new Piece("until … (…)", "until ", Program.Join.STEP, "residual.palette.until"),
                        new Piece("for each … (…)", "for each $x in ", Program.Join.STEP, "residual.palette.for_each"),
                        new Piece("wait until …", "wait until ", Program.Join.STEP, "residual.palette.wait_until"),
                        new Piece("(", "(", Program.Join.WORD, "residual.palette.open"),
                        new Piece(")", ")", Program.Join.ON, "residual.palette.close"))),
                new Group("residual.palette.group.variables", VALUE, List.of(
                        new Piece("set $x …", "set $x ", Program.Join.STEP, "residual.palette.set"),
                        word("$x", "var_program"), word("@x", "var_body"), word("#x", "var_team"), word("$it", "var_it"), word("$result", "var_result"))),
                new Group("residual.palette.group.procedures", BLOCK, List.of(
                        new Piece("to name $a (…)", "to name $a (", Program.Join.STEP, "residual.palette.to_steps"),
                        new Piece("name 1", "name 1", Program.Join.STEP, "residual.palette.call"),
                        new Piece("return", "return ", Program.Join.STEP, "residual.palette.return"))),
                new Group("residual.palette.group.events", BLOCK, List.of(
                        new Piece("on redstone (…)", "on redstone at 1 (", Program.Join.STEP, "residual.palette.on_redstone"),
                        new Piece("on item (…)", "on item at front (", Program.Join.STEP, "residual.palette.on_item"),
                        new Piece("on block (…)", "on block at 1 (", Program.Join.STEP, "residual.palette.on_block"),
                        new Piece("on timer (…)", "on timer 5 (", Program.Join.STEP, "residual.palette.on_timer"),
                        new Piece("on change (…)", "on change #x (", Program.Join.STEP, "residual.palette.on_change"),
                        new Piece("when … (…)", "when ", Program.Join.STEP, "residual.palette.when"),
                        word("priority", "priority"))),
                new Group("residual.palette.group.leaving", BLOCK, List.of(
                        new Piece("leave", "leave", Program.Join.STEP, "residual.palette.leave"),
                        new Piece("next", "next", Program.Join.STEP, "residual.palette.next"),
                        new Piece("stop", "stop", Program.Join.STEP, "residual.palette.stop"),
                        new Piece("breakpoint", "breakpoint", Program.Join.STEP, "residual.palette.breakpoint"))),
                new Group("residual.palette.group.operators", VALUE, List.of(
                        word("+", "op_add"), word("-", "op_sub"), word("*", "op_mul"), word("/", "op_div"), word("%", "op_mod"),
                        word("=", "op_eq"), word("!=", "op_ne"), word("<", "op_lt"), word("<=", "op_le"), word(">", "op_gt"), word(">=", "op_ge"),
                        word("and", "op_and"), word("or", "op_or"), word("not", "not"),
                        word("true", "true"), word("false", "false"), word("nothing", "nothing"),
                        new Piece("(", "(", Program.Join.WORD, "residual.palette.op_open"), new Piece(")", ")", Program.Join.ON, "residual.palette.op_close"),
                        new Piece("[", "[", Program.Join.WORD, "residual.palette.op_list"), new Piece("]", "]", Program.Join.ON, "residual.palette.op_end"),
                        new Piece(",", ",", Program.Join.ON, "residual.palette.op_comma"),
                        new Piece("\"…\"", "\"\"", Program.Join.WORD, "residual.palette.op_quote")))));
    }

    private static Group functions(String group, int kind) {
        List<Piece> pieces = new ArrayList<>();
        for (Ops.Fn fn : Ops.all()) {
            if (fn.group().equals(group)) {
                pieces.add(new Piece(fn.name() + (fn.most() == 0 ? "()" : "(…)"), fn.most() == 0 ? fn.name() + "()" : fn.name() + "(", Program.Join.WORD, "residual.palette.fn." + fn.name()));
            }
        }
        return new Group("residual.palette.group." + group, kind, pieces);
    }

    private static Page places() {
        List<Piece> spots = new ArrayList<>();
        for (String word : List.of("front", "back", "left", "right", "up", "down", "here", "home", "network", "hands", "owner")) {
            spots.add(new Piece(word, word, Program.Join.WORD, "residual.palette.spot." + word));
        }
        return new Page("residual.palette.page.places", List.of(new Group("residual.palette.group.spots", VALUE, spots),
                functions("places", VALUE), functions("areas", VALUE), functions("filters", VALUE)));
    }

    private static Page senses() {
        List<Piece> first = new ArrayList<>();
        for (Program.Sense s : Program.Sense.values()) {
            String rest = s.arg == Program.Arg.MARK ? " 1" : s.arg == Program.Arg.NONE ? "" : " ";
            first.add(new Piece(s.word, s.word + rest, Program.Join.WORD, "residual.palette." + s.word));
        }
        first.add(new Piece("not", "not ", Program.Join.WORD, "residual.palette.not"));
        List<Group> groups = new ArrayList<>();
        groups.add(new Group("residual.palette.checks", CHECK, first));
        for (String group : List.of("inventories", "tanks", "energy", "redstone", "blocks", "entities", "machines", "world", "network", "self")) {
            groups.add(functions(group, CHECK));
        }
        return new Page("residual.palette.page.senses", groups);
    }

    private static Page working() {
        return new Page("residual.palette.page.working", List.of(functions("math", VALUE), functions("logic", VALUE), functions("text", VALUE), functions("lists", VALUE),
                functions("items", VALUE), functions("fluids", VALUE)));
    }

    /** Words that stand in the palette for what the language can say, for a test that nothing it can say is missing. */
    public static Set<String> words() {
        java.util.Set<String> out = new java.util.HashSet<>();
        for (Page page : PAGES) {
            for (Group group : page.groups()) {
                for (Piece piece : group.pieces()) {
                    out.add(piece.label().replace("(…)", "").replace("()", "").replace(" …", "").strip());
                }
            }
        }
        return out;
    }
}
