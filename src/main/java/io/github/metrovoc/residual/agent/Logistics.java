package io.github.metrovoc.residual.agent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/**
 * Requester and provider logistics, as logistics frames have it: a body's program says what its places offer and want, and
 * carriers read that off their team's board. {@code provide} offers a place's things to whoever requests them; {@code push}
 * also wants them gone, into any place that accepts them; {@code accept} takes what is pushed; {@code request} wants a
 * number kept at a place; and {@code deliver} is one trip of a carrier. A notice lasts as long as a program keeps saying it.
 */
final class Logistics {
    /** Ticks a notice lasts unless it is said again. */
    private static final int LASTS = 40;
    private static final Map<ServerLevel, Map<String, List<Notice>>> BOARDS = new WeakHashMap<>();

    private enum Kind { PROVIDE, PUSH, ACCEPT, REQUEST }

    /** What a place offers or wants. */
    private record Notice(Kind kind, Value.Place place, Picker pick, int count, long until) {
    }

    private Logistics() {
    }

    private static List<Notice> board(Body a) {
        return BOARDS.computeIfAbsent(a.world(), l -> new HashMap<>()).computeIfAbsent(a.team(), t -> new ArrayList<>());
    }

    /** A notice put up, or put up again. */
    static Acts.Action board(Body a, Eval e, Program.Act act) {
        Kind kind = switch (act.verb()) {
            case PROVIDE -> Kind.PROVIDE;
            case PUSH -> Kind.PUSH;
            case ACCEPT -> Kind.ACCEPT;
            default -> Kind.REQUEST;
        };
        Picker pick = Deeds.pick(e, act);
        int count = Deeds.count(e, act);
        Value where = Deeds.clause(a, e, act, "at", "front");
        return level -> {
            Value.Place p = Places.place(a, where);
            if (p == null) {
                return Outcome.failed("residual.program.failed.no_way");
            }
            List<Notice> board = board(a);
            long now = level.getGameTime();
            board.removeIf(n -> n.until() < now || n.kind() == kind && n.place().pos().equals(p.pos()) && n.pick().thing().equals(pick.thing()));
            board.add(new Notice(kind, p, pick, count, now + LASTS));
            return Outcome.DONE;
        };
    }

    /** One trip: the first request a provider can fill, then the first thing pushed that a place accepts. */
    static Acts.Action deliver(Body a) {
        return new Acts.Action() {
            @Nullable
            private Value.Place from, to;
            @Nullable
            private Picker pick;
            private int want;
            private boolean carrying;

            @Override
            public boolean yields() {
                return true;
            }

            @Override
            public Outcome tick(ServerLevel level) {
                if (from == null && !find(level)) {
                    a.dry(false);
                    return Outcome.done("residual.program.delivered", 0);
                }
                if (a.legs()) {
                    if (!carrying) {
                        switch (a.walk(from.pos(), 2.5)) {
                            case ON_THE_WAY -> {
                                return null;
                            }
                            case LOST -> {
                                return Outcome.failed("residual.program.failed.unreachable");
                            }
                            case HERE -> {
                            }
                        }
                        Places.Ends src = Places.items(a, from);
                        if (src == null) {
                            return Outcome.done("residual.program.delivered", 0);
                        }
                        Places.move(src, Places.hands(a), pick, want);
                        a.handle(from.pos());
                        carrying = true;
                    }
                    switch (a.walk(to.pos(), 2.5)) {
                        case ON_THE_WAY -> {
                            return null;
                        }
                        case LOST -> {
                            return Outcome.failed("residual.program.failed.unreachable");
                        }
                        case HERE -> {
                        }
                    }
                    Places.Ends dst = Places.items(a, to);
                    int put = dst == null ? 0 : Places.move(Places.hands(a), dst, pick, want);
                    a.handle(to.pos());
                    a.dry(put > 0);
                    return Outcome.done("residual.program.delivered", put);
                }
                if (!a.reaches(from.pos()) || !a.reaches(to.pos())) {
                    return Outcome.failed("residual.program.failed.out_of_reach");
                }
                Places.Ends src = Places.items(a, from), dst = Places.items(a, to);
                int moved = src == null || dst == null ? 0 : Places.move(src, dst, pick, Math.min(want, a.itemsPerRun()));
                a.dry(moved > 0);
                a.moved(moved, false);
                if (a.interval() > 1) {
                    a.pause(a.interval() - 1);
                }
                return Outcome.done("residual.program.delivered", moved);
            }

            private boolean find(ServerLevel level) {
                List<Notice> board = board(a);
                long now = level.getGameTime();
                board.removeIf(n -> n.until() < now);
                for (Notice r : board) {
                    if (r.kind() != Kind.REQUEST) {
                        continue;
                    }
                    Places.Ends there = Places.items(a, r.place());
                    double have = there == null ? 0 : count(there, r.pick());
                    if (have >= r.count()) {
                        continue;
                    }
                    for (Notice p : board) {
                        if ((p.kind() == Kind.PROVIDE || p.kind() == Kind.PUSH) && !p.place().pos().equals(r.place().pos())) {
                            Places.Ends source = Places.items(a, p.place());
                            if (source != null && overlap(source, p.pick(), r.pick())) {
                                from = p.place();
                                to = r.place();
                                pick = both(p.pick(), r.pick());
                                want = (int) Math.min(r.count() - have, 64);
                                return true;
                            }
                        }
                    }
                }
                for (Notice p : board) {
                    if (p.kind() != Kind.PUSH) {
                        continue;
                    }
                    Places.Ends source = Places.items(a, p.place());
                    for (Notice s : board) {
                        if (s.kind() == Kind.ACCEPT && source != null && !s.place().pos().equals(p.place().pos()) && overlap(source, p.pick(), s.pick())) {
                            from = p.place();
                            to = s.place();
                            pick = both(p.pick(), s.pick());
                            want = 64;
                            return true;
                        }
                    }
                }
                return false;
            }
        };
    }

    private static double count(Places.Ends ends, Picker pick) {
        double[] n = {0};
        ends.forEach((kind, amount) -> {
            if (pick.passes(kind.toStack())) {
                n[0] += amount;
            }
        });
        return n[0];
    }

    private static boolean overlap(Places.Ends source, Picker x, Picker y) {
        boolean[] any = {false};
        source.forEach((kind, amount) -> {
            if (x.passes(kind.toStack()) && y.passes(kind.toStack())) {
                any[0] = true;
            }
        });
        return any[0];
    }

    private static Picker both(Picker x, Picker y) {
        return new Picker() {
            @Override
            public boolean exactly(net.minecraft.world.item.ItemStack s) {
                return x.passes(s) && y.passes(s);
            }

            @Override
            public String thing() {
                return y.thing();
            }
        };
    }
}
