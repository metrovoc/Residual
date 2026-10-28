package io.github.metrovoc.residual.net;

import io.github.metrovoc.residual.block.CoreBlockEntity;
import io.github.metrovoc.residual.block.OperatorBlockEntity;
import io.github.metrovoc.residual.menu.CoreMenu;
import io.github.metrovoc.residual.menu.OperatorMenu;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Registers the mod's packets and handles the ones sent from screens. Client handlers live in the client package. */
public final class Network {
    private Network() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToServer(CoreAction.TYPE, CoreAction.STREAM_CODEC, Network::onCoreAction);
        r.playToServer(MachineEdit.TYPE, MachineEdit.STREAM_CODEC, Network::onMachineEdit);
        r.playToClient(CoreSync.TYPE, CoreSync.STREAM_CODEC);
        r.playToClient(SampleResult.TYPE, SampleResult.STREAM_CODEC);
        r.playToClient(CodeText.TYPE, CodeText.STREAM_CODEC);
        r.playToClient(ExportChunk.TYPE, ExportChunk.STREAM_CODEC);
    }

    /** Only the player looking at a Core's screen may operate it. */
    private static void onCoreAction(CoreAction a, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player) || !(player.containerMenu instanceof CoreMenu menu)
                || !menu.pos.equals(a.pos()) || !menu.stillValid(player)
                || !(player.level().getBlockEntity(a.pos()) instanceof CoreBlockEntity core)) {
            return;
        }
        switch (a.kind()) {
            case SELECT -> core.select(player, a.value());
            case CHECK -> core.check();
            case START -> core.start(player);
            case STOP -> core.stop();
            case RESET -> core.reset();
            case LEARNING_RATE -> core.setLearningRate(a.value());
            case BATCH_SIZE -> core.setBatchSize(a.value());
            case CLIP -> core.setClip(a.value());
            case SPEED -> core.setSpeed(a.value());
            case BUILD_CASE -> core.buildCase();
            case SAMPLE -> core.sample(player, a.text(), a.value());
            case CODE -> core.code(player);
            case EXPORT -> core.export(player);
        }
    }

    private static void onMachineEdit(MachineEdit e, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player) || !(player.containerMenu instanceof OperatorMenu menu)
                || !menu.pos.equals(e.pos()) || !menu.stillValid(player)
                || !(player.level().getBlockEntity(e.pos()) instanceof OperatorBlockEntity machine)) {
            return;
        }
        if (MachineEdit.NAME.equals(e.key())) {
            machine.rename(e.value());
        } else {
            machine.configure(e.key(), e.value());
        }
    }
}
