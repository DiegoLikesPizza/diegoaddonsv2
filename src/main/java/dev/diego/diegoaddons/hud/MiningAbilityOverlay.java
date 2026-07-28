package dev.diego.diegoaddons.hud;

import com.render.api.GuiCanvas;
import com.render.api.RenderColor;
import com.render.api.RenderLibHud;
import dev.diego.diegoaddons.gui.Themes;
import dev.diego.diegoaddons.module.modules.MiningAbilityModule;
import net.minecraft.client.Minecraft;

import java.util.List;

/**
 * The mining ability reminder, drawn large across the middle of the screen rather than as a chip you
 * place yourself - it is a "look at this now" message, not a readout you park in a corner.
 *
 * <p>Because there is nothing to move or persist, this is an immediate HUD renderer instead of an
 * element of the managed layout: it is derived fresh each frame from the module's current line.
 */
public final class MiningAbilityOverlay {
    /** Text size, and how far above the centre of the screen the line sits. */
    private static final float TEXT_PX = 26f;
    private static final float ABOVE_CENTRE = 40f;

    private static boolean registered;

    private MiningAbilityOverlay() {
    }

    /** Registers the overlay once. Safe to call repeatedly. */
    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        RenderLibHud.register(MiningAbilityOverlay::render);
    }

    private static void render(GuiCanvas canvas, net.minecraft.client.DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options != null && mc.options.hideGui) {
            return;
        }
        // The hydration reminder wants the same spot for the same reason, so it shares the pass
        // rather than registering a second overlay that could draw over this one.
        dev.diego.diegoaddons.module.modules.HydrationReminderModule water =
                dev.diego.diegoaddons.module.modules.HydrationReminderModule.INSTANCE;
        if (water != null && water.isEnabled() && water.message() != null) {
            drawLines(canvas, List.of(water.message()), Themes.current().accent());
            return;
        }

        MiningAbilityModule module = MiningAbilityModule.INSTANCE;
        if (module == null || !module.isEnabled()) {
            return;
        }
        List<String> lines = module.hudLines(mc);
        if (lines.isEmpty()) {
            return;
        }
        drawLines(canvas, lines, module.color());
    }

    /** Draws the lines big, centred, with a dark copy behind so they read over bright terrain. */
    private static void drawLines(GuiCanvas canvas, List<String> lines, int argb) {

        int x = canvas.width() / 2;
        int y = Math.round(canvas.height() / 2f - ABOVE_CENTRE);
        RenderColor color = RenderColor.argb(argb);
        RenderColor shadow = RenderColor.argb(0xAA000000);
        for (String line : lines) {
            canvas.centeredText(line, x + 2, y + 2, shadow, HudElement.MEDIUM, TEXT_PX);
            canvas.centeredText(line, x, y, color, HudElement.MEDIUM, TEXT_PX);
            y += Math.round(TEXT_PX * 1.25f);
        }
    }
}
