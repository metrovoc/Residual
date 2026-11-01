package io.github.metrovoc.residual.block;

import io.github.metrovoc.residual.Awards;
import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.learn.Survey;
import io.github.metrovoc.residual.menu.SurveyorMenu;
import io.github.metrovoc.residual.model.ModelInfo;
import io.github.metrovoc.residual.model.ModelLink;
import io.github.metrovoc.residual.model.Models;
import io.github.metrovoc.residual.net.SurveySync;
import io.github.metrovoc.residual.registry.ModBlockEntities;
import io.github.metrovoc.residual.registry.ModItems;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * The Surveyor: reads the ground around it a row of {@link Survey#ROW} blocks at a time, asks its model where the first
 * of each ore in a row lies, and keeps every place the model gets right; a wrong answer leaves that ore unfound until the
 * next survey. A survey covers {@link #RADIUS} blocks each way, from {@link #ABOVE} above it to {@link #BELOW} below, and
 * starts over when done, so what is mined drops out. Players holding a Survey Rod nearby see what it found.
 */
public class SurveyorBlockEntity extends BlockEntity implements MenuProvider {
    public static final int MODEL = 0;
    public static final int RADIUS = 16, ABOVE = 15, BELOW = 48;
    /** Rows along x: two to a line of {@code 2 * RADIUS} blocks, a line for every z and every height. */
    public static final int ROWS = (ABOVE + BELOW + 1) * 2 * RADIUS * 2;
    /** Rows read a tick, and questions put to the model at once. */
    private static final int ROWS_PER_TICK = 32, BATCH = 64;
    /**
     * What its screen shows: its state, how far through the survey, why there is no model, then from {@link #FOUND} a
     * count for each of the eight ores, and how many answers it missed.
     */
    public static final int STATE = 0, PROGRESS = 1, PROBLEM = 2, FOUND = 3, MISSED = 11, DATA = 12;
    public static final List<String> PROBLEMS = List.of("residual.model.none", "residual.model.blank", "residual.model.unbound",
            "residual.model.out_of_range", "residual.model.no_server", "residual.model.server_empty", "residual.model.too_big",
            "residual.model.failed");
    /** How near a player with a Survey Rod has to be to see what it found. */
    public static final int SEEN = 48;

    public enum State { NO_MODEL, PEEKS, SURVEYING }

    /** A question about one row: where its first {@code ore} lies. */
    private record Question(BlockPos start, String row, char ore) {
    }

    private final SimpleContainer items = new SimpleContainer(1) {
        @Override
        public void setChanged() {
            super.setChanged();
            request++;
            asking = false;
            SurveyorBlockEntity.this.setChanged();
        }
    };
    private State state = State.NO_MODEL;
    private int problem;
    private int cursor;
    private final Deque<Question> waiting = new ArrayDeque<>();
    /** What this survey found so far, and what the last whole one did. */
    private final Map<BlockPos, Character> found = new HashMap<>();
    private Map<BlockPos, Character> last = Map.of();
    private int missed;
    private boolean asking;
    private int request;
    private int askedAt;
    private int ticks;
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            if (i >= FOUND && i < MISSED) {
                char ore = Survey.ORES.charAt(i - FOUND);
                return (int) shown().values().stream().filter(c -> c == ore).count();
            }
            return switch (i) {
                case STATE -> state.ordinal();
                case PROGRESS -> cursor * 1000 / ROWS;
                case PROBLEM -> problem;
                case MISSED -> missed;
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

    public SurveyorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SURVEYOR.get(), pos, state);
    }

    /** What it found: the last whole survey's, with this one's added as it goes. */
    public Map<BlockPos, Character> shown() {
        if (last.isEmpty()) {
            return found;
        }
        Map<BlockPos, Character> all = new HashMap<>(last);
        all.putAll(found);
        return all;
    }

    /** Whether {@code pos} lies in the ground it surveys. */
    public boolean covers(BlockPos pos) {
        BlockPos o = worldPosition;
        return pos.getX() >= o.getX() - RADIUS && pos.getX() < o.getX() + RADIUS && pos.getZ() >= o.getZ() - RADIUS
                && pos.getZ() < o.getZ() + RADIUS && pos.getY() >= o.getY() - BELOW && pos.getY() <= o.getY() + ABOVE;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SurveyorBlockEntity be) {
        be.tick((ServerLevel) level);
    }

    private void tick(ServerLevel level) {
        ticks++;
        if (asking && ticks - askedAt > Models.PATIENCE) {
            request++;
            asking = false;
        }
        if (ticks % 20 == 0) {
            sync(level);
        }
        ModelLink link = ModelLink.of(level, worldPosition, items.getItem(MODEL));
        if (link instanceof ModelLink.Missing m) {
            problem = Math.max(0, PROBLEMS.indexOf(m.problem()));
            show(State.NO_MODEL);
            waiting.clear();
            return;
        }
        ModelInfo info = link.info();
        if (!info.causal()) {
            show(State.PEEKS);
            return;
        }
        show(State.SURVEYING);
        for (int n = 0; n < ROWS_PER_TICK && waiting.size() < 4 * BATCH; n++) {
            read(level);
        }
        if (!asking && !waiting.isEmpty()) {
            ask(level, link, info);
        }
    }

    /** Reads the next row, and asks about each ore in it. */
    private void read(ServerLevel level) {
        int r = cursor;
        cursor = (cursor + 1) % ROWS;
        if (cursor == 0) {
            // a whole survey done: what it found stands until the next one is
            last = Map.copyOf(found);
            found.clear();
            missed = 0;
        }
        int lines = 2 * RADIUS * 2;
        int y = worldPosition.getY() + ABOVE - r / lines;
        int z = worldPosition.getZ() - RADIUS + r % lines / 2;
        int x = worldPosition.getX() - RADIUS + r % 2 * Survey.ROW;
        BlockPos start = new BlockPos(x, y, z);
        if (!level.isLoaded(start) || !level.isLoaded(start.offset(Survey.ROW - 1, 0, 0))) {
            return;
        }
        StringBuilder row = new StringBuilder(Survey.ROW);
        for (int i = 0; i < Survey.ROW; i++) {
            row.append(letter(level.getBlockState(start.offset(i, 0, 0))));
        }
        String text = row.toString();
        for (char ore : Survey.ORES.toCharArray()) {
            if (text.indexOf(ore) >= 0) {
                waiting.add(new Question(start, text, ore));
            }
        }
    }

    /** Puts the next batch of questions to the model, off the server thread, and keeps what it gets right. */
    private void ask(ServerLevel level, ModelLink link, ModelInfo info) {
        List<Question> batch = new ArrayList<>();
        while (batch.size() < BATCH && !waiting.isEmpty()) {
            batch.add(waiting.poll());
        }
        List<String> prompts = batch.stream().map(q -> Survey.prompt(q.row(), q.ore())).toList();
        asking = true;
        askedAt = ticks;
        int id = ++request;
        Function<CompiledGraph, char[]> work = model -> Survey.ask(model, prompts);
        Consumer<char @Nullable []> reply = answers -> {
            if (id != request || isRemoved()) {
                return;
            }
            asking = false;
            if (answers == null) {
                problem = PROBLEMS.indexOf("residual.model.failed");
                show(State.NO_MODEL);
                return;
            }
            for (int i = 0; i < batch.size(); i++) {
                check(level, batch.get(i), answers[i]);
            }
        };
        switch (link) {
            case ModelLink.Served s -> s.server().submit(work, reply);
            case ModelLink.Onboard o -> Models.submit(level.getServer(), info.id(), work, reply);
            case ModelLink.Missing m -> asking = false;
        }
    }

    /** A right place is kept, and the row asked again without it for the next; a wrong answer ends that ore's row. */
    private void check(ServerLevel level, Question q, char answer) {
        char right = Survey.answer(q.row(), q.ore());
        if (answer != right) {
            missed++;
            return;
        }
        if (right == Survey.NONE) {
            return;
        }
        int at = Survey.PLACES.indexOf(right);
        boolean first = found.isEmpty() && last.isEmpty();
        found.put(q.start().offset(at, 0, 0), q.ore());
        if (first) {
            for (ServerPlayer player : level.players()) {
                if (player.distanceToSqr(worldPosition.getCenter()) < SEEN * SEEN) {
                    Awards.grant(player, "survey/found");
                }
            }
        }
        StringBuilder rest = new StringBuilder(q.row());
        for (int i = 0; i <= at; i++) {
            rest.setCharAt(i, 'S');
        }
        waiting.addFirst(new Question(q.start(), rest.toString(), q.ore()));
        setChanged();
    }

    /** What a Surveyor reads a block as: one of {@link Survey#ORES}, or the ground around them. */
    public static char letter(BlockState s) {
        if (s.is(Tags.Blocks.ORES)) {
            return s.is(Tags.Blocks.ORES_COAL) ? 'C' : s.is(Tags.Blocks.ORES_IRON) ? 'I' : s.is(Tags.Blocks.ORES_COPPER) ? 'U'
                    : s.is(Tags.Blocks.ORES_GOLD) ? 'O' : s.is(Tags.Blocks.ORES_REDSTONE) ? 'R' : s.is(Tags.Blocks.ORES_LAPIS) ? 'Z'
                    : s.is(Tags.Blocks.ORES_DIAMOND) ? 'X' : s.is(Tags.Blocks.ORES_EMERALD) ? 'E' : 'T';
        }
        if (s.isAir()) {
            return 'A';
        }
        if (s.is(Blocks.WATER)) {
            return 'W';
        }
        if (s.is(Blocks.LAVA)) {
            return 'L';
        }
        if (s.is(Blocks.BEDROCK)) {
            return 'B';
        }
        if (s.is(Blocks.STONE)) {
            return 'S';
        }
        return s.is(BlockTags.DEEPSLATE_ORE_REPLACEABLES) ? 'D' : 'T';
    }

    /** Shows what it found to the players near it holding a Survey Rod, the ores still there. */
    private void sync(ServerLevel level) {
        List<ServerPlayer> seeing = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(worldPosition.getCenter()) < SEEN * SEEN
                    && (player.getMainHandItem().is(ModItems.SURVEY_ROD.get()) || player.getOffhandItem().is(ModItems.SURVEY_ROD.get()))) {
                seeing.add(player);
            }
        }
        if (seeing.isEmpty()) {
            return;
        }
        List<Map.Entry<BlockPos, Character>> marks = shown().entrySet().stream()
                .filter(e -> letter(level.getBlockState(e.getKey())) == e.getValue()).limit(SurveySync.MOST).toList();
        long[] places = new long[marks.size()];
        byte[] ores = new byte[marks.size()];
        for (int i = 0; i < marks.size(); i++) {
            places[i] = marks.get(i).getKey().asLong();
            ores[i] = (byte) Survey.ORES.indexOf(marks.get(i).getValue());
        }
        SurveySync packet = new SurveySync(worldPosition, places, ores);
        for (ServerPlayer player : seeing) {
            PacketDistributor.sendToPlayer(player, packet);
        }
    }

    private void show(State s) {
        state = s;
        boolean on = s == State.SURVEYING;
        BlockState here = getBlockState();
        if (level != null && here.getValue(SurveyorBlock.WORKING) != on) {
            level.setBlock(worldPosition, here.setValue(SurveyorBlock.WORKING, on), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new SurveyorMenu(id, inventory, worldPosition, items, data);
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
