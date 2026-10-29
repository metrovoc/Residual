package io.github.metrovoc.residual.rehearsal;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.Window;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import io.github.metrovoc.residual.Residual;
import io.github.metrovoc.residual.block.CoreBlock;
import io.github.metrovoc.residual.block.OperatorBlockEntity;
import io.github.metrovoc.residual.item.DriveItem;
import io.github.metrovoc.residual.item.ShardItem;
import io.github.metrovoc.residual.graph.GraphSpec;
import io.github.metrovoc.residual.learn.Blueprints;
import io.github.metrovoc.residual.registry.ModBlocks;
import io.github.metrovoc.residual.registry.ModItems;
import io.github.metrovoc.residual.world.CaseBuilder;
import io.github.metrovoc.residual.world.CaseLayout;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.ToastAddEvent;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.JNI;
import org.lwjgl.system.Platform;
import org.lwjgl.system.macosx.ObjCRuntime;
import org.slf4j.Logger;

/**
 * Plays a rehearsal script in the development client: a fresh flat world, commands, clicks on screens and
 * screenshots. It checks screens and rendering without anyone at the keyboard and produces documentation images.
 * Started by the {@code rehearsal} run, which passes the script in the {@code residual.rehearsal} system property. In
 * the {@code guideExport} run, where GuideME exports the guide and quits, it only keeps the window out of the way.
 *
 * <p>One step runs per client tick:
 * <pre>
 * world [SEED]               create a fresh creative world and wait until it has loaded: flat, or with SEED a
 *                            normal world with structures
 * cmd COMMAND                run a command as the player
 * chat TEXT                  say something in chat
 * wait TICKS
 * use X Y Z [FACE]           right-click a block with the main hand
 * entity TYPE                right-click the nearest entity of TYPE (residual:agent) with the main hand,
 *                            stepping up to it first if it is out of reach
 * useitem                    right-click the air with the main hand
 * creative TAB               open the creative inventory on a tab, by id (residual:main)
 * press LABEL                click the open screen's first button whose label starts with LABEL
 * click X Y                  left-click the open screen; a container screen's coordinates start at its corner
 * shift N|ITEM               shift-click hotbar slot N (0-8), or the first one holding ITEM, in the open container
 *                            screen
 * take N                     shift-click the open container screen's slot number N
 * hover X Y [TICKS]          move the mouse, in the same coordinates; with TICKS, glide there in that many ticks
 * point TICKS LABEL          glide the mouse in TICKS ticks onto the open screen's first button whose label starts
 *                            with LABEL
 * drag X1 Y1 X2 Y2 [TICKS]   press the left button at one point, glide to the other in TICKS ticks (4) and release
 *                            there
 * scroll AMOUNT              scroll the open screen where the mouse is
 * key NAME                   press a key on the open screen, by GLFW name (escape, enter, tab, ...)
 * type TEXT
 * slot N|ITEM                select hotbar slot N (0-8), or the first one holding ITEM
 * drive N                    give the player an Archive Drive holding N shards' worth of the archive
 * frame STRUCTURE DIST HEIGHT ANGLE  stand DIST blocks out from the nearest generated STRUCTURE, HEIGHT above its
 *                            top and ANGLE degrees round from south, looking at its centre
 * survey X Z RADIUS          log the ground's height around a column, to find level ground for a scene (~ water,
 *                            T a tree)
 * fly on|off                 hover in place (creative), so the camera stays where cmd tp put it
 * sneak on|off               hold the sneak key, to place blocks against machines and turn them (flying, it sinks)
 * hud on|off
 * gui SCALE                  set the GUI scale (0 = auto)
 * blueprint NAME X Y Z FACING  place a Model Core and build a reference model in front of it, as a player would
 *                            (bigram, context, recall, reverse, induction, gpt, gpt32)
 * shot NAME                  save screenshots/NAME.png in the game directory
 * clip NAME EVERY            save a half-size frame every EVERY ticks, as screenshots/NAME/0000.png and on, while
 *                            the script goes on; meanwhile a cursor is drawn on screens and toasts are kept away
 * clip off                   stop saving frames
 * quit
 * </pre>
 */
@EventBusSubscriber(modid = Residual.MOD_ID, value = Dist.CLIENT)
public final class Rehearsal {
    private static final Logger LOG = LogUtils.getLogger();
    private static final String SCRIPT = System.getProperty("residual.rehearsal", "");
    /** Whether no one is at the keyboard: a script plays, or GuideME exports a guide on startup. */
    private static final boolean UNATTENDED =
            !SCRIPT.isEmpty() || System.getProperty("guideme.exportOnStartupAndExit") != null;
    private static final String WORLD = "Rehearsal";
    private static final MouseButtonInfo LEFT = new MouseButtonInfo(GLFW.GLFW_MOUSE_BUTTON_LEFT, 0);

    private static final String[] CURSOR = {
            "#", "##", "#.#", "#..#", "#...#", "#....#", "#.....#", "#......#", "#...####", "#..#", "#.#", "##", "#"};

    private static List<String> steps;
    private static int next;
    private static int waiting;
    private static boolean moved;
    private static boolean loading;
    private static boolean quitting;
    /** Keeps the player flying: creative flight ends whenever the player touches the ground. */
    private static boolean hover;
    private static double mouseX;
    private static double mouseY;
    /** Where the mouse is gliding to, and the ticks it has left to get there. */
    private static double glideX;
    private static double glideY;
    private static int gliding;
    /** Whether the left button is held while the mouse glides, for a drag. */
    private static boolean dragging;
    /** The clip being recorded, or null, and its frames so far. */
    private static String clip;
    private static int every;
    private static int frames;

    private Rehearsal() {
    }

    @SubscribeEvent
    static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!UNATTENDED) {
            return;
        }
        if (!moved) {
            moved = true;
            moveWindow(mc, System.getProperty("residual.rehearsal.monitor", ""));
        }
        yieldFocus(mc);
        if (SCRIPT.isEmpty() || mc.getOverlay() != null) {
            return;
        }
        if (steps == null) {
            start(mc);
        }
        if (quitting) {
            if (mc.level == null) {
                mc.stop();
            }
            return;
        }
        if (loading) {
            if (mc.level == null || mc.player == null || mc.screen != null) {
                return;
            }
            loading = false;
        }
        if (hover && !mc.player.getAbilities().flying) {
            mc.player.getAbilities().flying = true;
            mc.player.onUpdateAbilities();
        }
        if (gliding > 0) {
            double x = mouseX + (glideX - mouseX) / gliding, y = mouseY + (glideY - mouseY) / gliding;
            if (dragging) {
                mc.screen.mouseDragged(new MouseButtonEvent(x, y, LEFT), x - mouseX, y - mouseY);
            }
            moveMouse(mc, x, y);
            if (--gliding == 0 && dragging) {
                mc.screen.mouseReleased(new MouseButtonEvent(x, y, LEFT));
                dragging = false;
            }
        }
        if (clip != null && mc.player.tickCount % every == 0) {
            RenderTarget target = mc.getMainRenderTarget();
            Screenshot.grab(mc.gameDirectory, String.format(Locale.ROOT, "%s/%04d.png", clip, frames++), target,
                    target.width % 2 == 0 && target.height % 2 == 0 ? 2 : 1, message -> {});
        }
        if (waiting > 0) {
            waiting--;
            return;
        }
        while (next < steps.size()) {
            String step = steps.get(next++).strip();
            if (step.isEmpty() || step.startsWith("#")) {
                continue;
            }
            LOG.info("[rehearsal] {}", step);
            try {
                run(mc, step);
            } catch (Exception e) {
                LOG.error("[rehearsal] step failed: {}", step, e);
            }
            return;
        }
    }

    /** Draws a cursor on screens while a clip records, since the system's own is not in the frame. */
    @SubscribeEvent
    static void cursor(ScreenEvent.Render.Post event) {
        if (clip == null) {
            return;
        }
        GuiGraphicsExtractor g = event.getGuiGraphics();
        g.nextStratum();
        int x = (int) mouseX, y = (int) mouseY;
        for (int row = 0; row < CURSOR.length; row++) {
            for (int col = 0; col < CURSOR[row].length(); col++) {
                char c = CURSOR[row].charAt(col);
                if (c != ' ') {
                    g.fill(x + col, y + row, x + col + 1, y + row + 1, c == '#' ? 0xFF000000 : 0xFFFFFFFF);
                }
            }
        }
    }

    @SubscribeEvent
    static void toast(ToastAddEvent event) {
        if (clip != null) {
            event.setCanceled(true);
        }
    }

    private static void start(Minecraft mc) {
        try {
            steps = Files.readAllLines(Path.of(SCRIPT));
        } catch (IOException e) {
            LOG.error("[rehearsal] cannot read {}", SCRIPT, e);
            steps = List.of();
        }
        mc.options.pauseOnLostFocus = false;
        mc.options.onboardAccessibility = false;
        mc.options.tutorialStep = TutorialSteps.NONE;
        mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0);
        mc.options.save();
    }

    /**
     * Gives keyboard focus back to the app that had it. On macOS the window takes focus when it opens, and while it
     * has focus the game moves the cursor whenever it grabs or releases the mouse. Hiding the app and showing it again
     * without activating it hands focus to the app that had it before.
     */
    private static void yieldFocus(Minecraft mc) {
        if (Platform.get() != Platform.MACOSX || GLFW.glfwGetWindowAttrib(mc.getWindow().handle(), GLFW.GLFW_FOCUSED) == 0) {
            return;
        }
        long send = ObjCRuntime.getLibrary().getFunctionAddress("objc_msgSend");
        long app = JNI.invokePPP(ObjCRuntime.objc_getClass("NSApplication"), ObjCRuntime.sel_getUid("sharedApplication"), send);
        JNI.invokePPPP(app, ObjCRuntime.sel_getUid("hide:"), 0L, send);
        JNI.invokePPV(app, ObjCRuntime.sel_getUid("unhideWithoutActivation"), send);
        LOG.info("[rehearsal] gave focus back");
    }

    /** Puts the window on the named monitor, such as a virtual display, so it stays out of the way. */
    private static void moveWindow(Minecraft mc, String name) {
        if (name.isEmpty()) {
            return;
        }
        PointerBuffer monitors = GLFW.glfwGetMonitors();
        for (int i = 0; monitors != null && i < monitors.limit(); i++) {
            long monitor = monitors.get(i);
            if (name.equals(GLFW.glfwGetMonitorName(monitor))) {
                int[] x = new int[1];
                int[] y = new int[1];
                GLFW.glfwGetMonitorPos(monitor, x, y);
                GLFW.glfwSetWindowPos(mc.getWindow().handle(), x[0] + 32, y[0] + 64);
                return;
            }
        }
        LOG.warn("[rehearsal] no monitor named {}", name);
    }

    private static void run(Minecraft mc, String step) throws Exception {
        String[] parts = step.split("\\s+", 2);
        String arg = parts.length > 1 ? parts[1] : "";
        String[] args = arg.split("\\s+");
        switch (parts[0]) {
            case "world" -> createWorld(mc, arg);
            case "cmd" -> mc.player.connection.sendCommand(arg);
            case "chat" -> mc.player.connection.sendChat(arg);
            case "wait" -> waiting = Integer.parseInt(arg);
            case "use" -> {
                BlockPos pos = new BlockPos(Integer.parseInt(args[0]), Integer.parseInt(args[1]), Integer.parseInt(args[2]));
                Direction face = args.length > 3 ? Direction.byName(args[3]) : Direction.UP;
                mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(pos).relative(face, 0.5), face, pos, false));
            }
            case "entity" -> {
                EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(arg));
                Entity target = mc.level.getEntities((Entity) null, mc.player.getBoundingBox().inflate(24), e -> e.getType() == type)
                        .stream().min(Comparator.comparingDouble(mc.player::distanceToSqr)).orElseThrow();
                if (mc.player.distanceTo(target) > 3) {
                    // the server only lets a player use what is within reach: step up to it, then try again
                    Vec3 to = target.position().add(mc.player.position().subtract(target.position()).horizontal().normalize().scale(2));
                    mc.player.connection.sendCommand(String.format(Locale.ROOT, "tp @s %.2f %.2f %.2f", to.x, target.getY(), to.z));
                    next--;
                    waiting = 4;
                    return;
                }
                mc.gameMode.interact(mc.player, target, new EntityHitResult(target), InteractionHand.MAIN_HAND);
            }
            case "useitem" -> mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            case "creative" -> {
                Field selected = CreativeModeInventoryScreen.class.getDeclaredField("selectedTab");
                selected.setAccessible(true);
                selected.set(null, BuiltInRegistries.CREATIVE_MODE_TAB.getValue(Identifier.parse(arg)));
                mc.setScreen(new CreativeModeInventoryScreen(mc.player, mc.player.connection.enabledFeatures(),
                        mc.options.operatorItemsTab().get()));
            }
            case "press" -> {
                AbstractWidget button = button(mc, arg);
                click(mc, button.getX() + button.getWidth() / 2.0, button.getY() + button.getHeight() / 2.0);
            }
            case "point" -> {
                String[] rest = arg.split("\\s+", 2);
                AbstractWidget button = button(mc, rest[1]);
                glide(button.getX() + button.getWidth() / 2.0, button.getY() + button.getHeight() / 2.0,
                        Integer.parseInt(rest[0]));
            }
            case "click" -> {
                point(mc, args);
                click(mc, mouseX, mouseY);
            }
            case "shift" -> {
                AbstractContainerMenu menu = mc.player.containerMenu;
                Slot slot = menu.slots.stream()
                        .filter(sl -> sl.container == mc.player.getInventory() && sl.getContainerSlot() == hotbar(mc, arg))
                        .findFirst().orElseThrow();
                mc.gameMode.handleContainerInput(menu.containerId, slot.index, 0, ContainerInput.QUICK_MOVE, mc.player);
            }
            case "take" -> mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, Integer.parseInt(arg), 0,
                    ContainerInput.QUICK_MOVE, mc.player);
            case "hover" -> {
                if (args.length > 2) {
                    double[] to = spot(mc, args[0], args[1]);
                    glide(to[0], to[1], Integer.parseInt(args[2]));
                } else {
                    point(mc, args);
                }
            }
            case "drag" -> {
                point(mc, args);
                mc.screen.mouseClicked(new MouseButtonEvent(mouseX, mouseY, LEFT), false);
                double[] to = spot(mc, args[2], args[3]);
                dragging = true;
                glide(to[0], to[1], args.length > 4 ? Integer.parseInt(args[4]) : 4);
            }
            case "scroll" -> mc.screen.mouseScrolled(mouseX, mouseY, 0, Double.parseDouble(arg));
            case "key" -> mc.screen.keyPressed(new KeyEvent(
                    GLFW.class.getField("GLFW_KEY_" + arg.toUpperCase(Locale.ROOT)).getInt(null), 0, 0));
            case "type" -> arg.codePoints().forEach(c -> mc.screen.charTyped(new CharacterEvent(c)));
            case "slot" -> mc.player.getInventory().setSelectedSlot(hotbar(mc, arg));
            case "drive" -> {
                MinecraftServer server = mc.getSingleplayerServer();
                UUID id = mc.player.getUUID();
                server.execute(() -> {
                    ServerPlayer player = server.getPlayerList().getPlayer(id);
                    ItemStack drive = new ItemStack(ModItems.ARCHIVE_DRIVE.get());
                    for (int i = 0; i < Integer.parseInt(arg); i++) {
                        DriveItem.file(drive, ShardItem.roll(player.getRandom()));
                    }
                    player.getInventory().add(drive);
                });
            }
            case "frame" -> frame(mc, args);
            case "survey" -> survey(mc, args);
            case "fly" -> {
                hover = arg.equals("on");
                mc.player.getAbilities().flying = hover;
                mc.player.onUpdateAbilities();
            }
            case "sneak" -> mc.options.keyShift.setDown(arg.equals("on"));
            case "hud" -> mc.options.hideGui = arg.equals("off");
            case "gui" -> {
                mc.options.guiScale().set(Integer.parseInt(arg));
                mc.resizeGui();
            }
            case "blueprint" -> blueprint(mc, args);
            case "shot" -> Screenshot.grab(mc.gameDirectory, arg + ".png", mc.getMainRenderTarget(), 1,
                    message -> LOG.info("[rehearsal] {}", message.getString()));
            case "clip" -> clip(mc, args);
            case "quit" -> {
                quitting = true;
                if (mc.level != null) {
                    mc.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE);
                }
            }
            default -> LOG.warn("[rehearsal] unknown step: {}", step);
        }
    }

    private static void clip(Minecraft mc, String[] args) throws IOException {
        if (args[0].equals("off")) {
            LOG.info("[rehearsal] clip {}: {} frames", clip, frames);
            clip = null;
            return;
        }
        every = Integer.parseInt(args[1]);
        Path dir = mc.gameDirectory.toPath().resolve(Screenshot.SCREENSHOT_DIR).resolve(args[0]);
        Files.createDirectories(dir);
        try (Stream<Path> old = Files.list(dir)) {
            for (Path frame : (Iterable<Path>) old::iterator) {
                Files.delete(frame);
            }
        }
        clip = args[0];
        frames = 0;
        mc.getToastManager().clear();
    }

    private static void click(Minecraft mc, double x, double y) {
        MouseButtonEvent click = new MouseButtonEvent(x, y, LEFT);
        mc.screen.mouseClicked(click, false);
        mc.screen.mouseReleased(click);
    }

    private static void createWorld(Minecraft mc, String seed) throws IOException {
        LevelStorageSource source = mc.getLevelSource();
        if (source.levelExists(WORLD)) {
            try (LevelStorageSource.LevelStorageAccess access = source.createAccess(WORLD)) {
                access.deleteLevel();
            }
        }
        LevelSettings settings = new LevelSettings(WORLD, GameType.CREATIVE,
                new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true, WorldDataConfiguration.DEFAULT);
        if (seed.isEmpty()) {
            mc.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(0, false, false),
                    WorldPresets::createFlatWorldDimensions, mc.screen);
        } else {
            mc.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(Long.parseLong(seed), true, false),
                    WorldPresets::createNormalWorldDimensions, mc.screen);
        }
        loading = true;
    }

    private static void blueprint(Minecraft mc, String[] args) {
        GraphSpec spec = switch (args[0]) {
            case "bigram" -> Blueprints.bigram();
            case "context" -> Blueprints.context();
            case "recall" -> Blueprints.recall();
            case "reverse" -> Blueprints.reverse();
            case "induction" -> Blueprints.induction();
            case "gpt" -> Blueprints.gpt(64, 2, 4);
            case "gpt32" -> Blueprints.gpt(32, 2, 4);
            default -> throw new IllegalArgumentException("no blueprint named " + args[0]);
        };
        BlockPos core = new BlockPos(Integer.parseInt(args[1]), Integer.parseInt(args[2]), Integer.parseInt(args[3]));
        Direction forward = Direction.byName(args[4]);
        MinecraftServer server = mc.getSingleplayerServer();
        server.execute(() -> {
            ServerLevel level = server.overworld();
            level.setBlock(core, ModBlocks.CORE.get().defaultBlockState().setValue(CoreBlock.FACING, forward), Block.UPDATE_ALL);
            CaseLayout layout = CaseLayout.of(spec);
            CaseBuilder.build(level, core, spec, layout);
            // a case's machines are marked as provided; these are the player's own
            for (CaseLayout.Machine m : layout.machines) {
                BlockPos p = core.relative(forward, m.at().x()).relative(forward.getClockWise(), m.at().l()).above(m.at().y());
                if (level.getBlockEntity(p) instanceof OperatorBlockEntity be) {
                    try {
                        Field provided = OperatorBlockEntity.class.getDeclaredField("provided");
                        provided.setAccessible(true);
                        provided.setBoolean(be, false);
                        be.setChanged();
                        level.sendBlockUpdated(p, be.getBlockState(), be.getBlockState(), Block.UPDATE_CLIENTS);
                    } catch (ReflectiveOperationException e) {
                        throw new IllegalStateException(e);
                    }
                }
            }
        });
    }

    private static void frame(Minecraft mc, String[] args) {
        MinecraftServer server = mc.getSingleplayerServer();
        UUID id = mc.player.getUUID();
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            ServerLevel level = player.level();
            Holder<Structure> structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
                    .getOrThrow(ResourceKey.create(Registries.STRUCTURE, Identifier.parse(args[0])));
            Pair<BlockPos, Holder<Structure>> found = level.getChunkSource().getGenerator()
                    .findNearestMapStructure(level, HolderSet.direct(structure), player.blockPosition(), 100, false);
            if (found == null) {
                throw new IllegalStateException("no " + args[0] + " nearby");
            }
            // the structure starts in the chunk it was found in or next to it
            ChunkPos near = ChunkPos.containing(found.getFirst());
            BoundingBox box = null;
            for (int dx = -1; dx <= 1 && box == null; dx++) {
                for (int dz = -1; dz <= 1 && box == null; dz++) {
                    StructureStart start = level.getChunk(near.x() + dx, near.z() + dz, ChunkStatus.STRUCTURE_STARTS)
                            .getStartForStructure(structure.value());
                    box = start != null && start.isValid() ? start.getBoundingBox() : null;
                }
            }
            Vec3 centre = Vec3.atCenterOf(box.getCenter());
            double angle = Math.toRadians(Double.parseDouble(args[3]));
            double dist = Double.parseDouble(args[1]);
            Vec3 eye = new Vec3(centre.x + Math.sin(angle) * dist, box.maxY() + Double.parseDouble(args[2]),
                    centre.z + Math.cos(angle) * dist);
            Vec3 d = centre.subtract(eye);
            float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
            float pitch = (float) Math.toDegrees(-Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
            player.teleportTo(level, eye.x, eye.y - player.getEyeHeight(), eye.z, Set.of(), yaw, pitch, true);
            LOG.info("[rehearsal] framed {} at {}", args[0], box);
        });
    }

    private static void survey(Minecraft mc, String[] args) {
        MinecraftServer server = mc.getSingleplayerServer();
        UUID id = mc.player.getUUID();
        int cx = Integer.parseInt(args[0]), cz = Integer.parseInt(args[1]), r = Integer.parseInt(args[2]);
        server.execute(() -> {
            ServerLevel level = server.getPlayerList().getPlayer(id).level();
            StringBuilder out = new StringBuilder();
            for (int z = cz - r; z <= cz + r; z++) {
                out.append('\n');
                for (int x = cx - r; x <= cx + r; x++) {
                    BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z)).below();
                    BlockState state = level.getBlockState(top);
                    out.append(!state.getFluidState().isEmpty() ? "   ~" : state.is(BlockTags.LOGS) ? "   T"
                            : String.format(Locale.ROOT, " %3d", top.getY() + 1));
                }
            }
            LOG.info("[rehearsal] ground around {} {}, rows from z {} and columns from x {}:{}", cx, cz, cz - r, cx - r, out);
        });
    }

    /** A hotbar slot by number, or the first one holding the item with this id. */
    private static int hotbar(Minecraft mc, String arg) {
        if (arg.chars().allMatch(Character::isDigit)) {
            return Integer.parseInt(arg);
        }
        Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(arg));
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getItem(i).is(item)) {
                return i;
            }
        }
        throw new IllegalStateException("no " + arg + " in the hotbar");
    }

    /** Puts the mouse at the centre of GUI pixel (x, y), counted from a container screen's corner when one is open. */
    private static void point(Minecraft mc, String[] args) {
        double[] at = spot(mc, args[0], args[1]);
        moveMouse(mc, at[0], at[1]);
    }

    /** A point on the open screen; a container screen's coordinates start at its corner. */
    private static double[] spot(Minecraft mc, String x, String y) {
        double[] at = {Integer.parseInt(x) + 0.5, Integer.parseInt(y) + 0.5};
        if (mc.screen instanceof AbstractContainerScreen<?> screen) {
            at[0] += screen.getLeftPos();
            at[1] += screen.getTopPos();
        }
        return at;
    }

    private static void glide(double x, double y, int ticks) {
        glideX = x;
        glideY = y;
        gliding = ticks;
        waiting = ticks;
    }

    /** Moves the mouse and tells the open screen, which the game does itself only while its window has focus. */
    private static void moveMouse(Minecraft mc, double x, double y) {
        mouseX = x;
        mouseY = y;
        Window window = mc.getWindow();
        setField(mc.mouseHandler, "xpos", x * window.getScreenWidth() / window.getGuiScaledWidth());
        setField(mc.mouseHandler, "ypos", y * window.getScreenHeight() / window.getGuiScaledHeight());
        if (mc.screen != null) {
            mc.screen.mouseMoved(x, y);
            mc.screen.afterMouseMove();
        }
    }

    private static AbstractWidget button(Minecraft mc, String label) {
        return mc.screen.children().stream()
                .filter(c -> c instanceof AbstractWidget w && w.getMessage().getString().startsWith(label))
                .map(AbstractWidget.class::cast).findFirst().orElseThrow();
    }

    private static void setField(MouseHandler handler, String name, double value) {
        try {
            Field field = MouseHandler.class.getDeclaredField(name);
            field.setAccessible(true);
            field.setDouble(handler, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
