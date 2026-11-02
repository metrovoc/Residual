package io.github.metrovoc.residual.rehearsal;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.google.gson.JsonParser;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.JsonOps;
import io.github.metrovoc.residual.Residual;
import io.github.metrovoc.residual.block.CoreBlock;
import io.github.metrovoc.residual.block.OperatorBlockEntity;
import io.github.metrovoc.residual.graph.GraphSpec;
import io.github.metrovoc.residual.item.CrystalItem;
import io.github.metrovoc.residual.item.DriveItem;
import io.github.metrovoc.residual.item.ShardItem;
import io.github.metrovoc.residual.learn.Blueprints;
import io.github.metrovoc.residual.model.ModelInfo;
import io.github.metrovoc.residual.registry.ModBlocks;
import io.github.metrovoc.residual.registry.ModComponents;
import io.github.metrovoc.residual.registry.ModItems;
import io.github.metrovoc.residual.world.CaseBuilder;
import io.github.metrovoc.residual.world.CaseLayout;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * What a script asks of the world through the server, so it works on a dedicated server too:
 * {@code /rehearsal blueprint NAME X Y Z FACING}, {@code /rehearsal drive SHARDS}, and {@code /rehearsal keep NAME} and
 * {@code /rehearsal model NAME}, which keep a trained model outside the world and give it back in any other, so a script
 * that only needs a model never trains one.
 */
@EventBusSubscriber(modid = Residual.MOD_ID)
public final class ServerSteps {
    /** Trained models kept between worlds: {@code run/models} when run from the build. */
    private static final Path KEPT = Path.of(System.getProperty("residual.models", "models"));

    private ServerSteps() {
    }

    /** Places a Model Core and builds a reference model in front of it, as a player would. */
    static void blueprint(ServerLevel level, String name, BlockPos core, Direction forward) {
        GraphSpec spec = switch (name) {
            case "bigram" -> Blueprints.bigram();
            case "context" -> Blueprints.context();
            case "recall" -> Blueprints.recall();
            case "reverse" -> Blueprints.reverse();
            case "induction" -> Blueprints.induction();
            case "gpt" -> Blueprints.gpt(64, 2, 4);
            case "gpt32" -> Blueprints.gpt(32, 2, 4);
            case "modern32" -> Blueprints.modern(32, 2, 4);
            case "gpt128" -> Blueprints.gpt(128, 2, 4);
            default -> throw new IllegalArgumentException("no blueprint named " + name);
        };
        level.setBlock(core, ModBlocks.CORE.get().defaultBlockState().setValue(CoreBlock.FACING, forward), Block.UPDATE_ALL);
        CaseLayout layout = CaseLayout.of(spec);
        CaseBuilder.build(level, core, spec, layout);
        // a case's machines are marked as provided; these are the player's own
        for (CaseLayout.Machine m : layout.machines) {
            BlockPos p = core.relative(forward, m.at().x()).relative(forward.getClockWise(), m.at().l()).above(m.at().y());
            if (level.getBlockEntity(p) instanceof OperatorBlockEntity be) {
                try {
                    Field provided = OperatorBlockEntity.class.getDeclaredField("provided");
                    provided.setAccessible(true);
                    provided.setBoolean(be, false);
                    be.setChanged();
                    level.sendBlockUpdated(p, be.getBlockState(), be.getBlockState(), Block.UPDATE_CLIENTS);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
    }

    /** Gives the player an Archive Drive holding that many shards' worth of the archive. */
    static void drive(ServerPlayer player, int shards) {
        ItemStack drive = new ItemStack(ModItems.ARCHIVE_DRIVE.get());
        for (int i = 0; i < shards; i++) {
            DriveItem.file(drive, ShardItem.roll(player.getRandom()));
        }
        player.getInventory().add(drive);
    }

    private static Path saved(MinecraftServer server, ModelInfo info) {
        return server.getWorldPath(LevelResource.ROOT).resolve("residual").resolve("models").resolve(info.id() + ".dat");
    }

    /** Keeps the first saved model the player carries, under a name. */
    static void keep(ServerPlayer player, String name) throws IOException {
        for (ItemStack stack : player.getInventory()) {
            ModelInfo info = CrystalItem.model(stack);
            if (info != null) {
                Files.createDirectories(KEPT);
                Files.copy(saved(player.level().getServer(), info), KEPT.resolve(name + ".dat"), StandardCopyOption.REPLACE_EXISTING);
                Files.writeString(KEPT.resolve(name + ".json"), ModelInfo.CODEC.encodeStart(JsonOps.INSTANCE, info).getOrThrow().toString());
                return;
            }
        }
        throw new IOException("no saved model to keep");
    }

    /** Gives the player a checkpoint holding a kept model. */
    static void model(ServerPlayer player, String name) throws IOException {
        ModelInfo info = ModelInfo.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(Files.readString(KEPT.resolve(name + ".json")))).getOrThrow();
        Path file = saved(player.level().getServer(), info);
        Files.createDirectories(file.getParent());
        Files.copy(KEPT.resolve(name + ".dat"), file, StandardCopyOption.REPLACE_EXISTING);
        ItemStack checkpoint = new ItemStack(ModItems.CHECKPOINT.get());
        checkpoint.set(ModComponents.MODEL.get(), info);
        player.getInventory().add(checkpoint);
    }

    private static int kept(CommandContext<CommandSourceStack> c, Step step) throws CommandSyntaxException {
        try {
            step.run(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "name"));
            return 1;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @FunctionalInterface
    private interface Step {
        void run(ServerPlayer player, String name) throws IOException;
    }

    @SubscribeEvent
    static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("rehearsal").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("blueprint").then(Commands.argument("name", StringArgumentType.word())
                        .then(Commands.argument("core", BlockPosArgument.blockPos()).then(Commands.argument("facing", StringArgumentType.word())
                                .executes(c -> {
                                    blueprint(c.getSource().getLevel(), StringArgumentType.getString(c, "name"), BlockPosArgument.getBlockPos(c, "core"),
                                            Direction.byName(StringArgumentType.getString(c, "facing")));
                                    return 1;
                                })))))
                .then(Commands.literal("drive").then(Commands.argument("shards", IntegerArgumentType.integer(1)).executes(c -> {
                    drive(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "shards"));
                    return 1;
                })))
                .then(Commands.literal("keep").then(Commands.argument("name", StringArgumentType.word()).executes(c -> kept(c, ServerSteps::keep))))
                .then(Commands.literal("model").then(Commands.argument("name", StringArgumentType.word()).executes(c -> kept(c, ServerSteps::model)))));
    }
}
