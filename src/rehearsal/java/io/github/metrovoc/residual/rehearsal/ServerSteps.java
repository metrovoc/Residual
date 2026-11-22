package io.github.metrovoc.residual.rehearsal;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import io.github.metrovoc.residual.Residual;
import io.github.metrovoc.residual.item.DriveItem;
import io.github.metrovoc.residual.item.ShardItem;
import io.github.metrovoc.residual.registry.ModItems;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** What a script asks of the world through the server, so it works on a dedicated server too: {@code /rehearsal drive SHARDS}. */
@EventBusSubscriber(modid = Residual.MOD_ID)
public final class ServerSteps {
    private ServerSteps() {
    }

    /** Gives the player an Archive Drive holding that many shards' worth of the archive. */
    static void drive(ServerPlayer player, int shards) {
        ItemStack drive = new ItemStack(ModItems.ARCHIVE_DRIVE.get());
        for (int i = 0; i < shards; i++) {
            DriveItem.file(drive, ShardItem.roll(player.getRandom()));
        }
        player.getInventory().add(drive);
    }

    @SubscribeEvent
    static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("rehearsal").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("drive").then(Commands.argument("shards", IntegerArgumentType.integer(1)).executes(c -> {
                    drive(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "shards"));
                    return 1;
                }))));
    }
}
