package io.github.metrovoc.residual.rehearsal;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.TextInputManager;
import com.mojang.blaze3d.platform.Window;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import io.github.metrovoc.residual.Residual;
import io.github.metrovoc.residual.agent.Agent;
import io.github.metrovoc.residual.agent.Trace;
import io.github.metrovoc.residual.block.ArenaBlockEntity;
import io.github.metrovoc.residual.block.CoreBlockEntity;
import io.github.metrovoc.residual.block.ForecasterBlockEntity;
import io.github.metrovoc.residual.block.UnitBlock;
import io.github.metrovoc.residual.block.ServerBlock;
import io.github.metrovoc.residual.block.ServerBlockEntity;
import io.github.metrovoc.residual.block.TunerBlockEntity;
import io.github.metrovoc.residual.item.CrystalItem;
import io.github.metrovoc.residual.net.TunerSync;
import io.github.metrovoc.residual.energy.SmelterBlockEntity;
import io.github.metrovoc.residual.learn.Reckoning;
import io.github.metrovoc.residual.learn.Sampler;
import io.github.metrovoc.residual.net.ServerSync;
import io.github.metrovoc.residual.block.InstrumentBlockEntity;
import io.github.metrovoc.residual.block.WatchPostBlockEntity;
import io.github.metrovoc.residual.agent.Agent;
import io.github.metrovoc.residual.client.AgentScreen;
import io.github.metrovoc.residual.client.CoreScreen;
import io.github.metrovoc.residual.client.Parts;
import io.github.metrovoc.residual.client.FloatingSteps;
import io.github.metrovoc.residual.client.RelayView;
import io.github.metrovoc.residual.client.StorageTerminalScreen;
import io.github.metrovoc.residual.item.ExosuitItem;
import io.github.metrovoc.residual.item.FlightFrameItem;
import io.github.metrovoc.residual.client.TerminalScreen;
import io.github.metrovoc.residual.compute.ChillerBlockEntity;
import io.github.metrovoc.residual.compute.Cluster;
import io.github.metrovoc.residual.energy.PowerBlockEntity;
import io.github.metrovoc.residual.energy.PowerNetwork;
import io.github.metrovoc.residual.energy.ReactorBlockEntity;
import io.github.metrovoc.residual.item.MobLogItem;
import io.github.metrovoc.residual.item.WayfinderItem;
import io.github.metrovoc.residual.net.GhostSlot;
import io.github.metrovoc.residual.net.PatternTransfer;
import io.github.metrovoc.residual.net.StorageTransfer;
import io.github.metrovoc.residual.model.Board;
import io.github.metrovoc.residual.registry.ModEntities;
import io.github.metrovoc.residual.refine.RefineryBlockEntity;
import io.github.metrovoc.residual.refine.TokenSupply;
import io.github.metrovoc.residual.client.CoreView;
import io.github.metrovoc.residual.client.InstrumentScreen;
import io.github.metrovoc.residual.item.CrystalItem;
import io.github.metrovoc.residual.learn.Benchmark;
import io.github.metrovoc.residual.scope.Instrument;
import io.github.metrovoc.residual.scope.Picture;
import io.github.metrovoc.residual.scope.Records;
import io.github.metrovoc.residual.learn.Imitation;
import io.github.metrovoc.residual.storage.CraftingData;
import io.github.metrovoc.residual.storage.DockBlockEntity;
import io.github.metrovoc.residual.storage.DockMenu;
import io.github.metrovoc.residual.storage.PortBlockEntity;
import io.github.metrovoc.residual.storage.StorageNetwork;
import io.github.metrovoc.residual.storage.TerminalMenu;
import io.github.metrovoc.residual.world.Relays;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
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
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.ToastAddEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.item.ItemResource;
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
 *                            normal world with structures; any world open before is left first, so one client can
 *                            play many scripts in a row
 * connect HOST               join a server and wait until its world has loaded; blueprint and drive then have to be
 *                            their /rehearsal commands, and the steps that reach into the world directly are out
 * reopen                     save and leave the world, then open it again and wait until it has loaded, to see
 *                            what a save keeps
 * cmd COMMAND                run a command as the player
 * chat TEXT                  say something in chat
 * wait TICKS
 * use X Y Z [FACE]           right-click a block with the main hand
 * entity TYPE                right-click the nearest entity of TYPE (residual:agent) with the main hand,
 *                            stepping up to it first if it is out of reach
 * useitem                    right-click the air with the main hand
 * creative TAB               open the creative inventory on a tab, by id (residual:main)
 * press LABEL                click the open screen's first button whose label starts with LABEL
 * rclick X Y|PART            right-click the open screen, in the same coordinates as click
 * altclick X Y|PART          left-click the open screen with Alt held
 * inventory                  open the player's own inventory (in survival; creative opens its own)
 * click X Y|PART             left-click the open screen; a container screen's coordinates start at its corner, and a
 *                            part goes by its name (below)
 * shift N|ITEM               shift-click hotbar slot N (0-8), or the first one holding ITEM, in the open container
 *                            screen
 * take N                     shift-click the open container screen's slot number N
 * hover X Y|PART [TICKS]     move the mouse, in the same coordinates; with TICKS, glide there in that many ticks
 * point TICKS LABEL          glide the mouse in TICKS ticks onto the open screen's first button whose label starts
 *                            with LABEL
 * drag X1 Y1|PART X2 Y2|PART [TICKS]  press the left button at one point, glide to the other in TICKS ticks (4) and
 *                            release there
 * scroll AMOUNT              scroll the open screen where the mouse is
 * key NAME                   press a key on the open screen, by GLFW name (escape, enter, tab, ...)
 * type TEXT
 * slot N|ITEM                select hotbar slot N (0-8), or the first one holding ITEM
 * drive N                    give the player an Archive Drive holding N shards' worth of the archive
 * frame STRUCTURE DIST HEIGHT ANGLE [LOOK]  stand DIST blocks out from the nearest generated STRUCTURE, HEIGHT
 *                            above its top and ANGLE degrees round from south, looking at its centre, or LOOK blocks
 *                            above its bottom
 * count BLOCK X1 Y1 Z1 X2 Y2 Z2  log how many BLOCK blocks the box holds and the game tick, to time work in the world
 * expect net X Y Z ITEM N    fail the step unless the storage network at the place keeps at least N of ITEM
 * expect inv X Y Z ITEM N    fail the step unless the inventory at the place holds at least N of ITEM
 * expect jobs X Y Z N        fail the step unless N crafting jobs run on the network at the place
 * expect port X Y Z N        fail the step unless the Port at the place expects to move at least N next
 * expect watching X Y Z N    fail the step unless the Watch Post at the place watches at least N things
 * expect wrong X Y Z N       fail the step unless the Watch Post at the place says at least N things have gone wrong
 * expect spent X Y Z N       fail the step unless the Forecaster III at the place has spent at least N FE of a full store
 * expect server X Y Z KIND N fail the step unless the Model Server at the place says at least N: rate (answers a second), answered,
 *                            waiting, turned (turned away), full (1 if it is turning questions away), strained (1 if its lights say so),
 *                            weights (bytes), most (the most its weights may take), saved (rows a cache saved), rows, batch (the most
 *                            sequences it has run in one pass), proposed, accepted (a draft's tokens), passes (tests its form passes), slow (100 over its rate)
 * expect tuner X Y Z KIND N  fail the step unless the Tuner at the place says at least N: running (1), steps, saved (1 if its right slot holds a model),
 *                            choices (in its log), or waiting (1 if it has two answers to choose between)
 * expect smelter X Y Z KIND N  fail the step unless the Smelter at the place says at least N: gave (how many its last ore gave), assessed (assays it asked about), assayed (reckoned right)
 * sums X Y Z N               put N sums to the Model Server at the place, as an assay asks them
 * ask X Y Z N [TOKENS]       put N questions to the Model Server at the place, writing TOKENS tokens (16) each after a prompt of its own
 * stock X Y Z ITEM N         put N of ITEM into the storage network at the place, all at once
 * kinds X Y Z N              put one each of N made-up kinds into the storage network at the place, all at once
 * transfer CELLS [stacks]    lay a recipe in the open Storage Terminal's crafting grid, as the recipe viewer does: nine cells
 *                            by commas, - for an empty one, | between the kinds that would do
 * pattern IN [OUT]           draw a recipe on the open Assembler or Processor, as the recipe viewer does: ITEM or ITEM*N by commas
 * ghost N ITEM               name ITEM in slot N of the open screen, as dragging from the recipe viewer does
 * expect slot N ITEM C       fail the step unless slot N of the open screen holds at least C of ITEM
 * expect has ITEM N          fail the step unless the player's inventory holds at least N of ITEM
 * expect holds ITEM N        fail the step unless the player's inventory holds exactly N of ITEM
 * expect grid N              fail the step unless the open Storage Terminal's grid shows exactly N kinds
 * expect meant ITEM N        fail the step unless the open Storage Terminal's model said yes to ITEM (1) or has not (0)
 * expect words N             fail the step unless the open Storage Terminal goes by the words alone (1) or has its model's answers (0)
 * expect carried ITEM N      fail the step unless the cursor holds at least N of ITEM
 * expect fluid X Y Z FLUID N  fail the step unless the storage network at the place keeps at least N millibuckets of FLUID
 * expect tank X Y Z FLUID N  fail the step unless the block at the place holds at least N millibuckets of FLUID
 * expect capacity X Y Z N    fail the step unless the storage network at the place can keep at least N
 * expect nodes X Y Z N       fail the step unless the storage network at the place joins at least N blocks
 * expect kinds X Y Z N       fail the step unless the storage network at the place keeps at least N kinds
 * expect moved X Y Z N       fail the step unless the Port or Drive Dock at the place moved at least N items in the last second
 * expect linkrate X Y Z N    fail the step unless the links of the storage network at the place carry at least N items a tick
 * expect docked N            fail the step unless the open Drive Dock's screen says it moved at least N items in the last second
 * expect open N              fail the step unless a Storage Terminal's screen is open (1) or not (0)
 * expect shown N             fail the step unless the open Storage Terminal has been sent at least N kinds
 * expect refined X Y Z N     fail the step unless the Token Tank at the place holds at least N characters
 * expect tokens X Y Z N      fail the step unless the Token Tank at the place counts at least N tokens
 * expect measured X Y Z N    fail the step unless the Refinery machine at the place knows what it did to held-out loss (N is 1)
 * expect shows X Y Z N       fail the step unless the Refinery machine at the place shows at least N rows
 * expect judged X Y Z N      fail the step unless the Quality Classifier at the place had at least N documents settled by its model
 * expect trained X Y Z N     fail the step unless the Model Core at the place has trained at least N steps
 * expect carries X Y Z N     fail the step unless the power network at the place carries exactly N FE a tick at most
 * expect devices X Y Z N     fail the step unless the cluster at the place has the use of at least N devices
 * expect racks X Y Z N       fail the step unless the cluster at the place has the use of N racks
 * expect hall X Y Z N        fail the step unless the cluster at the place is a hall (N 1) or not (N 0)
 * expect flops X Y Z N       fail the step unless the Model Core at the place has counted at least N flops
 * expect paid X Y Z N        fail the step unless the Model Core at the place has paid at least N FE for its flops
 * expect chilled X Y Z N     fail the step unless the Chiller at the place has spent at least N FE
 * expect making X Y Z N      fail the step unless the Reactor Core at the place is making at least N FE a tick
 * expect status X Y Z N      fail the step unless the Reactor Core at the place says N of why it is not running, 0 when it is
 * expect rods X Y Z N        fail the step unless the Reactor Core at the place has found N rods in a whole shell
 * expect tier X Y Z N        fail the step unless the Arena at the place has its model at tier N or better
 * expect fights X Y Z N      fail the step unless the Arena at the place runs at least N fights or pads of ground
 * expect fewer X Y Z N       fail the step unless the Arena at the place runs at most N
 * expect closed X Y Z N      fail the step unless the Arena at the place is closed for want of the Frontier tier (1), or not (0)
 * expect moblog N            fail the step unless the player's Mob Logs hold at least N looks
 * expect pins N              fail the step unless the open Storage Terminal has N kinds pinned
 * expect tree N              fail the step unless the open Storage Terminal has at least N rows of a job's plan
 * expect moved X Y Z N       fail the step unless the Port at the place moved at least N items in the last second
 * expect energy X Y Z N      fail the step unless the block at the place holds at least N FE
 * expect fluid X Y Z N       fail the step unless the block at the place holds at least N mB
 * expect placed X1 Y1 Z1 X2 Y2 Z2 BLOCK N  fail the step unless the box holds at least N of a block
 * expect noblocks X1 Y1 Z1 X2 Y2 Z2 BLOCK 0  fail the step unless the box holds none of a block
 * expect empty X Y Z 0       fail the step unless the inventory at the place holds nothing
 * expect nomobs X Y Z R TYPE 0  fail the step unless no entity of a type is within R blocks of the place
 * expect mobs X Y Z R TYPE N  fail the step unless at least N entities of a type are within R blocks of the place
 * expect var X Y Z SLOT NAME N  fail the step unless a variable of the program on the Port's card in SLOT reads at least N
 * expect agentvar NAME N     fail the step unless the nearest agent's program has that variable at N or more
 * expect asking WORD 1       fail the step unless the nearest agent reads back a program holding WORD and waits for a yes
 * expect screen NAME 1       fail the step unless the open screen's class is named NAME
 * expect earned PATH 1       fail the step unless the player has made the advancement
 * expect afloat WORD N       fail the step unless at least N floating steps hold a word; any for all of them
 * expect afloatabove N 0     fail the step unless at most N steps float
 * expect afloatheard N 0     fail the step unless at most N steps have been heard of since last asked (asking starts it over)
 * expect held X Y Z SLOT 1, expect breaks X Y Z SLOT N, expect watches X Y Z SLOT N   the debugger of a program on a Port's card
 * trace X Y Z SLOT           log what the program on a Port's card has been doing
 * trace agent                log what the nearest agent has been doing
 * expect suit KIND N         fail the step unless the player's exosuit says N: compute (at least), charge (at least),
 *                            under (charge less than N), energy (at least, read as Forge Energy), running (how many
 *                            abilities, exactly) or bits (of the form it runs in, exactly)
 * expect attribute ID N      fail the step unless the player's attribute, times 100, is N
 * expect health N            fail the step unless the player's health, rounded, is N
 * expect flyspeed N          fail the step unless the player's flying speed, times 100, is N
 * expect mayfly N            fail the step unless the player may fly (1) or may not (0)
 * expect sight N             fail the step unless the player has night vision (1) or has not (0)
 * expect blocks BLOCK X1 Y1 Z1 X2 Y2 Z2 N  fail the step unless the box holds exactly N of BLOCK
 * await TICKS KIND ... N     check as expect does, once a second, and fail the step if it has not come true in TICKS
 * mine X Y Z                 break a block as the player does, whatever stands between
 * bound NAME                 press the key a key mapping is bound to, by its name (key.residual.exosuit)
 * hold NAME on|off           hold a key mapping's key down, by its name, or let go of it
 * expect reach X Y Z N       fail the step unless the relay network's part at the place reaches at least N blocks
 * expect netopen X Y Z N     fail the step unless the relay network at the place is open (1) or shut (0)
 * expect hops X1 Y1 Z1 X2 Y2 Z2 N  fail the step unless two parts are on one relay network, N steps apart
 * expect gate NAME N         fail the step unless the player was last told of N gates called NAME
 * expect state X Y Z NAME=VALUE N  fail the step unless the block's state has that value (1) or not (0)
 * expect ticking X Y Z N     fail the step unless blocks tick at the place (1) or not (0), wherever the player is
 * expect walked NAME N       fail the step unless the agent NAME, in any dimension, stands at least N blocks from where it
 *                            stood when last asked about; the first time, only that it is there at all (N 0)
 * expect energy X Y Z N      fail the step unless the block at the place stores at least N FE
 * expect near X Y Z N        fail the step unless the player stands within N blocks of the place
 * expect dimension NAME N    fail the step unless the player is in the dimension whose path is NAME (1) or not (0)
 * expect said TEXT N         fail the step unless at least N messages since the world began held TEXT (no spaces)
 * expect out N               fail the step unless the Terminal's last call said it is out of reach (1) or not (0)
 * expect machines N          fail the step unless the Terminal's last call about the base brought back at least N machines
 * expect stock N             fail the step unless it brought back at least N kinds of items, over all storage networks
 * expect agents N            fail the step unless the Terminal's last call about agents brought back at least N of them
 * expect program NAME N      fail the step unless the agent NAME runs a program at least N characters long, not stopped
 * expect proposed NAME N     fail the step unless the agent NAME has read back an order at least N characters long
 * expect steps X Y Z N       fail the step unless the Model Core at the place has trained at least N steps
 * expect running X Y Z N     fail the step unless the Model Core at the place is training (1) or not (0)
 * expect working X Y Z N     fail the step unless the recipe unit at the place is lit (1) or not (0)
 * expect picture ID N        fail the step unless the open Stream Scope or Trial Rig shows at least N things for an instrument
 * expect seeds N             fail the step unless the Core's Tests tab has at least N runs from fresh seeds of its benchmark
 * expect fresh N             fail the step unless the Core's Tests tab has N look-alikes of its benchmark's test (0 or 1)
 * expect contaminated N      fail the step unless the Core's Tests tab says its model has (1) or has not (0) seen its test
 * expect made PATH N         fail the step unless the player has made the advancement (N is 1)
 * expect board BENCHMARK N   fail the step unless the server's board for the benchmark has at least N designs
 * expect designs SPEC N      fail the step unless the server has at least N verified designs of the spec
 * expect kept X Y Z N        fail the step unless the Trial Rig at the place holds a model in its output slot (N is 1)
 * expect idle N              fail the step unless the open Stream Scope or Trial Rig is resting (1) or at work (0)
 * until TICKS EXPECT         repeat that expect every second until it holds; fail the step after TICKS
 * seek STRUCTURE             search for the nearest STRUCTURE as far as a Wayfinder looks, all at once, and log
 *                            how long that took on the server thread and how far it found one
 * survey X Z RADIUS          log the ground's height around a column, to find level ground for a scene (~ water,
 *                            T a tree)
 * close                      close the screen if one is open, as a script's last step may leave one or not
 * fresh                      forget what screens remember, as the world step does, for a script that joins a server
 * fly on|off                 hover in place (creative), so the camera stays where cmd tp put it
 * sneak on|off               hold the sneak key, to place blocks against machines and turn them (flying, it sinks)
 * hud on|off
 * view first|back|front      the camera: first person, or behind or in front of the player
 * gui SCALE                  set the GUI scale (0 = auto)
 * lang CODE                  switch the game's language (zh_cn) and reload; every script starts in en_us
 * blueprint NAME X Y Z FACING  place a Model Core and build a reference model in front of it, as a player would
 *                            (bigram, context, recall, reverse, induction, gpt, gpt32, modern32)
 * shot NAME                  save screenshots/NAME.png in the game directory
 * clip NAME EVERY            save a half-size frame every EVERY ticks, as screenshots/NAME/0000.png and on, while
 *                            the script goes on; meanwhile a cursor is drawn on screens and toasts are kept away
 * clip off                   stop saving frames
 * parts [PREFIX]             log the name and place of every part of the open screen, or of those whose names start so
 * expect part PART N         fail the step unless the open screen has that part (1) or has not (0)
 * expect on PART N           fail the step unless the part is the chosen one, such as the open tab (1), or is not (0)
 * expect notes N             fail the step unless every page of the field notes, in the game's language, compiles with N errors
 * quit
 * </pre>
 *
 * <p>PART is a name from {@link Named}: {@code tab:train}, {@code core.save}, {@code slot:3}, {@code has:minecraft:coal},
 * {@code kind:minecraft:cobblestone}; {@code NAME+DX+DY} is the point that far from its corner, as in {@code canvas+60+40}.
 * A click on a widget that is switched off fails the step; on a guide page, the page scrolls until the part is in view. A
 * step by place logs the part it hit ({@code that is NAME}), and {@code tools/name_clicks.py} turns a script's places into
 * names from the log of a run of it. A shot of an open screen writes where its parts are beside it, as NAME.parts.json.
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
    private static final MouseButtonInfo RIGHT = new MouseButtonInfo(GLFW.GLFW_MOUSE_BUTTON_RIGHT, 0);

    private static final String[] CURSOR = {
            "#", "##", "#.#", "#..#", "#...#", "#....#", "#.....#", "#......#", "#...####", "#..#", "#.#", "##", "#"};

    private static List<String> steps;
    private static int next;
    private static int waiting;
    /** Ticks an {@code until} step has left, or -1 with none under way. */
    private static int untilLeft = -1;
    private static boolean moved;
    private static boolean loading;
    /** Left the world to open it again: waiting for it to be closed. */
    private static boolean reopening;
    /** The seed of the world a script's first step asked for, while the one before it closes. */
    private static String nextWorld;
    private static boolean quitting;
    /** Whether it played on a server, and whether the script itself stopped that server. */
    private static boolean online, stopping;
    /** Steps run, and the ones that threw: any of those makes the whole run end with a failing exit code. */
    private static int played;
    private static final List<String> failed = new ArrayList<>();
    /** Where each agent stood when {@code expect walked} last asked. */
    private static final Map<String, Vec3> WALKED = new HashMap<>();
    /** What the server has said to the player. */
    private static final List<String> HEARD = new ArrayList<>();
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
        leaveInputMethodAlone(mc);
        if (SCRIPT.isEmpty() || mc.getOverlay() != null) {
            return;
        }
        if (steps == null) {
            start(mc);
        }
        if (quitting) {
            if (mc.level == null) {
                finish();
                mc.stop();
            }
            return;
        }
        // a server it was thrown off leaves nothing to play on: the rest of the script would only fail
        if (online && !stopping && !loading && next > 0 && mc.level == null) {
            failed.add("lost the server");
            LOG.error("[rehearsal] lost the server at step {} of {}: {}", next, steps.size(), steps.get(next - 1));
            quitting = true;
            return;
        }
        if (nextWorld != null) {
            if (mc.level != null) {
                return;
            }
            String seed = nextWorld;
            nextWorld = null;
            // what the script before left set
            forgetScreens();
            hover = false;
            mc.options.hideGui = false;
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            try {
                createWorld(mc, seed);
            } catch (IOException e) {
                LOG.error("[rehearsal] cannot create a world", e);
            }
            return;
        }
        if (reopening) {
            if (mc.level != null) {
                return;
            }
            reopening = false;
            mc.createWorldOpenFlows().openWorld(WORLD, () -> {
            });
            loading = true;
            return;
        }
        if (loading) {
            if (mc.level == null || mc.player == null || mc.screen != null) {
                return;
            }
            loading = false;
        }
        // a server that stopped leaves the client without a player for the script's last steps
        if (hover && mc.player != null && !mc.player.getAbilities().flying) {
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
            played++;
            try {
                run(mc, step);
            } catch (Exception e) {
                failed.add(step);
                LOG.error("[rehearsal] step failed: {}", step, e);
            }
            return;
        }
    }

    /**
     * What screens remember while the game runs, a terminal's search, a Core's tab, as a player who just started the game
     * would find it: one client plays many scripts, and the next one's clicks count on that.
     */
    private static void forgetScreens() {
        remember(StorageTerminalScreen.class, "lastSearch", "");
        remember(StorageTerminalScreen.class, "descending", false);
        remember(StorageTerminalScreen.class, "sort", "NAME");
        remember(CoreScreen.class, "lastTab", "DATA");
        remember(CoreScreen.class, "lastTemperature", 2);
        remember(CoreScreen.class, "lastBenchmark", "FORECASTING");
        remember(AgentScreen.class, "lastTab", "WORK");
        remember(AgentScreen.class, "palette", false);
        remember(TerminalScreen.class, "lastTab", "MAP");
        InstrumentScreen.forget();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void remember(Class<?> screen, String field, Object value) {
        try {
            Field f = screen.getDeclaredField(field);
            f.setAccessible(true);
            f.set(null, f.getType().isEnum() ? Enum.valueOf((Class<Enum>) f.getType(), (String) value) : value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Says how the run went, and has the process exit with a failing code if any step failed. The language goes back to English
     * for whatever starts in this game folder next: a guide export takes the language it finds.
     */
    private static void finish() {
        Minecraft.getInstance().options.languageCode = "en_us";
        Minecraft.getInstance().options.save();
        LOG.info("[rehearsal] finished: {} steps, {} failed", played, failed.size());
        if (!failed.isEmpty()) {
            LOG.error("[rehearsal] failed steps: {}", failed);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> Runtime.getRuntime().halt(1)));
        }
    }

    /** What the server says to the player, so a command that failed shows in the log. */
    @SubscribeEvent
    static void chat(ClientChatReceivedEvent.System event) {
        LOG.info("[rehearsal] said: {}", event.getMessage().getString());
        HEARD.add(event.getMessage().getString());
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
        language(mc, "en_us");
    }

    private static void language(Minecraft mc, String code) {
        if (code.equals(mc.options.languageCode)) {
            return;
        }
        mc.options.languageCode = code;
        mc.getLanguageManager().setSelected(code);
        mc.options.save();
        mc.reloadResourcePacks();
        // the reload's overlay holds the script from the next tick on
        waiting = 1;
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

    /**
     * Keeps the game's hands off the system's input method while someone else is typing. A window that never had focus
     * still believes it has, since GLFW only reports changes, and a window that believes it has focus switches the
     * system's input source back to ASCII whenever no text box is open, which undoes every switch to Chinese the moment
     * it is made. What it would restore when a scripted text box opens goes too.
     */
    private static void leaveInputMethodAlone(Minecraft mc) {
        try {
            if (GLFW.glfwGetWindowAttrib(mc.getWindow().handle(), GLFW.GLFW_FOCUSED) == 0) {
                Field focused = Window.class.getDeclaredField("focused");
                focused.setAccessible(true);
                focused.setBoolean(mc.getWindow(), false);
            }
            Field requested = TextInputManager.class.getDeclaredField("imeRequested");
            requested.setAccessible(true);
            requested.setBoolean(mc.textInputManager(), false);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
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
            case "world" -> {
                if (mc.level != null) {
                    nextWorld = arg;
                    mc.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE);
                } else {
                    createWorld(mc, arg);
                }
            }
            case "connect" -> {
                online = true;
                ConnectScreen.startConnecting(new TitleScreen(), mc, ServerAddress.parseString(arg), new ServerData(WORLD, arg, ServerData.Type.OTHER),
                        false, null);
                loading = true;
            }
            case "reopen" -> {
                reopening = true;
                mc.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE);
            }
            case "cmd" -> {
                stopping |= arg.equals("stop");
                mc.player.connection.sendCommand(arg);
            }
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
                pointToClick(mc, args);
                click(mc, mouseX, mouseY);
            }
            case "rclick" -> {
                pointToClick(mc, args);
                click(mc, mouseX, mouseY, RIGHT);
            }
            case "altclick" -> {
                pointToClick(mc, args);
                click(mc, mouseX, mouseY, new MouseButtonInfo(GLFW.GLFW_MOUSE_BUTTON_LEFT, GLFW.GLFW_MOD_ALT));
            }
            case "inventory" -> mc.setScreen(new InventoryScreen(mc.player));
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
                double[] to = aim(mc, args, 0);
                if (args.length > (int) to[2]) {
                    glide(to[0], to[1], Integer.parseInt(args[(int) to[2]]));
                } else {
                    moveMouse(mc, to[0], to[1]);
                }
                sayPart(mc, to);
            }
            case "drag" -> {
                double[] from = aim(mc, args, 0);
                moveMouse(mc, from[0], from[1]);
                sayPart(mc, from);
                mc.screen.mouseClicked(new MouseButtonEvent(mouseX, mouseY, LEFT), false);
                double[] to = aim(mc, args, (int) from[2]);
                sayPart(mc, to);
                int used = (int) from[2] + (int) to[2];
                dragging = true;
                glide(to[0], to[1], args.length > used ? Integer.parseInt(args[used]) : 4);
            }
            case "scroll" -> mc.screen.mouseScrolled(mouseX, mouseY, 0, Double.parseDouble(arg));
            case "fresh" -> forgetScreens();
            case "close" -> {
                if (mc.screen != null) {
                    mc.screen.onClose();
                }
            }
            case "key" -> mc.screen.keyPressed(new KeyEvent(
                    GLFW.class.getField("GLFW_KEY_" + arg.toUpperCase(Locale.ROOT)).getInt(null), 0, 0));
            case "type" -> arg.codePoints().forEach(c -> mc.screen.charTyped(new CharacterEvent(c)));
            case "slot" -> mc.player.getInventory().setSelectedSlot(hotbar(mc, arg));
            case "drive" -> {
                MinecraftServer server = mc.getSingleplayerServer();
                UUID id = mc.player.getUUID();
                server.execute(() -> ServerSteps.drive(server.getPlayerList().getPlayer(id), Integer.parseInt(arg)));
            }
            case "frame" -> frame(mc, args);
            case "count" -> count(mc, args);
            case "expect" -> expect(mc, args);
            case "stock" -> stock(mc, args);
            case "transfer" -> {
                List<List<ItemResource>> cells = new ArrayList<>();
                for (String cell : args[0].split(",")) {
                    cells.add(cell.equals("-") ? List.of() : Stream.of(cell.split("\\|")).map(Rehearsal::kind).toList());
                }
                ClientPacketDistributor.sendToServer(new StorageTransfer(mc.player.containerMenu.containerId, cells, args.length > 1));
            }
            case "pattern" -> ClientPacketDistributor.sendToServer(new PatternTransfer(mc.player.containerMenu.containerId,
                    stacks(args[0]), args.length > 1 ? stacks(args[1]) : List.of()));
            case "sums" -> {
                MinecraftServer server = mc.getSingleplayerServer();
                UUID id = mc.player.getUUID();
                BlockPos at = new BlockPos(Integer.parseInt(args[0]), Integer.parseInt(args[1]), Integer.parseInt(args[2]));
                server.execute(() -> {
                    ServerBlockEntity be = (ServerBlockEntity) server.getPlayerList().getPlayer(id).level().getBlockEntity(at);
                    Random rng = new Random(1);
                    for (int i = 0; i < Integer.parseInt(args[3]); i++) {
                        String prompt = Reckoning.prompt(Reckoning.sum(rng, true));
                        be.ask(prompt, Reckoning.WRITTEN, '\n', r -> {
                        });
                    }
                });
            }
            case "ask" -> {
                MinecraftServer server = mc.getSingleplayerServer();
                UUID id = mc.player.getUUID();
                BlockPos at = new BlockPos(Integer.parseInt(args[0]), Integer.parseInt(args[1]), Integer.parseInt(args[2]));
                int tokens = args.length > 4 ? Integer.parseInt(args[4]) : 16;
                server.execute(() -> {
                    ServerBlockEntity be = (ServerBlockEntity) server.getPlayerList().getPlayer(id).level().getBlockEntity(at);
                    for (int i = 0; i < Integer.parseInt(args[3]); i++) {
                        String prompt = "> question " + (i % 40) + "\n= ";
                        be.submit(m -> Sampler.answer(m, prompt, tokens, (char) 1), r -> {
                        });
                    }
                });
            }
            case "ghost" -> ClientPacketDistributor.sendToServer(new GhostSlot(mc.player.containerMenu.containerId, Integer.parseInt(args[0]), kind(args[1]).toStack(1)));
            case "kinds" -> kinds(mc, args);
            case "await" -> {
                String[] rest = arg.split("\\s+", 2);
                int left = Integer.parseInt(rest[0]);
                try {
                    expect(mc, rest[1].split("\\s+"));
                } catch (IllegalStateException e) {
                    if (left <= 0) {
                        throw e;
                    }
                    steps.set(--next, "await " + (left - 20) + " " + rest[1]);
                    waiting = 19;
                }
            }
            case "mine" -> {
                MinecraftServer server = mc.getSingleplayerServer();
                UUID id = mc.player.getUUID();
                BlockPos pos = new BlockPos(Integer.parseInt(args[0]), Integer.parseInt(args[1]), Integer.parseInt(args[2]));
                server.execute(() -> server.getPlayerList().getPlayer(id).gameMode.destroyBlock(pos));
            }
            case "bound" -> KeyMapping.click(KeyMapping.get(arg).getDefaultKey());
            case "trace" -> trace(mc, args);
            case "hold" -> KeyMapping.get(args[0]).setDown(args[1].equals("on"));
            case "until" -> until(mc, args);
            case "seek" -> seek(mc, args);
            case "survey" -> survey(mc, args);
            case "fly" -> {
                hover = arg.equals("on");
                mc.player.getAbilities().flying = hover;
                mc.player.onUpdateAbilities();
            }
            case "sneak" -> mc.options.keyShift.setDown(arg.equals("on"));
            case "hud" -> mc.options.hideGui = arg.equals("off");
            case "view" -> mc.options.setCameraType(switch (arg) {
                case "back" -> CameraType.THIRD_PERSON_BACK;
                case "front" -> CameraType.THIRD_PERSON_FRONT;
                default -> CameraType.FIRST_PERSON;
            });
            case "gui" -> {
                mc.options.guiScale().set(Integer.parseInt(arg));
                mc.resizeGui();
            }
            case "lang" -> language(mc, arg);
            case "blueprint" -> blueprint(mc, args);
            case "shot" -> {
                Screenshot.grab(mc.gameDirectory, arg + ".png", mc.getMainRenderTarget(), 1,
                        message -> LOG.info("[rehearsal] {}", message.getString()));
                shotParts(mc, arg);
            }
            case "parts" -> parts(mc, arg);
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
        click(mc, x, y, LEFT);
    }

    private static void click(Minecraft mc, double x, double y, MouseButtonInfo button) {
        MouseButtonEvent click = new MouseButtonEvent(x, y, button);
        mc.screen.mouseClicked(click, false);
        // a button may close its screen
        if (mc.screen != null) {
            mc.screen.mouseReleased(click);
        }
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
        BlockPos core = new BlockPos(Integer.parseInt(args[1]), Integer.parseInt(args[2]), Integer.parseInt(args[3]));
        MinecraftServer server = mc.getSingleplayerServer();
        server.execute(() -> ServerSteps.blueprint(server.overworld(), args[0], core, Direction.byName(args[4])));
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
            if (args.length > 4) {
                centre = new Vec3(centre.x, box.minY() + Double.parseDouble(args[4]), centre.z);
            }
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

    /** Asks {@code expect} again every second, until it holds or the time is up. */
    private static void until(Minecraft mc, String[] args) throws Exception {
        if (untilLeft < 0) {
            untilLeft = Integer.parseInt(args[0]);
        }
        try {
            expect(mc, java.util.Arrays.copyOfRange(args, 1, args.length));
            untilLeft = -1;
        } catch (IllegalStateException e) {
            if (untilLeft <= 0) {
                untilLeft = -1;
                throw e;
            }
            untilLeft -= 20;
            next--;
            played--;
            waiting = 20;
        }
    }

    /** Checks something the run's result rests on, on the client or the integrated server; a miss fails the step. */
    private static void expect(Minecraft mc, String[] args) throws Exception {
        if (expectPart(mc, args)) {
            return;
        }
        long found, wanted = Long.parseLong(args[args.length - 1]);
        String what = String.join(" ", args);
        switch (args[0]) {
            case "pins" -> found = ((TerminalMenu) mc.player.containerMenu).pins.size();
            case "tree" -> found = ((TerminalMenu) mc.player.containerMenu).tree.size();
            case "moblog" -> {
                MinecraftServer server = mc.getSingleplayerServer();
                UUID id = mc.player.getUUID();
                found = server.submit(() -> {
                    long looks = 0;
                    for (ItemStack stack : server.getPlayerList().getPlayer(id).getInventory()) {
                        MobLogItem.Log log = MobLogItem.log(stack);
                        looks += log == null ? 0 : Imitation.lines(log.lines()).stream().mapToInt(l -> (l.length() - 1) / 2).sum();
                    }
                    return looks;
                }).get(10, TimeUnit.SECONDS);
            }
            case "open" -> found = mc.player.containerMenu instanceof TerminalMenu ? 1 : 0;
            case "docked" -> found = ((DockMenu) mc.player.containerMenu).moved();
            case "grid" -> found = ((StorageTerminalScreen) mc.screen).kindsShown();
            case "meant" -> found = ((TerminalMenu) mc.player.containerMenu).meanings.containsKey(kind(args[1])) ? 1 : 0;
            case "words" -> found = ((TerminalMenu) mc.player.containerMenu).meaningProblem.isEmpty() ? 0 : 1;
            case "slot" -> {
                ItemStack in = mc.player.containerMenu.getSlot(Integer.parseInt(args[1])).getItem();
                found = in.is(kind(args[2]).getItem()) ? in.getCount() : 0;
            }
            case "has", "holds" -> {
                long n = 0;
                for (ItemStack stack : mc.player.getInventory()) {
                    n += stack.is(kind(args[1]).getItem()) ? stack.getCount() : 0;
                }
                found = n;
            }
            case "carried" -> found = mc.player.containerMenu.getCarried().is(BuiltInRegistries.ITEM.getValue(Identifier.parse(args[1])))
                    ? mc.player.containerMenu.getCarried().getCount() : 0;
            case "shown" -> found = ((TerminalMenu) mc.player.containerMenu).items.size() + ((TerminalMenu) mc.player.containerMenu).fluids.size();
            case "suit" -> {
                ItemStack suit = ExosuitItem.on(mc.player);
                Board board = ExosuitItem.board(suit);
                Board.Form form = board == null ? null : board.running(ExosuitItem.memory());
                found = switch (args[1]) {
                    case "compute" -> ExosuitItem.compute(suit);
                    case "charge" -> FlightFrameItem.charge(suit);
                    case "under" -> FlightFrameItem.charge(suit) < wanted ? wanted : FlightFrameItem.charge(suit) - wanted;
                    case "energy" -> suit.getCapability(Capabilities.Energy.ITEM, ItemAccess.forStack(suit)).getAmountAsLong();
                    case "running" -> ExosuitItem.running(suit).size();
                    case "bits" -> form == null ? 0 : form.bits();
                    default -> throw new IllegalArgumentException(args[1]);
                };
            }
            case "attribute" -> found = Math.round(100 * mc.player.getAttributeValue(BuiltInRegistries.ATTRIBUTE.get(Identifier.parse(args[1])).orElseThrow()));
            case "health" -> found = Math.round(mc.player.getHealth());
            case "flyspeed" -> found = Math.round(100 * mc.player.getAbilities().getFlyingSpeed());
            case "mayfly" -> found = mc.player.getAbilities().mayfly ? 1 : 0;
            case "sight" -> found = mc.player.hasEffect(MobEffects.NIGHT_VISION) ? 1 : 0;
            case "blocks" -> {
                Block block = BuiltInRegistries.BLOCK.getValue(Identifier.parse(args[1]));
                found = 0;
                for (BlockPos p : BlockPos.betweenClosed(Integer.parseInt(args[2]), Integer.parseInt(args[3]), Integer.parseInt(args[4]),
                        Integer.parseInt(args[5]), Integer.parseInt(args[6]), Integer.parseInt(args[7]))) {
                    found += mc.level.getBlockState(p).is(block) ? 1 : 0;
                }
            }
            case "screen" -> found = mc.screen != null && mc.screen.getClass().getSimpleName().equals(args[1]) ? 1 : 0;
            case "afloat" -> found = FloatingSteps.shown().stream().filter(s -> args[1].equals("any") || s.contains(args[1])).count();
            case "afloatabove" -> found = Math.max(0, FloatingSteps.shown().size() - Integer.parseInt(args[1]));
            case "afloatheard" -> found = Math.max(0, FloatingSteps.heard() - Integer.parseInt(args[1]));
            case "earned" -> {
                MinecraftServer server = mc.getSingleplayerServer();
                UUID id = mc.player.getUUID();
                found = server.submit(() -> {
                    ServerPlayer p = server.getPlayerList().getPlayer(id);
                    return p.getAdvancements().getOrStartProgress(server.getAdvancements().get(Residual.id(args[1]))).isDone() ? 1L : 0L;
                }).get(10, TimeUnit.SECONDS);
            }
            case "asking" -> {
                MinecraftServer server = mc.getSingleplayerServer();
                UUID id = mc.player.getUUID();
                found = server.submit(() -> {
                    ServerPlayer p = server.getPlayerList().getPlayer(id);
                    Agent agent = p.level().getNearestEntity(Agent.class, net.minecraft.world.entity.ai.targeting.TargetingConditions.forNonCombat().ignoreLineOfSight().ignoreInvisibilityTesting(),
                            p, p.getX(), p.getY(), p.getZ(), p.getBoundingBox().inflate(256));
                    String asked = agent == null ? "" : agent.proposed();
                    LOG.info("[rehearsal] it reads back: {}", asked);
                    return asked.contains(args[1]) ? 1L : 0L;
                }).get(10, TimeUnit.SECONDS);
            }
            case "agentvar" -> {
                MinecraftServer server = mc.getSingleplayerServer();
                UUID id = mc.player.getUUID();
                found = server.submit(() -> {
                    ServerPlayer p = server.getPlayerList().getPlayer(id);
                    Agent agent = p.level().getNearestEntity(Agent.class, net.minecraft.world.entity.ai.targeting.TargetingConditions.forNonCombat().ignoreLineOfSight().ignoreInvisibilityTesting(),
                            p, p.getX(), p.getY(), p.getZ(), p.getBoundingBox().inflate(256));
                    if (agent == null) {
                        return -1L;
                    }
                    return (long) Math.floor(parse(agent.variable(args[1])));
                }).get(10, TimeUnit.SECONDS);
            }
            case "dimension" -> found = mc.player.level().dimension().identifier().getPath().equals(args[1]) ? 1 : 0;
            case "said" -> found = HEARD.stream().filter(line -> line.contains(args[1])).count();
            case "out" -> found = RelayView.base != null && !RelayView.base.problem().isEmpty()
                    || RelayView.agents != null && !RelayView.agents.problem().isEmpty() ? 1 : 0;
            case "machines" -> found = RelayView.base == null ? 0 : RelayView.base.machines().size();
            case "stock" -> found = RelayView.base == null ? 0 : RelayView.base.storage().stream().mapToLong(s -> s.kinds()).sum();
            case "agents" -> found = RelayView.agents == null ? 0 : RelayView.agents.agents().size();
            case "gate" -> found = RelayView.parts.stream().filter(p -> p.name().equals(args[1])).count();
            case "near" -> found = mc.player.position().distanceTo(new Vec3(Double.parseDouble(args[1]), Double.parseDouble(args[2]), Double.parseDouble(args[3]))) <= wanted ? wanted : 0;
            case "program", "proposed" -> {
                MinecraftServer server = mc.getSingleplayerServer();
                UUID id = mc.player.getUUID();
                found = server.submit(() -> {
                    ServerLevel level = server.getPlayerList().getPlayer(id).level();
                    for (Agent a : level.getEntities(ModEntities.AGENT.get(), a -> a.getName().getString().equals(args[1]))) {
                        return args[0].equals("proposed") ? (long) a.proposed().length() : a.stopped() ? 0L : (long) a.program().length();
                    }
                    return -1L;
                }).get(10, TimeUnit.SECONDS);
            }
            case "walked" -> {
                MinecraftServer server = mc.getSingleplayerServer();
                found = server.submit(() -> {
                    for (ServerLevel level : server.getAllLevels()) {
                        for (Agent a : level.getEntities(ModEntities.AGENT.get(), a -> a.getName().getString().equals(args[1]))) {
                            Vec3 before = WALKED.put(args[1], a.position());
                            return before == null ? 0L : Math.round(a.position().distanceTo(before));
                        }
                    }
                    return -1L;
                }).get(10, TimeUnit.SECONDS);
            }
            case "hops" -> {
                MinecraftServer server = mc.getSingleplayerServer();
                UUID id = mc.player.getUUID();
                found = server.submit(() -> {
                    var dimension = server.getPlayerList().getPlayer(id).level().dimension();
                    return (long) Relays.of(server).hops(GlobalPos.of(dimension, new BlockPos(Integer.parseInt(args[1]), Integer.parseInt(args[2]), Integer.parseInt(args[3]))),
                            GlobalPos.of(dimension, new BlockPos(Integer.parseInt(args[4]), Integer.parseInt(args[5]), Integer.parseInt(args[6]))));
                }).get(10, TimeUnit.SECONDS);
            }
            case "picture" -> {
                Picture picture = InstrumentScreen.PICTURES.get(Instrument.valueOf(args[1].toUpperCase(Locale.ROOT)));
                found = picture == null ? 0 : picture.boxes().size() + picture.lines().size() + picture.texts().size();
            }
            case "idle" -> found = InstrumentScreen.sync != null && !InstrumentScreen.sync.working() ? 1 : 0;
            case "seeds" -> found = CoreView.evaluation == null ? 0 : CoreView.evaluation.seeds().length;
            case "fresh" -> found = CoreView.evaluation == null || Float.isNaN(CoreView.evaluation.fresh()) ? 0 : 1;
            case "contaminated" -> found = CoreView.evaluation != null && CoreView.evaluation.contaminated() ? 1 : 0;
            case "made", "board", "designs", "kept" -> {
                MinecraftServer server = mc.getSingleplayerServer();
                UUID id = mc.player.getUUID();
                found = server.submit(() -> {
                    ServerPlayer player = server.getPlayerList().getPlayer(id);
                    return switch (args[0]) {
                        case "made" -> {
                            AdvancementHolder holder = server.getAdvancements().get(Residual.id(args[1]));
                            yield holder != null && player.getAdvancements().getOrStartProgress(holder).isDone() ? 1L : 0L;
                        }
                        case "board" -> (long) Records.of(server).board(Benchmark.byId(args[1])).size();
                        case "designs" -> (long) Records.of(server).designs(args[1]).size();
                        default -> {
                            BlockPos at = new BlockPos(Integer.parseInt(args[1]), Integer.parseInt(args[2]), Integer.parseInt(args[3]));
                            yield CrystalItem.model(((InstrumentBlockEntity) player.level().getBlockEntity(at)).items().getItem(InstrumentBlockEntity.OUTPUT)) != null ? 1L : 0L;
                        }
                    };
                }).get(10, TimeUnit.SECONDS);
            }
            default -> {
                MinecraftServer server = mc.getSingleplayerServer();
                UUID id = mc.player.getUUID();
                BlockPos at = new BlockPos(Integer.parseInt(args[1]), Integer.parseInt(args[2]), Integer.parseInt(args[3]));
                found = server.submit(() -> {
                    ServerLevel level = server.getPlayerList().getPlayer(id).level();
                    if (args[0].equals("steps")) {
                        return (long) ((CoreBlockEntity) level.getBlockEntity(at)).snapshot().steps();
                    }
                    if (args[0].equals("running")) {
                        return ((CoreBlockEntity) level.getBlockEntity(at)).running() ? 1L : 0L;
                    }
                    if (args[0].equals("working")) {
                        return level.getBlockState(at).getValue(UnitBlock.WORKING) ? 1L : 0L;
                    }
                    StorageNetwork net = StorageNetwork.at(level, at);
                    switch (args[0]) {
                        case "jobs" -> {
                            return (long) CraftingData.of(server).on(level, net).size();
                        }
                        case "capacity" -> {
                            return net.usage()[1];
                        }
                        case "nodes" -> {
                            return (long) net.nodes.size();
                        }
                        case "kinds" -> {
                            return (long) net.contents().size() + net.fluids().size();
                        }
                        case "linkrate" -> {
                            return (long) net.linkRate();
                        }
                        case "moved" -> {
                            return level.getBlockEntity(at) instanceof DockBlockEntity dock ? (long) dock.moved()
                                    : level.getBlockEntity(at) instanceof PortBlockEntity port ? (long) port.moved() : 0L;
                        }
                        case "fluid" -> {
                            return net.amount(FluidResource.of(BuiltInRegistries.FLUID.getValue(Identifier.parse(args[4]))));
                        }
                        case "tank" -> {
                            FluidResource fluid = FluidResource.of(BuiltInRegistries.FLUID.getValue(Identifier.parse(args[4])));
                            ResourceHandler<FluidResource> tank = level.getCapability(Capabilities.Fluid.BLOCK, at, null);
                            long held = 0;
                            for (int slot = 0; tank != null && slot < tank.size(); slot++) {
                                held += tank.getResource(slot).equals(fluid) ? tank.getAmountAsLong(slot) : 0;
                            }
                            return held;
                        }
                        default -> {
                        }
                    }
                    if (args[0].equals("server")) {
                        return serverSays((ServerBlockEntity) level.getBlockEntity(at), args[4]);
                    }
                    if (args[0].equals("smelter")) {
                        SmelterBlockEntity be = (SmelterBlockEntity) level.getBlockEntity(at);
                        return switch (args[4]) {
                            case "gave" -> (long) be.lastGave();
                            case "assessed" -> (long) be.assessed();
                            case "assayed" -> (long) be.assayed();
                            default -> throw new IllegalArgumentException(args[4]);
                        };
                    }
                    if (args[0].equals("tuner")) {
                        TunerBlockEntity be = (TunerBlockEntity) level.getBlockEntity(at);
                        TunerSync s = be.sync(0);
                        return switch (args[4]) {
                            case "running" -> s.running() ? 1L : 0L;
                            case "steps" -> (long) s.steps();
                            case "saved" -> CrystalItem.model(be.items().getItem(TunerBlockEntity.OUT)) != null ? 1L : 0L;
                            case "choices" -> (long) s.choices();
                            case "waiting" -> s.first().isEmpty() ? 0L : 1L;
                            default -> throw new IllegalArgumentException(args[4]);
                        };
                    }
                    if (args[0].equals("watching")) {
                        return (long) ((WatchPostBlockEntity) level.getBlockEntity(at)).rows().size();
                    }
                    if (args[0].equals("wrong")) {
                        return ((WatchPostBlockEntity) level.getBlockEntity(at)).rows().stream().filter(r -> r.verdict() != WatchPostBlockEntity.FINE).count();
                    }
                    if (args[0].equals("spent")) {
                        return (long) ForecasterBlockEntity.POWER - ((ForecasterBlockEntity) level.getBlockEntity(at)).energy().getAmountAsInt();
                    }
                    if (args[0].equals("tier")) {
                        ArenaBlockEntity arena = (ArenaBlockEntity) level.getBlockEntity(at);
                        LOG.info("[rehearsal] the Arena's check: {}", arena.check());
                        return (long) arena.tier();
                    }
                    if (args[0].equals("fights") || args[0].equals("fewer") || args[0].equals("closed")) {
                        ArenaBlockEntity arena = (ArenaBlockEntity) level.getBlockEntity(at);
                        LOG.info("[rehearsal] the Arena's state: {}, on {} compute", arena.state(), arena.compute());
                        return args[0].equals("closed") ? (arena.state() == ArenaBlockEntity.State.CLOSED ? 1L : 0L) : (long) arena.running();
                    }
                    if (args[0].equals("refined") || args[0].equals("tokens")) {
                        TokenSupply held = ((RefineryBlockEntity) level.getBlockEntity(at)).supply();
                        return held == null ? 0L : args[0].equals("refined") ? held.characters() : held.tokens();
                    }
                    if (args[0].equals("measured")) {
                        return Float.isNaN(((RefineryBlockEntity) level.getBlockEntity(at)).delta()) ? 0L : 1L;
                    }
                    if (args[0].equals("shows")) {
                        return (long) ((RefineryBlockEntity) level.getBlockEntity(at)).rows().size();
                    }
                    if (args[0].equals("judged")) {
                        return (long) ((RefineryBlockEntity) level.getBlockEntity(at)).judged();
                    }
                    if (args[0].equals("trained")) {
                        return (long) ((CoreBlockEntity) level.getBlockEntity(at)).snapshot().steps();
                    }
                    if (args[0].equals("port")) {
                        return (long) ((PortBlockEntity) level.getBlockEntity(at)).expects();
                    }
                    if (args[0].equals("moved")) {
                        return level.getBlockEntity(at) instanceof PortBlockEntity port ? (long) port.movedLastSecond() : -1L;
                    }
                    if (args[0].equals("energy")) {
                        var h = level.getCapability(Capabilities.Energy.BLOCK, at, null);
                        return h == null ? -1L : h.getAmountAsLong();
                    }
                    if (args[0].equals("fluid")) {
                        var h = level.getCapability(Capabilities.Fluid.BLOCK, at, null);
                        long n = 0;
                        for (int slot = 0; h != null && slot < h.size(); slot++) {
                            n += h.getAmountAsLong(slot);
                        }
                        return n;
                    }
                    if (args[0].equals("held") || args[0].equals("breaks") || args[0].equals("watches")) {
                        var debug = level.getBlockEntity(at) instanceof PortBlockEntity port ? port.work.debugOf(Integer.parseInt(args[4])) : null;
                        return debug == null ? -1L : args[0].equals("held") ? (debug.paused ? 1L : 0L) : args[0].equals("breaks") ? debug.breaks.size() : debug.watches.size();
                    }
                    if (args[0].equals("var")) {
                        return level.getBlockEntity(at) instanceof PortBlockEntity port ? (long) Math.floor(parse(port.work.variable(Integer.parseInt(args[4]), args[5]))) : -1L;
                    }
                    if (args[0].equals("empty")) {
                        ResourceHandler<ItemResource> inv = level.getCapability(Capabilities.Item.BLOCK, at, null);
                        long n = 0;
                        for (int slot = 0; inv != null && slot < inv.size(); slot++) {
                            n += inv.getAmountAsLong(slot);
                        }
                        return n;
                    }
                    if (args[0].equals("mobs") || args[0].equals("nomobs")) {
                        var type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(args[5]));
                        return (long) level.getEntities((Entity) null, new net.minecraft.world.phys.AABB(at).inflate(Integer.parseInt(args[4])), e -> e.getType() == type).size();
                    }
                    if (args[0].equals("placed") || args[0].equals("noblocks")) {
                        BlockPos to = new BlockPos(Integer.parseInt(args[4]), Integer.parseInt(args[5]), Integer.parseInt(args[6]));
                        Block block = BuiltInRegistries.BLOCK.getValue(Identifier.parse(args[7]));
                        long n = 0;
                        for (BlockPos q : BlockPos.betweenClosed(at, to)) {
                            n += level.getBlockState(q).is(block) ? 1 : 0;
                        }
                        return n;
                    }
                    GlobalPos place = GlobalPos.of(level.dimension(), at);
                    if (args[0].equals("reach")) {
                        return Relays.of(server).part(place) == null ? -1L : (long) Relays.of(server).part(place).reach();
                    }
                    if (args[0].equals("netopen")) {
                        return Relays.of(server).netOf(place) != null && Relays.of(server).netOf(place).open() ? 1L : 0L;
                    }
                    if (args[0].equals("state")) {
                        String[] kv = args[4].split("=");
                        for (var e : level.getBlockState(at).getValues().toList()) {
                            if (e.property().getName().equals(kv[0])) {
                                return e.valueName().equals(kv[1]) ? 1L : 0L;
                            }
                        }
                        return -1L;
                    }
                    if (args[0].equals("ticking")) {
                        return level.shouldTickBlocksAt(at) ? 1L : 0L;
                    }
                    if (args[0].equals("carries")) {
                        return (long) PowerNetwork.at(level, at).carries();
                    }
                    if (args[0].equals("devices")) {
                        return (long) Cluster.at(level, at).devices().size();
                    }
                    if (args[0].equals("racks")) {
                        return (long) Cluster.at(level, at).racks().size();
                    }
                    if (args[0].equals("hall")) {
                        return Cluster.at(level, at).hall() ? 1L : 0L;
                    }
                    if (args[0].equals("flops")) {
                        return ((CoreBlockEntity) level.getBlockEntity(at)).meter().flops();
                    }
                    if (args[0].equals("paid")) {
                        return ((CoreBlockEntity) level.getBlockEntity(at)).meter().energy();
                    }
                    if (args[0].equals("chilled")) {
                        return ((ChillerBlockEntity) level.getBlockEntity(at)).spentSoFar();
                    }
                    if (args[0].equals("making")) {
                        return (long) ((ReactorBlockEntity) level.getBlockEntity(at)).rate();
                    }
                    if (args[0].equals("status")) {
                        return (long) ((ReactorBlockEntity) level.getBlockEntity(at)).status();
                    }
                    if (args[0].equals("rods")) {
                        return (long) ((ReactorBlockEntity) level.getBlockEntity(at)).rods();
                    }
                    ItemResource kind = ItemResource.of(BuiltInRegistries.ITEM.getValue(Identifier.parse(args[4])));
                    if (args[0].equals("net")) {
                        return StorageNetwork.at(level, at).count(kind);
                    }
                    ResourceHandler<ItemResource> inv = level.getCapability(Capabilities.Item.BLOCK, at, null);
                    long n = 0;
                    for (int slot = 0; inv != null && slot < inv.size(); slot++) {
                        n += inv.getResource(slot).equals(kind) ? inv.getAmountAsLong(slot) : 0;
                    }
                    return n;
                }).get(10, TimeUnit.SECONDS);
            }
        }
        boolean exact = args[0].equals("jobs") || args[0].equals("pins") || args[0].equals("open") || args[0].equals("grid")
                || args[0].equals("meant") || args[0].equals("words") || args[0].equals("holds") || args[0].equals("attribute")
                || args[0].equals("flyspeed") || args[0].equals("mayfly") || args[0].equals("sight") || args[0].equals("blocks") || args[0].equals("health")
                || args[0].equals("suit") && (args[1].equals("running") || args[1].equals("bits")) || args[0].equals("nomobs") || args[0].equals("noblocks") || args[0].equals("empty")
                || wanted == 0 && (args[0].equals("program") || args[0].equals("proposed"))
                || args[0].equals("netopen") || args[0].equals("hops") || args[0].equals("dimension") || args[0].equals("out")
                || args[0].equals("state") || args[0].equals("ticking")
                || args[0].equals("running") || args[0].equals("working") || args[0].equals("afloatabove") || args[0].equals("afloatheard")
                || args[0].equals("racks") || args[0].equals("hall") || args[0].equals("carries") || args[0].equals("status") || args[0].equals("rods")
                || args[0].equals("contaminated") || args[0].equals("idle") || args[0].equals("closed");
        if (exact ? found != wanted : args[0].equals("fewer") ? found > wanted : found < wanted) {
            throw new IllegalStateException("expected " + what + ", found " + found);
        }
        LOG.info("[rehearsal] expected {}, found {}", what, found);
    }

    /** What a Model Server says of {@code what}, for a script. */
    private static long serverSays(ServerBlockEntity be, String what) {
        ServerSync s = be.sync(0);
        return switch (what) {
            case "rate" -> (long) s.rate();
            case "answered" -> s.answered();
            case "waiting" -> s.waiting();
            case "turned" -> s.turnedAway();
            case "full" -> be.full() ? 1 : 0;
            case "strained" -> be.getBlockState().getValue(ServerBlock.STRAINED) ? 1 : 0;
            case "weights" -> s.weights();
            case "most" -> s.memory() - s.weights();
            case "saved" -> s.saved();
            case "rows" -> s.rows();
            case "batch" -> be.biggestBatch();
            case "proposed" -> s.proposed();
            case "accepted" -> s.accepted();
            case "passes" -> s.scores().stream().filter(ServerSync.Scored::passes).count();
            case "slow" -> Math.round(100 / Math.max(s.rate(), 0.01));
            default -> throw new IllegalArgumentException(what);
        };
    }

    private static ItemResource kind(String id) {
        return ItemResource.of(BuiltInRegistries.ITEM.getValue(Identifier.parse(id)));
    }

    /** {@code minecraft:coal,minecraft:raw_iron*8}: stacks by commas, - for an empty cell. */
    private static List<ItemStack> stacks(String list) {
        List<ItemStack> out = new ArrayList<>();
        for (String one : list.split(",")) {
            String[] parts = one.split("\\*");
            out.add(one.equals("-") ? ItemStack.EMPTY : kind(parts[0]).toStack(parts.length > 1 ? Integer.parseInt(parts[1]) : 1));
        }
        return out;
    }

    private static void stock(Minecraft mc, String[] args) throws Exception {
        MinecraftServer server = mc.getSingleplayerServer();
        UUID id = mc.player.getUUID();
        BlockPos at = new BlockPos(Integer.parseInt(args[0]), Integer.parseInt(args[1]), Integer.parseInt(args[2]));
        server.submit(() -> StorageNetwork.at(server.getPlayerList().getPlayer(id).level(), at)
                .insert(ItemResource.of(BuiltInRegistries.ITEM.getValue(Identifier.parse(args[3]))), Long.parseLong(args[4]), false)).get(10, TimeUnit.SECONDS);
    }

    private static void kinds(Minecraft mc, String[] args) throws Exception {
        MinecraftServer server = mc.getSingleplayerServer();
        UUID id = mc.player.getUUID();
        BlockPos at = new BlockPos(Integer.parseInt(args[0]), Integer.parseInt(args[1]), Integer.parseInt(args[2]));
        server.submit(() -> {
            StorageNetwork net = StorageNetwork.at(server.getPlayerList().getPlayer(id).level(), at);
            for (int i = 0; i < Integer.parseInt(args[3]); i++) {
                ItemStack stack = new ItemStack(Items.PAPER);
                stack.set(DataComponents.CUSTOM_NAME, Component.literal("Kind " + i));
                net.insert(ItemResource.of(stack), 1, false);
            }
        }).get(30, TimeUnit.SECONDS);
    }

    private static void trace(Minecraft mc, String[] args) throws Exception {
        MinecraftServer server = mc.getSingleplayerServer();
        UUID id = mc.player.getUUID();
        List<Trace> lines = server.submit(() -> {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (args[0].equals("agent")) {
                Agent agent = p.level().getNearestEntity(Agent.class, net.minecraft.world.entity.ai.targeting.TargetingConditions.forNonCombat().ignoreLineOfSight().ignoreInvisibilityTesting(),
                        p, p.getX(), p.getY(), p.getZ(), p.getBoundingBox().inflate(256));
                return agent == null ? List.<Trace>of() : List.copyOf(agent.traced());
            }
            BlockPos at = new BlockPos(Integer.parseInt(args[0]), Integer.parseInt(args[1]), Integer.parseInt(args[2]));
            return p.level().getBlockEntity(at) instanceof PortBlockEntity port ? List.copyOf(port.work.traced(Integer.parseInt(args[3]))) : List.<Trace>of();
        }).get(10, TimeUnit.SECONDS);
        for (Trace t : lines) {
            LOG.info("[rehearsal] trace: {} {} {} {} x{}", t.kind(), t.step(), t.key(), t.args(), t.times());
        }
    }

    /** A variable as a number, or -1 if it reads as none. */
    private static double parse(String shown) {
        try {
            return Double.parseDouble(shown);
        } catch (NumberFormatException e) {
            return shown.equals("true") ? 1 : -1;
        }
    }

    private static void count(Minecraft mc, String[] args) {
        MinecraftServer server = mc.getSingleplayerServer();
        UUID id = mc.player.getUUID();
        server.execute(() -> {
            ServerLevel level = server.getPlayerList().getPlayer(id).level();
            Block block = BuiltInRegistries.BLOCK.getValue(Identifier.parse(args[0]));
            int n = 0;
            for (BlockPos p : BlockPos.betweenClosed(Integer.parseInt(args[1]), Integer.parseInt(args[2]), Integer.parseInt(args[3]),
                    Integer.parseInt(args[4]), Integer.parseInt(args[5]), Integer.parseInt(args[6]))) {
                n += level.getBlockState(p).is(block) ? 1 : 0;
            }
            LOG.info("[rehearsal] count {}: {} at tick {}", args[0], n, level.getGameTime());
        });
    }

    private static void seek(Minecraft mc, String[] args) {
        MinecraftServer server = mc.getSingleplayerServer();
        UUID id = mc.player.getUUID();
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            ServerLevel level = player.level();
            Holder<Structure> structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
                    .getOrThrow(ResourceKey.create(Registries.STRUCTURE, Identifier.parse(args[0])));
            int rings = WayfinderItem.rings(level, structure);
            long start = System.nanoTime();
            Pair<BlockPos, Holder<Structure>> found = level.getChunkSource().getGenerator()
                    .findNearestMapStructure(level, HolderSet.direct(structure), player.blockPosition(), rings, false);
            long ms = (System.nanoTime() - start) / 1_000_000;
            LOG.info("[rehearsal] seek {}: {} rings, {} ms, {}", args[0], rings, ms, found == null ? "none"
                    : Math.round(Math.sqrt(player.blockPosition().distToLowCornerSqr(found.getFirst().getX(), player.getY(),
                    found.getFirst().getZ()))) + " blocks");
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

    /** Puts the mouse where a click goes, and says what is under it when the script went by place. */
    private static void pointToClick(Minecraft mc, String[] args) {
        double[] at = aim(mc, args, 0);
        if (at[2] == 1 && Named.find(mc, args[0]).disabled()) {
            throw new IllegalStateException(args[0] + " is switched off");
        }
        moveMouse(mc, at[0], at[1]);
        sayPart(mc, at);
    }

    /**
     * A point from a script's words at {@code from}: the centre of a part by name, or GUI pixel (x, y), counted from a container
     * screen's corner when one is open; then how many words that took.
     */
    private static double[] aim(Minecraft mc, String[] args, int from) {
        if (args[from].matches("-?\\d+")) {
            double[] at = spot(mc, args[from], args[from + 1]);
            return new double[]{at[0], at[1], 2};
        }
        Parts.Part part = Named.find(mc, args[from]);
        return new double[]{part.x() + (part.w() == 0 ? 0.5 : part.w() / 2.0), part.y() + (part.h() == 0 ? 0.5 : part.h() / 2.0), 1};
    }

    /** What a step by place points at, for {@code tools/name_clicks.py}; nothing a script needs, so it never fails one. */
    private static void sayPart(Minecraft mc, double[] at) {
        if (at[2] != 2 || mc.screen == null) {
            return;
        }
        try {
            Parts.Part part = Named.at(mc.screen, at[0], at[1]);
            if (part != null) {
                LOG.info("[rehearsal] that is {} at {} {} of {} {}", part.name(), (int) at[0] - part.x(), (int) at[1] - part.y(), part.w(), part.h());
            } else {
                LOG.info("[rehearsal] that is nothing");
            }
        } catch (RuntimeException e) {
            LOG.warn("[rehearsal] cannot name the parts", e);
        }
    }

    private static void parts(Minecraft mc, String prefix) {
        for (Parts.Part p : Named.of(mc.screen).values()) {
            if (p.name().startsWith(prefix)) {
                LOG.info("[rehearsal] part {} at {} {} size {} {}{}{}", p.name(), p.x(), p.y(), p.w(), p.h(), p.on() ? " on" : "", p.disabled() ? " off" : "");
            }
        }
    }

    /** Where the parts of a screen just shot are, beside the shot; a script that only wants the picture never fails for it. */
    private static void shotParts(Minecraft mc, String name) throws IOException {
        if (mc.screen == null) {
            return;
        }
        Path file = mc.gameDirectory.toPath().resolve(Screenshot.SCREENSHOT_DIR).resolve(name + ".parts.json");
        Files.createDirectories(file.getParent());
        try {
            Named.write(mc, file);
        } catch (RuntimeException e) {
            LOG.warn("[rehearsal] cannot name the parts", e);
        }
    }

    /** The expect steps about parts; false when it is another kind. */
    private static boolean expectPart(Minecraft mc, String[] args) {
        long found;
        switch (args[0]) {
            case "part" -> found = mc.screen != null && Named.of(mc.screen).containsKey(args[1]) ? 1 : 0;
            case "on" -> found = Named.find(mc, args[1]).on() ? 1 : 0;
            case "notes" -> found = Pages.errors();
            default -> {
                return false;
            }
        }
        if (found != Long.parseLong(args[args.length - 1])) {
            throw new IllegalStateException("expected " + String.join(" ", args) + ", found " + found);
        }
        LOG.info("[rehearsal] expected {}, found {}", String.join(" ", args), found);
        return true;
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
