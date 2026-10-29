package io.github.metrovoc.residual.client;

import io.github.metrovoc.residual.block.OperatorBlockEntity;
import io.github.metrovoc.residual.graph.ConfigField;
import io.github.metrovoc.residual.graph.Op;
import io.github.metrovoc.residual.menu.OperatorMenu;
import io.github.metrovoc.residual.net.MachineEdit;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jetbrains.annotations.Nullable;

/** A machine's screen: its name and settings, what the last check found, its pictures and its math. */
public class OperatorScreen extends AbstractContainerScreen<OperatorMenu> {
    private EditBox name;
    private String sentName = "";

    public OperatorScreen(OperatorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 248, 196);
    }

    @Nullable
    private OperatorBlockEntity machine() {
        return minecraft.level.getBlockEntity(menu.pos) instanceof OperatorBlockEntity be ? be : null;
    }

    @Override
    protected void init() {
        super.init();
        OperatorBlockEntity be = machine();
        if (be == null) {
            return;
        }
        name = addRenderableWidget(new EditBox(font, leftPos + 8, topPos + 20, 116, 16, Component.translatable("residual.machine.name")));
        name.setMaxLength(OperatorBlockEntity.NAME_LENGTH);
        name.setHint(Component.translatable("residual.machine.name_hint").withColor(Ui.DIM));
        name.setValue(be.name());
        sentName = be.name();

        List<ConfigField> fields = be.op().fields();
        for (int i = 0; i < fields.size(); i++) {
            ConfigField f = fields.get(i);
            List<String> shown = new ArrayList<>();
            for (String choice : f.choices()) {
                shown.add(display(f, choice));
            }
            addRenderableWidget(new Cycler(leftPos + 8, topPos + 40 + i * 18, 116, Component.translatable("residual.field." + f.key()),
                    shown, f.choices().indexOf(be.config().get(f.key())), index -> {
                        String value = f.choices().get(index);
                        // show the change at once; the server's update confirms it
                        be.config().set(f.key(), value);
                        ClientPacketDistributor.sendToServer(new MachineEdit(menu.pos, f.key(), value));
                    }));
        }
    }

    private static String display(ConfigField f, String value) {
        if (f.isToggle()) {
            return I18n.get("true".equals(value) ? "residual.field.on" : "residual.field.off");
        }
        String key = "residual.choice." + value;
        return I18n.exists(key) ? I18n.get(key) : value;
    }

    private void sendName() {
        if (name != null && !name.getValue().equals(sentName)) {
            sentName = name.getValue();
            ClientPacketDistributor.sendToServer(new MachineEdit(menu.pos, MachineEdit.NAME, sentName));
        }
    }

    @Override
    public void onClose() {
        sendName();
        super.onClose();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isEscape()) {
            return super.keyPressed(event);
        }
        if (name != null && name.isFocused()) {
            if (event.isConfirmation()) {
                sendName();
                name.setFocused(false);
                setFocused(null);
                return true;
            }
            return name.keyPressed(event) || name.canConsumeInput() || super.keyPressed(event);
        }
        return super.keyPressed(event);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractBackground(g, mouseX, mouseY, a);
        Ui.window(g, leftPos, topPos, imageWidth, imageHeight);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        OperatorBlockEntity be = machine();
        if (be == null) {
            return;
        }
        Op op = be.op();
        OperatorBlockEntity.Status status = be.status();
        Component title = Component.translatable("block.residual." + op.id);
        g.text(font, title, 8, 7, Ui.LABEL, false);
        String arity = I18n.get("residual.machine.arity." + op.arity);
        g.text(font, arity, imageWidth - 8 - font.width(arity), 7, Ui.LABEL_DIM, false);
        int mx = mouseX - leftPos, my = mouseY - topPos;
        if (Ui.inside(mx, my, 8, 6, font.width(title), 10)) {
            g.setTooltipForNextFrame(font.split(Component.translatable("residual.op." + op.id + ".desc"), 200), mouseX, mouseY);
        }

        // what the last check found: beside the pictures, then across the window below them
        int y = 42 + op.fields().size() * 18;
        if (!status.error().isEmpty()) {
            if (flow(g, status.error(), y, 0xFFB03030) && Ui.inside(mx, my, 8, y, 232, 128 - y)) {
                g.setTooltipForNextFrame(font.split(Component.literal(status.error()), 240), mouseX, mouseY);
            }
        } else if (!status.shape().isEmpty()) {
            g.text(font, Component.translatable("residual.machine.output", status.shape()), 8, y, 0xFF1F7A72, false);
            y += 11;
            if (status.parameters() > 0) {
                g.text(font, Component.translatable("residual.machine.parameters", Ui.count(status.parameters())), 8, y, Ui.LABEL, false);
                y += 11;
            }
            if (status.gradNorm() >= 0 && status.parameters() > 0) {
                g.text(font, Component.translatable("residual.machine.gradient", Ui.num(status.gradNorm())), 8, y, Ui.LABEL, false);
            }
        } else {
            flow(g, I18n.get("residual.machine.unchecked"), y, Ui.LABEL_DIM);
        }

        // pictures: what comes out of the front, and the main weight matrix on the sides
        g.text(font, Component.translatable(op == Op.ATTENTION || op == Op.TRANSFORMER ? "residual.machine.pattern" : "residual.machine.picture"),
                132, 22, Ui.LABEL_DIM, false);
        Ui.screen(g, 132, 32, 50, 50);
        Ui.heatmap(g, be.output(), 133, 33, 48, 48);
        boolean hasWeights = be.weights() != null || status.parameters() > 0;
        if (hasWeights) {
            g.text(font, Component.translatable("residual.machine.weights"), 190, 22, Ui.LABEL_DIM, false);
            Ui.screen(g, 190, 32, 50, 50);
            Ui.heatmap(g, be.weights(), 191, 33, 48, 48);
        }
        if (be.output() != null) {
            String scale = "±" + Ui.num(be.output().scale());
            g.text(font, scale, 132, 86, Ui.LABEL_DIM, false);
        }
        if (be.weights() != null) {
            String scale = "±" + Ui.num(be.weights().scale());
            g.text(font, scale, 190, 86, Ui.LABEL_DIM, false);
        }
        // the same machine as math
        g.text(font, Component.translatable("residual.machine.math"), 8, 128, Ui.LABEL_DIM, false);
        if (be.provided()) {
            String provided = I18n.get("residual.machine.provided");
            g.text(font, provided, imageWidth - 8 - font.width(provided), 128, Ui.LABEL_DIM, false);
        }
        Ui.screen(g, 8, 138, 232, 50);
        int ly = 142;
        for (String line : I18n.get(mathKey(op, be)).split("\n")) {
            for (FormattedCharSequence part : font.split(Component.literal(line), 226)) {
                if (ly > 178) {
                    break;
                }
                g.text(font, part, 12, ly, Ui.TEAL, false);
                ly += 10;
            }
        }
    }

    /** The translation key of the machine's computation, which for some machines depends on a setting. */
    private static String mathKey(Op op, OperatorBlockEntity be) {
        String key = "residual.op." + op.id + ".math";
        return switch (op) {
            case ACTIVATION -> key + "." + be.config().get("fn");
            case NORM -> key + "." + be.config().get("kind");
            case MATMUL -> be.config().flag("transpose") ? key + ".transposed" : key;
            default -> key;
        };
    }

    /** Writes text beside the pictures and then across the window below them; true if it had to be cut short. */
    private boolean flow(GuiGraphicsExtractor g, String text, int y, int color) {
        for (; !text.isEmpty(); y += 10) {
            int width = y < 96 ? 116 : 232;
            if (y + 10 > 118) {
                g.text(font, Ui.fit(font, text, width), 8, y, color, false);
                return font.width(text) > width;
            }
            int end = font.getSplitter().findLineBreak(text, width, Style.EMPTY);
            g.text(font, text.substring(0, end), 8, y, color, false);
            text = text.substring(end).stripLeading();
        }
        return false;
    }
}
