package io.github.metrovoc.residual.block;

import io.github.metrovoc.residual.Awards;
import io.github.metrovoc.residual.agent.Agent;
import io.github.metrovoc.residual.learn.Demand;
import io.github.metrovoc.residual.learn.Watch;
import io.github.metrovoc.residual.menu.WatchPostMenu;
import io.github.metrovoc.residual.model.ModelLink;
import io.github.metrovoc.residual.net.WatchSync;
import io.github.metrovoc.residual.registry.ModBlockEntities;
import io.github.metrovoc.residual.registry.ModItems;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A Watch Post at work: every look it writes down, for each machine and agent around it and for its crops together, one digit
 * for how much that thing did, and has its model read one line. How much the line surprises the model says whether something has
 * gone wrong; a line the model cannot follow at all is left to the rule, which only sees a thing that has stopped for two minutes.
 * While anything is wrong it gives a redstone signal and marks the thing, and whoever is near hears of each new fault.
 */
public class WatchPostBlockEntity extends BlockEntity implements MenuProvider {
    public static final int MODEL = 0;
    /** How far around it, and how many things at most, it watches. */
    public static final int RADIUS = 16, HEIGHT = 4, MOST = 16;
    /** What it thinks of a thing: all well, wrong by its model, wrong by the rule. */
    public static final int FINE = 0, WRONG = 1, BY_RULE = 2;
    /** Ticks between two looks at how a machine or agent is getting on. */
    private static final int SAMPLE = 20;
    /** How far off a player hears of a fault. */
    private static final int HEARD = 64;

    /** One thing it watches. */
    private static final class Watched {
        final ItemStack icon;
        Component name;
        BlockPos at;
        String line = "";
        /** How much it did since the last look, and where it stood or what its timer said at the last sample. */
        long did;
        int timer = -1;
        @Nullable
        Vec3 stood;
        /** Looks its model's last alarm still holds: by the time a thing has been quiet long enough for the rule, it surprises no one. */
        int alarm;
        int verdict = FINE;

        Watched(ItemStack icon, Component name, BlockPos at) {
            this.icon = icon;
            this.name = name;
            this.at = at;
        }
    }

    private final SimpleContainer items = new SimpleContainer(1) {
        @Override
        public void setChanged() {
            super.setChanged();
            WatchPostBlockEntity.this.setChanged();
        }
    };
    private final Map<String, Watched> watched = new LinkedHashMap<>();
    private final Map<Long, Integer> ages = new HashMap<>();
    private int nextAsked;
    private boolean asking;

    public WatchPostBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WATCH_POST.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, WatchPostBlockEntity be) {
        ServerLevel server = (ServerLevel) level;
        long now = level.getGameTime() + pos.asLong();
        if (now % Demand.LOOK == 0) {
            be.look(server);
        } else if (now % SAMPLE == 0) {
            be.sample(server);
        }
    }

    /** Sees how each machine and agent is getting on, between looks. */
    private void sample(ServerLevel level) {
        for (BlockEntity be : machines(level)) {
            String key = "m" + be.getBlockPos().asLong();
            Watched w = watched.computeIfAbsent(key, k -> {
                ItemStack icon = new ItemStack(be.getBlockState().getBlock().asItem());
                return new Watched(icon, be.getBlockState().getBlock().getName(), be.getBlockPos());
            });
            int timer = ForecasterBlockEntity.timer(be);
            if (w.timer >= 0 && timer != w.timer) {
                w.did += SAMPLE;
            }
            w.timer = timer;
        }
        for (Agent a : level.getEntitiesOfClass(Agent.class, around())) {
            Watched w = watched.computeIfAbsent("a" + a.getUUID(), k -> new Watched(new ItemStack(ModItems.AGENT.get()), a.getName(), a.blockPosition()));
            w.name = a.getName();
            if (w.stood != null) {
                w.did += Math.round(a.position().distanceTo(w.stood));
            }
            w.stood = a.position();
            w.at = a.blockPosition();
        }
    }

    private AABB around() {
        return new AABB(worldPosition).inflate(RADIUS, HEIGHT, RADIUS);
    }

    /** The machines around it a Forecaster could watch. */
    private List<BlockEntity> machines(ServerLevel level) {
        List<BlockEntity> out = new ArrayList<>();
        ChunkPos from = ChunkPos.containing(worldPosition.offset(-RADIUS, 0, -RADIUS));
        ChunkPos to = ChunkPos.containing(worldPosition.offset(RADIUS, 0, RADIUS));
        for (int cx = from.x(); cx <= to.x(); cx++) {
            for (int cz = from.z(); cz <= to.z(); cz++) {
                if (!level.hasChunk(cx, cz)) {
                    continue;
                }
                for (BlockEntity be : level.getChunk(cx, cz).getBlockEntities().values()) {
                    BlockPos p = be.getBlockPos();
                    if (ForecasterBlockEntity.cook(be) > 0 && Math.abs(p.getX() - worldPosition.getX()) <= RADIUS
                            && Math.abs(p.getZ() - worldPosition.getZ()) <= RADIUS && Math.abs(p.getY() - worldPosition.getY()) <= HEIGHT
                            && out.size() < MOST) {
                        out.add(be);
                    }
                }
            }
        }
        return out;
    }

    /** How much the crops around it grew since the last look, all together; null with none around it. */
    private void lookAtCrops(ServerLevel level) {
        long grown = 0;
        Set<Long> seen = new HashSet<>();
        BlockPos first = null;
        for (BlockPos p : BlockPos.betweenClosed(worldPosition.offset(-RADIUS, -HEIGHT, -RADIUS), worldPosition.offset(RADIUS, HEIGHT, RADIUS))) {
            if (!level.isLoaded(p)) {
                continue;
            }
            int age = ForecasterBlockEntity.age(level.getBlockState(p));
            if (age >= 0) {
                long key = p.asLong();
                seen.add(key);
                first = first == null ? p.immutable() : first;
                Integer before = ages.put(key, age);
                grown += before != null && age > before ? age - before : 0;
            }
        }
        ages.keySet().retainAll(seen);
        if (first != null) {
            BlockPos at = first;
            Watched w = watched.computeIfAbsent("f", k -> new Watched(new ItemStack(Items.WHEAT), Component.translatable("residual.watch.field"), at));
            w.did += grown;
        } else {
            watched.remove("f");
        }
    }

    /** Writes the digits, asks about one line, and goes by what it knows of each. */
    private void look(ServerLevel level) {
        sample(level);
        lookAtCrops(level);
        Set<String> here = new HashSet<>();
        machines(level).forEach(be -> here.add("m" + be.getBlockPos().asLong()));
        level.getEntitiesOfClass(Agent.class, around()).forEach(a -> here.add("a" + a.getUUID()));
        here.add("f");
        watched.keySet().retainAll(here);
        for (Watched w : watched.values()) {
            String line = w.line + Demand.look(w.did);
            w.line = line.length() > Watch.LINE ? line.substring(line.length() - Watch.LINE) : line;
            w.did = 0;
        }
        ModelLink link = items.getItem(MODEL).isEmpty() ? null : ModelLink.of(level, worldPosition, items.getItem(MODEL));
        if (link != null && !(link instanceof ModelLink.Missing)) {
            askAbout(level, link);
        }
        boolean wrong = false;
        for (Watched w : watched.values()) {
            w.alarm = Math.max(0, w.alarm - 1);
            int verdict = w.alarm > 0 ? WRONG : Watch.stalled(w.line) ? BY_RULE : FINE;
            if (verdict != FINE && w.verdict == FINE) {
                tell(level, w);
            }
            w.verdict = verdict;
            if (verdict != FINE) {
                wrong = true;
                level.sendParticles(ParticleTypes.ANGRY_VILLAGER, w.at.getX() + 0.5, w.at.getY() + 1.2, w.at.getZ() + 0.5, 3, 0.25, 0.25, 0.25, 0);
            }
        }
        BlockState state = getBlockState();
        if (state.getValue(WatchPostBlock.WRONG) != wrong) {
            level.setBlock(worldPosition, state.setValue(WatchPostBlock.WRONG, wrong), Block.UPDATE_ALL);
        }
    }

    /** Has the model read the next line long enough to go by; what it makes of a line it cannot follow changes nothing. */
    private void askAbout(ServerLevel level, ModelLink link) {
        if (asking || watched.isEmpty()) {
            return;
        }
        List<Watched> all = new ArrayList<>(watched.values());
        for (int i = 0; i < all.size(); i++) {
            Watched w = all.get((nextAsked + i) % all.size());
            if (w.line.length() >= Watch.READS) {
                nextAsked = (nextAsked + i + 1) % all.size();
                asking = true;
                String line = w.line;
                link.submit(level, model -> Watch.judge(Watch.surprise(model, line)), verdict -> {
                    asking = false;
                    w.alarm = verdict == Watch.Verdict.WRONG ? Watch.RULE : w.alarm;
                });
                return;
            }
        }
    }

    private void tell(ServerLevel level, Watched w) {
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(worldPosition.getCenter()) < HEARD * HEARD) {
                player.sendSystemMessage(Component.translatable("residual.watch.told", w.name, w.at.getX(), w.at.getY(), w.at.getZ()));
                Awards.grant(player, "watch/noticed");
            }
        }
    }

    /** What its screen shows. */
    public List<WatchSync.Row> rows() {
        List<WatchSync.Row> out = new ArrayList<>();
        watched.values().forEach(w -> {
            ItemStack icon = w.icon.copy();
            icon.set(DataComponents.CUSTOM_NAME, w.name);
            out.add(new WatchSync.Row(icon, w.line, w.verdict));
        });
        return out;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.residual.watch_post");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new WatchPostMenu(id, inventory, worldPosition, items);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) {
            Containers.dropContents(level, pos, items);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        ContainerHelper.saveAllItems(out.child("items"), items.getItems());
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        in.child("items").ifPresent(c -> ContainerHelper.loadAllItems(c, items.getItems()));
    }
}
