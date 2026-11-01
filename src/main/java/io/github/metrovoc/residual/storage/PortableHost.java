package io.github.metrovoc.residual.storage;

import io.github.metrovoc.residual.item.PortableDriveItem;
import io.github.metrovoc.residual.item.StorageDriveItem;
import io.github.metrovoc.residual.registry.ModComponents;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jetbrains.annotations.Nullable;

/** A Portable Drive's own screen: what the drive holds, a network of one, wherever the player carries it. */
public final class PortableHost implements TerminalHost {
    private final ServerPlayer player;
    private final UUID id;
    private final Drive drive;

    private PortableHost(ServerPlayer player, UUID id, Drive drive) {
        this.player = player;
        this.id = id;
        this.drive = drive;
    }

    public static void open(ServerPlayer player, ItemStack stack) {
        PortableDriveItem item = (PortableDriveItem) stack.getItem();
        UUID id = StorageDriveItem.id(stack);
        Drive drive = StorageData.of(player.level().getServer()).drive(id, item.capacity);
        PortableHost host = new PortableHost(player, id, drive);
        player.openMenu(new SimpleMenuProvider((menu, inventory, p) -> new TerminalMenu(menu, inventory, player.blockPosition(), host), stack.getHoverName()),
                buf -> buf.writeBlockPos(player.blockPosition()));
    }

    /** The drive, wherever in the inventory or on the cursor it is now. */
    @Nullable
    private ItemStack stack() {
        for (ItemStack stack : player.getInventory()) {
            if (mine(stack)) {
                return stack;
            }
        }
        ItemStack carried = player.containerMenu.getCarried();
        return mine(carried) ? carried : null;
    }

    private boolean mine(ItemStack stack) {
        StorageDriveItem.Label label = stack.get(ModComponents.STORAGE_DRIVE.get());
        return stack.getItem() instanceof PortableDriveItem && label != null && label.id().equals(id);
    }

    @Override
    public StorageNetwork network() {
        return StorageNetwork.holding((ServerLevel) player.level(), drive, player.blockPosition());
    }

    /** Keeps the note on the drive of how full it is, as a rack does for the drives in it. */
    @Override
    public void tick() {
        ItemStack stack = stack();
        if (stack != null) {
            StorageDriveItem.note(stack, drive);
        }
    }

    @Override
    public List<ItemResource> pins() {
        ItemStack stack = stack();
        return stack == null ? List.of() : stack.getOrDefault(ModComponents.PINS.get(), List.of());
    }

    @Override
    public void pin(ItemResource kind) {
        ItemStack stack = stack();
        if (stack != null) {
            stack.set(ModComponents.PINS.get(), TerminalHost.pinned(pins(), kind));
        }
    }

    @Override
    public boolean reaches(Player who) {
        return who == player && stack() != null;
    }

    /** A drive inside itself could never be taken out. */
    @Override
    public boolean takes(ItemStack stack) {
        return !mine(stack);
    }
}
