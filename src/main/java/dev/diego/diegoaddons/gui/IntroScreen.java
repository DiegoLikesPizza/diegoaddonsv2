package dev.diego.diegoaddons.gui;

import dev.diego.diegoaddons.DiegoAddonsV2Client;
import dev.diego.diegoaddons.config.ConfigManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * One-time welcome screen shown the first time the mod runs in an instance. Features a round custom
 * loading spinner, a short intro, and the current keybind. Dismissing it records
 * {@code introShown = true} so it never re-appears unless replayed. Drawn supersampled (see
 * {@link DiegoScreen}); all coordinates below are in hi-res units.
 */
public class IntroScreen extends DiegoScreen {
    private final Screen parent;

    public IntroScreen(Screen parent) {
        super(Component.literal("Welcome"));
        this.parent = parent;
    }

    @Override
    protected String subtitle() {
        return "DiegoAddons V2";
    }

    @Override
    protected int desiredWidth() {
        return 370;
    }

    @Override
    protected int desiredHeight() {
        return 350;
    }

    private void finish() {
        ConfigManager.get().introShown = true;
        ConfigManager.save();
    }

    @Override
    protected void layout() {
        int left = panelX + PAD;
        int innerW = panelW - PAD * 2;
        int gap = 18;
        int halfW = (innerW - gap) / 2;
        int bh = 56;
        int by = panelY + panelH - PAD - bh;

        UiButton open = new UiButton(left, by, halfW, bh, "Open menu", UiButton.Kind.SECONDARY, () -> {
            finish();
            minecraft.setScreen(new ClickGuiScreen());
        });
        UiButton go = new UiButton(left + halfW + gap, by, halfW, bh, "Get started", UiButton.Kind.PRIMARY, () -> {
            finish();
            minecraft.setScreen(parent);
        });
        open.hiRes = true;
        go.hiRes = true;
        widgets.add(open);
        widgets.add(go);
    }

    @Override
    protected void renderBody(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        Theme t = theme();
        int cx = panelX + panelW / 2;
        int left = panelX + PAD;
        int innerW = panelW - PAD * 2;
        int y = contentTop() + 10;

        // Round loading spinner.
        UiRender.spinner(g, cx, y + 26, 26, 4, t.accent(), Theme.withAlpha(t.textFaint(), 0.5f),
                System.currentTimeMillis(), smooth());
        y += 66;

        UiRender.textCenteredVC(g, font, "Thanks for installing!", Fonts.UI_LABEL, Fonts.UI_LABEL_SZ,
                cx, y, Fonts.UI_LABEL_SZ, t.accent());
        y += 40;

        for (String line : wrap("A clean, rounded ClickGUI with a custom font, five themes, and "
                + "grouped modules with per-feature settings. Right-click a feature for its options.", innerW)) {
            UiRender.textCentered(g, font, line, Fonts.UI_BODY, cx, y, t.textMuted());
            y += 30;
        }
        y += 16;

        String key = DiegoAddonsV2Client.OPEN_MENU.getTranslatedKeyMessage().getString();
        bullet(g, t, left, y, "Five built-in themes - switch any time");
        y += 34;
        bullet(g, t, left, y, "Press [" + key + "] in-game to open the menu");
        y += 34;
        bullet(g, t, left, y, "Live modules: FPS, coordinates, and more");
        y += 44;

        // Theme swatches.
        int sw = 30, sgap = 14;
        int total = Themes.ALL.size() * sw + (Themes.ALL.size() - 1) * sgap;
        int swx = cx - total / 2;
        for (Theme th : Themes.ALL) {
            UiRender.fillRoundedGradient(g, swx, y, sw, sw, 9, th.accent(), th.accentTo(), smooth());
            if (th.name().equals(t.name())) {
                UiRender.strokeRoundedThick(g, swx - 4, y - 4, sw + 8, sw + 8, 12, t.text(), S, smooth());
            }
            swx += sw + sgap;
        }
    }

    private void bullet(GuiGraphicsExtractor g, Theme t, int x, int y, String text) {
        UiRender.circle(g, x + 5, y + Fonts.UI_BODY_SZ / 3, 4, t.accent(), smooth());
        UiRender.text(g, font, text, Fonts.UI_BODY, x + 20, y, t.textMuted());
    }

    private List<String> wrap(String s, int maxW) {
        List<String> lines = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : s.split(" ")) {
            String test = cur.isEmpty() ? word : cur + " " + word;
            if (font.width(Fonts.t(test, Fonts.UI_BODY)) <= maxW) {
                cur = new StringBuilder(test);
            } else {
                if (!cur.isEmpty()) {
                    lines.add(cur.toString());
                }
                cur = new StringBuilder(word);
            }
        }
        if (!cur.isEmpty()) {
            lines.add(cur.toString());
        }
        return lines;
    }

    @Override
    public void onClose() {
        finish();
        minecraft.setScreen(parent);
    }
}
