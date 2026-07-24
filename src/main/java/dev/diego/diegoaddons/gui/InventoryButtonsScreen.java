package dev.diego.diegoaddons.gui;

import dev.diego.diegoaddons.config.ConfigManager;
import dev.diego.diegoaddons.config.InventoryButton;
import dev.diego.diegoaddons.util.IconCatalogue;
import dev.diego.diegoaddons.util.InventoryButtons;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Editor for the inventory buttons.
 *
 * <p>Buttons are placed freely around a stand-in for the container GUI: drag to move, right-click to
 * remove, and the "Add" button drops a new one beside the menu.
 *
 * <p>On release a button anchors itself to whichever <b>corner of the menu it is nearest</b>, and its
 * position is stored relative to that corner. That is what keeps it in place across menus of
 * different sizes - a button parked under a six-row chest stays under a three-row one instead of
 * floating in the middle of it.
 *
 * <p>The icon is chosen from a searchable grid - warps and common actions as presets, or the full
 * item list - rather than typed as an id.
 */
public class InventoryButtonsScreen extends Screen {
    /** Vanilla container GUI size, so the layout matches the real thing. */
    private static final int GUI_W = 176;
    private static final int GUI_H = 166;
    private static final int SIZE = InventoryButtons.SIZE;
    private static final int STRIDE = SIZE + 2;
    private static final int PANEL_W = 208;
    private static final int PAD = 8;
    private static final int ICON_COLS = 9;

    private final Screen parent;

    private EditBox commandBox;
    private EditBox searchBox;
    private final List<UiButton> buttons = new ArrayList<>();

    private final List<Item> icons = new ArrayList<>();
    private final List<IconCatalogue.Entry> presets = new ArrayList<>();
    /** Picker mode: presets (warps and actions) or the full item registry. */
    private boolean presetTab = true;

    private InventoryButton selected;
    private InventoryButton dragging;
    private int dragDX, dragDY;
    private boolean snap = true;
    private int iconScroll;

    private int guiX, guiY, panelX, panelY, panelH, gridTop, gridRows;

    public InventoryButtonsScreen(Screen parent) {
        super(Component.literal("Inventory Buttons"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        buttons.clear();

        int totalW = GUI_W + 4 * STRIDE + 24 + PANEL_W;
        guiX = (width - totalW) / 2 + STRIDE * 2;
        guiY = (height - GUI_H) / 2;
        panelX = guiX + GUI_W + STRIDE * 2 + 24;
        panelY = guiY;
        panelH = GUI_H;

        commandBox = new EditBox(font, panelX + PAD, panelY + 26, PANEL_W - PAD * 2, 16,
                Component.literal("Command"));
        commandBox.setMaxLength(128);
        commandBox.setHint(Component.literal("ah"));
        commandBox.setResponder(v -> {
            if (selected != null) {
                selected.command = v;
            }
        });

        searchBox = new EditBox(font, panelX + PAD, panelY + 62, PANEL_W - PAD * 2, 16,
                Component.literal("Search"));
        searchBox.setMaxLength(64);
        searchBox.setHint(Component.literal("Search icons…"));
        searchBox.setResponder(v -> {
            iconScroll = 0;
            refreshIcons();
        });
        addRenderableWidget(commandBox);
        addRenderableWidget(searchBox);

        gridTop = panelY + 96;
        gridRows = Math.max(1, (panelY + panelH - 30 - gridTop) / STRIDE);

        int by = panelY + panelH - 24;
        UiButton add = new UiButton(panelX + PAD, by, 44, 20, "Add", UiButton.Kind.PRIMARY, this::addButton);
        UiButton big = new UiButton(add.x + 48, by, 52, 20, "2x", UiButton.Kind.SECONDARY, this::toggleGigantic);
        UiButton del = new UiButton(big.x + 56, by, 52, 20, "Delete", UiButton.Kind.SECONDARY, this::deleteSelected);
        UiButton done = new UiButton(panelX + PANEL_W - PAD - 44, by, 44, 20,
                "Done", UiButton.Kind.PRIMARY, this::onClose);
        buttons.add(add);
        buttons.add(big);
        buttons.add(del);
        buttons.add(done);

        refreshIcons();
        syncBoxes();
    }

    /** Drops a new button beside the menu, left of the top-left corner. */
    private void addButton() {
        int y = 0;
        while (occupied(-STRIDE, y) && y + SIZE < GUI_H) {
            y += STRIDE;
        }
        InventoryButton b = new InventoryButton(-STRIDE, y, "", "minecraft:chest");
        InventoryButtons.all().add(b);
        selected = b;
        syncBoxes();
        ConfigManager.save();
    }

    private boolean occupied(int x, int y) {
        return buttonAt(x, y) != null;
    }

    private void toggleGigantic() {
        if (selected != null) {
            selected.gigantic = !selected.gigantic;
            ConfigManager.save();
        }
    }

    private void deleteSelected() {
        if (selected != null) {
            InventoryButtons.all().remove(selected);
            selected = null;
            syncBoxes();
            ConfigManager.save();
        }
    }

    private void refreshIcons() {
        String q = searchBox == null ? "" : searchBox.getValue().toLowerCase(Locale.ROOT).trim();

        presets.clear();
        for (IconCatalogue.Entry e : IconCatalogue.all()) {
            if (q.isEmpty() || e.name().toLowerCase(Locale.ROOT).contains(q)
                    || e.command().toLowerCase(Locale.ROOT).contains(q)) {
                presets.add(e);
            }
        }

        icons.clear();
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR) {
                continue;
            }
            if (q.isEmpty() || BuiltInRegistries.ITEM.getKey(item).getPath().contains(q)) {
                icons.add(item);
            }
        }
    }

    /** How many entries the active picker tab holds. */
    private int pickerSize() {
        return presetTab ? presets.size() : icons.size();
    }

    private void syncBoxes() {
        boolean has = selected != null;
        commandBox.setValue(has ? selected.command : "");
        commandBox.active = has;
    }

    /** Where a button sits inside the editor, resolved against the corner it is anchored to. */
    private int buttonX(InventoryButton b) {
        return b.anchorRight ? guiX + GUI_W + b.x : guiX + b.x;
    }

    private int buttonY(InventoryButton b) {
        return b.anchorBottom ? guiY + GUI_H + b.y : guiY + b.y;
    }

    /**
     * Re-anchors a button to the menu corner it is nearest and rewrites its offset to match, so the
     * stored position means the same thing at any menu size.
     */
    private void reanchor(InventoryButton b, int screenX, int screenY) {
        int size = InventoryButtons.size(b);
        b.anchorRight = screenX + size / 2 > guiX + GUI_W / 2;
        b.anchorBottom = screenY + size / 2 > guiY + GUI_H / 2;
        b.x = screenX - (b.anchorRight ? guiX + GUI_W : guiX);
        b.y = screenY - (b.anchorBottom ? guiY + GUI_H : guiY);
    }

    private InventoryButton buttonAt(int ax, int ay) {
        for (InventoryButton b : InventoryButtons.all()) {
            if (b.x == ax && b.y == ay) {
                return b;
            }
        }
        return null;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        Theme t = Themes.current();
        boolean sm = ConfigManager.get().smoothCorners;
        g.fill(0, 0, width, height, t.overlay());

        // Stand-in for the container GUI.
        UiRender.dropShadow(g, guiX, guiY, GUI_W, GUI_H, 8, t.shadow(), 8, 4);
        UiRender.fillRounded(g, guiX, guiY, GUI_W, GUI_H, 8, t.surface(), sm);
        UiRender.strokeRounded(g, guiX, guiY, GUI_W, GUI_H, 8, t.border(), sm);
        UiRender.textCentered(g, font, "Your menu goes here", Fonts.SMALL,
                guiX + GUI_W / 2, guiY + GUI_H / 2 - 4, t.textFaint());

        for (InventoryButton b : InventoryButtons.all()) {
            int x = buttonX(b);
            int y = buttonY(b);
            int size = InventoryButtons.size(b);
            boolean hover = UiRender.inside(mouseX, mouseY, x, y, size, size);
            InventoryButtons.draw(g, minecraft, t, b, x, y, hover, false);
            if (b == selected) {
                UiRender.strokeRounded(g, x - 2, y - 2, size + 4, size + 4, 6, t.accent(), sm);
            }
        }

        renderPanel(g, t, sm, mouseX, mouseY);
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        for (UiButton b : buttons) {
            b.render(g, mouseX, mouseY, t, font, sm);
        }
    }

    private void renderPanel(GuiGraphicsExtractor g, Theme t, boolean sm, int mouseX, int mouseY) {
        UiRender.dropShadow(g, panelX, panelY, PANEL_W, panelH, 10, t.shadow(), 8, 4);
        UiRender.fillRounded(g, panelX, panelY, PANEL_W, panelH, 10, t.surface(), sm);
        UiRender.strokeRounded(g, panelX, panelY, PANEL_W, panelH, 10, t.border(), sm);

        if (selected == null) {
            UiRender.text(g, font, "INVENTORY BUTTONS", Fonts.SMALL, panelX + PAD, panelY + 8, t.textFaint());
            UiRender.text(g, font, "Click a free slot to add", Fonts.SMALL,
                    panelX + PAD, panelY + 28, t.textMuted());
            UiRender.text(g, font, "a button to the menu.", Fonts.SMALL,
                    panelX + PAD, panelY + 40, t.textMuted());
            UiRender.text(g, font, "Right-click one to remove it.", Fonts.SMALL,
                    panelX + PAD, panelY + 60, t.textFaint());
            return;
        }

        UiRender.text(g, font, "COMMAND", Fonts.SMALL, panelX + PAD, panelY + 12, t.textFaint());

        // Picker tabs.
        int tabW = (PANEL_W - PAD * 2) / 2;
        for (int i = 0; i < 2; i++) {
            boolean active = (i == 0) == presetTab;
            int tx = panelX + PAD + i * tabW;
            int ty = panelY + 46;
            UiRender.fillRounded(g, tx, ty, tabW - 2, 14, 4,
                    active ? Theme.withAlpha(t.accent(), 0.30f) : t.surfaceAlt(), sm);
            UiRender.textCentered(g, font, i == 0 ? "Presets" : "All items", Fonts.SMALL,
                    tx + (tabW - 2) / 2, ty + 3, active ? t.accent() : t.textMuted());
        }

        int shown = Math.min(pickerSize() - iconScroll * ICON_COLS, gridRows * ICON_COLS);
        for (int i = 0; i < Math.max(0, shown); i++) {
            int idx = iconScroll * ICON_COLS + i;
            int x = panelX + PAD + (i % ICON_COLS) * STRIDE;
            int y = gridTop + (i / ICON_COLS) * STRIDE;
            boolean hover = UiRender.inside(mouseX, mouseY, x, y, SIZE, SIZE);

            String iconId;
            if (presetTab) {
                iconId = presets.get(idx).icon();
            } else {
                iconId = BuiltInRegistries.ITEM.getKey(icons.get(idx)).toString();
            }
            boolean current = iconId.equals(selected.icon);
            if (hover || current) {
                UiRender.fillRounded(g, x, y, SIZE, SIZE, 3,
                        current ? Theme.withAlpha(t.accent(), 0.35f) : t.surfaceAlt(), sm);
            }
            g.item(InventoryButtons.icon(iconId), x + 1, y + 1);

            // A preset carries its command, so name it on hover - the grid alone is unreadable.
            if (hover && presetTab) {
                UiRender.text(g, font, presets.get(idx).name(), Fonts.SMALL,
                        panelX + PAD, panelY + panelH - 34, t.text());
            }
        }
        if (pickerSize() > gridRows * ICON_COLS) {
            UiRender.textRight(g, font, "scroll", Fonts.SMALL,
                    panelX + PANEL_W - PAD, panelY + panelH - 34, t.textFaint());
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();
        int btn = event.button();

        if (btn == 0) {
            for (UiButton b : buttons) {
                if (b.mouseClicked(mx, my, 0)) {
                    return true;
                }
            }
        }

        // Existing buttons: left selects and starts a drag, right removes.
        for (InventoryButton b : new ArrayList<>(InventoryButtons.all())) {
            int bx = buttonX(b);
            int by = buttonY(b);
            if (UiRender.inside(mx, my, bx, by, InventoryButtons.size(b), InventoryButtons.size(b))) {
                if (btn == 1) {
                    InventoryButtons.all().remove(b);
                    if (selected == b) {
                        selected = null;
                    }
                    ConfigManager.save();
                } else if (btn == 0) {
                    selected = b;
                    dragging = b;
                    dragDX = (int) (mx - bx);
                    dragDY = (int) (my - by);
                }
                syncBoxes();
                return true;
            }
        }

        if (btn == 0 && selected != null && pickIcon(mx, my)) {
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    private boolean pickIcon(double mx, double my) {
        // Tabs first.
        int tabW = (PANEL_W - PAD * 2) / 2;
        for (int i = 0; i < 2; i++) {
            if (UiRender.inside(mx, my, panelX + PAD + i * tabW, panelY + 46, tabW - 2, 14)) {
                presetTab = (i == 0);
                iconScroll = 0;
                return true;
            }
        }

        int start = iconScroll * ICON_COLS;
        for (int i = 0; i < gridRows * ICON_COLS; i++) {
            int idx = start + i;
            if (idx >= pickerSize()) {
                break;
            }
            int x = panelX + PAD + (i % ICON_COLS) * STRIDE;
            int y = gridTop + (i / ICON_COLS) * STRIDE;
            if (!UiRender.inside(mx, my, x, y, SIZE, SIZE)) {
                continue;
            }
            if (presetTab) {
                IconCatalogue.Entry e = presets.get(idx);
                selected.icon = e.icon();
                // A preset is icon plus command, so filling an empty command saves a step - but an
                // edited one is left alone.
                if (selected.command == null || selected.command.isBlank()) {
                    selected.command = e.command();
                    commandBox.setValue(e.command());
                }
            } else {
                Identifier id = BuiltInRegistries.ITEM.getKey(icons.get(idx));
                selected.icon = id.toString();
            }
            InventoryButtons.invalidateIcons();
            ConfigManager.save();
            return true;
        }
        return false;
    }

    /** Dragging moves the button freely; the grid keeps it tidy. */
    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging != null) {
            int nx = (int) Math.round(event.x() - dragDX);
            int ny = (int) Math.round(event.y() - dragDY);
            if (snap) {
                nx = guiX + Math.round((nx - guiX) / (float) STRIDE) * STRIDE;
                ny = guiY + Math.round((ny - guiY) / (float) STRIDE) * STRIDE;
            }
            reanchor(dragging, nx, ny);
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    /** On release the button keeps whichever corner it ended up nearest. */
    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging != null) {
            dragging = null;
            ConfigManager.save();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (selected != null && UiRender.inside(mx, my, panelX, gridTop, PANEL_W, gridRows * STRIDE)) {
            int maxScroll = Math.max(0, (pickerSize() + ICON_COLS - 1) / ICON_COLS - gridRows);
            iconScroll = Math.max(0, Math.min(maxScroll, iconScroll - (int) Math.signum(dy)));
            return true;
        }
        return super.mouseScrolled(mx, my, dx, dy);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        boolean typing = commandBox.isFocused() || searchBox.isFocused();
        if (!typing && event.key() == GLFW.GLFW_KEY_DELETE && selected != null) {
            InventoryButtons.all().remove(selected);
            selected = null;
            syncBoxes();
            ConfigManager.save();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        ConfigManager.save();
        InventoryButtons.invalidateIcons();
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
