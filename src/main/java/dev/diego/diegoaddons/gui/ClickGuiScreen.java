package dev.diego.diegoaddons.gui;

import dev.diego.diegoaddons.config.ConfigManager;
import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.KeybindSetting;
import dev.diego.diegoaddons.module.Module;
import dev.diego.diegoaddons.module.ModuleManager;
import dev.diego.diegoaddons.module.Setting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * The main ClickGUI: a custom-drawn, three-column window in the style of a modern web-app settings
 * panel.
 *
 * <ul>
 *   <li><b>Left</b> - the groups (categories).</li>
 *   <li><b>Middle</b> - the features of the selected group; left-click toggles, right-click opens
 *       that feature's settings.</li>
 *   <li><b>Right</b> - the selected feature's settings.</li>
 * </ul>
 *
 * <p>The window has a <b>fixed size</b>: it always occupies {@link #W_FRAC} x {@link #H_FRAC} of the
 * screen (about half its area) and never resizes as you click around. All three columns are always
 * present and the column widths are derived from the panel, so opening a feature's settings fills
 * the reserved right column instead of growing the window. Lists that do not fit scroll.
 *
 * <p>Everything is drawn <b>supersampled</b>: we push a pose scaled by {@code 1/}{@link UiRender#SS}
 * and lay the whole window out in "units" (1 unit = 1/SS screen pixel), so corners, strokes and the
 * custom Poppins text resolve at SS x the GUI-scale resolution - genuinely high-res, not chunky.
 */
public class ClickGuiScreen extends Screen {
    private static final int S = UiRender.SS;

    /**
     * Fixed window size as a fraction of the screen. 0.72 x 0.70 is about 50% of the screen area -
     * big enough to read comfortably, small enough to still see the game behind it.
     */
    private static final float W_FRAC = 0.72f;
    private static final float H_FRAC = 0.70f;

    // Layout metrics, all in hi-res units (visual size = unit / SS).
    private static final int PAD = 30;
    private static final int HEADER_H = 84;
    private static final int ROW_H = 50;
    private static final int ROW_GAP = 8;
    private static final int SET_ROW_H = 52;
    private static final int EYE_H = 34;
    private static final int COL_GAP = 30;
    private static final int RAD = 28;
    private static final int CARD_RAD = 14;
    private static final int CLOSE_SZ = 42;
    private static final int HUD_BTN_W = 158;
    private static final int BAR_W = 8;
    private static final int CARET_W = 20;      // room for the ▾ marker inside the theme chip
    private static final int MENU_ROW_H = 46;
    private static final int MENU_PAD = 6;

    // Relative column weights; the actual widths are these shares of the available inner width.
    private static final float CAT_SHARE = 220f / 1040f;
    private static final float SET_SHARE = 360f / 1040f;

    private int catIndex = 0;
    private Module settingsModule;
    private boolean themeOpen;
    /** Index into {@code sets} of the keybind row waiting for a key, or -1. */
    private int bindingRow = -1;

    // Scroll offsets, in whole rows, one per column.
    private int catScroll, modScroll, setScroll;

    private final List<UiButton> headerButtons = new ArrayList<>();

    // Cached per-layout state (all in units).
    private List<Category> cats;
    private List<Module> mods;
    private List<Setting> sets;
    private int panelX, panelY, panelW, panelH;
    private int catX, modX, setX, bodyTop, listTop, listBottom;
    private int catW, modW, setW;
    private int catRows, modRows, setRows;
    private int closeX, closeY, closeSz;
    private int themeX, themeY, themeW, themeH;
    private int menuX, menuY, menuW, menuH;

    public ClickGuiScreen() {
        super(Component.literal("DiegoAddons"));
    }

    @Override
    protected void init() {
        headerButtons.clear();
        cats = ModuleManager.categories();
        if (catIndex >= cats.size()) {
            catIndex = 0;
        }
        Category cur = cats.isEmpty() ? Category.HUD : cats.get(catIndex);
        mods = ModuleManager.modulesIn(cur);
        if (settingsModule != null && !mods.contains(settingsModule)) {
            settingsModule = null;
        }
        sets = settingsModule != null ? settingsModule.settings() : List.of();
        bindingRow = -1;

        int maxW = width * S;
        int maxH = height * S;

        // --- Header cluster sizing (so nothing overlaps, whatever the font metrics) ---
        closeSz = CLOSE_SZ;
        // Sized to the longest theme name, so picking a different theme never reflows the header.
        int nameW = 0;
        for (Theme th : Themes.ALL) {
            nameW = Math.max(nameW, font.width(Fonts.t(th.name(), Fonts.UI_LABEL)));
        }
        themeW = 20 + 26 + 12 + nameW + CARET_W + 16;          // padL + swatch + gap + name + caret + padR
        int rightClusterW = themeW + 16 + HUD_BTN_W + 16 + closeSz;
        int titleW = font.width(Fonts.t("DiegoAddons", Fonts.UI_TITLE));
        int brandW = 48 + 18 + titleW;                          // mark + gap + title
        int headerW = PAD + brandW + 56 + rightClusterW + PAD;  // +56 breathing gap between the two

        // Fixed size. The header width is a floor so the brand and the right cluster never collide
        // on very small screens; otherwise the window is exactly the configured fraction.
        panelW = Math.min(maxW - 40, Math.max(Math.round(maxW * W_FRAC), headerW));
        panelH = Math.min(maxH - 40, Math.round(maxH * H_FRAC));
        panelX = (maxW - panelW) / 2;
        panelY = (maxH - panelH) / 2;

        // All three columns always exist, so the window never has to grow when settings open.
        int inner = panelW - PAD * 2 - COL_GAP * 2;
        catW = Math.round(inner * CAT_SHARE);
        setW = Math.round(inner * SET_SHARE);
        modW = inner - catW - setW;

        catX = panelX + PAD;
        modX = catX + catW + COL_GAP;
        setX = modX + modW + COL_GAP;
        bodyTop = panelY + HEADER_H + 16;
        listTop = bodyTop + EYE_H;
        listBottom = panelY + panelH - PAD;

        catRows = fitRows(ROW_H, listTop);
        modRows = fitRows(ROW_H, listTop);
        setRows = fitRows(SET_ROW_H, listTop + ROW_H);   // the settings column has a title row first

        catScroll = clampScroll(catScroll, cats.size(), catRows);
        modScroll = clampScroll(modScroll, mods.size(), modRows);
        setScroll = clampScroll(setScroll, sets.size(), setRows);

        // Header right cluster, laid out from the right edge: [theme chip] [HUD Editor] [x].
        closeX = panelX + panelW - PAD - closeSz;
        closeY = panelY + (HEADER_H - closeSz) / 2;

        int bh = 48;
        int by = panelY + (HEADER_H - bh) / 2;
        int hudX = closeX - 16 - HUD_BTN_W;
        UiButton hud = new UiButton(hudX, by, HUD_BTN_W, bh, "HUD Editor", UiButton.Kind.SECONDARY,
                () -> minecraft.setScreen(new HudEditorScreen()));
        hud.hiRes = true;
        headerButtons.add(hud);

        themeH = bh;
        themeY = by;
        themeX = hudX - 16 - themeW;

        // The dropdown hangs under the chip, aligned to it.
        menuX = themeX;
        menuY = themeY + themeH + 8;
        menuW = themeW;
        menuH = MENU_PAD * 2 + Themes.ALL.size() * MENU_ROW_H;
    }

    /** How many rows of height {@code rowH} fit between {@code top} and the bottom of the window. */
    private int fitRows(int rowH, int top) {
        return Math.max(1, (listBottom - top + ROW_GAP) / (rowH + ROW_GAP));
    }

    private static int clampScroll(int scroll, int total, int visible) {
        return Math.max(0, Math.min(scroll, Math.max(0, total - visible)));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        Theme t = Themes.current();
        boolean sm = ConfigManager.get().smoothCorners;
        int mx = mouseX * S, my = mouseY * S;

        // Full-screen scrim (normal space), then everything else supersampled.
        g.fill(0, 0, width, height, t.overlay());
        UiRender.beginHiRes(g);

        // Window shell.
        UiRender.dropShadow(g, panelX, panelY, panelW, panelH, RAD, t.shadow(), 18, 10);
        UiRender.fillRounded(g, panelX, panelY, panelW, panelH, RAD, t.surface(), sm);
        UiRender.strokeRoundedThick(g, panelX, panelY, panelW, panelH, RAD, t.border(), S, sm);
        // A faint accent hairline along the very top edge for a premium sheen.
        UiRender.fillRounded(g, panelX + RAD, panelY + S, panelW - RAD * 2, S, S,
                Theme.withAlpha(t.accent(), 0.5f), sm);

        renderHeader(g, t, sm, mx, my);

        // Column dividers.
        int divTop = bodyTop - 4;
        int divBot = panelY + panelH - PAD;
        vline(g, catX + catW + COL_GAP / 2, divTop, divBot, Theme.withAlpha(t.border(), 0.8f));
        vline(g, modX + modW + COL_GAP / 2, divTop, divBot, Theme.withAlpha(t.border(), 0.8f));

        renderGroups(g, t, sm, mx, my);
        renderFeatures(g, t, sm, mx, my);
        renderSettings(g, t, sm, mx, my);

        // Last, so the open dropdown floats above the columns instead of being drawn under them.
        renderThemeMenu(g, t, sm, mx, my);

        UiRender.endHiRes(g);
    }

    private void renderHeader(GuiGraphicsExtractor g, Theme t, boolean sm, int mx, int my) {
        // Brand mark: a rounded accent tile with a "D", plus a soft glow.
        int mark = 48;
        int markX = panelX + PAD;
        int markY = panelY + (HEADER_H - mark) / 2;
        UiRender.glow(g, markX, markY, mark, mark, 14, t.accent(), 12, 0.12f);
        UiRender.fillRoundedGradient(g, markX, markY, mark, mark, 14, t.accent(), t.accentTo(), sm);
        UiRender.textCenteredVC(g, font, "D", Fonts.UI_TITLE, Fonts.UI_TITLE_SZ, markX + mark / 2, markY, mark, t.accentText());

        int tx = markX + mark + 18;
        UiRender.textVC(g, font, "DiegoAddons", Fonts.UI_TITLE, Fonts.UI_TITLE_SZ, tx,
                panelY, HEADER_H, t.text());

        // Theme chip - the closed state of the theme dropdown.
        boolean themeHover = UiRender.inside(mx, my, themeX, themeY, themeW, themeH);
        UiRender.fillRounded(g, themeX, themeY, themeW, themeH, 22,
                themeHover || themeOpen ? t.elevated() : t.surfaceAlt(), sm);
        UiRender.strokeRoundedThick(g, themeX, themeY, themeW, themeH, 22,
                themeOpen ? Theme.withAlpha(t.accent(), 0.8f) : t.border(), S, sm);
        int sw = 26, swx = themeX + 18, swy = themeY + (themeH - sw) / 2;
        UiRender.fillRoundedGradient(g, swx, swy, sw, sw, 8, t.accent(), t.accentTo(), sm);
        UiRender.textVC(g, font, Themes.current().name(), Fonts.UI_LABEL, Fonts.UI_LABEL_SZ,
                swx + sw + 12, themeY, themeH, t.text());
        UiRender.textCenteredVC(g, font, themeOpen ? "▴" : "▾", Fonts.UI_LABEL, Fonts.UI_LABEL_SZ,
                themeX + themeW - 16 - CARET_W / 2, themeY, themeH,
                themeOpen ? t.accent() : t.textMuted());

        for (UiButton b : headerButtons) {
            b.render(g, mx, my, t, font, sm);
        }

        // Close.
        boolean closeHover = UiRender.inside(mx, my, closeX, closeY, closeSz, closeSz);
        if (closeHover) {
            UiRender.fillRounded(g, closeX, closeY, closeSz, closeSz, 12, t.elevated(), sm);
            UiRender.strokeRoundedThick(g, closeX, closeY, closeSz, closeSz, 12, t.border(), S, sm);
        }
        UiRender.textCenteredVC(g, font, "×", Fonts.UI_TITLE, Fonts.UI_TITLE_SZ,
                closeX + closeSz / 2, closeY, closeSz, closeHover ? t.text() : t.textMuted());
    }

    /**
     * The open theme dropdown: one row per theme, each showing that theme's own accent swatch so the
     * list previews what you are picking. The active theme is marked with an accent bar and a tick.
     */
    private void renderThemeMenu(GuiGraphicsExtractor g, Theme t, boolean sm, int mx, int my) {
        if (!themeOpen) {
            return;
        }
        UiRender.dropShadow(g, menuX, menuY, menuW, menuH, 16, t.shadow(), 12, 6);
        UiRender.fillRounded(g, menuX, menuY, menuW, menuH, 16, t.elevated(), sm);
        UiRender.strokeRoundedThick(g, menuX, menuY, menuW, menuH, 16, t.border(), S, sm);

        Theme cur = Themes.current();
        for (int i = 0; i < Themes.ALL.size(); i++) {
            Theme th = Themes.ALL.get(i);
            int ry = menuY + MENU_PAD + i * MENU_ROW_H;
            boolean hover = UiRender.inside(mx, my, menuX + MENU_PAD, ry, menuW - MENU_PAD * 2, MENU_ROW_H);
            boolean active = th == cur;
            if (hover || active) {
                UiRender.fillRounded(g, menuX + MENU_PAD, ry, menuW - MENU_PAD * 2, MENU_ROW_H, 10,
                        active ? t.surfaceAlt() : Theme.withAlpha(t.surfaceAlt(), 0.6f), sm);
            }
            if (active) {
                UiRender.fillRounded(g, menuX + MENU_PAD, ry + 10, 5, MENU_ROW_H - 20, 3, t.accent(), sm);
            }
            // Each row previews its own theme's accent, not the active one.
            int sw = 22;
            int swx = menuX + MENU_PAD + 16;
            UiRender.fillRoundedGradient(g, swx, ry + (MENU_ROW_H - sw) / 2, sw, sw, 7,
                    th.accent(), th.accentTo(), sm);
            UiRender.textVC(g, font, th.name(), Fonts.UI_LABEL, Fonts.UI_LABEL_SZ,
                    swx + sw + 12, ry, MENU_ROW_H, active || hover ? t.text() : t.textMuted());
            if (active) {
                UiRender.textCenteredVC(g, font, "✓", Fonts.UI_LABEL, Fonts.UI_LABEL_SZ,
                        menuX + menuW - MENU_PAD - 18, ry, MENU_ROW_H, t.accent());
            }
        }
    }

    private void renderGroups(GuiGraphicsExtractor g, Theme t, boolean sm, int mx, int my) {
        eyebrow(g, t, "GROUPS", catX + 4, bodyTop);
        int end = Math.min(cats.size(), catScroll + catRows);
        for (int i = catScroll; i < end; i++) {
            int ry = listTop + (i - catScroll) * (ROW_H + ROW_GAP);
            boolean selected = i == catIndex;
            boolean hover = UiRender.inside(mx, my, catX, ry, catW, ROW_H);
            if (selected) {
                UiRender.fillRounded(g, catX, ry, catW, ROW_H, CARD_RAD, t.elevated(), sm);
                UiRender.fillRounded(g, catX, ry + 12, 6, ROW_H - 24, 3, t.accent(), sm);
            } else if (hover) {
                UiRender.fillRounded(g, catX, ry, catW, ROW_H, CARD_RAD, t.surfaceAlt(), sm);
            }
            int col = selected || hover ? t.text() : t.textMuted();
            UiRender.textVC(g, font, cats.get(i).display, Fonts.UI_LABEL, Fonts.UI_LABEL_SZ,
                    catX + (selected ? 22 : 18), ry, ROW_H, col);
        }
        scrollbar(g, t, sm, catX + catW - BAR_W, listTop, ROW_H, cats.size(), catRows, catScroll);
    }

    private void renderFeatures(GuiGraphicsExtractor g, Theme t, boolean sm, int mx, int my) {
        Category cur = cats.isEmpty() ? Category.HUD : cats.get(catIndex);
        eyebrow(g, t, cur.display.toUpperCase(), modX + 4, bodyTop);
        int end = Math.min(mods.size(), modScroll + modRows);
        for (int j = modScroll; j < end; j++) {
            Module m = mods.get(j);
            int ry = listTop + (j - modScroll) * (ROW_H + ROW_GAP);
            boolean hover = UiRender.inside(mx, my, modX, ry, modW, ROW_H);
            boolean on = m.isEnabled();
            boolean isSettings = m == settingsModule;

            UiRender.fillRounded(g, modX, ry, modW, ROW_H, CARD_RAD,
                    hover || isSettings ? t.elevated() : t.surfaceAlt(), sm);
            if (isSettings) {
                UiRender.strokeRoundedThick(g, modX, ry, modW, ROW_H, CARD_RAD, Theme.withAlpha(t.accent(), 0.8f), S, sm);
            }
            // Status dot.
            int dotC = on ? t.accent() : Theme.withAlpha(t.textFaint(), 0.7f);
            UiRender.circle(g, modX + 20, ry + ROW_H / 2, 5, dotC, sm);

            UiRender.textVC(g, font, m.name, Fonts.UI_LABEL, Fonts.UI_LABEL_SZ, modX + 36, ry, ROW_H,
                    on ? t.text() : t.textMuted());
            pill(g, modX + modW - 20 - 46, ry + (ROW_H - 26) / 2, 46, 26, on, t, sm);
        }
        scrollbar(g, t, sm, modX + modW - BAR_W, listTop, ROW_H, mods.size(), modRows, modScroll);
    }

    private void renderSettings(GuiGraphicsExtractor g, Theme t, boolean sm, int mx, int my) {
        eyebrow(g, t, "SETTINGS", setX + 4, bodyTop);
        if (settingsModule == null) {
            UiRender.text(g, font, "Right-click a feature", Fonts.UI_SMALL, setX + 4, listTop + 6, t.textFaint());
            UiRender.text(g, font, "to see its settings.", Fonts.UI_SMALL, setX + 4, listTop + 32, t.textFaint());
            return;
        }
        UiRender.textVC(g, font, settingsModule.name, Fonts.UI_LABEL, Fonts.UI_LABEL_SZ,
                setX + 4, listTop, ROW_H, t.text());
        int y0 = listTop + ROW_H;
        if (sets.isEmpty()) {
            UiRender.text(g, font, "No settings for this feature.", Fonts.UI_SMALL, setX + 4, y0 + 6, t.textFaint());
            return;
        }
        int end = Math.min(sets.size(), setScroll + setRows);
        for (int k = setScroll; k < end; k++) {
            Setting s = sets.get(k);
            int ry = y0 + (k - setScroll) * (SET_ROW_H + ROW_GAP);
            boolean hover = UiRender.inside(mx, my, setX, ry, setW, SET_ROW_H);
            UiRender.fillRounded(g, setX, ry, setW, SET_ROW_H, CARD_RAD, hover ? t.elevated() : t.surfaceAlt(), sm);
            UiRender.textVC(g, font, s.name, Fonts.UI_BODY, Fonts.UI_BODY_SZ, setX + 18, ry, SET_ROW_H, t.text());
            if (s instanceof BooleanSetting bs) {
                pill(g, setX + setW - 18 - 46, ry + (SET_ROW_H - 26) / 2, 46, 26, bs.get(), t, sm);
            } else if (s instanceof KeybindSetting ks) {
                keyChip(g, t, sm, ks, ry, k == bindingRow);
            }
        }
        scrollbar(g, t, sm, setX + setW - BAR_W, y0, SET_ROW_H, sets.size(), setRows, setScroll);
    }

    /**
     * A slim scroll indicator down the right edge of a column, drawn only when the list is longer
     * than the fixed window can show.
     */
    private void scrollbar(GuiGraphicsExtractor g, Theme t, boolean sm, int x, int top, int rowH,
                           int total, int visible, int scroll) {
        if (total <= visible) {
            return;
        }
        int h = visible * rowH + (visible - 1) * ROW_GAP;
        UiRender.fillRounded(g, x, top, BAR_W, h, BAR_W / 2, Theme.withAlpha(t.textFaint(), 0.25f), sm);
        int thumbH = Math.max(BAR_W * 3, h * visible / total);
        int thumbY = top + (h - thumbH) * scroll / (total - visible);
        UiRender.fillRounded(g, x, thumbY, BAR_W, thumbH, BAR_W / 2, Theme.withAlpha(t.accent(), 0.75f), sm);
    }

    /**
     * The key chip on a keybind row: shows the bound key, or "Press a key…" while it is listening.
     * Clicking it starts listening; the next key pressed is bound (Escape clears it).
     */
    private void keyChip(GuiGraphicsExtractor g, Theme t, boolean sm, KeybindSetting ks, int ry, boolean listening) {
        String label = listening ? "Press a key…" : ks.display();
        int w = Math.max(96, font.width(Fonts.t(label, Fonts.UI_BODY)) + 28);
        int h = 32;
        int x = setX + setW - 18 - w;
        int y = ry + (SET_ROW_H - h) / 2;
        UiRender.fillRounded(g, x, y, w, h, 10,
                listening ? Theme.withAlpha(t.accent(), 0.22f) : t.surface(), sm);
        UiRender.strokeRoundedThick(g, x, y, w, h, 10,
                listening ? t.accent() : Theme.withAlpha(t.border(), 0.9f), S, sm);
        int col = listening ? t.accent() : (ks.isBound() ? t.text() : t.textFaint());
        UiRender.textCenteredVC(g, font, label, Fonts.UI_BODY, Fonts.UI_BODY_SZ, x + w / 2, y, h, col);
    }

    private void eyebrow(GuiGraphicsExtractor g, Theme t, String s, int x, int y) {
        UiRender.text(g, font, s, Fonts.UI_EYEBROW, x, y, t.textFaint());
    }

    private void vline(GuiGraphicsExtractor g, int x, int y0, int y1, int color) {
        g.fill(x, y0, x + S, y1, color);
    }

    private void pill(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean on, Theme t, boolean sm) {
        if (on) {
            UiRender.fillRoundedGradient(g, x, y, w, h, h / 2, t.accent(), t.accentTo(), sm);
        } else {
            UiRender.fillRounded(g, x, y, w, h, h / 2, Theme.withAlpha(t.textFaint(), 0.5f), sm);
        }
        int knobR = h - 8;
        int knobX = on ? (x + w - knobR - 4) : (x + 4);
        UiRender.circle(g, knobX + knobR / 2, y + 4 + knobR / 2, knobR / 2, on ? t.accentText() : t.text(), sm);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int mx = (int) Math.round(event.x() * S);
        int my = (int) Math.round(event.y() * S);
        int btn = event.button();

        // While the dropdown is open it owns the next click: either a pick, or a click-away to close.
        if (themeOpen) {
            if (UiRender.inside(mx, my, menuX, menuY, menuW, menuH)) {
                for (int i = 0; i < Themes.ALL.size(); i++) {
                    int ry = menuY + MENU_PAD + i * MENU_ROW_H;
                    if (UiRender.inside(mx, my, menuX + MENU_PAD, ry, menuW - MENU_PAD * 2, MENU_ROW_H)) {
                        Themes.select(Themes.ALL.get(i));
                        themeOpen = false;
                        rebuildWidgets();
                        break;
                    }
                }
                return true;
            }
            themeOpen = false;
            if (UiRender.inside(mx, my, themeX, themeY, themeW, themeH)) {
                return true;   // clicking the chip again just closes it
            }
            // Otherwise fall through, so the click still lands on whatever was under it.
        }

        if (UiRender.inside(mx, my, closeX, closeY, closeSz, closeSz)) {
            onClose();
            return true;
        }
        if (UiRender.inside(mx, my, themeX, themeY, themeW, themeH)) {
            themeOpen = true;
            return true;
        }
        for (UiButton b : headerButtons) {
            if (b.mouseClicked(mx, my, btn)) {
                return true;
            }
        }

        // Categories.
        int catEnd = Math.min(cats.size(), catScroll + catRows);
        for (int i = catScroll; i < catEnd; i++) {
            if (UiRender.inside(mx, my, catX, listTop + (i - catScroll) * (ROW_H + ROW_GAP), catW, ROW_H)) {
                if (btn == 0) {
                    catIndex = i;
                    settingsModule = null;
                    modScroll = 0;
                    setScroll = 0;
                    rebuildWidgets();
                }
                return true;
            }
        }

        // Features.
        int modEnd = Math.min(mods.size(), modScroll + modRows);
        for (int j = modScroll; j < modEnd; j++) {
            if (UiRender.inside(mx, my, modX, listTop + (j - modScroll) * (ROW_H + ROW_GAP), modW, ROW_H)) {
                Module m = mods.get(j);
                if (btn == 1) {
                    settingsModule = (settingsModule == m) ? null : m;
                    setScroll = 0;
                    rebuildWidgets();
                } else if (btn == 0) {
                    ModuleManager.toggle(m);
                }
                return true;
            }
        }

        // Settings.
        if (settingsModule != null && btn == 0) {
            int y0 = listTop + ROW_H;
            int setEnd = Math.min(sets.size(), setScroll + setRows);
            for (int k = setScroll; k < setEnd; k++) {
                if (UiRender.inside(mx, my, setX, y0 + (k - setScroll) * (SET_ROW_H + ROW_GAP), setW, SET_ROW_H)) {
                    if (sets.get(k) instanceof BooleanSetting bs) {
                        bs.toggle();
                    } else if (sets.get(k) instanceof KeybindSetting) {
                        bindingRow = (bindingRow == k) ? -1 : k;   // click again to cancel
                    }
                    return true;
                }
            }
        }

        return super.mouseClicked(event, doubleClick);
    }

    /**
     * While a keybind row is listening, the next key press is captured as that binding (Escape
     * clears it) instead of reaching the screen. Otherwise Escape closes the theme dropdown first,
     * and only then the whole screen.
     */
    @Override
    public boolean keyPressed(KeyEvent event) {
        if (bindingRow >= 0 && bindingRow < sets.size()
                && sets.get(bindingRow) instanceof KeybindSetting ks) {
            if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
                ks.clear();
            } else {
                ks.set(event.key());
            }
            bindingRow = -1;
            return true;
        }
        if (themeOpen && event.key() == GLFW.GLFW_KEY_ESCAPE) {
            themeOpen = false;
            return true;
        }
        return super.keyPressed(event);
    }

    /** Scrolls whichever column the cursor is over; the window itself never resizes. */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int mx = (int) Math.round(mouseX * S);
        int my = (int) Math.round(mouseY * S);
        int step = scrollY > 0 ? -1 : (scrollY < 0 ? 1 : 0);
        if (step == 0 || my < listTop - EYE_H || my > listBottom) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        if (mx >= catX && mx < catX + catW) {
            catScroll = clampScroll(catScroll + step, cats.size(), catRows);
            return true;
        }
        if (mx >= modX && mx < modX + modW) {
            modScroll = clampScroll(modScroll + step, mods.size(), modRows);
            return true;
        }
        if (mx >= setX && mx < setX + setW) {
            setScroll = clampScroll(setScroll + step, sets.size(), setRows);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
