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
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
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
 * A body for a model. Walking, carrying and placing are its own; every real decision is put to its model as a
 * prompt in exactly the format of that benchmark's test, and it does whatever the model answers, right or wrong.
 */
public class Agent extends PathfinderMob {
    /** Slots it carries things in; the slot after them holds its model. */
    public static final int CARRY = 12;
    public static final int MODEL = CARRY;
    public static final List<String> NAMES = List.of("Pip", "Clank", "Moss", "Lantern", "Bolt", "Tinker", "Wick", "Pebble", "Copper");
    /** How long what it last decided hangs over its head, in ticks. */
    public static final int SHOWN = 50;

    private static final EntityDataAccessor<Integer> JOB = SynchedEntityData.defineId(Agent.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Component> SAID = SynchedEntityData.defineId(Agent.class, EntityDataSerializers.COMPONENT);
    private static final EntityDataAccessor<Integer> SAID_COUNT = SynchedEntityData.defineId(Agent.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> MODELED = SynchedEntityData.defineId(Agent.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> THINKING = SynchedEntityData.defineId(Agent.class, EntityDataSerializers.BOOLEAN);

    public final SimpleContainer items = new SimpleContainer(CARRY + 1) {
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
    private Work work = new Resting(this);
    @Nullable
    private Errand errand;
    @Nullable
    private PendingOrder pendingOrder;
    private boolean waiting;
    /** Which question it waits on, and since when; the answer to one it gave up on is ignored. */
    private int question;
    private int askedAt;
    private int pause;

    // what its screen shows
    private String status = "residual.agent.status.resting";
    private List<String> statusArgs = List.of();
    private String lastPrompt = "";
    private String lastAnswer = "";
    private float lastConfidence = Float.NaN;
    private int decisions;
    private int right;

    // walking
    @Nullable
    private BlockPos walkTarget;
    private int walkTicks;
    private int stuckTicks;
    private double bestDistance;

    /** Client side: the tick its latest words arrived, to fade them out. */
    public int saidAt = -1000;

    private record PendingOrder(UUID player, String said) {
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
        builder.define(JOB, 0);
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

    public Job job() {
        return Job.values()[Math.floorMod(entityData.get(JOB), Job.values().length)];
    }

    public void setJob(Job job) {
        question++;
        waiting = false;
        entityData.set(THINKING, false);
        pause = 0;
        entityData.set(JOB, job.ordinal());
        errand = null;
        work = switch (job) {
            case REST -> new Resting(this);
            case FARM -> new Farming(this);
            case STORE -> new Storing(this);
            case HAUL -> new Hauling(this);
            case BUILD -> new Building(this);
        };
        getNavigation().stop();
        walkTarget = null;
    }

    public BlockPos home() {
        return home;
    }

    public void setHome(BlockPos pos) {
        home = pos.immutable();
        setJob(job());
    }

    public List<BlockPos> marks() {
        return marks;
    }

    public void setMarks(List<BlockPos> marks) {
        this.marks = List.copyOf(marks);
        setJob(job());
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

    // ---------------------------------------------------------------- thinking

    /**
     * Puts {@code prompt} to its model and hands the answer to {@code then} on the server thread. {@code expected} is
     * what a perfect model would answer, kept only to count how often it is right.
     */
    void ask(String prompt, int max, char stop, @Nullable String expected, Consumer<String> then) {
        ModelLink link = model();
        if (link instanceof ModelLink.Missing m) {
            status(m.problem(), m.args().toArray());
            pause(100);
            return;
        }
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
        if (expected != null && expected.equals(answer.text())) {
            right++;
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

    private void startOrder(PendingOrder o) {
        ServerPlayer player = serverLevel().getServer().getPlayerList().getPlayer(o.player());
        String prompt = Orders.prompt(o.said());
        ask(prompt, Orders.LONGEST, '\n', null, meant -> {
            Orders.Command c = Orders.parse(meant);
            Component reply = c == null ? Component.translatable("residual.agent.puzzled", meant)
                    : Component.literal(meant);
            tell(decided(reply), player);
            if (c == null) {
                status("residual.agent.status.puzzled", o.said());
                return;
            }
            if (player != null) {
                Awards.grant(player, "agent/order");
            }
            switch (c.meaning()) {
                case FARM -> setJob(Job.FARM);
                case STORE -> setJob(Job.STORE);
                case BUILD -> setJob(Job.BUILD);
                case HAUL -> setJob(Job.HAUL);
                case STAY -> setJob(Job.REST);
                case HOME -> errand = Errand.home(this);
                case COME -> errand = player == null ? null : Errand.follow(this, player);
                case FETCH -> errand = player == null ? null : Errand.fetch(this, player, c.thing());
            }
        });
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
        if (pause > 0) {
            pause--;
            return;
        }
        if (errand != null) {
            if (errand.tick(level)) {
                errand = null;
            }
            return;
        }
        work.tick(level);
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
        if (held.is(ModItems.SURVEY_ROD.get()) || held.is(net.minecraft.world.item.Items.NAME_TAG)) {
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

    /** Turns back into an item; what it carried and its model drop where it stood. */
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
        ModelLink link = model();
        ModelInfo info = link.info();
        String problem = link instanceof ModelLink.Missing m ? m.problem() : "";
        List<String> problemArgs = link instanceof ModelLink.Missing m ? m.args() : List.of();
        return new AgentSync(getId(), job().ordinal(), status, statusArgs, lastPrompt, lastAnswer, lastConfidence, decisions,
                right, link instanceof ModelLink.Served, problem, problemArgs, info, marks.size());
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
        out.putInt("job", job().ordinal());
        out.putInt("decisions", decisions);
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
        right = in.getIntOr("right", 0);
        setJob(Job.values()[Math.floorMod(in.getIntOr("job", 0), Job.values().length)]);
    }
}
