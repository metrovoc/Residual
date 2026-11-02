package io.github.metrovoc.residual.agent;

import io.github.metrovoc.residual.storage.StorageNetwork;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ObjLongConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.PlayerInventoryWrapper;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jetbrains.annotations.Nullable;

/** Where a program's words and values point: places, and what can be taken from and put into them. */
final class Places {
    private Places() {
    }

    /** What a word for a place stands for, from where {@code a} stands and the way it faces. */
    static Value spot(Body a, String word) {
        return switch (word) {
            case "here" -> new Value.Place(a.place());
            case "home" -> new Value.Place(a.home());
            case "network", "hands", "owner" -> new Value.Spot(word);
            default -> {
                Direction d = side(a.facing(), word);
                yield new Value.Place(a.place().relative(d), d.getOpposite(), null);
            }
        };
    }

    /** The direction a side's word means on something facing {@code facing}. */
    static Direction side(Direction facing, String word) {
        Direction flat = facing.getAxis().isVertical() ? Direction.NORTH : facing;
        return switch (word) {
            case "front" -> facing;
            case "back" -> facing.getOpposite();
            case "left" -> flat.getCounterClockWise();
            case "right" -> flat.getClockWise();
            case "up" -> Direction.UP;
            case "down" -> Direction.DOWN;
            default -> {
                Direction named = Direction.byName(word);
                yield named == null ? facing : named;
            }
        };
    }

    /** A value as a place: a place itself, a mark by its number, where an entity is. Null if it is none. */
    @Nullable
    static Value.Place place(Body a, Value v) {
        return switch (v) {
            case Value.Place p -> p;
            case Value.Num n -> {
                int i = (int) n.v() - 1;
                yield i >= 0 && i < a.marks().size() ? new Value.Place(a.marks().get(i)) : null;
            }
            case Value.Creature c -> new Value.Place(c.entity().blockPosition());
            default -> null;
        };
    }

    /** The level a place is in; another dimension takes the relay network. */
    @Nullable
    static ServerLevel level(Body a, Value.Place p) {
        if (p.dimension() == null || p.dimension().equals(a.world().dimension())) {
            return a.world();
        }
        return a.relayed() ? a.world().getServer().getLevel(p.dimension()) : null;
    }

    /** Things to take from and put into: an inventory, a network, its own hands. */
    interface Ends {
        /** Puts up to {@code n} in; returns how many went, or would. */
        long give(ItemResource kind, long n, boolean simulate);

        /** Takes up to {@code n} out; returns how many came, or would. */
        long take(ItemResource kind, long n, boolean simulate);

        void forEach(ObjLongConsumer<ItemResource> each);

        /** How many slots it has, or -1 where it has no slots to speak of. */
        default int slots() {
            return -1;
        }

        /** What is in slot {@code i}. */
        default ItemStack slot(int i) {
            return ItemStack.EMPTY;
        }
    }

    /** Items picked up for one delivery, kept apart from what the body already carried. */
    static Ends cargo(Ends hands) {
        return new Ends() {
            private final Map<ItemResource, Long> contents = new LinkedHashMap<>();

            @Override
            public long give(ItemResource kind, long n, boolean simulate) {
                long stored = hands.give(kind, n, simulate);
                if (!simulate && stored > 0) {
                    contents.merge(kind, stored, Long::sum);
                }
                return stored;
            }

            @Override
            public long take(ItemResource kind, long n, boolean simulate) {
                long held = contents.getOrDefault(kind, 0L);
                long taken = hands.take(kind, Math.min(n, held), simulate);
                if (!simulate && taken > 0) {
                    if (taken == held) {
                        contents.remove(kind);
                    } else {
                        contents.put(kind, held - taken);
                    }
                }
                return taken;
            }

            @Override
            public void forEach(ObjLongConsumer<ItemResource> each) {
                contents.forEach((kind, n) -> each.accept(kind, n));
            }
        };
    }

    static Ends of(ResourceHandler<ItemResource> handler) {
        return new Ends() {
            @Override
            public long give(ItemResource kind, long n, boolean simulate) {
                try (Transaction tx = Transaction.openRoot()) {
                    int put = handler.insert(kind, (int) Math.min(n, Integer.MAX_VALUE), tx);
                    if (!simulate) {
                        tx.commit();
                    }
                    return put;
                }
            }

            @Override
            public long take(ItemResource kind, long n, boolean simulate) {
                try (Transaction tx = Transaction.openRoot()) {
                    int took = handler.extract(kind, (int) Math.min(n, Integer.MAX_VALUE), tx);
                    if (!simulate) {
                        tx.commit();
                    }
                    return took;
                }
            }

            @Override
            public void forEach(ObjLongConsumer<ItemResource> each) {
                for (int slot = 0; slot < handler.size(); slot++) {
                    ItemResource kind = handler.getResource(slot);
                    if (!kind.isEmpty()) {
                        each.accept(kind, handler.getAmountAsLong(slot));
                    }
                }
            }

            @Override
            public int slots() {
                return handler.size();
            }

            @Override
            public ItemStack slot(int i) {
                return handler.getResource(i).toStack((int) handler.getAmountAsLong(i));
            }
        };
    }

    static Ends of(StorageNetwork net) {
        return new Ends() {
            @Override
            public long give(ItemResource kind, long n, boolean simulate) {
                return net.insert(kind, n, simulate);
            }

            @Override
            public long take(ItemResource kind, long n, boolean simulate) {
                return net.extract(kind, n, simulate);
            }

            @Override
            public void forEach(ObjLongConsumer<ItemResource> each) {
                net.contents().object2LongEntrySet().forEach(entry -> each.accept(entry.getKey(), entry.getLongValue()));
            }
        };
    }

    /** Its own hands: the slots it carries in. */
    static Ends hands(Body a) {
        return new Ends() {
            @Override
            public long give(ItemResource kind, long n, boolean simulate) {
                Container items = a.items();
                long left = n;
                int stack = kind.getMaxStackSize();
                for (int i = 0; i < a.carry() && left > 0; i++) {
                    ItemStack in = items.getItem(i);
                    if (in.isEmpty() || !kind.matches(in)) {
                        continue;
                    }
                    int put = (int) Math.min(left, stack - in.getCount());
                    if (put > 0 && !simulate) {
                        in.grow(put);
                    }
                    left -= put;
                }
                for (int i = 0; i < a.carry() && left > 0; i++) {
                    if (items.getItem(i).isEmpty()) {
                        int put = (int) Math.min(left, stack);
                        if (!simulate) {
                            items.setItem(i, kind.toStack(put));
                        }
                        left -= put;
                    }
                }
                if (!simulate && left < n) {
                    items.setChanged();
                }
                return n - left;
            }

            @Override
            public long take(ItemResource kind, long n, boolean simulate) {
                Container items = a.items();
                long left = n;
                for (int i = 0; i < a.carry() && left > 0; i++) {
                    ItemStack in = items.getItem(i);
                    if (!in.isEmpty() && kind.matches(in)) {
                        int took = (int) Math.min(left, in.getCount());
                        if (!simulate) {
                            in.shrink(took);
                        }
                        left -= took;
                    }
                }
                if (!simulate && left < n) {
                    items.setChanged();
                }
                return n - left;
            }

            @Override
            public void forEach(ObjLongConsumer<ItemResource> each) {
                for (int i = 0; i < a.carry(); i++) {
                    ItemStack in = a.items().getItem(i);
                    if (!in.isEmpty()) {
                        each.accept(ItemResource.of(in), in.getCount());
                    }
                }
            }

            @Override
            public int slots() {
                return a.carry();
            }

            @Override
            public ItemStack slot(int i) {
                return a.items().getItem(i);
            }
        };
    }

    /** The network {@code a} works through: the one a Port is part of, or that of the nearest terminal around an agent's home. */
    @Nullable
    static StorageNetwork network(Body a) {
        if (!a.legs()) {
            return StorageNetwork.at(a.world(), a.place());
        }
        List<BlockPos> terminals = Stores.terminals(a.world(), a.home());
        return terminals.isEmpty() ? null : StorageNetwork.at(a.world(), terminals.getFirst());
    }

    /** Whoever owns {@code a}, if they are on the server: anywhere the relay network reaches, or close by. */
    @Nullable
    static ServerPlayer owner(Body a) {
        if (a.owner() == null) {
            return null;
        }
        ServerPlayer p = a.world().getServer().getPlayerList().getPlayer(a.owner());
        if (p == null || !a.relayed() && (p.level() != a.world() || !a.reaches(p.blockPosition()))) {
            return null;
        }
        return p;
    }

    /** What a value lets things be taken from and put into, or null if it lets nothing. */
    @Nullable
    static Ends items(Body a, Value v) {
        return switch (v) {
            case Value.Spot s -> switch (s.word()) {
                case "hands" -> hands(a);
                case "network" -> {
                    StorageNetwork net = network(a);
                    yield net == null ? null : of(net);
                }
                default -> {
                    ServerPlayer p = owner(a);
                    yield p == null ? null : ofPlayer(p, s.word());
                }
            };
            case Value.Creature c -> ofEntity(c.entity());
            default -> {
                Value.Place p = place(a, v);
                ServerLevel level = p == null ? null : level(a, p);
                if (level == null || !level.isLoaded(p.pos())) {
                    yield null;
                }
                ResourceHandler<ItemResource> handler = level.getCapability(Capabilities.Item.BLOCK, p.pos(), p.face());
                yield handler == null ? null : of(handler);
            }
        };
    }

    private static Ends ofPlayer(ServerPlayer p, String word) {
        PlayerInventoryWrapper all = PlayerInventoryWrapper.of(p);
        return switch (word) {
            case "owner:armor" -> of(all.getArmorSlots());
            case "owner:offhand" -> of(all.getHandSlot(net.minecraft.world.InteractionHand.OFF_HAND));
            case "owner:ender" -> of(VanillaContainerWrapper.of(p.getEnderChestInventory()));
            default -> of(all.getMainSlots());
        };
    }

    @Nullable
    private static Ends ofEntity(Entity e) {
        ResourceHandler<ItemResource> handler = e.getCapability(Capabilities.Item.ENTITY_AUTOMATION, null);
        return handler == null ? null : of(handler);
    }

    /** Moves up to {@code most} items {@code pick} passes; returns how many went. */
    static int move(Ends from, Ends to, Picker pick, int most) {
        List<ItemResource> kinds = new ArrayList<>();
        from.forEach((kind, n) -> {
            if (!kinds.contains(kind)) {
                kinds.add(kind);
            }
        });
        int moved = 0;
        for (ItemResource kind : kinds) {
            if (moved >= most) {
                break;
            }
            if (!pick.passes(kind.toStack())) {
                continue;
            }
            long fits = to.give(kind, most - moved, true);
            long have = fits <= 0 ? 0 : from.take(kind, fits, false);
            if (have <= 0) {
                continue;
            }
            long stored = to.give(kind, have, false);
            if (stored < have) {
                from.give(kind, have - stored, false);
            }
            moved += (int) stored;
        }
        return moved;
    }

    /** The fluids at a place, or the body's tank. */
    @Nullable
    static ResourceHandler<FluidResource> fluids(Body a, Value v) {
        if (v instanceof Value.Spot s) {
            return s.word().equals("hands") ? a.tank() : null;
        }
        if (v instanceof Value.Creature c) {
            return c.entity().getCapability(Capabilities.Fluid.ENTITY, null);
        }
        Value.Place p = place(a, v);
        ServerLevel level = p == null ? null : level(a, p);
        return level == null || !level.isLoaded(p.pos()) ? null : level.getCapability(Capabilities.Fluid.BLOCK, p.pos(), p.face());
    }

    /** The energy at a place, or the body's battery. */
    @Nullable
    static EnergyHandler energy(Body a, Value v) {
        if (v instanceof Value.Spot s) {
            return s.word().equals("hands") ? a.battery() : null;
        }
        if (v instanceof Value.Creature c) {
            return c.entity().getCapability(Capabilities.Energy.ENTITY, null);
        }
        Value.Place p = place(a, v);
        ServerLevel level = p == null ? null : level(a, p);
        return level == null || !level.isLoaded(p.pos()) ? null : level.getCapability(Capabilities.Energy.BLOCK, p.pos(), p.face());
    }
}
