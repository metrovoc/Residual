package io.github.metrovoc.residual.client;

import io.github.metrovoc.residual.agent.Debug;
import io.github.metrovoc.residual.agent.Program;
import io.github.metrovoc.residual.agent.Upgrades;
import io.github.metrovoc.residual.item.ProgramCardItem;
import io.github.metrovoc.residual.net.PortProgramAction;
import io.github.metrovoc.residual.net.ProgramSync;
import io.github.metrovoc.residual.registry.ModItems;
import io.github.metrovoc.residual.storage.PortProgramMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * A Port's programs: a row of Program Cards, the program on the one chosen, its trace with the debugger beside it, what the
 * programs hold in their hands, and its upgrades in a panel beside the window. A program is clicked together from the same
 * palette as an agent's, and goes on the card with Run.
 */
public class PortProgramScreen extends AbstractContainerScreen<PortProgramMenu> implements Parts.Drawn {
    private enum Tab { PROGRAM, TRACE }

    private static final int PALETTE = Palette.WIDTH;
    private static final int WINDOW_W = 176;
    private static final int WIDEST = 340;

    private static boolean palette;
    private static boolean wide;
    private Tab tab = Tab.PROGRAM;
    private final Palette chips = new Palette();
    private DebugPanel debugger;
    @Nullable
    private MultiLineEditBox code;
    @Nullable
    private IconButton run;
    @Nullable
    private IconButton access;
    private int shownAccess;
    /** What the box holds, and whether the player has changed it since it last showed the card. */
    private String draft = "";
    private boolean edited;
    private boolean syncing;
    private Program.Parsed parsed = Program.parse("");

    public PortProgramScreen(PortProgramMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, PortProgramMenu.SIDE_X + PortProgramMenu.SIDE_W, 248);
    }

    private void send(PortProgramAction.Kind kind, int value, String text) {
        ClientPacketDistributor.sendToServer(new PortProgramAction(menu.pos, menu.selected(), kind, value, text));
    }

    /** What the chosen card says. */
    private String written() {
        return ProgramCardItem.program(menu.getSlot(PortProgramMenu.CARDS + menu.selected()).getItem());
    }

    private final DebugPanel.Host debugHost = new DebugPanel.Host() {
        @Override
        public void debug(Debug.Move move, int number, String text) {
            send(PortProgramAction.Kind.DEBUG, move.ordinal() + number * Debug.Move.values().length, text);
        }

        @Override
        public String program() {
            return written();
        }

        @Override
        public ProgramSync view() {
            return PortProgramScreen.this.view();
        }
    };

    @Nullable
    private ProgramSync view() {
        ProgramSync p = AgentView.program;
        return p != null && p.pos().equals(menu.pos) && p.slot() == menu.selected() ? p : null;
    }

    @Override
    protected void init() {
        super.init();
        debugger = new DebugPanel(debugHost, font);
        rebuild();
    }

    private boolean wideShown() {
        return wide && tab == Tab.PROGRAM;
    }

    private int windowWidth() {
        return wideShown() ? Math.clamp(width - (palette ? PALETTE + 12 : 8), WINDOW_W, WIDEST) : WINDOW_W;
    }

    private boolean leftShown() {
        return palette && tab == Tab.PROGRAM || tab == Tab.TRACE;
    }

    private int rowY() {
        return wideShown() ? imageHeight - 26 : 114;
    }

    private int boxY() {
        return wideShown() ? 20 : 40;
    }

    private String shown(String text) {
        Program.Parsed p = wideShown() ? Program.parse(text) : null;
        return p != null && p.ok() && !edited ? p.program().pretty() : text;
    }

    private void rebuild() {
        clearWidgets();
        code = null;
        run = null;
        menu.hideSlots(wideShown());
        leftPos = wideShown() ? (width - windowWidth()) / 2 : (width - imageWidth) / 2;
        if (leftShown()) {
            leftPos = Math.min(Math.max(leftPos, PALETTE + 8), wideShown() ? width - windowWidth() : width - imageWidth);
        }
        int x = leftPos, y = topPos;
        if (tab == Tab.PROGRAM) {
            code = addRenderableWidget(MultiLineEditBox.builder().setX(x + 8).setY(y + boxY()).setShowBackground(false).setShowDecorations(false)
                    .setTextColor(Ui.TEXT).setCursorColor(Ui.TEAL).setPlaceholder(Component.translatable("residual.program.hint"))
                    .build(font, windowWidth() - 16, rowY() - boxY() - 18, Component.translatable("residual.tab.program")));
            code.setCharacterLimit(Program.LONGEST * 2);
            code.setValue(shown(draft));
            parsed = Program.parse(code.getValue());
            code.setValueListener(v -> {
                parsed = Program.parse(v);
                if (!syncing) {
                    draft = v;
                    edited = true;
                }
            });
            int row = rowY();
            run = addRenderableWidget(IconButton.action(x + 8, y + row, "run", Component.translatable("residual.program.run"),
                    Component.translatable("residual.port.program.run.help"), () -> {
                        send(PortProgramAction.Kind.WRITE, 0, code.getValue());
                        send(PortProgramAction.Kind.RUN, 0, "");
                        edited = false;
                    }));
            addRenderableWidget(IconButton.action(x + 28, y + row, "stop", Component.translatable("residual.program.stop"),
                    Component.translatable("residual.program.stop.help"), () -> send(PortProgramAction.Kind.STOP, 0, "")));
            addRenderableWidget(IconButton.action(x + 48, y + row, "palette", Component.translatable("residual.palette"),
                    Component.translatable(palette ? "residual.palette.hide" : "residual.palette.show"), () -> {
                        palette = !palette;
                        rebuild();
                    }));
            addRenderableWidget(IconButton.action(x + 68, y + row, "expand", Component.translatable("residual.program.wide"),
                    Component.translatable(wideShown() ? "residual.program.wide.off" : "residual.program.wide.on"), () -> {
                        wide = !wide;
                        rebuild();
                    }));
            if (palette) {
                chips.lay(x - PALETTE - 4, y, imageHeight, this::addRenderableWidget, this::add, this::things);
            }
        } else {
            debugger.lay(x - DebugPanel.WIDTH - 4, y, imageHeight, this::addRenderableWidget);
        }
        if (wideShown()) {
            return;
        }
        int toolbar = y + PortProgramMenu.SIDE_Y + PortProgramMenu.SIDE_H + 4;
        addRenderableWidget(IconButton.action(x + PortProgramMenu.UPGRADES_X - 1, toolbar, "filter", Component.translatable("residual.port.to_filter"),
                Component.translatable("residual.port.to_filter.help"), () -> send(PortProgramAction.Kind.FILTER, 0, "")));
        shownAccess = menu.access();
        access = addRenderableWidget(IconButton.setting(x + PortProgramMenu.UPGRADES_X + 17, toolbar, List.of("open", "team", "locked"), Component.translatable("residual.owned.access"),
                List.of(Component.translatable("residual.owned.anyone").getString(), Component.translatable("residual.owned.team").getString(),
                        Component.translatable("residual.owned.owner").getString()),
                shownAccess, Component.translatable("residual.owned.help"), i -> send(PortProgramAction.Kind.ACCESS, i, "")));
    }

    private List<ItemStack> things() {
        List<ItemStack> out = new ArrayList<>();
        menu.slots.forEach(slot -> out.add(slot.getItem()));
        return out;
    }

    private void add(String piece, Program.Join join) {
        if (code != null) {
            code.setValue(Program.append(code.getValue(), piece, join));
            setFocused(code);
        }
    }

    /** Whichever card is clicked is the one shown. */
    @Override
    protected void slotClicked(@Nullable Slot slot, int slotId, int button, ContainerInput input) {
        if (slot != null && slotId >= PortProgramMenu.CARDS && slotId < PortProgramMenu.HANDS && slotId != menu.selected()) {
            menu.select(slotId);
            send(PortProgramAction.Kind.SELECT, 0, "");
            edited = false;
            draft = "\0";
            if (slot.hasItem() && menu.getCarried().isEmpty()) {
                return;
            }
        }
        super.slotClicked(slot, slotId, button, input);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        String text = written();
        if (!edited && !text.equals(draft)) {
            draft = text;
            if (code != null) {
                syncing = true;
                code.setValue(shown(draft));
                syncing = false;
            }
        }
        if (run != null) {
            run.active = parsed.ok() && menu.getSlot(PortProgramMenu.CARDS + menu.selected()).getItem().is(ModItems.PROGRAM_CARD.get());
        }
        if (access != null && menu.access() != shownAccess) {
            shownAccess = menu.access();
            access.set(shownAccess);
        }
    }

    private Component tabTitle(Tab t) {
        return Component.translatable("residual.tab." + t.name().toLowerCase(Locale.ROOT));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (palette && tab == Tab.PROGRAM && chips.clickedTab(font, event.x(), event.y())) {
            rebuild();
            return true;
        }
        if (tab == Tab.TRACE && debugger.clicked(event.x(), event.y())) {
            return true;
        }
        int tx = leftPos + 8;
        for (Tab t : Tab.values()) {
            int w = font.width(tabTitle(t)) + 10;
            if (Ui.inside(event.x(), event.y(), tx - 4, topPos + 4, w, 14)) {
                if (t != tab) {
                    tab = t;
                    rebuild();
                }
                return true;
            }
            tx += w + 2;
        }
        ItemStack held = menu.getCarried();
        if (!held.isEmpty() && event.button() == 0 && code != null && code.isMouseOver(event.x(), event.y())) {
            add(io.github.metrovoc.residual.agent.Things.name(held), Program.Join.WORD);
            return true;
        }
        boolean handled = super.mouseClicked(event, doubleClick);
        if (getFocused() instanceof Palette.Chip && code != null) {
            // the program keeps the keys after a piece is picked
            setFocused(code);
            setDragging(false);
        }
        return handled;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double dx, double dy) {
        if (palette && tab == Tab.PROGRAM && chips.scrolled(x, y, dy) || tab == Tab.TRACE && debugger.scrolled(x, y, dy)) {
            return true;
        }
        return super.mouseScrolled(x, y, dx, dy);
    }

    @Override
    protected boolean hasClickedOutside(double mx, double my, int xo, int yo) {
        if (wideShown() && Ui.inside(mx, my, xo, yo, windowWidth(), imageHeight)) {
            return false;
        }
        return super.hasClickedOutside(mx, my, xo, yo) && !(leftShown() && Ui.inside(mx, my, xo - PALETTE - 4, yo, PALETTE, imageHeight));
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isEscape()) {
            return super.keyPressed(event);
        }
        if (code != null && code.isFocused()) {
            code.keyPressed(event);
            return true;
        }
        if (tab == Tab.TRACE && debugger.typing()) {
            if (event.isConfirmation()) {
                debugger.submit();
                return true;
            }
            return debugger.keyPressed(event) || super.keyPressed(event);
        }
        return super.keyPressed(event);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractBackground(g, mouseX, mouseY, a);
        if (!wideShown()) {
            Ui.window(g, leftPos + PortProgramMenu.SIDE_X, topPos + PortProgramMenu.SIDE_Y, PortProgramMenu.SIDE_W, PortProgramMenu.SIDE_H);
        }
        Ui.window(g, leftPos, topPos, windowWidth(), imageHeight);
        if (palette && tab == Tab.PROGRAM) {
            chips.draw(g, font, mouseX, mouseY);
        } else if (tab == Tab.TRACE) {
            debugger.draw(g, mouseX, mouseY);
        }
        int tx = leftPos + 8;
        for (Tab t : Tab.values()) {
            Component name = tabTitle(t);
            int w = font.width(name) + 10;
            boolean on = t == tab;
            boolean hover = Ui.inside(mouseX, mouseY, tx - 4, topPos + 4, w, 14);
            if (on) {
                g.fill(tx - 4, topPos + 17, tx - 4 + w - 2, topPos + 19, 0xFF2A9D92);
            }
            g.text(font, name, tx, topPos + 7, on ? Ui.LABEL : hover ? 0xFF222222 : Ui.LABEL_DIM, false);
            tx += w + 2;
        }
        Ui.screen(g, leftPos + 8, topPos + boxY(), windowWidth() - 16, tab == Tab.TRACE ? 90 : rowY() - boxY() - 4);
        for (Slot slot : menu.slots) {
            if (slot.isActive()) {
                Ui.slot(g, leftPos + slot.x, topPos + slot.y);
            }
        }
        if (!wideShown()) {
            Slot chosen = menu.getSlot(PortProgramMenu.CARDS + menu.selected());
            g.outline(leftPos + chosen.x - 1, topPos + chosen.y - 1, 18, 18, Ui.TEAL);
            if (!chosen.hasItem()) {
                Ui.ghost(g, new ItemStack(ModItems.PROGRAM_CARD.get()), leftPos + chosen.x, topPos + chosen.y);
            }
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int tabs = 8;
        for (Tab t : Tab.values()) {
            tabs += font.width(tabTitle(t)) + 12;
        }
        int right = windowWidth() - 8;
        String name = Ui.fit(font, title.getString(), right - tabs);
        g.text(font, name, right - font.width(name), 7, Ui.LABEL_DIM, false);
        int mx = mouseX - leftPos, my = mouseY - topPos;
        ProgramSync v = view();
        if (tab == Tab.TRACE) {
            if (v != null) {
                TraceView.draw(g, font, v.trace(), 40, 7 + 3, mx, my, mouseX, mouseY);
            }
            return;
        }
        Component check;
        int color;
        if (!parsed.ok()) {
            Program.Problem p = parsed.problem();
            check = Component.translatable("residual.program.slip", Component.translatable(p.key(), p.word(), Program.LONGEST), p.at() + 1);
            color = Ui.AMBER;
        } else if (!menu.getSlot(PortProgramMenu.CARDS + menu.selected()).hasItem()) {
            check = Component.translatable("residual.port.program.no_card");
            color = Ui.AMBER;
        } else {
            Program p = parsed.program();
            check = Component.translatable(p.steps().isEmpty() ? "residual.program.empty" : p.endless() ? "residual.program.ok_endless" : "residual.program.ok");
            color = Ui.TEAL;
        }
        int row = rowY();
        int checkW = windowWidth() - 24;
        g.text(font, Ui.fit(font, check.getString(), checkW), 12, row - 14, color, false);
        if (Ui.inside(mx, my, 8, row - 16, windowWidth() - 16, 12) && font.width(check) > checkW) {
            g.setTooltipForNextFrame(font.split(check, 220), mouseX, mouseY);
        }
        if (v != null && !v.status().isEmpty()) {
            Component status = Component.translatable(v.status(), v.statusArgs().toArray());
            int statusW = windowWidth() - 100;
            g.text(font, Ui.fit(font, status.getString(), statusW), 92, row + 5, Ui.LABEL, false);
            if (Ui.inside(mx, my, 90, row, statusW + 2, 18) && font.width(status) > statusW) {
                g.setTooltipForNextFrame(font.split(status, 220), mouseX, mouseY);
            }
        }
        if (!wideShown() && Ui.inside(mx, my, PortProgramMenu.SIDE_X, PortProgramMenu.SIDE_Y, PortProgramMenu.SIDE_W, PortProgramMenu.SIDE_H)
                && hoveredSlot != null && !hoveredSlot.hasItem() && hoveredSlot.getSlotIndex() >= 0 && menu.getCarried().isEmpty()) {
            g.setTooltipForNextFrame(font.split(Component.translatable("residual.upgrades.help"), 200), mouseX, mouseY);
        }
    }

    @Override
    public void parts(Parts out) {
        int tx = leftPos + 8;
        for (Tab t : Tab.values()) {
            int w = font.width(tabTitle(t)) + 10;
            out.add("tab:" + t.name().toLowerCase(Locale.ROOT), tx - 4, topPos + 4, w, 14, t == tab);
            tx += w + 2;
        }
        if (!wideShown()) {
            out.group("cards", this, PortProgramMenu.CARDS, PortProgramMenu.HANDS);
            out.group("hands", this, PortProgramMenu.HANDS, PortProgramMenu.UPGRADES);
            out.group("upgrades", this, PortProgramMenu.UPGRADES, PortProgramMenu.INVENTORY);
        }
        if (tab == Tab.PROGRAM) {
            out.add("program", code);
            out.add("check", leftPos + 8, topPos + rowY() - 16, windowWidth() - 16, 12);
            if (palette) {
                chips.parts(out, font);
            }
        } else {
            out.add("trace", leftPos + 8, topPos + boxY(), windowWidth() - 16, 90);
            debugger.parts(out);
        }
    }
}
