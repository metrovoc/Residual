package io.github.metrovoc.residual.rehearsal;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.logging.LogUtils;
import io.github.metrovoc.residual.Residual;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.slf4j.Logger;

/** Server-side rehearsal operations and assertions, with an acknowledged reply for the driving client. */
@EventBusSubscriber(modid = Residual.MOD_ID)
public final class ServerVerify {
    private static final Logger LOG = LogUtils.getLogger();
    private static final Map<String, Set<UUID>> SIGNALS = new HashMap<>();
    private static final Set<String> EXACT = Set.of("nomobs", "noblocks", "empty", "state", "ticking", "operator", "menu", "distinct");

    private ServerVerify() {
    }

    @SubscribeEvent
    static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("rehearsal_verify")
                .then(Commands.argument("id", IntegerArgumentType.integer(1))
                        .then(Commands.argument("operation", StringArgumentType.greedyString()).executes(c -> {
                            ServerPlayer player = c.getSource().getPlayerOrException();
                            int id = IntegerArgumentType.getInteger(c, "id");
                            String operation = StringArgumentType.getString(c, "operation");
                            String reply;
                            try {
                                String[] a = operation.split("\\s+");
                                // A second player must be able to verify refusal while genuinely lacking operator privileges.
                                if (!a[0].equals("expect") && !a[0].equals("signal")
                                        && !player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
                                    throw new IllegalStateException("operator privileges required for " + a[0]);
                                }
                                run(player, a);
                                reply = "OK";
                            } catch (Exception e) {
                                if (e instanceof IllegalStateException) {
                                    LOG.info("[rehearsal] server check: {}", e.getMessage());
                                } else {
                                    LOG.error("[rehearsal] server operation failed: {}", operation, e);
                                }
                                reply = "FAIL " + e.getClass().getSimpleName() + ": " + e.getMessage();
                            }
                            player.sendSystemMessage(Component.literal("[rehearsal-server " + id + "] " + reply));
                            return reply.equals("OK") ? 1 : 0;
                        }))));
    }

    private static void run(ServerPlayer player, String[] words) throws Exception {
        String[] a = java.util.Arrays.copyOfRange(words, 1, words.length);
        ServerLevel level = player.level();
        switch (words[0]) {
            case "expect" -> check(player, a);
            case "signal" -> SIGNALS.computeIfAbsent(a[0], key -> new HashSet<>()).add(player.getUUID());
            case "drive" -> ServerSteps.drive(player, Integer.parseInt(a[0]));
            case "mine" -> {
                if (!player.gameMode.destroyBlock(at(a, 0))) {
                    throw new IllegalStateException("could not mine " + at(a, 0));
                }
            }
            case "reset" -> reset(player, a);
            default -> throw new IllegalArgumentException("unknown server operation " + words[0]);
        }
    }

    /** Restores a bounded test area in the flat overworld, or empties a bounded fixture in another dimension. */
    private static void reset(ServerPlayer player, String[] a) {
        ServerLevel level = player.level().getServer().getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(a[0])));
        BlockPos low = at(a, 1), high = at(a, 4);
        boolean flat = a[0].equals("minecraft:overworld");
        int changed = 0;
        for (BlockPos pos : BlockPos.betweenClosed(low, high)) {
            BlockState wanted = (flat && pos.getY() == -64 ? Blocks.BEDROCK
                    : flat && (pos.getY() == -63 || pos.getY() == -62) ? Blocks.DIRT
                    : flat && pos.getY() == -61 ? Blocks.GRASS_BLOCK : Blocks.AIR).defaultBlockState();
            if (level.getBlockState(pos) != wanted) {
                level.setBlock(pos, wanted, Block.UPDATE_ALL);
                changed++;
            }
        }
        LOG.info("[rehearsal] reset {} {} to {}: {} blocks", a[0], low, high, changed);
    }

    private static BlockPos at(String[] args, int start) {
        return new BlockPos(Integer.parseInt(args[start]), Integer.parseInt(args[start + 1]), Integer.parseInt(args[start + 2]));
    }

    private static void check(ServerPlayer player, String[] args) {
        long wanted = Long.parseLong(args[args.length - 1]), found = read(player, args);
        if (EXACT.contains(args[0]) ? found != wanted : found < wanted) {
            throw new IllegalStateException("expected " + String.join(" ", args) + ", found " + found);
        }
        LOG.info("[rehearsal] expected {}, found {}", String.join(" ", args), found);
    }

    private static long read(ServerPlayer player, String[] args) {
        MinecraftServer server = player.level().getServer();
        ServerLevel level = player.level();
        switch (args[0]) {
            case "signal": return SIGNALS.getOrDefault(args[1], Set.of()).size();
            case "players": return server.getPlayerList().getPlayerCount();
            case "distinct": return !server.getPlayerList().getPlayerByName(args[1]).getUUID()
                    .equals(server.getPlayerList().getPlayerByName(args[2]).getUUID()) ? 1 : 0;
            case "operator": return server.getPlayerList().getPlayerByName(args[1]).permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER) ? 1 : 0;
            case "menu": return player.containerMenu.getClass().getSimpleName().equals(args[1]) ? 1 : 0;
            case "earned", "made": {
                AdvancementHolder holder = server.getAdvancements().get(Residual.id(args[1]));
                return holder != null && player.getAdvancements().getOrStartProgress(holder).isDone() ? 1 : 0;
            }
            default: break;
        }
        BlockPos at = at(args, 1);
        if (args[0].equals("empty")) {
            ResourceHandler<ItemResource> inv = level.getCapability(Capabilities.Item.BLOCK, at, null);
            long n = 0;
            for (int slot = 0; inv != null && slot < inv.size(); slot++) {
                n += inv.getAmountAsLong(slot);
            }
            return n;
        }
        if (args[0].equals("mobs") || args[0].equals("nomobs")) {
            var type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(args[5]));
            return (long) level.getEntities((Entity) null, new net.minecraft.world.phys.AABB(at).inflate(Integer.parseInt(args[4])), e -> e.getType() == type).size();
        }
        if (args[0].equals("placed") || args[0].equals("noblocks")) {
            BlockPos to = new BlockPos(Integer.parseInt(args[4]), Integer.parseInt(args[5]), Integer.parseInt(args[6]));
            Block block = BuiltInRegistries.BLOCK.getValue(Identifier.parse(args[7]));
            long n = 0;
            for (BlockPos q : BlockPos.betweenClosed(at, to)) {
                n += level.getBlockState(q).is(block) ? 1 : 0;
            }
            return n;
        }
        if (args[0].equals("state")) {
            String[] kv = args[4].split("=");
            for (var e : level.getBlockState(at).getValues().toList()) {
                if (e.property().getName().equals(kv[0])) {
                    return e.valueName().equals(kv[1]) ? 1L : 0L;
                }
            }
            return -1L;
        }
        if (args[0].equals("ticking")) {
            return level.shouldTickBlocksAt(at) ? 1L : 0L;
        }
        if (!args[0].equals("inv")) {
            throw new IllegalArgumentException("unknown server check " + args[0]);
        }
        ItemResource kind = ItemResource.of(BuiltInRegistries.ITEM.getValue(Identifier.parse(args[4])));
        ResourceHandler<ItemResource> inv = level.getCapability(Capabilities.Item.BLOCK, at, null);
        long n = 0;
        for (int slot = 0; inv != null && slot < inv.size(); slot++) {
            n += inv.getResource(slot).equals(kind) ? inv.getAmountAsLong(slot) : 0;
        }
        return n;
    }
}
