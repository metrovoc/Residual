package io.github.metrovoc.residual.block;

import com.mojang.authlib.GameProfile;
import io.github.metrovoc.residual.Awards;
import io.github.metrovoc.residual.energy.PowerBlockEntity;
import io.github.metrovoc.residual.energy.PowerNetwork;
import io.github.metrovoc.residual.item.FieldLogItem;
import io.github.metrovoc.residual.item.MobLogItem;
import io.github.metrovoc.residual.learn.Benchmark;
import io.github.metrovoc.residual.learn.Bout;
import io.github.metrovoc.residual.learn.Ground;
import io.github.metrovoc.residual.learn.Imitation;
import io.github.metrovoc.residual.menu.ArenaMenu;
import io.github.metrovoc.residual.model.ModelInfo;
import io.github.metrovoc.residual.model.ModelLink;
import io.github.metrovoc.residual.model.Models;
import io.github.metrovoc.residual.net.ArenaSync;
import io.github.metrovoc.residual.registry.ModBlockEntities;
import io.github.metrovoc.residual.world.Cooling;
import io.github.metrovoc.residual.world.FrontierTier;
import io.github.metrovoc.residual.world.GraphScanner;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityProcessor;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * The Arena: a chamber where a model plays a mob. With a Mob Log in it, the model is first checked on the tenth of that log
 * practice never reads, and how often it picks what the mob really did sets its tier ({@link Imitation.Check}). Then
 * it fights: on each pad of the room's floor it plays the mob against a sparring frame, asked what the mob does at every look,
 * and when the mob falls, that kind's real loot table is rolled, as often as the model was right, and as the tier says. Cooled
 * Accelerators touching it run more fights at once, up to one a pad, those past the free ones on power it pays for while it
 * works, and a slow model simply runs fewer looks.
 *
 * <p>At the Frontier tier ({@link FrontierTier}) a Surveyor's Field Log does the same for ground: the model writes rows of it
 * on each pad, and what ore it writes, as far as the ground it knows really holds that much, comes out as mining it would.
 */
public class ArenaBlockEntity extends PowerBlockEntity implements MenuProvider {
    public static final int LOG = 0, MODEL = 1, OUT = 2, OUTS = 15, SLOTS = OUT + OUTS;
    /** Power it keeps, and takes in a tick: seven Withers' fights from end to end, and six of them going at once. */
    public static final int POWER = 4_000_000, POWER_IN = 16_000;
    /** What a fight costs a tick for each point of its mob's health. A zombie's fight comes to 38,400 FE. */
    public static final int FE_PER_HEALTH = 8;
    /** What a pad of simulated ground costs a tick: 640 FE a row. */
    public static final int FE_A_ROW = 640 / Imitation.LOOK;
    /** The compute a simulation of ground asks for: its own, and 16 cooled Accelerators'. */
    public static final int GROUND_COMPUTE = 17;
    /** Ticks between looks at the room, the log and the model. */
    private static final int SURVEY = 20;
    /** How long a check may take: it reads far more than a look does, and a large model on a busy machine can take minutes over it. */
    private static final int CHECK_PATIENCE = 20 * Models.PATIENCE;
    private static final int HEARD = 32;
    /** Ticks a mob lies on its pad after it falls, and rows of ground stand after the last. */
    private static final int FALLEN = 20, ROWS_KEPT = 40;
    /** Who a fight's blow comes from, when the tier has the mob die to one. */
    private static final GameProfile SPARRING = new GameProfile(UUID.fromString("6f0d8a50-3a7b-4e0e-9a54-41a1a93e6c11"), "[Arena]");

    /** What it can't do yet, or that it can. */
    public enum State { NO_LOG, FEW_LOOKS, NO_MODEL, PEEKS, CHECKING, NO_LIKENESS, NO_ROOM, CLOSED, NO_GROUND_TEST, LOW_COMPUTE, FULL, NO_POWER, READY }

    /** What its screen shows, as container data: everything stays well under the 32,767 a menu's number carries. */
    public static final int STATE = 0, PROBLEM = 1, SCORE = 2, LOOKS = 3, TIER = 4, RUNNING = 5, LIMIT = 6, PADS = 7, COMPUTE = 8, POWER_SHOWN = 9, RATE = 10,
            PLAIN = 11, READ_BACK = 12, FAULT = 13, GROUND = FAULT + 3, TRUST = GROUND + 1, DATA = TRUST + Ground.ORES.length();
    public static final List<String> PROBLEMS = List.of("residual.model.none", "residual.model.blank", "residual.model.unbound",
            "residual.model.out_of_range", "residual.model.no_server", "residual.model.server_empty", "residual.model.too_big",
            "residual.model.failed");
    /** What of a specimen never drops: what it carries, however it came by it. */
    private static final List<String> CARRIED = List.of("equipment", "drop_chances", "Inventory", "Items", "body_armor_item", "saddle");

    /** One fight on one pad. */
    private record Fight(Bout bout, int pad) {
    }

    private final Stock items = new Stock();
    private final List<Fight> fights = new ArrayList<>();
    /** When each pad's last fight will have cleared away. */
    private final Map<Integer, Integer> clearAt = new HashMap<>();
    private final Random rng = new Random();
    private State state = State.NO_LOG;
    private int problem;
    @Nullable
    private ArenaRoom room;
    @Nullable
    private BlockPos fault;
    private int compute = 1;
    /** What it last checked: the model and log, the share right and looks scored, or for ground how far it trusts each ore. */
    @Nullable
    private ModelInfo checkedModel;
    @Nullable
    private String checkedLog;
    /** The log in its slot as it last looked at it. */
    @Nullable
    private String heldLog;
    @Nullable
    private Imitation.Check check;
    private float[] trust = new float[Ground.ORES.length()];
    private boolean checking;
    private boolean asking;
    private int askedAt;
    /** Which request it waits to hear about; an answer to an older one is ignored. */
    private int request;
    private int ticks;
    private int sinceLook;
    private int sinceStart;
    /** What its likeness in the fights costs a tick, for one fight. */
    private int rate;
    @Nullable
    private Mob specimen;
    @Nullable
    private UUID owner;
    /** On the client: how each pad's fight stands, what the room is, and what plays the mob. */
    private final Map<Integer, View> views = new HashMap<>();
    private final Map<Integer, Deque<String>> rows = new HashMap<>();
    private long rowsAt;
    @Nullable
    private CompoundTag shown;
    @Nullable
    private Mob likeness;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            if (i >= TRUST) {
                return Math.round(trust[i - TRUST] * 1000);
            }
            return switch (i) {
                case STATE -> state.ordinal();
                case PROBLEM -> problem;
                case SCORE -> check == null ? -1 : (int) Math.round(check.score() * 1000);
                case PLAIN -> check == null ? 0 : (int) Math.round(check.plain() * 1000);
                case READ_BACK -> check == null ? 0 : (int) Math.round(check.readBack() * 1000);
                case LOOKS -> check == null ? 0 : (int) Math.min(Short.MAX_VALUE, check.looks());
                case TIER -> tier();
                case RUNNING -> ground() ? working() : fights.size();
                case LIMIT -> limit();
                case PADS -> room == null ? 0 : room.pads();
                case COMPUTE -> compute;
                case POWER_SHOWN -> (int) ((long) energy().getAmountAsInt() * 1000 / POWER);
                case RATE -> Math.min(Short.MAX_VALUE, ground() ? FE_A_ROW : rate);
                case FAULT -> fault == null ? 0 : (fault.getX() - worldPosition.getX() + 1024);
                case FAULT + 1 -> fault == null ? 0 : (fault.getY() - worldPosition.getY() + 1024);
                case FAULT + 2 -> fault == null ? 0 : (fault.getZ() - worldPosition.getZ() + 1024);
                case GROUND -> ground() ? 1 : 0;
                default -> 0;
            };
        }

        @Override
        public void set(int i, int value) {
        }

        @Override
        public int getCount() {
            return DATA;
        }
    };

    public ArenaBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ARENA.get(), pos, state, POWER, POWER_IN, 0);
    }

    @Override
    public Role role() {
        return Role.MACHINE;
    }

    public WorldlyContainer items() {
        return items;
    }

    void owner(UUID placedBy) {
        owner = placedBy;
        setChanged();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ArenaBlockEntity be) {
        be.tick((ServerLevel) level);
    }

    /** The Mob Log it holds, or null. */
    @Nullable
    private MobLogItem.Log mobLog() {
        return MobLogItem.log(items.getItem(LOG));
    }

    /** Whether it holds rows of ground rather than a mob's lines. */
    private boolean ground() {
        ItemStack log = items.getItem(LOG);
        return log.getItem() instanceof FieldLogItem && Ground.isLog(FieldLogItem.log(log));
    }

    public int tier() {
        return ground() || check == null ? 0 : check.tier();
    }

    /** How many fights it runs at once: a pad each, as far as its compute goes. */
    private int limit() {
        return room == null ? 0 : Math.min(room.pads(), compute);
    }

    private int working() {
        return state == State.READY ? limit() : 0;
    }

    @Override
    protected void tick(ServerLevel level) {
        super.tick(level);
        ticks++;
        if (asking && ticks - askedAt > Models.PATIENCE || checking && ticks - askedAt > CHECK_PATIENCE) {
            asking = false;
            checking = false;
            request++;
        }
        if (ticks % SURVEY == 0) {
            survey(level);
        }
        if (state == State.READY) {
            Cooling.payRated(level, worldPosition);
        }
        if (state == State.READY || state == State.NO_POWER || state == State.FULL) {
            run(level);
        }
    }

    /** Sees how things stand: the room, the compute, the log and the model. */
    private void survey(ServerLevel level) {
        ArenaRoom.Found found = ArenaRoom.find(level, worldPosition, getBlockState().getValue(ArenaBlock.FACING));
        fault = found.fault();
        if (!Objects.equals(found.room(), room)) {
            room = found.room();
            fights.clear();
            synced(level);
        }
        compute = Cooling.compute(level, worldPosition, 1, GraphScanner.MAX_ACCELERATORS).threads();
        Cooling.working(level, worldPosition, state == State.READY);
        ModelLink link = ModelLink.of(level, worldPosition, items.getItem(MODEL));
        state = ground() ? decideGround(level, link) : decideMob(level, link);
        if (state != State.READY && state != State.NO_POWER && state != State.FULL) {
            fights.clear();
        }
        working(state == State.READY);
    }

    private State decideMob(ServerLevel level, ModelLink link) {
        MobLogItem.Log log = mobLog();
        if (log == null) {
            return State.NO_LOG;
        }
        List<String> lines = Imitation.lines(log.lines());
        if (Imitation.scorable(Imitation.heldOut(lines)) < Imitation.LOOKS_FOR[0]) {
            return State.FEW_LOOKS;
        }
        State model = model(link);
        if (model != null) {
            return model;
        }
        if (stale(link.info(), log.lines())) {
            check(level, link, log.lines());
            return State.CHECKING;
        }
        if (checking || check == null) {
            return State.CHECKING;
        }
        if (tier() == 0) {
            return State.NO_LIKENESS;
        }
        return ready();
    }

    private State decideGround(ServerLevel level, ModelLink link) {
        if (!FrontierTier.reached(level, owner)) {
            return State.CLOSED;
        }
        String log = FieldLogItem.log(items.getItem(LOG));
        if (Ground.rows(log).size() < Ground.FEWEST) {
            return State.FEW_LOOKS;
        }
        State model = model(link);
        if (model != null) {
            return model;
        }
        if (!link.info().passes(Benchmark.GROUND)) {
            return State.NO_GROUND_TEST;
        }
        if (stale(link.info(), log)) {
            check(level, link, log);
            return State.CHECKING;
        }
        if (checking) {
            return State.CHECKING;
        }
        if (compute < GROUND_COMPUTE) {
            return State.LOW_COMPUTE;
        }
        return ready();
    }

    /** What is wrong with the model, or null if nothing is. */
    @Nullable
    private State model(ModelLink link) {
        if (link instanceof ModelLink.Missing m) {
            problem = Math.max(0, PROBLEMS.indexOf(m.problem()));
            return State.NO_MODEL;
        }
        return link.info().causal() ? null : State.PEEKS;
    }

    private State ready() {
        if (room == null) {
            return State.NO_ROOM;
        }
        if (free() == 0 && fights.isEmpty()) {
            return State.FULL;
        }
        return energy().getAmountAsInt() < cost() ? State.NO_POWER : State.READY;
    }

    private boolean stale(ModelInfo info, String log) {
        return !checking && (!info.equals(checkedModel) || !log.equals(checkedLog));
    }

    /** Has the model checked against what its log holds, off the server thread. */
    private void check(ServerLevel level, ModelLink link, String log) {
        checking = true;
        askedAt = ticks;
        ModelInfo info = link.info();
        int id = ++request;
        asking = false;
        fights.clear();
        if (ground()) {
            List<String> real = Ground.rows(log);
            long seed = rng.nextLong();
            link.submit(level, model -> Ground.trust(model, real, new Random(seed)), result -> {
                if (id == request) {
                    checking = false;
                    checkedModel = info;
                    checkedLog = log;
                    trust = result == null ? new float[trust.length] : result;
                    check = null;
                }
            });
            return;
        }
        List<String> lines = Imitation.lines(log);
        link.submit(level, model -> Imitation.check(model, lines), result -> {
            if (id == request) {
                checking = false;
                checkedModel = info;
                checkedLog = log;
                check = result == null ? new Imitation.Check(0, 0, 0, 0) : result;
                specimen = null;
                Mob mob = specimen(level);
                rate = mob == null ? 0 : Math.round(FE_PER_HEALTH * mob.getMaxHealth());
                synced(level);
                if (tier() == Imitation.TIERS.length) {
                    tell(level, "arena/uncanny");
                }
            }
        });
    }

    /** What a look costs now: the fights it runs, or a row on every pad at work. A slow model fights more slowly, not more dearly. */
    private int cost() {
        return (ground() ? limit() * FE_A_ROW : Math.max(1, fights.size()) * rate) * Imitation.LOOK;
    }

    /** At each look it pays for what runs and has the model say what happens next. */
    private void run(ServerLevel level) {
        if (++sinceLook < Imitation.LOOK || asking) {
            return;
        }
        int stored = energy().getAmountAsInt(), cost = cost();
        ModelLink link = ModelLink.of(level, worldPosition, items.getItem(MODEL));
        if (stored < cost || link instanceof ModelLink.Missing || room == null) {
            return;
        }
        sinceLook = 0;
        energy().set(stored - cost);
        if (ground()) {
            lookAtGround(level, link);
        } else {
            lookAtFights(level, link);
        }
    }

    private void lookAtFights(ServerLevel level, ModelLink link) {
        MobLogItem.Log log = mobLog();
        if (log == null) {
            return;
        }
        // spread over a fight's length, so they come round each in its own time
        sinceStart++;
        if (fights.size() < limit() && free() > fights.size() && sinceStart >= Bout.LOOKS / limit()) {
            sinceStart = 0;
            for (int pad = 0; pad < room.pads(); pad++) {
                if (!padTaken(pad)) {
                    fights.add(new Fight(new Bout(Imitation.letter(log.kind().toString()), Imitation.targeting(log.lines()), rng), pad));
                    break;
                }
            }
        }
        if (fights.isEmpty()) {
            return;
        }
        List<String> prompts = fights.stream().map(f -> f.bout().prompt()).toList();
        asking = true;
        askedAt = ticks;
        int id = ++request;
        link.submit(level, model -> Imitation.actions(model, prompts), probs -> {
            if (id == request) {
                asking = false;
                if (probs != null) {
                    fought(level, probs);
                }
            }
        });
    }

    /** A pad is taken while its fight runs, and while what fell on it lies there. */
    private boolean padTaken(int pad) {
        for (Fight f : fights) {
            if (f.pad() == pad) {
                return true;
            }
        }
        return ticks < clearAt.getOrDefault(pad, 0);
    }

    /** The model said what each mob does: it happens, and those whose time is up fall, and pay if the fight counts. */
    private void fought(ServerLevel level, float[][] probs) {
        long[] sent = new long[fights.size()];
        List<Fight> over = new ArrayList<>();
        for (int i = 0; i < fights.size(); i++) {
            Fight f = fights.get(i);
            int action = draw(probs[i]);
            f.bout().act(Imitation.ACTIONS.charAt(action), rng);
            boolean ended = f.bout().over();
            boolean counts = ended && check != null && rng.nextFloat() < check.score();
            sent[i] = ArenaSync.pack(f.pad(), action, f.bout().hurt(), f.bout().struck(), ended, counts, f.bout().distance(), f.bout().turn());
            if (ended) {
                over.add(f);
                clearAt.put(f.pad(), ticks + FALLEN);
                if (counts) {
                    pay(level, f);
                } else {
                    fizzle(level, f);
                }
            }
        }
        fights.removeAll(over);
        PacketDistributor.sendToPlayersTrackingChunk(level, net.minecraft.world.level.ChunkPos.containing(worldPosition),
                new ArenaSync(worldPosition, sent, List.of()));
    }

    private int draw(float[] probs) {
        float r = rng.nextFloat();
        for (int a = 0; a < probs.length; a++) {
            r -= probs[a];
            if (r <= 0) {
                return a;
            }
        }
        return 0;
    }

    /** The mob's real loot table, and whatever else its kind drops when it dies, as the tier has it die. */
    private void pay(ServerLevel level, Fight f) {
        Vec3 at = room == null ? worldPosition.getCenter() : room.pad(f.pad());
        Mob mob = specimen(level);
        if (mob != null) {
            for (ItemStack stack : drops(level, mob, tier())) {
                give(level, stack);
            }
        }
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 0.5, at.z, 12, 0.5, 0.3, 0.5, 0.02);
        level.playSound(null, BlockPos.containing(at), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.4f, 1.2f + rng.nextFloat() * 0.4f);
        tell(level, "arena/drops");
    }

    private void fizzle(ServerLevel level, Fight f) {
        Vec3 at = room == null ? worldPosition.getCenter() : room.pad(f.pad());
        level.sendParticles(ParticleTypes.SMOKE, at.x, at.y + 0.4, at.z, 6, 0.3, 0.2, 0.3, 0.01);
    }

    /** A likeness to roll loot with: what the log's specimen was, but carrying nothing, never in the world itself. */
    @Nullable
    private Mob specimen(ServerLevel level) {
        MobLogItem.Log log = mobLog();
        if (specimen == null && log != null) {
            CompoundTag tag = log.specimen().copy();
            CARRIED.forEach(tag::remove);
            Entity e = EntityType.loadEntityRecursive(tag, level, EntitySpawnReason.LOAD, EntityProcessor.NOP);
            if (e instanceof Mob mob) {
                mob.setPos(worldPosition.getCenter());
                specimen = mob;
            }
        }
        return specimen;
    }

    /** What {@code mob} drops dying as {@code tier} has it die; a blow from the frame hands out what a player's would, with a sword of as much Looting as the tier gives. */
    public static List<ItemStack> drops(ServerLevel level, Mob mob, int tier) {
        boolean byPlayer = Imitation.byPlayer(tier);
        FakePlayer frame = FakePlayerFactory.get(level, SPARRING);
        frame.setItemSlot(EquipmentSlot.MAINHAND, sword(level, Imitation.looting(tier)));
        DamageSource source = byPlayer ? level.damageSources().playerAttack(frame) : level.damageSources().generic();
        if (byPlayer) {
            mob.setLastHurtByPlayer(frame, 100);
        }
        mob.captureDrops(new ArrayList<>());
        mob.getLootTable().ifPresent(table -> mob.dropFromLootTable(level, source, byPlayer, table));
        ((LivingEntity) mob).dropCustomDeathLoot(level, source, byPlayer);
        Collection<ItemEntity> dropped = mob.captureDrops(null);
        return dropped.stream().map(ItemEntity::getItem).toList();
    }

    private static ItemStack sword(ServerLevel level, int looting) {
        ItemStack sword = new ItemStack(Items.NETHERITE_SWORD);
        if (looting > 0) {
            sword.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.LOOTING), looting);
        }
        return sword;
    }

    private void lookAtGround(ServerLevel level, ModelLink link) {
        int n = limit();
        if (n == 0 || free() == 0) {
            return;
        }
        long seed = rng.nextLong();
        asking = true;
        askedAt = ticks;
        int id = ++request;
        link.submit(level, model -> Ground.draw(model, n, new Random(seed)), drawn -> {
            if (id == request) {
                asking = false;
                if (drawn != null) {
                    dug(level, drawn);
                }
            }
        });
    }

    /** The model wrote these rows: each ore in them counts as far as it is trusted, and gives what mining it would. */
    private void dug(ServerLevel level, String[] drawn) {
        List<String> sent = new ArrayList<>(drawn.length);
        boolean paidAny = false;
        for (int pad = 0; pad < drawn.length; pad++) {
            sent.add(pad + " " + drawn[pad]);
            for (char c : drawn[pad].toCharArray()) {
                int ore = Ground.ORES.indexOf(c);
                if (ore >= 0 && rng.nextFloat() < trust[ore]) {
                    BlockState block = oreBlock(c).defaultBlockState();
                    for (ItemStack stack : Block.getDrops(block, level, worldPosition, null)) {
                        give(level, stack);
                    }
                    paidAny = true;
                }
            }
        }
        if (paidAny) {
            tell(level, "arena/materials");
        }
        PacketDistributor.sendToPlayersTrackingChunk(level, net.minecraft.world.level.ChunkPos.containing(worldPosition),
                new ArenaSync(worldPosition, new long[0], sent));
    }

    private static Block oreBlock(char ore) {
        return switch (ore) {
            case 'C' -> Blocks.COAL_ORE;
            case 'I' -> Blocks.IRON_ORE;
            case 'U' -> Blocks.COPPER_ORE;
            case 'O' -> Blocks.GOLD_ORE;
            case 'R' -> Blocks.REDSTONE_ORE;
            case 'Z' -> Blocks.LAPIS_ORE;
            case 'X' -> Blocks.DIAMOND_ORE;
            default -> Blocks.EMERALD_ORE;
        };
    }

    /** How many of its output slots stand empty. */
    private int free() {
        int n = 0;
        for (int i = OUT; i < SLOTS; i++) {
            n += items.getItem(i).isEmpty() ? 1 : 0;
        }
        return n;
    }

    /** Into the output slots, and what they cannot take onto the floor in front of it. */
    private void give(ServerLevel level, ItemStack stack) {
        for (int i = OUT; i < SLOTS && !stack.isEmpty(); i++) {
            ItemStack there = items.getItem(i);
            if (ItemStack.isSameItemSameComponents(there, stack)) {
                int moved = Math.min(stack.getCount(), there.getMaxStackSize() - there.getCount());
                there.grow(moved);
                stack.shrink(moved);
            }
        }
        for (int i = OUT; i < SLOTS && !stack.isEmpty(); i++) {
            if (items.getItem(i).isEmpty()) {
                items.setItem(i, stack.copy());
                stack.setCount(0);
            }
        }
        if (!stack.isEmpty()) {
            Vec3 at = worldPosition.getCenter().relative(getBlockState().getValue(ArenaBlock.FACING), 1);
            Containers.dropItemStack(level, at.x, at.y, at.z, stack);
        }
        items.setChanged();
    }

    private void tell(ServerLevel level, String advancement) {
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(worldPosition.getCenter()) < HEARD * HEARD) {
                Awards.grant(player, advancement);
            }
        }
    }

    private void working(boolean on) {
        BlockState here = getBlockState();
        if (level != null && here.getValue(ArenaBlock.WORKING) != on) {
            level.setBlock(worldPosition, here.setValue(ArenaBlock.WORKING, on), Block.UPDATE_CLIENTS);
        }
    }

    /** Tells those near enough what the room and the likeness are now. */
    private void synced(ServerLevel level) {
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    private void slotsChanged() {
        setChanged();
        MobLogItem.Log mob = mobLog();
        String text = mob != null ? mob.lines() : FieldLogItem.log(items.getItem(LOG));
        // a new log is a new check, and whatever was running stops
        if (!text.equals(heldLog)) {
            heldLog = text;
            request++;
            checking = false;
            asking = false;
            checkedLog = null;
            check = null;
            specimen = null;
            fights.clear();
            if (level instanceof ServerLevel server) {
                synced(server);
            }
        }
    }

    // ---------------------------------------------------------------- what players see

    /** How one pad's fight stood at its last two looks, on the client. */
    public static final class View {
        public float distanceBefore, distance, turnBefore, turn;
        public float walkedBefore, walked;
        public int action;
        public boolean hurt, struck, over, paid;
        public long at;
    }

    public void receive(ArenaSync sync, long now) {
        for (long f : sync.fights()) {
            View v = views.get(ArenaSync.pad(f));
            boolean fresh = v == null || v.over;
            if (fresh) {
                v = new View();
                views.put(ArenaSync.pad(f), v);
                v.distance = ArenaSync.distance(f);
                v.turn = ArenaSync.turn(f);
            }
            v.distanceBefore = v.distance;
            v.turnBefore = v.turn;
            v.walkedBefore = v.walked;
            v.distance = ArenaSync.distance(f);
            v.turn = ArenaSync.turn(f);
            // the shortest way round
            float t = v.turn - v.turnBefore;
            v.turnBefore += t > 0.5f ? 1 : t < -0.5f ? -1 : 0;
            v.walked += Math.abs(v.distance - v.distanceBefore) + Math.abs(v.turn - v.turnBefore) * 4;
            v.action = ArenaSync.action(f);
            v.hurt = ArenaSync.hurt(f);
            v.struck = ArenaSync.struck(f);
            v.over = ArenaSync.over(f);
            v.paid = ArenaSync.paid(f);
            v.at = now;
            if ((v.struck || Imitation.ACTIONS.charAt(v.action) == Imitation.STRIKE) && room != null && level != null) {
                Vec3 at = room.pad(ArenaSync.pad(f));
                for (int i = 0; i < 3; i++) {
                    level.addParticle(ParticleTypes.CRIT, at.x, at.y + 0.5, at.z, (i - 1) * 0.1, 0.1, (1 - i) * 0.1);
                }
            }
        }
        for (String row : sync.rows()) {
            rowsAt = now;
            int space = row.indexOf(' ');
            Deque<String> kept = rows.computeIfAbsent(Integer.parseInt(row.substring(0, space)), k -> new ArrayDeque<>());
            kept.addLast(row.substring(space + 1));
            if (kept.size() > 8) {
                kept.removeFirst();
            }
        }
    }

    public State state() {
        return state;
    }

    /** The fights its compute lets it run at once, as it last looked. */
    public int compute() {
        return compute;
    }

    /** How its model did when it was last checked, if it has been. */
    @Nullable
    public Imitation.Check check() {
        return check;
    }

    public int running() {
        return ground() ? working() : fights.size();
    }

    /** What a pad's fights looked like last, on the client. */
    public Map<Integer, View> views() {
        return views;
    }

    /** The newest rows of ground on each pad, on the client: gone a while after the last was heard of. */
    public Map<Integer, Deque<String>> rows(long now) {
        if (now - rowsAt > ROWS_KEPT) {
            rows.clear();
        }
        return rows;
    }

    @Nullable
    public ArenaRoom room() {
        return room;
    }

    /** The mob's likeness, made once for the client from what the server last said of it. */
    @Nullable
    public Mob likeness() {
        if (likeness == null && shown != null && level != null) {
            if (EntityType.loadEntityRecursive(shown, level, EntitySpawnReason.LOAD, EntityProcessor.NOP) instanceof Mob mob) {
                likeness = mob;
            }
        }
        return likeness;
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level instanceof ServerLevel server) {
            PowerNetwork.changed(server);
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.residual.arena");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new ArenaMenu(id, inventory, worldPosition, items, data);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) {
            Containers.dropContents(level, pos, items);
            Cooling.working(level, worldPosition, false);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        ContainerHelper.saveAllItems(out.child("items"), items.getItems());
        if (owner != null) {
            out.putString("owner", owner.toString());
        }
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        in.child("items").ifPresent(c -> ContainerHelper.loadAllItems(c, items.getItems()));
        in.getString("owner").ifPresent(s -> owner = UUID.fromString(s));
        in.read("room_min", BlockPos.CODEC).ifPresent(min -> room = new ArenaRoom(min, in.read("room_max", BlockPos.CODEC).orElse(min)));
        if (in.getBooleanOr("no_room", false)) {
            room = null;
        }
        in.read("shown", CompoundTag.CODEC).ifPresent(tag -> {
            if (!tag.equals(shown)) {
                shown = tag;
                likeness = null;
            }
        });
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
        if (room != null) {
            out.store("room_min", BlockPos.CODEC, room.min());
            out.store("room_max", BlockPos.CODEC, room.max());
        } else {
            out.putBoolean("no_room", true);
        }
        MobLogItem.Log log = mobLog();
        if (log != null) {
            out.store("shown", CompoundTag.CODEC, log.specimen());
        }
        return out.buildResult();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** The log and the model go in by hand, or a hopper the log; only what it made comes out. */
    private final class Stock extends SimpleContainer implements WorldlyContainer {
        Stock() {
            super(SLOTS);
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return slot == LOG ? ArenaMenu.isLog(stack) : slot == MODEL && ArenaMenu.isModel(stack);
        }

        @Override
        public int[] getSlotsForFace(Direction side) {
            int[] out = new int[OUTS];
            for (int i = 0; i < OUTS; i++) {
                out[i] = OUT + i;
            }
            return out;
        }

        @Override
        public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
            return false;
        }

        @Override
        public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
            return slot >= OUT;
        }

        @Override
        public void setChanged() {
            super.setChanged();
            slotsChanged();
        }
    }
}
