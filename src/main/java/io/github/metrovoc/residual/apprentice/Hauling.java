package io.github.metrovoc.residual.apprentice;

import io.github.metrovoc.residual.learn.Drill;
import java.util.List;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/**
 * Runs errands along a route of six marked waymarks: out to the last one, where it loads up from a chest or barrel,
 * and back home. Each trip the waymarks get distinct numbers; on the way back its mind is shown the numbers it passed
 * and names them in reverse, the way the errand test writes it ({@code 381925|529183}), and it walks wherever its
 * mind says.
 */
final class Hauling extends Work {
    private enum Leg { OUT, LOAD, BACK, HOME }

    private final Random rng = new Random();
    private Leg leg = Leg.OUT;
    private int index;
    private String labels = "";
    /** The numbers named so far on the way back: the right ones, as the test shows them. */
    private final StringBuilder named = new StringBuilder();
    private BlockPos heading;

    Hauling(Apprentice a) {
        super(a);
    }

    @Override
    void tick(ServerLevel level) {
        List<BlockPos> route = a.marks();
        if (route.isEmpty()) {
            a.status("residual.apprentice.status.no_route");
            a.pause(100);
            return;
        }
        // the errand test always names six waymarks; a route of any other length would ask its mind something new
        if (route.size() != Drill.LEG) {
            a.status("residual.apprentice.status.route_length", Drill.LEG, route.size());
            a.pause(100);
            return;
        }
        switch (leg) {
            case OUT -> out(route);
            case LOAD -> load(level, route.getLast());
            case BACK -> back(route);
            case HOME -> home(level);
        }
    }

    private void out(List<BlockPos> route) {
        if (index == 0 && labels.length() != route.size()) {
            int[] d = Drill.distinct(rng, Drill.LANDMARKS.length(), route.size());
            StringBuilder sb = new StringBuilder();
            for (int i : d) {
                sb.append(Drill.LANDMARKS.charAt(i));
            }
            labels = sb.toString();
        }
        a.status("residual.apprentice.status.hauling_out", index + 1, route.size());
        switch (a.walk(route.get(index), 1.8)) {
            case ON_THE_WAY -> {
                return;
            }
            case LOST -> {
                a.status("residual.apprentice.status.waymark_unreachable", index + 1);
                a.pause(100);
                return;
            }
            case HERE -> {
            }
        }
        a.show(Component.translatable("residual.apprentice.said.waymark", String.valueOf(labels.charAt(index))));
        a.pause(10);
        if (++index >= route.size()) {
            leg = Leg.LOAD;
        }
    }

    private void load(ServerLevel level, BlockPos last) {
        for (BlockPos p : Stores.near(level, last, 2, Stores.ANY)) {
            Container c = Stores.at(level, p);
            if (c == null) {
                continue;
            }
            switch (a.walk(p, 2.2)) {
                case ON_THE_WAY -> {
                    return;
                }
                case LOST -> {
                    a.pause(100);
                    return;
                }
                case HERE -> {
                }
            }
            while (!a.full()) {
                ItemStack got = Stores.take(c, s -> true, 64);
                if (got.isEmpty()) {
                    break;
                }
                a.keep(got);
            }
            a.handle(p);
            break;
        }
        named.setLength(0);
        heading = null;
        leg = Leg.BACK;
    }

    private void back(List<BlockPos> route) {
        if (heading == null) {
            if (named.length() >= labels.length()) {
                leg = Leg.HOME;
                return;
            }
            String reversed = new StringBuilder(labels).reverse().toString();
            char expected = reversed.charAt(named.length());
            String prompt = labels + "|" + named;
            a.ask(prompt, 1, '\n', String.valueOf(expected), answer -> {
                named.append(expected);
                int i = answer.isEmpty() ? -1 : labels.indexOf(answer.charAt(0));
                if (i < 0) {
                    a.show(a.decided(Component.translatable("residual.apprentice.said.lost", answer)));
                    leg = Leg.HOME;
                    return;
                }
                heading = route.get(i);
                a.show(a.decided(Component.translatable("residual.apprentice.said.waymark", String.valueOf(answer.charAt(0)))));
            });
            return;
        }
        a.status("residual.apprentice.status.hauling_back");
        if (a.walk(heading, 1.8) != Apprentice.Arrival.ON_THE_WAY) {
            heading = null;
        }
    }

    private void home(ServerLevel level) {
        BlockPos chest = Stores.homeChest(level, a.home());
        BlockPos target = chest != null ? chest : a.home();
        a.status("residual.apprentice.status.hauling_home");
        switch (a.walk(target, chest != null ? 2.2 : 1.5)) {
            case ON_THE_WAY -> {
                return;
            }
            case LOST -> {
                a.pause(100);
                return;
            }
            case HERE -> {
            }
        }
        Container c = chest == null ? null : Stores.at(level, chest);
        if (c != null) {
            for (int i = 0; i < Apprentice.CARRY; i++) {
                a.items.setItem(i, Stores.insert(c, a.items.getItem(i)));
            }
            a.items.setChanged();
            a.handle(chest);
        }
        leg = Leg.OUT;
        index = 0;
        labels = "";
        a.pause(100);
    }
}
