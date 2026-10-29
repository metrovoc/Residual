package io.github.metrovoc.residual.net;

import io.github.metrovoc.residual.apprentice.Apprentice;
import io.github.metrovoc.residual.apprentice.Job;
import io.github.metrovoc.residual.block.CoreBlockEntity;
import io.github.metrovoc.residual.block.OperatorBlockEntity;
import io.github.metrovoc.residual.learn.Sampler;
import io.github.metrovoc.residual.learn.Vocab;
import io.github.metrovoc.residual.menu.ApprenticeMenu;
import io.github.metrovoc.residual.menu.CoreMenu;
import io.github.metrovoc.residual.menu.OperatorMenu;
import io.github.metrovoc.residual.mind.MindLink;
import io.github.metrovoc.residual.registry.ModItems;
import java.util.List;
import java.util.Random;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
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
        r.playToServer(ApprenticeAction.TYPE, ApprenticeAction.STREAM_CODEC, Network::onApprenticeAction);
        r.playToServer(TerminalAsk.TYPE, TerminalAsk.STREAM_CODEC, Network::onTerminalAsk);
        r.playToClient(ApprenticeSync.TYPE, ApprenticeSync.STREAM_CODEC);
        r.playToClient(TerminalAnswer.TYPE, TerminalAnswer.STREAM_CODEC);
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
            case CODE -> core.code(player);
            case EXPORT -> core.export(player);
            case TEST -> core.runTests();
            case SAVE -> core.save(player);
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

    private static void onApprenticeAction(ApprenticeAction a, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player) || !(player.containerMenu instanceof ApprenticeMenu menu)
                || menu.entityId != a.entity() || !menu.stillValid(player) || menu.apprentice == null) {
            return;
        }
        Apprentice apprentice = menu.apprentice;
        switch (a.kind()) {
            case JOB -> {
                if (a.job() >= 0 && a.job() < Job.values().length) {
                    apprentice.setJob(Job.values()[a.job()]);
                }
            }
            case SAY -> apprentice.order(player, a.said());
            case HOME -> apprentice.setHome(apprentice.blockPosition());
        }
        PacketDistributor.sendToPlayer(player, apprentice.snapshot());
    }

    /** Continues the player's text with the mind their Terminal is tuned to, if the network reaches them. */
    private static void onTerminalAsk(TerminalAsk a, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack held = player.getItemInHand(a.offhand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        if (!held.is(ModItems.TERMINAL.get())) {
            return;
        }
        switch (MindLink.of(player.level(), player.blockPosition(), held)) {
            case MindLink.Served s -> s.server().submit(model -> {
                int[] prompt = Vocab.WORLD.encode(a.prompt());
                StringBuilder out = new StringBuilder();
                for (Sampler.Step step : Sampler.generate(model, prompt, 160, Vocab.CONTEXT, 0.8f, new Random())) {
                    out.append(Vocab.WORLD.symbol(step.token()));
                }
                return out.toString();
            }, text -> PacketDistributor.sendToPlayer(player, text == null
                    ? new TerminalAnswer(a.prompt(), "", "residual.mind.failed", List.of())
                    : new TerminalAnswer(a.prompt(), text, "", List.of())));
            case MindLink.Missing m -> PacketDistributor.sendToPlayer(player, new TerminalAnswer(a.prompt(), "", m.problem(), m.args()));
            case MindLink.Onboard o -> {
            }
        }
    }
}
