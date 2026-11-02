package io.github.metrovoc.residual.item;

import io.github.metrovoc.residual.Awards;
import io.github.metrovoc.residual.Residual;
import io.github.metrovoc.residual.ResidualConfig;
import io.github.metrovoc.residual.client.ClientHooks;
import io.github.metrovoc.residual.learn.Pilot;
import io.github.metrovoc.residual.model.Board;
import io.github.metrovoc.residual.model.ModelInfo;
import io.github.metrovoc.residual.model.Models;
import io.github.metrovoc.residual.net.SuitFlight;
import io.github.metrovoc.residual.registry.ModComponents;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * An exosuit: armour with a board in it, which carries a model quantized to fit. A model goes in and out as into a flight
 * frame. The board packs its weights to 8 and to 4 bits, off the server thread, and tests each form; what it found is
 * the suit's {@link Board}, and the form's compute sets which {@link SuitAbility abilities} run: those left on, cheapest
 * first, as far as it goes. Without a model that fits and passes, or without charge, it is only armour.
 */
public class ExosuitItem extends FlightFrameItem {
    public static final int CAPACITY = 16_000_000;
    /** How much Forge Energy moves in or out of it a tick, for other mods' chargers. */
    public static final int TRANSFER = 80_000;
    /** How much further its wearer reaches, to blocks and to what they strike, and what they step up. */
    public static final double REACH = 5, STEP = 0.5;
    /** Power a point of damage costs the shield: a full suit takes 640 points, or 320 hearts. */
    public static final int SHIELD_POWER = 25_000;
    /** How fast it flies, twice as fast as a frame or as anyone in creative play. */
    public static final float FAST = 0.1f;
    private static final float USUAL = 0.05f;
    private static final Identifier REACH_BLOCKS = Residual.id("reach_blocks"), REACH_ENTITIES = Residual.id("reach_entities"), STEP_UP = Residual.id("step_assist");
    /** The ticks between practice flights: one look every half second. */
    public static final int LOOK_EVERY = 10;
    private static final int FLIGHT_EVERY = LOOK_EVERY * Pilot.LOOKS;

    private record Asked(UUID model, int steps) {
    }

    /** What boards found, by the model they tested, and the models being tested now. */
    private static final Map<Asked, Board> FOUND = new HashMap<>();
    private static final Set<Asked> TESTING = new HashSet<>();
    /** The players a suit is doing something for, with the tick of their next practice flight. */
    private static final Map<UUID, Long> WORN = new HashMap<>();
    /** The players a suit is holding up. */
    private static final Set<UUID> HELD = new HashSet<>();

    public ExosuitItem(Properties properties) {
        super(properties);
    }

    @Override
    protected int capacity() {
        return CAPACITY;
    }

    /** What a suit's board can hold: as much as an agent's head, in bytes. */
    public static long memory() {
        return ResidualConfig.ONBOARD_PARAMETERS.get() * Float.BYTES;
    }

    /** The suit a player wears, or else holds; empty if none. */
    public static ItemStack on(Player player) {
        for (ItemStack stack : List.of(player.getItemBySlot(EquipmentSlot.CHEST), player.getMainHandItem(), player.getOffhandItem())) {
            if (stack.getItem() instanceof ExosuitItem) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    /** What {@code suit}'s board measured of the model on it now, or null while it has not. */
    @Nullable
    public static Board board(ItemStack suit) {
        ModelInfo model = pilot(suit);
        Board board = suit.get(ModComponents.BOARD.get());
        return model != null && board != null && model.causal() && board.model().equals(model.id()) && board.steps() == model.steps() ? board : null;
    }

    public static long compute(ItemStack suit) {
        Board board = board(suit);
        return board == null ? 0 : board.compute(memory());
    }

    public static boolean isOn(ItemStack suit, SuitAbility ability) {
        return (suit.getOrDefault(ModComponents.ABILITIES_OFF.get(), 0) >> ability.ordinal() & 1) == 0;
    }

    public static void turn(ItemStack suit, SuitAbility ability, boolean on) {
        int off = suit.getOrDefault(ModComponents.ABILITIES_OFF.get(), 0);
        suit.set(ModComponents.ABILITIES_OFF.get(), on ? off & ~(1 << ability.ordinal()) : off | 1 << ability.ordinal());
    }

    /** What {@code suit} runs now: nothing without charge. */
    public static EnumSet<SuitAbility> running(ItemStack suit) {
        Board board = board(suit);
        Board.Form form = board == null ? null : board.running(memory());
        if (form == null || charge(suit) <= 0) {
            return EnumSet.noneOf(SuitAbility.class);
        }
        return SuitAbility.running(board.compute(form, memory()), suit.getOrDefault(ModComponents.ABILITIES_OFF.get(), 0), form.passesSurvey());
    }

    /** The compute left over from what runs. */
    public static long free(ItemStack suit) {
        long free = compute(suit);
        for (SuitAbility a : running(suit)) {
            free -= a.compute;
        }
        return free;
    }

    /** Opens its screen when used while sneaking; otherwise it goes on, as armour does. */
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!player.isSecondaryUseActive()) {
            return super.use(level, player, hand);
        }
        if (level.isClientSide()) {
            ClientHooks.openExosuit();
        }
        return InteractionResult.SUCCESS;
    }

    /** Has the model on it tested, once for each model: quantizing and testing takes seconds. */
    @Override
    public void inventoryTick(ItemStack suit, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
        ModelInfo model = pilot(suit);
        if (model == null) {
            suit.remove(ModComponents.BOARD.get());
            return;
        }
        Asked asked = new Asked(model.id(), model.steps());
        Board board = suit.get(ModComponents.BOARD.get());
        if (board != null && new Asked(board.model(), board.steps()).equals(asked)) {
            return;
        }
        Board found = FOUND.get(asked);
        if (found != null) {
            suit.set(ModComponents.BOARD.get(), found);
            level.playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.4f, 1.6f);
        } else if (TESTING.add(asked)) {
            Models.submit(level.getServer(), model.id(), graph -> Board.measure(model, graph), measured -> {
                TESTING.remove(asked);
                FOUND.put(asked, measured != null ? measured : Board.unreadable(model));
            });
        }
    }

    @Override
    public void appendHoverText(ItemStack suit, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        out.accept(Component.translatable("residual.frame.charge", String.format(Locale.ROOT, "%,d", charge(suit)), String.format(Locale.ROOT, "%,d", CAPACITY))
                .withStyle(ChatFormatting.GRAY));
        ModelInfo model = pilot(suit);
        Board board = board(suit);
        if (model == null) {
            out.accept(Component.translatable("residual.exosuit.no_model").withStyle(ChatFormatting.DARK_GRAY));
        } else if (board == null) {
            out.accept(Component.translatable("residual.exosuit.testing", model.tag()).withStyle(ChatFormatting.GRAY));
        } else if (board.running(memory()) == null) {
            out.accept(Component.translatable("residual.exosuit.fails", model.tag()).withStyle(ChatFormatting.RED));
        } else {
            out.accept(Component.translatable("residual.exosuit.carries", model.tag(), String.format(Locale.ROOT, "%,d", compute(suit))).withStyle(ChatFormatting.AQUA));
        }
        out.accept(Component.translatable("residual.exosuit.open", Component.keybind("key.residual.exosuit")).withStyle(ChatFormatting.DARK_GRAY));
    }

    /** Runs what a worn suit's compute allows, spends its charge for that, and takes back all it gave once it stops. */
    public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isSpectator()) {
            return;
        }
        UUID uuid = player.getUUID();
        ItemStack suit = player.getItemBySlot(EquipmentSlot.CHEST);
        Set<SuitAbility> running = suit.getItem() instanceof ExosuitItem ? running(suit) : Set.of();
        if (running.isEmpty() && !WORN.containsKey(uuid)) {
            return;
        }
        modifier(player, Attributes.STEP_HEIGHT, STEP_UP, STEP, running.contains(SuitAbility.STEP_ASSIST));
        modifier(player, Attributes.BLOCK_INTERACTION_RANGE, REACH_BLOCKS, REACH, running.contains(SuitAbility.REACH));
        modifier(player, Attributes.ENTITY_INTERACTION_RANGE, REACH_ENTITIES, REACH, running.contains(SuitAbility.REACH));
        MobEffectInstance sight = player.getEffect(MobEffects.NIGHT_VISION);
        if (running.contains(SuitAbility.NIGHT_VISION)) {
            // kept above the ten seconds in which it starts to flicker
            if (sight == null || sight.getDuration() < 300) {
                player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 400, 0, true, false, false));
            }
        } else if (sight != null && sight.isAmbient()) {
            player.removeEffect(MobEffects.NIGHT_VISION);
        }
        fly(player, running.contains(SuitAbility.FLIGHT));
        if (running.isEmpty()) {
            WORN.remove(uuid);
            return;
        }
        long now = player.level().getGameTime();
        if (now >= WORN.getOrDefault(uuid, 0L)) {
            WORN.put(uuid, now + FLIGHT_EVERY);
            practise(player, suit);
        }
        if (now % 20 == 0) {
            int power = 0;
            for (SuitAbility a : running) {
                power += a != SuitAbility.FLIGHT || player.getAbilities().flying ? a.power : 0;
            }
            suit.set(ModComponents.CHARGE.get(), Math.max(0, charge(suit) - 20 * power));
            Awards.grant(player, "exosuit/board");
            if (running.size() == SuitAbility.values().length) {
                Awards.grant(player, "exosuit/all");
            }
        }
    }

    /**
     * The shield: the suit's power takes what armour left of a blow, as far as its charge goes, and the rest gets through.
     * Nothing stops what goes through invulnerability itself.
     */
    public static void shield(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getNewDamage() <= 0 || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }
        ItemStack suit = player.getItemBySlot(EquipmentSlot.CHEST);
        if (!(suit.getItem() instanceof ExosuitItem) || !running(suit).contains(SuitAbility.SHIELD)) {
            return;
        }
        float taken = Math.min(event.getNewDamage(), (float) charge(suit) / SHIELD_POWER);
        suit.set(ModComponents.CHARGE.get(), Math.max(0, charge(suit) - Math.round(taken * SHIELD_POWER)));
        event.setNewDamage(event.getNewDamage() - taken);
        ServerLevel level = player.level();
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 1, player.getZ(), 12, 0.3, 0.5, 0.3, 0.1);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.6f, 1.4f);
    }

    private static void modifier(ServerPlayer player, Holder<Attribute> attribute, Identifier id, double amount, boolean on) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null && instance.hasModifier(id) != on) {
            if (on) {
                instance.addTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE));
            } else {
                instance.removeModifier(id);
            }
        }
    }

    /** The second flight tier: lets its wearer fly fast, and lets go of those it held. */
    private static void fly(ServerPlayer player, boolean on) {
        if (player.isCreative()) {
            return;
        }
        Abilities abilities = player.getAbilities();
        if (!on) {
            if (HELD.remove(player.getUUID())) {
                abilities.mayfly = false;
                abilities.flying = false;
                abilities.setFlyingSpeed(USUAL);
                player.onUpdateAbilities();
            }
            return;
        }
        if (HELD.add(player.getUUID()) || !abilities.mayfly) {
            abilities.mayfly = true;
            abilities.setFlyingSpeed(FAST);
            player.onUpdateAbilities();
        }
    }

    /**
     * The model at work: a practice flight, flown on board in the form it runs in, off the server thread. Its wearer's
     * screen plays it back a look at a time.
     */
    private static void practise(ServerPlayer player, ItemStack suit) {
        Board board = board(suit);
        Board.Form form = board == null ? null : board.running(memory());
        if (form != null) {
            Models.submit(player.level().getServer(), board.model(), form.bits(), model -> new Pilot().demo(model, new Random()).line.toString(), line -> {
                if (line != null && !player.hasDisconnected()) {
                    PacketDistributor.sendToPlayer(player, new SuitFlight(line));
                }
            });
        }
    }
}
