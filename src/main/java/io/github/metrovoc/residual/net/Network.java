package io.github.metrovoc.residual.net;

import io.github.metrovoc.residual.Awards;
import io.github.metrovoc.residual.agent.Agent;
import io.github.metrovoc.residual.agent.Debug;
import io.github.metrovoc.residual.agent.Job;
import io.github.metrovoc.residual.agent.Ownership;
import io.github.metrovoc.residual.agent.PortWork;
import io.github.metrovoc.residual.bench.Spec;
import io.github.metrovoc.residual.bench.Specs;
import io.github.metrovoc.residual.block.BenchBlockEntity;
import io.github.metrovoc.residual.block.CoreBlockEntity;
import io.github.metrovoc.residual.block.OperatorBlockEntity;
import io.github.metrovoc.residual.item.ExosuitItem;
import io.github.metrovoc.residual.item.ProgramCardItem;
import io.github.metrovoc.residual.item.PortableDriveItem;
import io.github.metrovoc.residual.item.SuitAbility;
import io.github.metrovoc.residual.item.Vein;
import io.github.metrovoc.residual.item.WayfinderItem;
import io.github.metrovoc.residual.item.WirelessTerminalItem;
import io.github.metrovoc.residual.learn.Sampler;
import io.github.metrovoc.residual.learn.Vocab;
import io.github.metrovoc.residual.menu.AgentMenu;
import io.github.metrovoc.residual.menu.CoreMenu;
import io.github.metrovoc.residual.menu.OperatorMenu;
import io.github.metrovoc.residual.model.ModelLink;
import io.github.metrovoc.residual.refine.RefinerySync;
import io.github.metrovoc.residual.registry.ModItems;
import io.github.metrovoc.residual.storage.AssemblerBlockEntity;
import io.github.metrovoc.residual.storage.AssemblerMenu;
import io.github.metrovoc.residual.storage.DockBlockEntity;
import io.github.metrovoc.residual.storage.DockMenu;
import io.github.metrovoc.residual.storage.PortableHost;
import io.github.metrovoc.residual.storage.WirelessHost;
import io.github.metrovoc.residual.storage.ProcessorBlockEntity;
import io.github.metrovoc.residual.storage.ProcessorMenu;
import io.github.metrovoc.residual.storage.PortBlockEntity;
import io.github.metrovoc.residual.storage.PortMenu;
import io.github.metrovoc.residual.storage.PortMode;
import io.github.metrovoc.residual.storage.PortProgramMenu;
import io.github.metrovoc.residual.storage.TerminalMenu;
import io.github.metrovoc.residual.world.TerminalApi;
import java.util.List;
import java.util.Random;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
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
        r.playToServer(AgentAction.TYPE, AgentAction.STREAM_CODEC, Network::onAgentAction);
        r.playToServer(TerminalAsk.TYPE, TerminalAsk.STREAM_CODEC, Network::onTerminalAsk);
        r.playToServer(TerminalCall.TYPE, TerminalCall.STREAM_CODEC, Network::onTerminalCall);
        r.playToServer(BenchEdit.TYPE, BenchEdit.STREAM_CODEC, Network::onBenchEdit);
        r.playToServer(BenchRecord.TYPE, BenchRecord.STREAM_CODEC, Network::onBenchRecord);
        r.playToServer(StorageClick.TYPE, StorageClick.STREAM_CODEC, Network::onStorageClick);
        r.playToServer(PortEdit.TYPE, PortEdit.STREAM_CODEC, Network::onPortEdit);
        r.playToServer(AssemblerEdit.TYPE, AssemblerEdit.STREAM_CODEC, Network::onAssemblerEdit);
        r.playToServer(CraftAsk.TYPE, CraftAsk.STREAM_CODEC, Network::onCraftAsk);
        r.playToServer(CraftCancel.TYPE, CraftCancel.STREAM_CODEC, Network::onCraftCancel);
        r.playToServer(CraftWatch.TYPE, CraftWatch.STREAM_CODEC, Network::onCraftWatch);
        r.playToServer(StoragePin.TYPE, StoragePin.STREAM_CODEC, Network::onStoragePin);
        r.playToServer(StorageFill.TYPE, StorageFill.STREAM_CODEC, Network::onStorageFill);
        r.playToServer(StorageMean.TYPE, StorageMean.STREAM_CODEC, Network::onStorageMean);
        r.playToServer(StorageTransfer.TYPE, StorageTransfer.STREAM_CODEC, Network::onStorageTransfer);
        r.playToServer(PatternTransfer.TYPE, PatternTransfer.STREAM_CODEC, Network::onPatternTransfer);
        r.playToServer(GhostSlot.TYPE, GhostSlot.STREAM_CODEC, Network::onGhostSlot);
        r.playToServer(FindRuin.TYPE, FindRuin.STREAM_CODEC, Network::onFindRuin);
        r.playToServer(DockEdit.TYPE, DockEdit.STREAM_CODEC, Network::onDockEdit);
        r.playToServer(OpenTerminal.TYPE, OpenTerminal.STREAM_CODEC, Network::onOpenTerminal);
        r.playToServer(SuitEdit.TYPE, SuitEdit.STREAM_CODEC, Network::onSuitEdit);
        r.playToServer(PortProgramAction.TYPE, PortProgramAction.STREAM_CODEC, Network::onPortProgram);
        r.playToServer(SuitVein.TYPE, SuitVein.STREAM_CODEC, Network::onSuitVein);
        r.playToClient(AgentSync.TYPE, AgentSync.STREAM_CODEC);
        r.playToClient(ProgramSync.TYPE, ProgramSync.STREAM_CODEC);
        r.playToClient(TerminalAnswer.TYPE, TerminalAnswer.STREAM_CODEC);
        r.playToClient(TerminalBaseSync.TYPE, TerminalBaseSync.STREAM_CODEC);
        r.playToClient(TerminalAgentsSync.TYPE, TerminalAgentsSync.STREAM_CODEC);
        r.playToClient(RelaySync.TYPE, RelaySync.STREAM_CODEC);
        r.playToClient(CoreSync.TYPE, CoreSync.STREAM_CODEC);
        r.playToClient(SampleResult.TYPE, SampleResult.STREAM_CODEC);
        r.playToClient(WaveSync.TYPE, WaveSync.STREAM_CODEC);
        r.playToClient(StorageSync.TYPE, StorageSync.STREAM_CODEC);
        r.playToClient(StorageMeaningSync.TYPE, StorageMeaningSync.STREAM_CODEC);
        r.playToClient(FlowSync.TYPE, FlowSync.STREAM_CODEC);
        r.playToClient(StepSync.TYPE, StepSync.STREAM_CODEC);
        r.playToClient(GhostSync.TYPE, GhostSync.STREAM_CODEC);
        r.playToClient(SurveySync.TYPE, SurveySync.STREAM_CODEC);
        r.playToClient(CraftPlanSync.TYPE, CraftPlanSync.STREAM_CODEC);
        r.playToClient(CraftJobsSync.TYPE, CraftJobsSync.STREAM_CODEC);
        r.playToClient(CraftTreeSync.TYPE, CraftTreeSync.STREAM_CODEC);
        r.playToClient(WatchSync.TYPE, WatchSync.STREAM_CODEC);
        r.playToClient(ShimmerSync.TYPE, ShimmerSync.STREAM_CODEC);
        r.playToClient(SuitFlight.TYPE, SuitFlight.STREAM_CODEC);
        r.playToClient(RefinerySync.TYPE, RefinerySync.STREAM_CODEC);
    }

    private static void onDockEdit(DockEdit e, IPayloadContext ctx) {
        if (ctx.player() instanceof ServerPlayer player && player.containerMenu instanceof DockMenu menu && menu.pos.equals(e.pos())
                && menu.stillValid(player) && player.level().getBlockEntity(e.pos()) instanceof DockBlockEntity dock) {
            dock.setFilling(e.filling());
        }
    }

    /** Opens the first Wireless Terminal in the player's inventory, or else the first Portable Drive. */
    private static void onOpenTerminal(OpenTerminal open, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) {
            return;
        }
        for (ItemStack stack : player.getInventory()) {
            if (stack.getItem() instanceof WirelessTerminalItem) {
                WirelessHost.open(player, stack);
                return;
            }
        }
        for (ItemStack stack : player.getInventory()) {
            if (stack.getItem() instanceof PortableDriveItem) {
                PortableHost.open(player, stack);
                return;
            }
        }
    }

    private static void onFindRuin(FindRuin f, IPayloadContext ctx) {
        if (ctx.player() instanceof ServerPlayer player) {
            WayfinderItem.find(player, f.mainHand() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND, f.kind());
        }
    }

    private static void onSuitVein(SuitVein v, IPayloadContext ctx) {
        if (ctx.player() instanceof ServerPlayer player) {
            Vein.hold(player, v.held());
        }
    }

    private static void onSuitEdit(SuitEdit e, IPayloadContext ctx) {
        if (ctx.player() instanceof ServerPlayer player && e.ability() >= 0 && e.ability() < SuitAbility.values().length
                && !ExosuitItem.on(player).isEmpty()) {
            ExosuitItem.turn(ExosuitItem.on(player), SuitAbility.values()[e.ability()], e.on());
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

    private static void onAgentAction(AgentAction a, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player) || !(player.containerMenu instanceof AgentMenu menu)
                || menu.entityId != a.entity() || !menu.stillValid(player) || menu.agent == null) {
            return;
        }
        Agent agent = menu.agent;
        switch (a.kind()) {
            case JOB -> {
                if (a.job() >= 0 && a.job() < Job.values().length) {
                    agent.setProgram(Job.values()[a.job()].program, player);
                }
            }
            case PROGRAM -> agent.setProgram(a.said(), player);
            case STOP -> agent.stop();
            case RUN -> agent.run();
            case SAY -> agent.order(player, a.said());
            case HOME -> agent.setHome(agent.blockPosition());
            case CARD -> ProgramCardItem.write(player, menu.getCarried(), a.said());
            case ANSWER -> agent.answer(player, a.job() == 1);
            case DEBUG -> {
                // the move and the step's number share one number, the move in the low bits
                Debug.Move[] moves = Debug.Move.values();
                agent.debug(moves[Math.floorMod(a.job(), moves.length)], a.job() / moves.length, a.said());
                Awards.grant(player, "agent/debugger");
            }
            case ACCESS -> agent.ownership().setAccess(player, Ownership.Access.values()[Math.floorMod(a.job(), Ownership.Access.values().length)]);
        }
        PacketDistributor.sendToPlayer(player, agent.snapshot());
    }

    private static void onBenchEdit(BenchEdit e, IPayloadContext ctx) {
        Spec spec = Specs.get(e.spec());
        if (ctx.player() instanceof ServerPlayer player && spec != null && player.distanceToSqr(e.pos().getCenter()) < 64
                && player.level().getBlockEntity(e.pos()) instanceof BenchBlockEntity bench) {
            bench.edit(spec, e.design());
        }
    }

    private static void onBenchRecord(BenchRecord r, IPayloadContext ctx) {
        Spec spec = Specs.get(r.spec());
        if (ctx.player() instanceof ServerPlayer player && spec != null && player.distanceToSqr(r.pos().getCenter()) < 64
                && player.level().getBlockEntity(r.pos()) instanceof BenchBlockEntity bench) {
            bench.record(player, spec);
        }
    }

    private static void onTerminalCall(TerminalCall call, IPayloadContext ctx) {
        if (ctx.player() instanceof ServerPlayer player) {
            TerminalApi.call(player, call);
        }
    }

    /** Continues the player's text with the model their Terminal is tuned to, if the network reaches them. */
    private static void onTerminalAsk(TerminalAsk a, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack held = player.getItemInHand(a.offhand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        if (!held.is(ModItems.TERMINAL.get())) {
            return;
        }
        switch (ModelLink.of(player.level(), player.blockPosition(), held)) {
            case ModelLink.Served s -> s.server().submit(model -> {
                Vocab vocab = model.env.tokenizer();
                StringBuilder out = new StringBuilder();
                for (Sampler.Step step : Sampler.generate(model, vocab.encode(a.prompt()), 160, 0.8f, new Random())) {
                    out.append(vocab.piece(step.token()));
                }
                return out.toString();
            }, text -> PacketDistributor.sendToPlayer(player, text == null
                    ? new TerminalAnswer(a.prompt(), "", "residual.model.failed", List.of())
                    : new TerminalAnswer(a.prompt(), text, "", List.of())));
            case ModelLink.Missing m -> PacketDistributor.sendToPlayer(player, new TerminalAnswer(a.prompt(), "", m.problem(), m.args()));
            case ModelLink.Onboard o -> {
            }
        }
    }

    /** Only the player looking at a Storage Terminal's screen may take from or put into its network. */
    private static void onStorageClick(StorageClick click, IPayloadContext ctx) {
        if (ctx.player() instanceof ServerPlayer player && player.containerMenu instanceof TerminalMenu menu
                && menu.containerId == click.menu() && menu.stillValid(player)) {
            menu.click(click);
        }
    }

    /** Only the player looking at a Storage Terminal's screen may ask its network to make something. */
    private static void onCraftAsk(CraftAsk ask, IPayloadContext ctx) {
        if (ctx.player() instanceof ServerPlayer player && player.containerMenu instanceof TerminalMenu menu
                && menu.containerId == ask.menu() && menu.stillValid(player)) {
            menu.ask(ask);
        }
    }

    private static void onCraftCancel(CraftCancel cancel, IPayloadContext ctx) {
        if (ctx.player() instanceof ServerPlayer player && player.containerMenu instanceof TerminalMenu menu
                && menu.containerId == cancel.menu() && menu.stillValid(player)) {
            menu.cancel(cancel);
        }
    }

    private static void onCraftWatch(CraftWatch watch, IPayloadContext ctx) {
        if (ctx.player() instanceof ServerPlayer player && player.containerMenu instanceof TerminalMenu menu
                && menu.containerId == watch.menu() && menu.stillValid(player)) {
            menu.watch(watch);
        }
    }

    private static void onStoragePin(StoragePin pin, IPayloadContext ctx) {
        if (ctx.player() instanceof ServerPlayer player && player.containerMenu instanceof TerminalMenu menu
                && menu.containerId == pin.menu() && menu.stillValid(player)) {
            menu.pin(pin);
        }
    }

    private static void onStorageFill(StorageFill fill, IPayloadContext ctx) {
        if (ctx.player() instanceof ServerPlayer player && player.containerMenu instanceof TerminalMenu menu
                && menu.containerId == fill.menu() && menu.stillValid(player)) {
            menu.fill(fill.fluid());
        }
    }

    private static void onStorageMean(StorageMean mean, IPayloadContext ctx) {
        if (ctx.player() instanceof ServerPlayer player && player.containerMenu instanceof TerminalMenu menu
                && menu.containerId == mean.menu() && menu.stillValid(player)) {
            menu.mean(mean.query());
        }
    }

    private static void onStorageTransfer(StorageTransfer transfer, IPayloadContext ctx) {
        if (ctx.player() instanceof ServerPlayer player && player.containerMenu instanceof TerminalMenu menu
                && menu.containerId == transfer.menu() && menu.stillValid(player)) {
            menu.transfer(transfer.cells(), transfer.stacks());
        }
    }

    /** Draws a recipe from the recipe viewer on the Assembler or Processor whose screen is open. */
    private static void onPatternTransfer(PatternTransfer transfer, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player) || player.containerMenu.containerId != transfer.menu()) {
            return;
        }
        if (player.containerMenu instanceof AssemblerMenu menu && menu.stillValid(player)) {
            menu.draw(transfer.inputs());
        } else if (player.containerMenu instanceof ProcessorMenu menu && menu.stillValid(player)) {
            menu.draw(transfer.inputs(), transfer.outputs());
        }
    }

    /** A thing dragged from the recipe viewer onto a slot of pictures counts as a click with it there; only those slots take one. */
    private static void onGhostSlot(GhostSlot ghost, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player) || player.containerMenu.containerId != ghost.menu() || !player.containerMenu.stillValid(player)) {
            return;
        }
        AbstractContainerMenu menu = player.containerMenu;
        int pictures = menu instanceof PortMenu ? PortBlockEntity.FILTERS : menu instanceof AssemblerMenu ? AssemblerBlockEntity.GRID
                : menu instanceof ProcessorMenu ? ProcessorMenu.CELLS : 0;
        if (ghost.slot() >= 0 && ghost.slot() < pictures) {
            ItemStack carried = menu.getCarried();
            menu.setCarried(ghost.stack());
            menu.clicked(ghost.slot(), 0, ContainerInput.PICKUP, player);
            menu.setCarried(carried);
        }
    }

    private static void onAssemblerEdit(AssemblerEdit e, IPayloadContext ctx) {
        if (ctx.player() instanceof ServerPlayer player && player.containerMenu instanceof AssemblerMenu menu
                && menu.pos.equals(e.pos()) && menu.stillValid(player)
                && player.level().getBlockEntity(e.pos()) instanceof AssemblerBlockEntity assembler) {
            assembler.setKeep(e.keep());
        }
        if (ctx.player() instanceof ServerPlayer player && player.containerMenu instanceof ProcessorMenu menu
                && menu.pos.equals(e.pos()) && menu.stillValid(player)
                && player.level().getBlockEntity(e.pos()) instanceof ProcessorBlockEntity processor) {
            processor.setKeep(e.keep());
        }
    }

    private static void onPortProgram(PortProgramAction a, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player) || !(player.containerMenu instanceof PortProgramMenu menu)
                || !menu.pos.equals(a.pos()) || !menu.stillValid(player)
                || !(player.level().getBlockEntity(a.pos()) instanceof PortBlockEntity port)) {
            return;
        }
        int slot = Math.floorMod(a.slot(), PortWork.CARDS);
        switch (a.kind()) {
            case SELECT -> menu.select(slot);
            case WRITE -> port.work.write(slot, a.text(), player);
            case STOP -> port.work.stop(slot, true);
            case RUN -> port.work.stop(slot, false);
            case DEBUG -> {
                Debug.Move[] moves = Debug.Move.values();
                port.work.debug(slot, moves[Math.floorMod(a.value(), moves.length)], a.value() / moves.length, a.text());
                Awards.grant(player, "agent/debugger");
            }
            case ACCESS -> port.work.ownership().setAccess(player, Ownership.Access.values()[Math.floorMod(a.value(), Ownership.Access.values().length)]);
            case FILTER -> player.openMenu(port, buf -> buf.writeBlockPos(a.pos()));
        }
    }

    private static void onPortEdit(PortEdit e, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player) || !(player.containerMenu instanceof PortMenu menu)
                || !menu.pos.equals(e.pos()) || !menu.stillValid(player)
                || !(player.level().getBlockEntity(e.pos()) instanceof PortBlockEntity port)) {
            return;
        }
        switch (e.field()) {
            case PortEdit.MODE -> port.setMode(PortMode.values()[Math.floorMod(e.value(), PortMode.values().length)]);
            case PortEdit.AMOUNT -> port.setAmount(e.value());
            case PortEdit.PRIORITY -> port.setPriority(e.value());
            case PortEdit.CRAFT -> port.setCraft(e.value() != 0);
            case PortEdit.PROGRAMS -> player.openMenu(port.programs(), buf -> buf.writeBlockPos(e.pos()));
            default -> {
            }
        }
    }
}
