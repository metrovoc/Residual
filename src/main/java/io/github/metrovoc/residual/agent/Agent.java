package io.github.metrovoc.residual.agent;

import io.github.metrovoc.residual.Awards;
import io.github.metrovoc.residual.learn.Orders;
import io.github.metrovoc.residual.learn.Sampler;
import io.github.metrovoc.residual.menu.AgentMenu;
import io.github.metrovoc.residual.model.ModelInfo;
import io.github.metrovoc.residual.model.ModelLink;
import io.github.metrovoc.residual.model.Models;
import io.github.metrovoc.residual.net.AgentSync;
import io.github.metrovoc.residual.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * A frame that runs a program. Walking, carrying and the acts of a program are its own; in the routines of the old jobs
 * every real decision is put to its model as a prompt in exactly the format of that benchmark's test, and it does
 * whatever the model answers, right or wrong. Orders said to it are written as programs by its model.
 */
public class Agent extends PathfinderMob {
    /** Slots it carries things in; the two after them hold its models. */
    public static final int CARRY = 12;
    /** Its model, which writes the orders it is given as programs and makes the decisions of the old jobs. */
    public static final int MODEL = CARRY;
    /** Its filter, which tells what kind each thing is, for sorting. */
    public static final int FILTER = CARRY + 1;
    public static final List<String> NAMES = List.of("Pip", "Clank", "Moss", "Lantern", "Bolt", "Tinker", "Wick", "Pebble", "Copper");
    /** How long what it last decided hangs over its head, in ticks. */
    public static final int SHOWN = 50;
    /** Lines of its trace it keeps. */
    public static final int TRACED = 10;
    /** How long an order read back waits for a yes, in ticks. */
    public static final int ASKING = 1200;

    private static final EntityDataAccessor<Component> SAID = SynchedEntityData.defineId(Agent.class, EntityDataSerializers.COMPONENT);
    private static final EntityDataAccessor<Integer> SAID_COUNT = SynchedEntityData.defineId(Agent.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> MODELED = SynchedEntityData.defineId(Agent.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> THINKING = SynchedEntityData.defineId(Agent.class, EntityDataSerializers.BOOLEAN);

    public final SimpleContainer items = new SimpleContainer(CARRY + 2) {
        @Override
        public void setChanged() {
            super.setChanged();
            if (!level().isClientSide()) {
                entityData.set(MODELED, !getItem(MODEL).isEmpty());
            }
        }
    };
    private BlockPos home = BlockPos.ZERO;
    private List<BlockPos> marks = List.of();
    /** The program it runs when nothing else is asked of it, as written. */
    private String program = "";
    /** Whether that program was stopped, or is over. */
    private boolean stopped;
    /** What its status says while its program is stopped or over. */
    private String idle = "residual.agent.status.resting";
    private Running work = new Running(this, Program.of());
    /** An order it is carrying out; it goes back to its program after. */
    @Nullable
    private Running errand;
    /** Who it works for: whoever last set it down, gave it a program or an order. */
    @Nullable
    private UUID boss;
    /** An order of several steps, read back to whoever gave it and waiting for their yes. */
    @Nullable
    private Proposal proposal;
    @Nullable
    private PendingOrder pendingOrder;
    private boolean waiting;
    /** Which question it waits on, and since when; the answer to one it gave up on is ignored. */
    private int question;
    private int askedAt;
    private int pause;

    // what its screen shows
    private final List<Trace> trace = new ArrayList<>();
    private String status = "residual.agent.status.resting";
    private List<String> statusArgs = List.of();
    /** The slot whose model made its last decision: {@link #MODEL} or {@link #FILTER}. */
    private int deciding = MODEL;
    private String lastPrompt = "";
    private String lastAnswer = "";
    private float lastConfidence = Float.NaN;
    private int decisions;
    /** Decisions whose right answer was known, and how many of them it got right. */
    private int checked;
    private int right;

    // walking
    @Nullable
    private BlockPos walkTarget;
    private int walkTicks;
    private int stuckTicks;
    private double bestDistance;
    /** Walks in a row that got nowhere. */
    private int lost;

    /** Client side: the tick its latest words arrived, to fade them out. */
    public int saidAt = -1000;

    private record PendingOrder(UUID player, String said) {
    }

    private record Proposal(Program program, UUID by, int at) {
    }

    public Agent(EntityType<? extends Agent> type, Level level) {
        super(type, level);
        if (getNavigation() instanceof GroundPathNavigation ground) {
            ground.setCanOpenDoors(true);
        }
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return createMobAttributes().add(Attributes.MAX_HEALTH, 20).add(Attributes.MOVEMENT_SPEED, 0.3).add(Attributes.FOLLOW_RANGE, 48);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SAID, Component.empty());
        builder.define(SAID_COUNT, 0);
        builder.define(MODELED, false);
        builder.define(THINKING, false);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (SAID_COUNT.equals(key) && level().isClientSide()) {
            saidAt = tickCount;
        }
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new OpenDoorGoal(this, true));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 6f));
        goalSelector.addGoal(9, new RandomLookAroundGoal(this));
    }

    // ---------------------------------------------------------------- what it is doing

    /** The job its program is the preset of, or null for a program of its own. */
    @Nullable
    public Job job() {
        return Job.of(program);
    }

    public String program() {
        return program;
    }

    /** Gives it a program to run from now on; null if the text is one, else what is wrong with it. */
    public Program.@Nullable Problem setProgram(String text, @Nullable Player by) {
        Program.Parsed p = Program.parse(text);
        if (!p.ok()) {
            return p.problem();
        }
        if (by != null) {
            boss = by.getUUID();
        }
        program = p.program().toString();
        stopped = false;
        errand = null;
        proposal = null;
        restart();
        trace(Trace.Kind.NOTE, program, program.isEmpty() ? "residual.trace.rest" : "residual.trace.program", List.of());
        if (by instanceof ServerPlayer player && !program.isEmpty() && job() == null) {
            Awards.grant(player, "agent/program");
        }
        return null;
    }

    /** Stops its program until it is run again. */
    public void stop() {
        stopped = true;
        errand = null;
        proposal = null;
        idle = "residual.agent.status.stopped";
        restart();
        trace(Trace.Kind.NOTE, "", "residual.trace.stopped", List.of());
    }

    /** Runs its program again from the top. */
    public void run() {
        stopped = false;
        errand = null;
        restart();
        trace(Trace.Kind.NOTE, program, program.isEmpty() ? "residual.trace.rest" : "residual.trace.program", List.of());
    }

    public boolean stopped() {
        return stopped;
    }

    /** Starts its program over, forgetting any question in flight: after a change of program, home or marks. */
    private void restart() {
        cutShort();
        question++;
        waiting = false;
        entityData.set(THINKING, false);
        pause = 0;
        Program.Parsed p = Program.parse(program);
        work = new Running(this, p.ok() ? p.program() : Program.of());
        getNavigation().stop();
        walkTarget = null;
    }

    public BlockPos home() {
        return home;
    }

    public void setHome(BlockPos pos) {
        home = pos.immutable();
        restart();
    }

    public List<BlockPos> marks() {
        return marks;
    }

    public void setMarks(List<BlockPos> marks) {
        this.marks = List.copyOf(marks);
        restart();
    }

    public void setBoss(Player player) {
        boss = player.getUUID();
    }

    /** Whoever it works for, if they are here to come to. */
    @Nullable
    ServerPlayer boss() {
        ServerPlayer p = boss == null ? null : serverLevel().getServer().getPlayerList().getPlayer(boss);
        return p != null && p.level() == level() && p.isAlive() ? p : null;
    }

    /** Notes a line in its trace; the same line coming round again is counted rather than repeated. */
    void trace(Trace.Kind kind, String step, String key, List<String> args) {
        if (kind != Trace.Kind.RUNNING && !trace.isEmpty() && trace.getLast().kind() == Trace.Kind.RUNNING
                && trace.getLast().step().equals(step)) {
            // the step it started is over
            trace.removeLast();
        }
        Trace line = new Trace(kind, step, key, args, 1);
        for (int i = trace.size() - 1; i >= Math.max(0, trace.size() - 4); i--) {
            if (trace.get(i).same(line)) {
                line = new Trace(kind, step, key, args, trace.remove(i).times() + 1);
                break;
            }
        }
        trace.add(line);
        while (trace.size() > TRACED) {
            trace.removeFirst();
        }
    }

    /** Marks the step under way, if any, as cut short by something else coming first. */
    private void cutShort() {
        if (!trace.isEmpty() && trace.getLast().kind() == Trace.Kind.RUNNING) {
            Trace t = trace.removeLast();
            trace.add(new Trace(Trace.Kind.FAILED, t.step(), "residual.program.failed.cut_short", List.of(), 1));
        }
    }

    /** Thinking or pausing: not free to take another step. */
    boolean busy() {
        return waiting || pause > 0;
    }

    public boolean modeled() {
        return entityData.get(MODELED);
    }

    public boolean thinking() {
        return entityData.get(THINKING);
    }

    public Component said() {
        return entityData.get(SAID);
    }

    /** Shows {@code what} over its head for a moment. */
    void show(Component what) {
        entityData.set(SAID, what);
        entityData.set(SAID_COUNT, entityData.get(SAID_COUNT) + 1);
    }

    /** Says {@code what} over its head and in the chat of players nearby, and of {@code also} wherever they are. */
    void tell(Component what, @Nullable ServerPlayer also) {
        show(what);
        Component line = Component.literal("<").append(getDisplayName()).append("> ").append(what);
        for (ServerPlayer p : serverLevel().players()) {
            if (p == also || p.distanceToSqr(this) < 48 * 48) {
                p.sendSystemMessage(line);
            }
        }
        if (also != null && also.level() != level()) {
            also.sendSystemMessage(line);
        }
    }

    void tell(Component what) {
        tell(what, null);
    }

    void status(String key, Object... args) {
        status = key;
        List<String> out = new ArrayList<>();
        for (Object a : args) {
            out.add(String.valueOf(a));
        }
        statusArgs = out;
    }

    void pause(int ticks) {
        pause = Math.max(pause, ticks);
    }

    ServerLevel serverLevel() {
        return (ServerLevel) level();
    }

    public ModelLink model() {
        return ModelLink.of(serverLevel(), blockPosition(), items.getItem(MODEL));
    }

    ModelLink filter() {
        return ModelLink.of(serverLevel(), blockPosition(), items.getItem(FILTER));
    }

    // ---------------------------------------------------------------- thinking

    /**
     * Puts {@code prompt} to its model and hands the answer to {@code then} on the server thread. {@code expected} is
     * what a perfect model would answer, kept only to count how often it is right.
     */
    void ask(String prompt, int max, char stop, @Nullable String expected, Consumer<String> then) {
        ask(MODEL, prompt, max, stop, expected, then);
    }

    /**
     * Puts {@code prompt} to the model in {@code slot}, its model's or its filter's, as
     * {@link #ask(String, int, char, String, Consumer)}.
     */
    void ask(int slot, String prompt, int max, char stop, @Nullable String expected, Consumer<String> then) {
        ModelLink link = ModelLink.of(serverLevel(), blockPosition(), items.getItem(slot));
        if (link instanceof ModelLink.Missing m) {
            status(m.problem(), m.args().toArray());
            pause(100);
            return;
        }
        deciding = slot;
        waiting = true;
        askedAt = tickCount;
        int id = ++question;
        entityData.set(THINKING, true);
        link.ask(serverLevel(), prompt, max, stop, answer -> {
            if (id == question) {
                answered(prompt, expected, answer, then);
            }
        });
    }

    private void answered(String prompt, @Nullable String expected, Sampler.@Nullable Answer answer, Consumer<String> then) {
        waiting = false;
        entityData.set(THINKING, false);
        if (isRemoved()) {
            return;
        }
        if (answer == null) {
            status("residual.model.failed");
            pause(100);
            return;
        }
        lastPrompt = prompt;
        lastAnswer = answer.text();
        lastConfidence = answer.confidence();
        decisions++;
        if (expected != null) {
            checked++;
            if (expected.equals(answer.text())) {
                right++;
            }
        }
        then.accept(answer.text());
    }

    float confidence() {
        return lastConfidence;
    }

    /** "harvest · 97%": what it decided and how sure its model was, for over its head. */
    Component decided(Component what) {
        return what.copy().append(Component.literal(String.format(Locale.ROOT, " · %.0f%%", 100 * lastConfidence))
                .withStyle(ChatFormatting.GRAY));
    }

    // ---------------------------------------------------------------- orders

    /** Takes an order said by {@code player}; its model decides what the words mean. */
    public void order(ServerPlayer player, String said) {
        pendingOrder = new PendingOrder(player.getUUID(), said);
    }

    /**
     * Takes an order said by {@code player}: its model writes it as a program. One step, or one of the jobs, it does at
     * once; any other program it reads back and does only once the player says yes.
     */
    private void startOrder(PendingOrder o) {
        ServerPlayer player = serverLevel().getServer().getPlayerList().getPlayer(o.player());
        String prompt = Orders.prompt(o.said());
        ask(prompt, Orders.WRITTEN, '\n', null, meant -> {
            Program.Parsed p = Program.parse(meant);
            if (!p.ok() || p.program().steps().isEmpty()) {
                tell(decided(Component.translatable("residual.agent.puzzled", meant)), player);
                status("residual.agent.status.puzzled", o.said());
                return;
            }
            Program program = p.program();
            boss = o.player();
            if (player != null) {
                Awards.grant(player, "agent/order");
            }
            if (program.size() > 1 && Job.of(program.toString()) == null) {
                proposal = new Proposal(program, o.player(), tickCount);
                tell(decided(Component.literal(program.toString())).copy().append(" ").append(choice(true)).append(" ")
                        .append(choice(false)), player);
                status("residual.agent.status.asking", program.toString());
                return;
            }
            tell(decided(Component.literal(program.toString())), player);
            obey(program);
        });
    }

    /** [Do it] or [Cancel], to click in chat. */
    private Component choice(boolean yes) {
        String word = yes ? "yes" : "no";
        return Component.translatable("residual.agent.choice." + word).withStyle(s -> s
                .withColor(yes ? ChatFormatting.GREEN : ChatFormatting.RED)
                .withClickEvent(new ClickEvent.RunCommand("/residual " + word + " " + getId()))
                .withHoverEvent(new HoverEvent.ShowText(Component.translatable("residual.agent.choice." + word + ".help"))));
    }

    /** The player who gave the order it read back says yes or no to it. */
    public void answer(ServerPlayer player, boolean yes) {
        if (proposal == null || !proposal.by().equals(player.getUUID())) {
            return;
        }
        Program program = proposal.program();
        proposal = null;
        if (yes) {
            obey(program);
        } else {
            tell(Component.translatable("residual.agent.said.never_mind"), player);
            status("residual.agent.status.never_mind");
        }
    }

    /** Does what it was told: a program that goes on for good becomes its program, any other it does in between. */
    private void obey(Program program) {
        if (program.endless()) {
            setProgram(program.toString(), null);
            return;
        }
        cutShort();
        trace(Trace.Kind.NOTE, program.toString(), "residual.trace.order", List.of());
        errand = new Running(this, program);
        getNavigation().stop();
    }

    // ---------------------------------------------------------------- ticking

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (waiting) {
            if (tickCount - askedAt < Models.PATIENCE) {
                return;
            }
            // no answer came, perhaps because its server's chunk unloaded: give up on the question
            question++;
            waiting = false;
            entityData.set(THINKING, false);
            status("residual.model.failed");
            pause(100);
            return;
        }
        if (pendingOrder != null) {
            PendingOrder o = pendingOrder;
            pendingOrder = null;
            startOrder(o);
            return;
        }
        if (proposal != null && tickCount - proposal.at() > ASKING) {
            proposal = null;
        }
        if (pause > 0) {
            pause--;
            return;
        }
        if (errand != null) {
            errand.tick(level);
            if (errand.over()) {
                errand = null;
                ServerPlayer p = boss();
                if (p != null) {
                    getLookControl().setLookAt(p);
                }
                // a moment for another order before it goes back to its program
                pause(60);
            }
            return;
        }
        if (stopped) {
            status(idle);
            pause(20);
            return;
        }
        work.tick(level);
        if (work.over()) {
            stopped = true;
            idle = program.isEmpty() ? "residual.agent.status.resting" : "residual.agent.status.finished";
            if (!program.isEmpty()) {
                trace(Trace.Kind.NOTE, "", "residual.trace.finished", List.of());
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide() && thinking() && random.nextInt(4) == 0) {
            level().addParticle(ParticleTypes.ENCHANT, getX(), getY() + 1.6, getZ(), random.nextGaussian() * 0.3, 0.4, random.nextGaussian() * 0.3);
        }
        if (!level().isClientSide() && tickCount % 10 == 0) {
            for (ServerPlayer p : serverLevel().players()) {
                if (p.containerMenu instanceof AgentMenu m && m.agent == this) {
                    PacketDistributor.sendToPlayer(p, snapshot());
                }
            }
        }
    }

    // ---------------------------------------------------------------- moving and carrying

    enum Arrival { HERE, ON_THE_WAY, LOST }

    /** Walks towards {@code target} until within {@code reach} blocks of its centre; LOST if it stops getting closer. */
    Arrival walk(BlockPos target, double reach) {
        Vec3 at = Vec3.atBottomCenterOf(target);
        double d = distanceToSqr(at.x, at.y, at.z);
        if (d <= reach * reach) {
            walkTarget = null;
            lost = 0;
            getNavigation().stop();
            return Arrival.HERE;
        }
        if (!target.equals(walkTarget)) {
            walkTarget = target;
            walkTicks = 0;
            stuckTicks = 0;
            bestDistance = d;
        }
        if (walkTicks++ % 20 == 0 || getNavigation().isDone()) {
            getNavigation().moveTo(at.x, at.y, at.z, 1.0);
        }
        if (d < bestDistance - 0.25) {
            bestDistance = d;
            stuckTicks = 0;
        } else if (++stuckTicks > 160) {
            walkTarget = null;
            getNavigation().stop();
            if (++lost >= 3 && level().isLoaded(home)) {
                // stuck for good, in a hole or behind a door it can't open: home the quick way
                lost = 0;
                level().playSound(null, blockPosition(), SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.NEUTRAL, 0.6f, 1.2f);
                teleportTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5);
                trace(Trace.Kind.NOTE, "", "residual.trace.stuck", List.of());
            }
            return Arrival.LOST;
        }
        return Arrival.ON_THE_WAY;
    }

    /** Puts {@code stack} into its own carrying slots; returns what didn't fit. */
    ItemStack keep(ItemStack stack) {
        for (int i = 0; i < CARRY && !stack.isEmpty(); i++) {
            ItemStack in = items.getItem(i);
            if (!in.isEmpty() && ItemStack.isSameItemSameComponents(in, stack) && in.getCount() < in.getMaxStackSize()) {
                int moved = Math.min(stack.getCount(), in.getMaxStackSize() - in.getCount());
                in.grow(moved);
                stack.shrink(moved);
            }
        }
        for (int i = 0; i < CARRY && !stack.isEmpty(); i++) {
            if (items.getItem(i).isEmpty()) {
                items.setItem(i, stack.copy());
                stack.setCount(0);
            }
        }
        items.setChanged();
        return stack;
    }

    boolean carrying() {
        for (int i = 0; i < CARRY; i++) {
            if (!items.getItem(i).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    boolean full() {
        for (int i = 0; i < CARRY; i++) {
            if (items.getItem(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** Swings an arm and plays a sound, to show it did something with its hands. */
    void handle(BlockPos pos) {
        swing(InteractionHand.MAIN_HAND);
        level().playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.3f, 0.9f + random.nextFloat() * 0.2f);
    }

    // ---------------------------------------------------------------- the player

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (held.is(ModItems.SURVEY_ROD.get()) || held.is(ModItems.PROGRAM_CARD.get()) || held.is(net.minecraft.world.item.Items.NAME_TAG)) {
            return InteractionResult.PASS;
        }
        if (player.isSecondaryUseActive() && held.isEmpty()) {
            if (!level().isClientSide()) {
                pickUp(player);
            }
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer sp) {
            sp.openMenu(new SimpleMenuProvider((id, inv, p) -> new AgentMenu(id, inv, this), getDisplayName()),
                    buf -> buf.writeVarInt(getId()));
            PacketDistributor.sendToPlayer(sp, snapshot());
        }
        return InteractionResult.SUCCESS;
    }

    /** Turns back into an item; what it carried and its models drop where it stood. */
    private void pickUp(Player player) {
        ItemStack self = new ItemStack(ModItems.AGENT.get());
        if (hasCustomName()) {
            self.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, getCustomName());
        }
        Containers.dropContents(level(), blockPosition(), items);
        if (!player.getInventory().add(self)) {
            spawnAtLocation(serverLevel(), self);
        }
        level().playSound(null, blockPosition(), SoundEvents.COPPER_PLACE, SoundSource.NEUTRAL, 1f, 1.2f);
        discard();
    }

    public AgentSync snapshot() {
        ModelLink filter = deciding == FILTER ? filter() : null;
        boolean byFilter = filter != null && !(filter instanceof ModelLink.Missing);
        ModelLink link = byFilter ? filter : model();
        ModelInfo info = link.info();
        String problem = link instanceof ModelLink.Missing m ? m.problem() : "";
        List<String> problemArgs = link instanceof ModelLink.Missing m ? m.args() : List.of();
        Job job = job();
        return new AgentSync(getId(), job == null ? -1 : job.ordinal(), program, stopped, List.copyOf(trace), status, statusArgs,
                lastPrompt, lastAnswer, lastConfidence, decisions, checked, right, link instanceof ModelLink.Served, byFilter, problem, problemArgs,
                info, marks.size(), proposal != null);
    }

    @Override
    public boolean removeWhenFarAway(double distSqr) {
        return false;
    }

    @Override
    public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
        return !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY) || super.isInvulnerableTo(level, source);
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
        super.dropCustomDeathLoot(level, source, killedByPlayer);
        Containers.dropContents(level, blockPosition(), items);
    }

    // ---------------------------------------------------------------- saving

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        ContainerHelper.saveAllItems(out.child("carried"), items.getItems());
        out.store("home", BlockPos.CODEC, home);
        out.store("marks", BlockPos.CODEC.listOf(), marks);
        out.putString("program", program);
        out.putBoolean("stopped", stopped);
        if (boss != null) {
            out.store("boss", UUIDUtil.CODEC, boss);
        }
        out.putInt("decisions", decisions);
        out.putInt("checked", checked);
        out.putInt("right", right);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        in.child("carried").ifPresent(c -> ContainerHelper.loadAllItems(c, items.getItems()));
        entityData.set(MODELED, !items.getItem(MODEL).isEmpty());
        home = in.read("home", BlockPos.CODEC).orElse(blockPosition());
        marks = in.read("marks", BlockPos.CODEC.listOf()).orElse(List.of());
        decisions = in.getIntOr("decisions", 0);
        // before orders and sorting, every decision was checked
        checked = in.getIntOr("checked", decisions);
        right = in.getIntOr("right", 0);
        // before programs, agents kept a job
        program = in.getString("program").orElseGet(() -> Job.values()[Math.floorMod(in.getIntOr("job", 0), Job.values().length)].program);
        stopped = in.getBooleanOr("stopped", false);
        idle = program.isEmpty() ? "residual.agent.status.resting" : "residual.agent.status.stopped";
        boss = in.read("boss", UUIDUtil.CODEC).orElse(null);
        restart();
    }
}
