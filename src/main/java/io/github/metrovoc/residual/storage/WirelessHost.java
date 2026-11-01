package io.github.metrovoc.residual.storage;

import io.github.metrovoc.residual.Awards;
import io.github.metrovoc.residual.item.WirelessTerminalItem;
import io.github.metrovoc.residual.registry.ModComponents;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jetbrains.annotations.Nullable;

/**
 * A Wireless Terminal's screen: the network of the Access Point it is tuned to, while the player is in reach of any Access
 * Point on that network. A network far from anyone would be unloaded, so its chunks are held loaded while the screen is open.
 */
public final class WirelessHost implements TerminalHost {
    /** A network holds at most this many chunks loaded for a screen. */
    private static final int MOST_CHUNKS = 256;

    private final ServerPlayer player;
    private final ServerLevel level;
    private final GlobalPos link;
    /** Which terminal this is, among others tuned to the same network. */
    private final UUID id;
    private final Set<ChunkPos> held = new HashSet<>();
    private final HeldModel model;
    private int ticks;

    private WirelessHost(ServerPlayer player, ServerLevel level, GlobalPos link, ItemStack holding) {
        this.player = player;
        this.level = level;
        this.link = link;
        if (!holding.has(ModComponents.TERMINAL_ID.get())) {
            holding.set(ModComponents.TERMINAL_ID.get(), UUID.randomUUID());
        }
        this.id = holding.get(ModComponents.TERMINAL_ID.get());
        this.model = new HeldModel(holding, this::terminal);
    }

    /** Opens the terminal in the player's inventory, if its network is in reach. */
    public static void open(ServerPlayer player, ItemStack terminal) {
        GlobalPos link = terminal.get(ModComponents.LINK.get());
        if (link == null) {
            player.sendOverlayMessage(Component.translatable("residual.wireless.untuned"));
            return;
        }
        ServerLevel level = player.level();
        if (!level.dimension().equals(link.dimension())) {
            player.sendOverlayMessage(Component.translatable("residual.wireless.out_of_reach"));
            return;
        }
        WirelessHost host = new WirelessHost(player, level, link, terminal);
        // the Access Point's own chunk first, loaded now: the rest of its network comes as its chunks load
        host.hold(List.of(ChunkPos.containing(link.pos())));
        level.getChunk(link.pos());
        if (!(level.getBlockEntity(link.pos()) instanceof AccessPointBlockEntity)) {
            player.sendOverlayMessage(Component.translatable("residual.wireless.gone"));
            return;
        }
        if (!host.inReach()) {
            player.sendOverlayMessage(Component.translatable("residual.wireless.out_of_reach"));
            return;
        }
        Awards.grant(player, "storage/wireless");
        if (!host.inRange()) {
            Awards.grant(player, "storage/relay");
        }
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new TerminalMenu(id, inventory, link.pos(), host), terminal.getHoverName()),
                buf -> buf.writeBlockPos(link.pos()));
    }

    /** Whether the player is in plain range of an Access Point on the network, with or without a Link Driver. */
    private boolean inRange() {
        StorageNetwork net = network();
        boolean driven = net.driverHasPower();
        for (AccessPointBlockEntity point : net.accessPoints()) {
            if (point.inRange(player.blockPosition(), driven)) {
                return true;
            }
        }
        return false;
    }

    /** Whether the player is in range of an Access Point on the network, or its relays cover them. */
    private boolean inReach() {
        if (inRange()) {
            return true;
        }
        for (AccessPointBlockEntity point : network().accessPoints()) {
            if (point.relayCovers(level, player.blockPosition())) {
                return true;
            }
        }
        return false;
    }

    /** Asks that these chunks stay loaded a little longer. */
    private void hold(List<ChunkPos> chunks) {
        for (ChunkPos chunk : chunks) {
            level.getChunkSource().addTicketWithRadius(TicketType.ENDER_PEARL, chunk, 2);
        }
    }

    @Override
    public StorageNetwork network() {
        return StorageNetwork.at(level, link.pos());
    }

    /** Keeps the network's chunks loaded: a network found further as they load is held further. */
    @Override
    public void tick() {
        if (ticks++ % 4 != 0) {
            return;
        }
        Set<ChunkPos> chunks = new HashSet<>();
        chunks.add(ChunkPos.containing(link.pos()));
        for (BlockPos node : network().nodes) {
            chunks.add(ChunkPos.containing(node));
            if (chunks.size() >= MOST_CHUNKS) {
                break;
            }
        }
        hold(new ArrayList<>(chunks));
        if (held.addAll(chunks)) {
            StorageNetwork.changed(level);
        }
        // reaching further than an Access Point's plain range is paid for by the Link Driver, by the second, at the rate it drives
        StorageNetwork net = network();
        boolean within = false;
        for (AccessPointBlockEntity point : net.accessPoints()) {
            within |= point.inRange(player.blockPosition(), false);
        }
        if (!within) {
            net.payForReach(20);
        }
    }

    @Override
    public Container searchModel() {
        return model;
    }

    @Override
    public List<ItemResource> pins() {
        ItemStack terminal = terminal();
        return terminal == null ? List.of() : terminal.getOrDefault(ModComponents.PINS.get(), List.of());
    }

    @Override
    public void pin(ItemResource kind) {
        ItemStack terminal = terminal();
        if (terminal != null) {
            terminal.set(ModComponents.PINS.get(), TerminalHost.pinned(pins(), kind));
        }
    }

    /** The terminal, wherever in the inventory or on the cursor it is now. */
    @Nullable
    private ItemStack terminal() {
        for (ItemStack stack : player.getInventory()) {
            if (mine(stack)) {
                return stack;
            }
        }
        ItemStack carried = player.containerMenu.getCarried();
        return mine(carried) ? carried : null;
    }

    private boolean mine(ItemStack stack) {
        return stack.getItem() instanceof WirelessTerminalItem && id.equals(stack.get(ModComponents.TERMINAL_ID.get()))
                && link.equals(stack.get(ModComponents.LINK.get()));
    }

    @Override
    public boolean reaches(Player who) {
        if (who != player || player.level() != level || terminal() == null) {
            return false;
        }
        boolean in = level.getBlockEntity(link.pos()) instanceof AccessPointBlockEntity && inReach();
        if (!in) {
            player.sendOverlayMessage(Component.translatable("residual.wireless.out_of_reach"));
        }
        return in;
    }
}
