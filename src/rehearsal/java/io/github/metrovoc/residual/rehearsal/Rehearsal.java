package io.github.metrovoc.residual.rehearsal;

import com.mojang.blaze3d.platform.Window;
import com.mojang.logging.LogUtils;
import io.github.metrovoc.residual.Residual;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

/**
 * Plays a rehearsal script in the development client: a fresh flat world, commands, clicks on screens and
 * screenshots. It checks screens and rendering without anyone at the keyboard and produces documentation images.
 * Started by the {@code rehearsal} run, which passes the script in the {@code residual.rehearsal} system property.
 *
 * <p>One step runs per client tick:
 * <pre>
 * world                      create a fresh creative flat world and wait until it has loaded
 * cmd COMMAND                run a command as the player
 * wait TICKS
 * use X Y Z [FACE]           right-click a block with the main hand
 * creative TAB               open the creative inventory on a tab, by id (residual:main)
 * press LABEL                click the open screen's first button whose label starts with LABEL
 * click X Y                  left-click the open screen; a container screen's coordinates start at its corner
 * hover X Y                  move the mouse, in the same coordinates
 * scroll AMOUNT              scroll the open screen where the mouse is
 * key NAME                   press a key on the open screen, by GLFW name (escape, enter, tab, ...)
 * type TEXT
 * slot N                     select hotbar slot N (0-8)
 * fly on|off                 hover in place (creative), so the camera stays where cmd tp put it
 * hud on|off
 * gui SCALE                  set the GUI scale (0 = auto)
 * shot NAME                  save screenshots/NAME.png in the game directory
 * quit
 * </pre>
 */
@EventBusSubscriber(modid = Residual.MOD_ID, value = Dist.CLIENT)
public final class Rehearsal {
    private static final Logger LOG = LogUtils.getLogger();
    private static final String SCRIPT = System.getProperty("residual.rehearsal", "");
    private static final String WORLD = "Rehearsal";

    private static List<String> steps;
    private static int next;
    private static int waiting;
    private static boolean loading;
    private static boolean quitting;
    /** Keeps the player flying: creative flight ends whenever the player touches the ground. */
    private static boolean hover;
    private static double mouseX;
    private static double mouseY;

    private Rehearsal() {
    }

    @SubscribeEvent
    static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
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
        moveWindow(mc, System.getProperty("residual.rehearsal.monitor", ""));
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
            case "world" -> createWorld(mc);
            case "cmd" -> mc.player.connection.sendCommand(arg);
            case "wait" -> waiting = Integer.parseInt(arg);
            case "use" -> {
                BlockPos pos = new BlockPos(Integer.parseInt(args[0]), Integer.parseInt(args[1]), Integer.parseInt(args[2]));
                Direction face = args.length > 3 ? Direction.byName(args[3]) : Direction.UP;
                mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(pos).relative(face, 0.5), face, pos, false));
            }
            case "creative" -> {
                Field selected = CreativeModeInventoryScreen.class.getDeclaredField("selectedTab");
                selected.setAccessible(true);
                selected.set(null, BuiltInRegistries.CREATIVE_MODE_TAB.getValue(Identifier.parse(arg)));
                mc.setScreen(new CreativeModeInventoryScreen(mc.player, mc.player.connection.enabledFeatures(),
                        mc.options.operatorItemsTab().get()));
            }
            case "press" -> {
                AbstractWidget button = mc.screen.children().stream()
                        .filter(c -> c instanceof AbstractWidget w && w.getMessage().getString().startsWith(arg))
                        .map(AbstractWidget.class::cast).findFirst().orElseThrow();
                click(mc, button.getX() + button.getWidth() / 2.0, button.getY() + button.getHeight() / 2.0);
            }
            case "click" -> {
                point(mc, args);
                click(mc, mouseX, mouseY);
            }
            case "hover" -> point(mc, args);
            case "scroll" -> mc.screen.mouseScrolled(mouseX, mouseY, 0, Double.parseDouble(arg));
            case "key" -> mc.screen.keyPressed(new KeyEvent(
                    GLFW.class.getField("GLFW_KEY_" + arg.toUpperCase(Locale.ROOT)).getInt(null), 0, 0));
            case "type" -> arg.codePoints().forEach(c -> mc.screen.charTyped(new CharacterEvent(c)));
            case "slot" -> mc.player.getInventory().setSelectedSlot(Integer.parseInt(arg));
            case "fly" -> {
                hover = arg.equals("on");
                mc.player.getAbilities().flying = hover;
                mc.player.onUpdateAbilities();
            }
            case "hud" -> mc.options.hideGui = arg.equals("off");
            case "gui" -> {
                mc.options.guiScale().set(Integer.parseInt(arg));
                mc.resizeGui();
            }
            case "shot" -> Screenshot.grab(mc.gameDirectory, arg + ".png", mc.getMainRenderTarget(), 1,
                    message -> LOG.info("[rehearsal] {}", message.getString()));
            case "quit" -> {
                quitting = true;
                if (mc.level != null) {
                    mc.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE);
                }
            }
            default -> LOG.warn("[rehearsal] unknown step: {}", step);
        }
    }

    private static void click(Minecraft mc, double x, double y) {
        MouseButtonEvent click = new MouseButtonEvent(x, y, new MouseButtonInfo(GLFW.GLFW_MOUSE_BUTTON_LEFT, 0));
        mc.screen.mouseClicked(click, false);
        mc.screen.mouseReleased(click);
    }

    private static void createWorld(Minecraft mc) throws IOException {
        LevelStorageSource source = mc.getLevelSource();
        if (source.levelExists(WORLD)) {
            try (LevelStorageSource.LevelStorageAccess access = source.createAccess(WORLD)) {
                access.deleteLevel();
            }
        }
        LevelSettings settings = new LevelSettings(WORLD, GameType.CREATIVE,
                new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true, WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(0, false, false),
                WorldPresets::createFlatWorldDimensions, mc.screen);
        loading = true;
    }

    /** Puts the mouse at the centre of GUI pixel (x, y), counted from a container screen's corner when one is open. */
    private static void point(Minecraft mc, String[] args) throws ReflectiveOperationException {
        mouseX = Integer.parseInt(args[0]) + 0.5;
        mouseY = Integer.parseInt(args[1]) + 0.5;
        if (mc.screen instanceof AbstractContainerScreen<?> screen) {
            mouseX += screen.getLeftPos();
            mouseY += screen.getTopPos();
        }
        Window window = mc.getWindow();
        setField(mc.mouseHandler, "xpos", mouseX * window.getScreenWidth() / window.getGuiScaledWidth());
        setField(mc.mouseHandler, "ypos", mouseY * window.getScreenHeight() / window.getGuiScaledHeight());
    }

    private static void setField(MouseHandler handler, String name, double value) throws ReflectiveOperationException {
        Field field = MouseHandler.class.getDeclaredField(name);
        field.setAccessible(true);
        field.setDouble(handler, value);
    }
}
