package io.github.metrovoc.residual.net;

import io.github.metrovoc.residual.block.CoreBlockEntity;
import io.github.metrovoc.residual.block.InstrumentBlockEntity;
import io.github.metrovoc.residual.block.OperatorBlockEntity;
import io.github.metrovoc.residual.item.WayfinderItem;
import io.github.metrovoc.residual.menu.CoreMenu;
import io.github.metrovoc.residual.menu.InstrumentMenu;
import io.github.metrovoc.residual.menu.OperatorMenu;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
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
        r.playToServer(FindRuin.TYPE, FindRuin.STREAM_CODEC, Network::onFindRuin);
        r.playToServer(ScopeAction.TYPE, ScopeAction.STREAM_CODEC, Network::onScopeAction);
        r.playToClient(ScopeSync.TYPE, ScopeSync.STREAM_CODEC);
        r.playToClient(ScopeReading.TYPE, ScopeReading.STREAM_CODEC);
        r.playToClient(StreamMarks.TYPE, StreamMarks.STREAM_CODEC);
        r.playToClient(EvaluationSync.TYPE, EvaluationSync.STREAM_CODEC);
        r.playToClient(CoreSync.TYPE, CoreSync.STREAM_CODEC);
        r.playToClient(ComputeSync.TYPE, ComputeSync.STREAM_CODEC);
        r.playToClient(SampleResult.TYPE, SampleResult.STREAM_CODEC);
        r.playToClient(WaveSync.TYPE, WaveSync.STREAM_CODEC);
        r.playToClient(ShimmerSync.TYPE, ShimmerSync.STREAM_CODEC);
    }

    /** Only the player looking at a Stream Scope's or Trial Rig's screen may set it to work. */
    private static void onScopeAction(ScopeAction a, IPayloadContext ctx) {
        if (ctx.player() instanceof ServerPlayer player && player.containerMenu instanceof InstrumentMenu menu && menu.pos.equals(a.pos()) && menu.stillValid(player)
                && player.level().getBlockEntity(a.pos()) instanceof InstrumentBlockEntity instrument) {
            instrument.act(player, a);
        }
    }

    private static void onFindRuin(FindRuin f, IPayloadContext ctx) {
        if (ctx.player() instanceof ServerPlayer player) {
            WayfinderItem.find(player, f.mainHand() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND, f.kind());
        }
    }

    /** Only the player looking at a Core's screen may operate it. */
    private static void onCoreAction(CoreAction a, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player) || !(player.containerMenu instanceof CoreMenu menu)
                || !menu.pos.equals(a.pos()) || !menu.stillValid(player)
                || !(player.level().getBlockEntity(a.pos()) instanceof CoreBlockEntity core)) {
            return;
        }
        switch (a.kind()) {
            case CHECK -> core.check();
            case START -> core.start(player);
            case STOP -> core.stop();
            case RESET -> core.reset();
            case LEARNING_RATE -> core.setLearningRate(a.value());
            case BATCH_SIZE -> core.setBatchSize(a.value());
            case CLIP -> core.setClip(a.value());
            case SPEED -> core.setSpeed(a.value());
            case BUILD_WRECK -> core.buildWreck();
            case SAMPLE -> core.sample(player, a.text(), a.value());
            case TEST -> core.runTests();
            case SAVE -> core.save(player);
            case SCHEDULE -> core.setSchedule(a.value());
            case WARMUP -> core.setWarmup(a.value());
            case LENGTH -> core.setLength(a.value());
            case PRECISION -> core.setPrecision(a.value());
            case MOVES -> core.setMoves(a.value());
            case PACKING -> core.setPacking(a.value());
            case DECAY -> core.setDecay(a.value());
            case EVALUATE -> core.evaluate(player, a.value(), !a.text().isEmpty());
            case SEEDS -> core.seeds(player, a.value());
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
