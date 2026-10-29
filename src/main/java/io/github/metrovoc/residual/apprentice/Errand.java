package io.github.metrovoc.residual.apprentice;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/** Something it was told to do once. It goes back to its job when the errand is over. */
abstract class Errand {
    final Apprentice a;
    private int ticks;

    Errand(Apprentice a) {
        this.a = a;
    }

    /** Does a little more of it; true once it is over. */
    boolean tick(ServerLevel level) {
        return ++ticks > 2400 || step(level);
    }

    abstract boolean step(ServerLevel level);

    static Errand home(Apprentice a) {
        return new Errand(a) {
            @Override
            boolean step(ServerLevel level) {
                a.status("residual.apprentice.status.going_home");
                return a.walk(a.home(), 1.5) != Apprentice.Arrival.ON_THE_WAY;
            }
        };
    }

    /** Keeps close to {@code player} for a couple of minutes. */
    static Errand follow(Apprentice a, ServerPlayer player) {
        return new Errand(a) {
            @Override
            boolean step(ServerLevel level) {
                if (player.isRemoved() || player.level() != level || !player.isAlive()) {
                    return true;
                }
                a.status("residual.apprentice.status.following", player.getName().getString());
                if (a.walk(player.blockPosition(), 2.5) == Apprentice.Arrival.HERE) {
                    a.getLookControl().setLookAt(player);
                }
                return false;
            }
        };
    }

    /** Finds {@code thing} in the stores around its home and brings a stack of it to {@code player}. */
    static Errand fetch(Apprentice a, ServerPlayer player, String thing) {
        Things.Wanted wanted = Things.of(thing);
        return new Errand(a) {
            private BlockPos from;
            private boolean looked;

            @Override
            boolean step(ServerLevel level) {
                if (player.isRemoved() || player.level() != level) {
                    return true;
                }
                if (!carrying()) {
                    if (!looked) {
                        looked = true;
                        from = find(level);
                        if (from == null) {
                            a.tell(Component.translatable("residual.apprentice.said.none_of", wanted.thing()));
                            a.status("residual.apprentice.status.none_of", wanted.thing());
                            return true;
                        }
                    }
                    a.status("residual.apprentice.status.fetching", wanted.thing());
                    switch (a.walk(from, 2.2)) {
                        case ON_THE_WAY -> {
                            return false;
                        }
                        case LOST -> {
                            return true;
                        }
                        case HERE -> {
                        }
                    }
                    Container c = Stores.at(level, from);
                    if (c != null) {
                        if (Stores.fetch(c, a, wanted::exactly, 64) == 0 && Stores.fetch(c, a, wanted::loosely, 64) == 0
                                && Stores.has(c, s -> wanted.exactly(s) || wanted.loosely(s))) {
                            a.tell(Component.translatable("residual.apprentice.said.hands_full"));
                            return true;
                        }
                        a.handle(from);
                    }
                    if (!carrying()) {
                        looked = false;
                        return false;
                    }
                }
                a.status("residual.apprentice.status.bringing", wanted.thing(), player.getName().getString());
                if (a.walk(player.blockPosition(), 2.5) != Apprentice.Arrival.HERE) {
                    return false;
                }
                for (int i = 0; i < Apprentice.CARRY; i++) {
                    ItemStack s = a.items.getItem(i);
                    if (wanted.exactly(s) || wanted.loosely(s)) {
                        a.items.setItem(i, ItemStack.EMPTY);
                        if (!player.getInventory().add(s)) {
                            player.drop(s, false);
                        }
                    }
                }
                a.items.setChanged();
                a.handle(player.blockPosition());
                return true;
            }

            private boolean carrying() {
                for (int i = 0; i < Apprentice.CARRY; i++) {
                    ItemStack s = a.items.getItem(i);
                    if (wanted.exactly(s) || wanted.loosely(s)) {
                        return true;
                    }
                }
                return false;
            }

            /** The nearest store with exactly the thing, else with a kind of it. */
            private BlockPos find(ServerLevel level) {
                BlockPos loose = null;
                for (BlockPos p : Stores.near(level, a.home(), Storing.RADIUS + 4, Stores.ANY)) {
                    Container c = Stores.at(level, p);
                    if (c == null) {
                        continue;
                    }
                    if (Stores.has(c, wanted::exactly)) {
                        return p;
                    }
                    if (loose == null && Stores.has(c, wanted::loosely)) {
                        loose = p;
                    }
                }
                return loose;
            }
        };
    }
}
