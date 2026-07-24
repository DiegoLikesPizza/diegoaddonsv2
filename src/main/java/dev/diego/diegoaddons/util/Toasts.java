package dev.diego.diegoaddons.util;

import dev.diego.diegoaddons.config.ConfigManager;
import dev.diego.diegoaddons.gui.Fonts;
import dev.diego.diegoaddons.gui.Theme;
import dev.diego.diegoaddons.gui.Themes;
import dev.diego.diegoaddons.gui.UiRender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

/**
 * Small notifications in the bottom-right corner, in the style of a desktop toast: they slide in,
 * hold, then slide back out. Used for confirmations that would otherwise be invisible, like copying
 * a chat message.
 *
 * <p>Toasts stack upwards from the corner, newest at the bottom, and are purely visual - they never
 * take input, so they cannot get in the way of whatever is on screen.
 */
public final class Toasts {
    private static final int W = 150;
    private static final int H = 30;
    private static final int MARGIN = 8;
    private static final int GAP = 4;
    private static final long SLIDE_MS = 220;
    private static final long HOLD_MS = 1800;
    private static final int MAX = 4;

    private record Toast(String title, String body, long start) {
    }

    private static final List<Toast> ACTIVE = new ArrayList<>();

    private Toasts() {
    }

    /** Queue a notification. Safe to call from anywhere; drawing happens on the render thread. */
    public static void show(String title, String body) {
        ACTIVE.add(new Toast(title, body, System.currentTimeMillis()));
        while (ACTIVE.size() > MAX) {
            ACTIVE.removeFirst();
        }
    }

    private static long life() {
        return SLIDE_MS * 2 + HOLD_MS;
    }

    /**
     * Draws and expires the active toasts. Called from the HUD and from this mod's own screens, so
     * a confirmation shows wherever it was triggered.
     */
    public static void render(GuiGraphicsExtractor g) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Theme t = Themes.current();
        boolean sm = ConfigManager.get().smoothCorners;
        long now = System.currentTimeMillis();
        ACTIVE.removeIf(toast -> now - toast.start() > life());

        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        for (int i = 0; i < ACTIVE.size(); i++) {
            Toast toast = ACTIVE.get(ACTIVE.size() - 1 - i);   // newest at the bottom
            long age = now - toast.start();

            // Slide in from off-screen right, hold, then slide back out.
            float p;
            if (age < SLIDE_MS) {
                p = age / (float) SLIDE_MS;
            } else if (age < SLIDE_MS + HOLD_MS) {
                p = 1f;
            } else {
                p = 1f - (age - SLIDE_MS - HOLD_MS) / (float) SLIDE_MS;
            }
            p = ease(Math.max(0f, Math.min(1f, p)));

            int x = Math.round(screenW - MARGIN - W * p);
            int y = screenH - MARGIN - H - i * (H + GAP);

            UiRender.dropShadow(g, x, y, W, H, 6, t.shadow(), 5, 2);
            UiRender.fillRounded(g, x, y, W, H, 6, (0xF2 << 24) | (t.surface() & 0x00FFFFFF), sm);
            UiRender.strokeRounded(g, x, y, W, H, 6, Theme.withAlpha(t.border(), 0.9f), sm);
            // Accent bar down the left edge, the way desktop toasts mark their kind.
            UiRender.fillRounded(g, x + 3, y + 6, 3, H - 12, 2, t.accent(), sm);

            UiRender.text(g, mc.font, toast.title(), Fonts.MEDIUM, x + 12, y + 6, t.text());
            if (toast.body() != null && !toast.body().isEmpty()) {
                UiRender.text(g, mc.font, clip(mc, toast.body()), Fonts.SMALL, x + 12, y + 17, t.textMuted());
            }
        }
    }

    /** Ease-out, so the slide settles rather than stopping dead. */
    private static float ease(float p) {
        return 1f - (1f - p) * (1f - p);
    }

    private static String clip(Minecraft mc, String s) {
        int max = W - 20;
        if (Fonts.width(mc.font, s, Fonts.SMALL) <= max) {
            return s;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            sb.append(s.charAt(i));
            if (Fonts.width(mc.font, sb + "…", Fonts.SMALL) > max) {
                sb.setLength(Math.max(0, sb.length() - 1));
                break;
            }
        }
        return sb + "…";
    }
}
