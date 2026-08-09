package dev.diego.diegoaddons.hud;

import dev.diego.diegoaddons.gui.Fonts;
import dev.diego.diegoaddons.gui.Themes;
import dev.diego.diegoaddons.gui.UiRender;
import dev.diego.diegoaddons.module.modules.HydrationReminderModule;
import dev.diego.diegoaddons.module.modules.MiningAbilityModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;

/**
 * The messages that are shouted rather than displayed, drawn large across the middle of the screen.
 *
 * <p>These are not HUD elements and deliberately have no position: a mining ability coming up and a
 * reminder to drink are "look at this now" messages, and the middle of the screen is where you are
 * already looking. Giving them a place in the editor would be offering a choice that should not be
 * honoured, so they are drawn from the plain HUD pass instead - see
 * {@link dev.diego.diegoaddons.module.HudModule#placeable()}.
 *
 * <p>One pass for both, not two overlays: they want the same spot for the same reason, and two
 * registrations could draw one on top of the other. Hydration wins when both are up, because it is
 * the rarer of the two and the ability will still be there a second later.
 */
public final class CentreOverlay {

    /** Point size of the line, and how far above the middle of the screen it sits. */
    private static final int TEXT_SZ = 26;
    private static final int ABOVE_CENTRE = 40;
    /** Line spacing, as a share of the text size. */
    private static final float LINE_SPACING = 1.25f;
    /**
     * The natural size of {@link Fonts#TITLE}, which is the largest face in the HUD family.
     *
     * <p>Nothing in {@link UiRender} takes a size: a style <i>is</i> a size, and the HUD family tops
     * out well below what this message wants to be. So the pose carries the difference - the glyphs
     * are rasterised larger rather than a small line being stretched.
     */
    private static final float TITLE_PX = 15f;
    private static final float SCALE = TEXT_SZ / TITLE_PX;

    private CentreOverlay() {
    }

    /** Draws whichever message is up, if any. Called from the mod's HUD pass. */
    public static void render(GuiGraphicsExtractor g, Minecraft mc) {
        Font font = mc.font;
        if (font == null) {
            return;
        }

        HydrationReminderModule water = HydrationReminderModule.INSTANCE;
        if (water != null && water.isEnabled() && water.message() != null) {
            draw(g, font, mc, List.of(water.message()), Themes.current().accent());
            return;
        }

        MiningAbilityModule ability = MiningAbilityModule.INSTANCE;
        if (ability == null || !ability.isEnabled()) {
            return;
        }
        List<String> lines = ability.hudLines(mc);
        if (!lines.isEmpty()) {
            draw(g, font, mc, lines, ability.color());
        }
    }

    /** Centred, with a dark copy behind so the line still reads over bright terrain. */
    private static void draw(GuiGraphicsExtractor g, Font font, Minecraft mc, List<String> lines,
                             int argb) {
        int cx = mc.getWindow().getGuiScaledWidth() / 2;
        int y = mc.getWindow().getGuiScaledHeight() / 2 - ABOVE_CENTRE;
        int step = Math.round(TEXT_SZ * LINE_SPACING);
        for (String line : lines) {
            // Inside the scaled pose the offsets are in scaled units, so the 1px shadow step comes
            // out as roughly two screen pixels - which is what it should be at this size.
            g.pose().pushMatrix();
            g.pose().translate((float) cx, (float) y);
            g.pose().scale(SCALE, SCALE);
            UiRender.textCentered(g, font, line, Fonts.TITLE, 1, 1, 0xAA000000);
            UiRender.textCentered(g, font, line, Fonts.TITLE, 0, 0, argb);
            g.pose().popMatrix();
            y += step;
        }
    }
}
