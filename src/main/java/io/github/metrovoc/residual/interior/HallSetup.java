package io.github.metrovoc.residual.interior;

import com.mojang.brigadier.arguments.StringArgumentType;
import io.github.metrovoc.residual.Residual;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Everything the halls add to the game, in one place. */
public final class HallSetup {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Residual.MOD_ID);
    public static final DeferredBlock<ConsoleBlock> CONSOLE = BLOCKS.registerBlock("console", ConsoleBlock::new,
            p -> p.mapColor(MapColor.COLOR_CYAN).strength(-1f, 3_600_000f).lightLevel(s -> 9).noLootTable());

    private HallSetup() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        modBus.addListener((RegisterPayloadHandlersEvent event) -> event.registrar("1").playToClient(HallSync.TYPE, HallSync.STREAM_CODEC)
                .playToServer(SpaceAct.TYPE, SpaceAct.STREAM_CODEC, (act, ctx) -> {
                    if (ctx.player() instanceof ServerPlayer player) {
                        Halls.get(player.level().getServer()).act(player, act);
                    }
                }));
        NeoForge.EVENT_BUS.addListener(Halls::tick);
        NeoForge.EVENT_BUS.addListener(Halls::stopping);
        NeoForge.EVENT_BUS.addListener(Halls::breaking);
        NeoForge.EVENT_BUS.addListener(Halls::placing);
        NeoForge.EVENT_BUS.addListener(HallSetup::commands);
    }

    /** {@code /residual hall <name>}: steps into the hall of the model called {@code name}, new if there is none. For operators. */
    private static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("residual").then(Commands.literal("hall")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.argument("name", StringArgumentType.word()).executes(c -> {
                    ServerPlayer player = c.getSource().getPlayerOrException();
                    UUID id = UUID.nameUUIDFromBytes(("hall:" + StringArgumentType.getString(c, "name")).getBytes(StandardCharsets.UTF_8));
                    if (!Halls.get(c.getSource().getServer()).enter(player, id)) {
                        c.getSource().sendFailure(Component.translatable("residual.inside.missing"));
                        return 0;
                    }
                    return 1;
                }))));
    }
}
