package io.github.metrovoc.residual.client;

import io.github.metrovoc.residual.model.Recovered;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;

/** Client-only actions that common code triggers; only ever called where {@code level.isClientSide()}. */
public final class ClientHooks {
    private ClientHooks() {
    }

    public static void readShard(Recovered record) {
        Minecraft.getInstance().setScreen(new ShardScreen(record));
    }

    public static void openBench(BlockPos pos) {
        Minecraft.getInstance().setScreen(new BenchScreen(pos));
    }

    public static void openTerminal(InteractionHand hand) {
        Minecraft.getInstance().setScreen(new TerminalScreen(hand));
    }
}
