package io.github.metrovoc.residual.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.metrovoc.residual.Awards;
import io.github.metrovoc.residual.Residual;
import io.github.metrovoc.residual.learn.Imitation;
import io.github.metrovoc.residual.registry.ModComponents;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.illager.SpellcasterIllager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Writes down what a kind of mob does. Used on a mob, a blank log takes that kind and keeps the look of that one; carried
 * anywhere in the inventory after that, it writes a line for every mob of the kind within {@value #RADIUS} blocks of whoever
 * carries it, a look four times a second. A fight is what it is best at, and in a Model Core's data slot it is practice for
 * Imitation. What it holds goes in an Arena.
 */
public class MobLogItem extends Item {
    /** The most a log holds, about a thousand lines; it keeps its newest. A model the size of a good likeness learns one log by heart at a quarter of this. */
    public static final int CAPACITY = 64_000;
    /** How far off it sees, how many mobs it follows at once, and how often it writes its finished lines down. */
    private static final int RADIUS = 16, MOST = 16, WRITE_EVERY = 100;
    /** How far a mob has to move in a look for it to count, and the share of that toward or away from what it is after. */
    private static final double MOVED = 0.2, TOWARD = 0.6, BLINKED = 6, ROSE = 0.25, NEAR = 2.5, SHUFFLE = 1;
    /** What of a mob a specimen forgets: where it was, what it was doing, what it had to do with others. */
    private static final List<String> FORGOTTEN = List.of("UUID", "Pos", "Motion", "Rotation", "Passengers", "leash", "Brain", "active_effects",
            "Fire", "fall_distance", "Air", "OnGround", "PortalCooldown", "HurtTime", "HurtByTimestamp", "DeathTime", "last_hurt_by_mob",
            "last_hurt_by_player", "ticks_since_last_hurt_by_mob", "ticks_since_last_hurt_by_player", "sleeping_pos", "home_pos", "home_radius",
            "Tags", "CustomName", "CustomNameVisible", "NoAI", "Silent", "Glowing", "Invulnerable", "PersistenceRequired");
    private static final Map<ItemStack, Recording> RECORDINGS = new WeakHashMap<>();

    /** What a log holds: the kind it follows, the one it was first used on, and its lines. */
    public record Log(Identifier kind, CompoundTag specimen, String lines) {
        public static final Codec<Log> CODEC = RecordCodecBuilder.create(i -> i.group(
                Identifier.CODEC.fieldOf("kind").forGetter(Log::kind),
                CompoundTag.CODEC.fieldOf("specimen").forGetter(Log::specimen),
                Codec.STRING.fieldOf("lines").forGetter(Log::lines)
        ).apply(i, Log::new));
        public static final StreamCodec<ByteBuf, Log> STREAM_CODEC = StreamCodec.composite(
                Identifier.STREAM_CODEC, Log::kind,
                ByteBufCodecs.TRUSTED_COMPOUND_TAG, Log::specimen,
                ByteBufCodecs.stringUtf8(CAPACITY), Log::lines,
                Log::new);

        public EntityType<?> type() {
            return BuiltInRegistries.ENTITY_TYPE.getValue(kind);
        }
    }

    public MobLogItem(Properties properties) {
        super(properties);
    }

    @Nullable
    public static Log log(ItemStack stack) {
        return stack.get(ModComponents.MOB_LOG.get());
    }

    /** What of {@code mob} a log keeps, to show its likeness and to roll what it drops. */
    private static CompoundTag specimen(Mob mob) {
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(Residual.LOG)) {
            TagValueOutput out = TagValueOutput.createWithContext(reporter, mob.registryAccess());
            mob.saveAsPassenger(out);
            CompoundTag tag = out.buildResult();
            FORGOTTEN.forEach(tag::remove);
            return tag;
        }
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof Mob mob) || log(stack) != null) {
            return InteractionResult.PASS;
        }
        if (player.level() instanceof ServerLevel level) {
            stack.set(ModComponents.MOB_LOG.get(), new Log(BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()), specimen(mob), ""));
            level.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1f, 1.2f);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, @Nullable EquipmentSlot slot) {
        Log log = log(stack);
        if (log != null && entity instanceof ServerPlayer player && level.getGameTime() % Imitation.LOOK == 0) {
            RECORDINGS.computeIfAbsent(stack, s -> new Recording(log)).look(level, player, stack);
        }
    }

    @Override
    public Component getName(ItemStack stack) {
        Log log = log(stack);
        return log == null ? super.getName(stack) : Component.translatable("item.residual.mob_log.of", log.type().getDescription());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        Log log = log(stack);
        if (log == null) {
            out.accept(Component.translatable("residual.mob_log.blank").withStyle(ChatFormatting.GRAY));
            return;
        }
        List<String> lines = Imitation.lines(log.lines());
        int looks = lines.stream().mapToInt(l -> (l.length() - 1) / 2).sum();
        out.accept(Component.translatable("residual.mob_log.looks", looks, lines.size()).withStyle(ChatFormatting.GRAY));
    }

    /** The lines being written for the mobs around one log. */
    private static final class Recording {
        private final EntityType<?> kind;
        private final char letter;
        private final Map<UUID, Line> lines = new HashMap<>();
        private final StringBuilder finished = new StringBuilder();
        private int sinceWritten;

        Recording(Log log) {
            kind = log.type();
            letter = Imitation.letter(log.kind().toString());
        }

        void look(ServerLevel level, ServerPlayer carrier, ItemStack stack) {
            Set<UUID> seen = new HashSet<>();
            AABB around = carrier.getBoundingBox().inflate(RADIUS);
            for (Mob mob : level.getEntitiesOfClass(Mob.class, around, m -> m.getType() == kind && m.isAlive())) {
                if (seen.size() >= MOST) {
                    break;
                }
                seen.add(mob.getUUID());
                lines.computeIfAbsent(mob.getUUID(), id -> new Line()).look(mob, carrier, this);
            }
            lines.entrySet().removeIf(e -> {
                if (!seen.contains(e.getKey())) {
                    e.getValue().close(this);
                    return true;
                }
                return false;
            });
            sinceWritten += Imitation.LOOK;
            if (sinceWritten >= WRITE_EVERY && !finished.isEmpty()) {
                sinceWritten = 0;
                write(stack, carrier);
            }
        }

        private void write(ItemStack stack, ServerPlayer carrier) {
            Log log = log(stack);
            if (log != null) {
                String text = log.lines() + finished;
                finished.setLength(0);
                int cut = 0;
                while (text.length() - cut > CAPACITY) {
                    cut = text.indexOf('\n', cut) + 1;
                }
                stack.set(ModComponents.MOB_LOG.get(), new Log(log.kind(), log.specimen(), text.substring(cut)));
                Awards.grant(carrier, "arena/recorded");
            }
        }
    }

    /** One mob's line so far, and how things stood at its last look. */
    private static final class Line {
        private final StringBuilder looks = new StringBuilder();
        @Nullable
        private Vec3 at;
        private char state;
        private int struckAt;
        private int active;

        void look(Mob mob, ServerPlayer carrier, Recording recording) {
            LivingEntity target = mob.getTarget();
            boolean targeting = target != null && target.isAlive();
            LivingEntity after = targeting ? target : carrier;
            Vec3 now = mob.position();
            if (at != null) {
                char action = action(mob, after, now);
                looks.append(state).append(action);
                active += action == Imitation.STILL ? 0 : 1;
                if (looks.length() >= 2 * Imitation.LOOKS) {
                    close(recording);
                }
            }
            at = now;
            struckAt = mob.getLastHurtMobTimestamp();
            state = Imitation.state(mob.distanceTo(after), targeting, mob.hurtTime > 0);
        }

        /** What it did since the last look, the most telling thing first. */
        private char action(Mob mob, LivingEntity after, Vec3 now) {
            double moved = now.subtract(at).horizontalDistance(), rose = now.y - at.y;
            if (moved > BLINKED) {
                return Imitation.BLINK;
            }
            // a blow counts once: a swing lasts longer than a look, and only monsters' swings ever end
            if (mob instanceof Monster && mob.swinging && mob.swingTime < Imitation.LOOK || mob.getLastHurtMobTimestamp() != struckAt) {
                return Imitation.STRIKE;
            }
            if (ready(mob)) {
                return Imitation.READY;
            }
            if (rose > ROSE && !mob.onGround()) {
                return Imitation.LEAP;
            }
            if (moved < MOVED) {
                return Imitation.STILL;
            }
            // only its own move counts: how much nearer it came to where the other now stands
            Vec3 other = after.position();
            double was = at.distanceTo(other), is = now.distanceTo(other);
            // and within reach, which way it shuffles means nothing
            if (was < NEAR && is < NEAR && moved < SHUFFLE) {
                return Imitation.ASIDE;
            }
            double came = was - is;
            return came > TOWARD * moved ? Imitation.CLOSER : came < -TOWARD * moved ? Imitation.AWAY : Imitation.ASIDE;
        }

        /** Whether it is winding up to something: drawing a bow, swelling, casting, charging. */
        private static boolean ready(Mob mob) {
            Pose pose = mob.getPose();
            return mob.isUsingItem() || pose != Pose.STANDING && pose != Pose.DYING
                    || mob instanceof Creeper creeper && creeper.getSwellDir() > 0
                    || mob instanceof SpellcasterIllager caster && caster.isCastingSpell()
                    || mob instanceof Ghast ghast && ghast.isCharging()
                    || mob instanceof Blaze && mob.isOnFire();
        }

        void close(Recording recording) {
            // a mob that only stands about shows nothing a model could learn from it
            if (looks.length() >= 2 * Imitation.FEWEST && active * 4 >= looks.length() / 2) {
                recording.finished.append(recording.letter).append(looks).append('\n');
            }
            looks.setLength(0);
            active = 0;
        }
    }
}
