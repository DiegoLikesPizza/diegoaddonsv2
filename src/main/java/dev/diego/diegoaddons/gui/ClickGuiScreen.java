package dev.diego.diegoaddons.gui;

import dev.diego.diegoaddons.config.ConfigManager;
import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.Module;
import dev.diego.diegoaddons.module.ModuleManager;
import dev.diego.diegoaddons.module.Setting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

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
 *   <li><b>Right</b> - appears on right-click; the selected feature's settings.</li>
 * </ul>
 *
 * <p>Everything is drawn <b>supersampled</b>: we push a pose scaled by {@code 1/}{@link UiRender#SS}
 * and lay the whole window out in "units" (1 unit = 1/SS screen pixel), so corners, strokes and the
 * custom Poppins text resolve at SS x the GUI-scale resolution - genuinely high-res, not chunky.
 */
public class ClickGuiScreen extends Screen {
    private static final int S = UiRender.SS;

    // Layout metrics, all in hi-res units (visual size = unit / SS).
    private static final int PAD = 30;
    private static final int HEADER_H = 84;
    private static final int ROW_H = 50;
    private static final int ROW_GAP = 8;
    private static final int SET_ROW_H = 52;
    private static final int EYE_H = 34;
    private static final int CAT_W = 220;
    private static final int MOD_W = 460;
    private static final int SET_W = 360;
    private static final int COL_GAP = 30;
    private static final int RAD = 28;
    private static final int CARD_RAD = 14;
    private static final int CLOSE_SZ = 42;
    private static final int HUD_BTN_W = 158;

    private int catIndex = 0;
    private Module settingsModule;

    private final List<UiButton> headerButtons = new ArrayList<>();

    // Cached per-layout state (all in units).
    private List<Category> cats;
    private List<Module> mods;
    private List<Setting> sets;
    private int panelX, panelY, panelW, panelH;
    private int catX, modX, setX, bodyTop, listTop;
    private int closeX, closeY, closeSz;
    private int themeX, themeY, themeW, themeH;

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

        boolean threeCol = settingsModule != null;

        // --- Header cluster sizing (so nothing overlaps, whatever the font metrics) ---
        closeSz = CLOSE_SZ;
        int nameW = font.width(Fonts.t(Themes.current().name(), Fonts.UI_LABEL));
        themeW = 20 + 26 + 12 + nameW + 20;                    // padL + swatch + gap + name + padR
        int rightClusterW = themeW + 16 + HUD_BTN_W + 16 + closeSz;
        int titleW = font.width(Fonts.t("DiegoAddons", Fonts.UI_TITLE));
        int brandW = 48 + 18 + titleW;                          // mark + gap + title
        int headerW = PAD + brandW + 56 + rightClusterW + PAD;  // +56 breathing gap between the two

        int twoColW = PAD + CAT_W + COL_GAP + MOD_W + PAD;
        int contentW = twoColW + (threeCol ? COL_GAP + SET_W : 0);
        panelW = Math.max(contentW, headerW);

        int catColH = colH(cats.size(), ROW_H);
        int midColH = colH(mods.size(), ROW_H);
        int setColH = threeCol ? ROW_H + colH(Math.max(1, sets.size()), SET_ROW_H) : 0;
        int bodyH = EYE_H + Math.max(catColH, Math.max(midColH, setColH));

        int maxW = width * S;
        int maxH = height * S;
        panelH = Math.min(maxH - 40, HEADER_H + 16 + bodyH + PAD);

        // Anchor the column content centred so opening settings grows rightwards.
        panelX = (maxW - contentW) / 2;
        panelX = Math.max(20, Math.min(panelX, maxW - 20 - panelW));
        panelY = (maxH - panelH) / 2;

        catX = panelX + PAD;
        modX = catX + CAT_W + COL_GAP;
        setX = modX + MOD_W + COL_GAP;
        bodyTop = panelY + HEADER_H + 16;
        listTop = bodyTop + EYE_H;

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
    }

    private static int colH(int n, int rowH) {
        return n <= 0 ? 0 : n * rowH + (n - 1) * ROW_GAP;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        Theme t = Themes.current();
        boolean sm = ConfigManager.get().smoothCorners;
        boolean threeCol = settingsModule != null;
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
        vline(g, catX + CAT_W + COL_GAP / 2, divTop, divBot, Theme.withAlpha(t.border(), 0.8f));
        if (threeCol) {
            vline(g, modX + MOD_W + COL_GAP / 2, divTop, divBot, Theme.withAlpha(t.border(), 0.8f));
        }

        renderGroups(g, t, sm, mx, my);
        renderFeatures(g, t, sm, mx, my);
        if (threeCol) {
            renderSettings(g, t, sm, mx, my);
        }

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

        // Theme chip.
        boolean themeHover = UiRender.inside(mx, my, themeX, themeY, themeW, themeH);
        UiRender.fillRounded(g, themeX, themeY, themeW, themeH, 22, themeHover ? t.elevated() : t.surfaceAlt(), sm);
        UiRender.strokeRoundedThick(g, themeX, themeY, themeW, themeH, 22, t.border(), S, sm);
        int sw = 26, swx = themeX + 18, swy = themeY + (themeH - sw) / 2;
        UiRender.fillRoundedGradient(g, swx, swy, sw, sw, 8, t.accent(), t.accentTo(), sm);
        UiRender.textVC(g, font, Themes.current().name(), Fonts.UI_LABEL, Fonts.UI_LABEL_SZ,
                swx + sw + 12, themeY, themeH, t.text());

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

    private void renderGroups(GuiGraphicsExtractor g, Theme t, boolean sm, int mx, int my) {
        eyebrow(g, t, "GROUPS", catX + 4, bodyTop);
        for (int i = 0; i < cats.size(); i++) {
            int ry = listTop + i * (ROW_H + ROW_GAP);
            boolean selected = i == catIndex;
            boolean hover = UiRender.inside(mx, my, catX, ry, CAT_W, ROW_H);
            if (selected) {
                UiRender.fillRounded(g, catX, ry, CAT_W, ROW_H, CARD_RAD, t.elevated(), sm);
                UiRender.fillRounded(g, catX, ry + 12, 6, ROW_H - 24, 3, t.accent(), sm);
            } else if (hover) {
                UiRender.fillRounded(g, catX, ry, CAT_W, ROW_H, CARD_RAD, t.surfaceAlt(), sm);
            }
            int col = selected ? t.text() : (hover ? t.text() : t.textMuted());
            UiRender.textVC(g, font, cats.get(i).display, Fonts.UI_LABEL, Fonts.UI_LABEL_SZ,
                    catX + (selected ? 22 : 18), ry, ROW_H, col);
        }
    }

    private void renderFeatures(GuiGraphicsExtractor g, Theme t, boolean sm, int mx, int my) {
        Category cur = cats.isEmpty() ? Category.HUD : cats.get(catIndex);
        eyebrow(g, t, cur.display.toUpperCase(), modX + 4, bodyTop);
        for (int j = 0; j < mods.size(); j++) {
            Module m = mods.get(j);
            int ry = listTop + j * (ROW_H + ROW_GAP);
            boolean hover = UiRender.inside(mx, my, modX, ry, MOD_W, ROW_H);
            boolean on = m.isEnabled();
            boolean isSettings = m == settingsModule;

            UiRender.fillRounded(g, modX, ry, MOD_W, ROW_H, CARD_RAD,
                    hover || isSettings ? t.elevated() : t.surfaceAlt(), sm);
            if (isSettings) {
                UiRender.strokeRoundedThick(g, modX, ry, MOD_W, ROW_H, CARD_RAD, Theme.withAlpha(t.accent(), 0.8f), S, sm);
            }
            // Status dot.
            int dotC = on ? t.accent() : Theme.withAlpha(t.textFaint(), 0.7f);
            UiRender.circle(g, modX + 20, ry + ROW_H / 2, 5, dotC, sm);

            UiRender.textVC(g, font, m.name, Fonts.UI_LABEL, Fonts.UI_LABEL_SZ, modX + 36, ry, ROW_H,
                    on ? t.text() : t.textMuted());
            pill(g, modX + MOD_W - 20 - 46, ry + (ROW_H - 26) / 2, 46, 26, on, t, sm);
        }
    }

    private void renderSettings(GuiGraphicsExtractor g, Theme t, boolean sm, int mx, int my) {
        eyebrow(g, t, "SETTINGS", setX + 4, bodyTop);
        UiRender.textVC(g, font, settingsModule.name, Fonts.UI_LABEL, Fonts.UI_LABEL_SZ,
                setX + 4, listTop, ROW_H, t.text());
        int y0 = listTop + ROW_H;
        if (sets.isEmpty()) {
            UiRender.text(g, font, "No settings for this feature.", Fonts.UI_SMALL, setX + 4, y0 + 6, t.textFaint());
            return;
        }
        for (int k = 0; k < sets.size(); k++) {
            Setting s = sets.get(k);
            int ry = y0 + k * (SET_ROW_H + ROW_GAP);
            boolean hover = UiRender.inside(mx, my, setX, ry, SET_W, SET_ROW_H);
            UiRender.fillRounded(g, setX, ry, SET_W, SET_ROW_H, CARD_RAD, hover ? t.elevated() : t.surfaceAlt(), sm);
            UiRender.textVC(g, font, s.name, Fonts.UI_BODY, Fonts.UI_BODY_SZ, setX + 18, ry, SET_ROW_H, t.text());
            if (s instanceof BooleanSetting bs) {
                pill(g, setX + SET_W - 18 - 46, ry + (SET_ROW_H - 26) / 2, 46, 26, bs.get(), t, sm);
            }
        }
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

        if (UiRender.inside(mx, my, closeX, closeY, closeSz, closeSz)) {
            onClose();
            return true;
        }
        if (UiRender.inside(mx, my, themeX, themeY, themeW, themeH)) {
            List<Theme> all = Themes.ALL;
            int i = all.indexOf(Themes.current());
            Themes.select(all.get((i + 1) % all.size()));
            rebuildWidgets();
            return true;
        }
        for (UiButton b : headerButtons) {
            if (b.mouseClicked(mx, my, btn)) {
                return true;
            }
        }

        // Categories.
        for (int i = 0; i < cats.size(); i++) {
            if (UiRender.inside(mx, my, catX, listTop + i * (ROW_H + ROW_GAP), CAT_W, ROW_H)) {
                if (btn == 0) {
                    catIndex = i;
                    settingsModule = null;
                    rebuildWidgets();
                }
                return true;
            }
        }

        // Features.
        for (int j = 0; j < mods.size(); j++) {
            if (UiRender.inside(mx, my, modX, listTop + j * (ROW_H + ROW_GAP), MOD_W, ROW_H)) {
                Module m = mods.get(j);
                if (btn == 1) {
                    settingsModule = (settingsModule == m) ? null : m;
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
            for (int k = 0; k < sets.size(); k++) {
                if (UiRender.inside(mx, my, setX, y0 + k * (SET_ROW_H + ROW_GAP), SET_W, SET_ROW_H)) {
                    if (sets.get(k) instanceof BooleanSetting bs) {
                        bs.toggle();
                    }
                    return true;
                }
            }
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
