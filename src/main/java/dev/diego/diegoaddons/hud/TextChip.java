package dev.diego.diegoaddons.hud;

import dev.diego.configlib.hud.HudStyle;
import dev.diego.configlib.hud.HudTemplate;
import dev.diego.diegoaddons.module.HudModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;

/**
 * The text half of the HUD: a module's lines, on the plate its style asks for.
 *
 * <p>This replaces configlib's {@code labelValue} prefab, which could not do three things the
 * modules need.
 *
 * <ul>
 *   <li><b>More than one line.</b> {@code labelValue} is a caption and a value, so a module that
 *       builds several rows in {@link HudModule#hudLines} - the performance readout is four -
 *       had only its {@code value()} asked for, which those modules return null from. The chip
 *       showed the label and nothing else.</li>
 *   <li><b>Settings that change.</b> The label was read once, while the element was being
 *       registered, so "Show label" did nothing until the game was restarted. Everything here is
 *       read per frame.</li>
 *   <li><b>Centring.</b> {@code labelValue} is always flush left; {@code Centered} had nothing
 *       reading it at all.</li>
 * </ul>
 */
public final class TextChip extends HudTemplate {

    private final HudModule module;

    public TextChip(HudModule module) {
        this.module = module;
    }

    /**
     * What to draw this frame.
     *
     * <p>Falls back to the editor's sample when there is nothing live, so the element still has a
     * size to be dragged by in a lobby. The live HUD does not reach that case - {@link
     * #shouldRender()} has already hidden it.
     */
    private List<String> lines() {
        Minecraft mc = Minecraft.getInstance();
        List<String> live = module.hudLines(mc);
        return live.isEmpty() ? module.editorLines(mc) : live;
    }

    /** Whether this frame is the single line of a label-and-value module, drawn in two colours. */
    private boolean labelled(List<String> lines) {
        return lines.size() == 1 && module.showLabel() && !module.hudLabel().isEmpty();
    }

    @Override
    public boolean shouldRender() {
        return !module.hudLines(Minecraft.getInstance()).isEmpty();
    }

    @Override
    protected int contentWidth(HudStyle s) {
        int w = 1;
        for (String line : lines()) {
            w = Math.max(w, textWidth(line, s.font()));
        }
        return w;
    }

    @Override
    protected int contentHeight(HudStyle s) {
        return Math.max(1, lines().size()) * s.lineHeight();
    }

    @Override
    protected void renderContent(GuiGraphicsExtractor g, HudStyle s) {
        List<String> lines = lines();
        int width = contentWidth(s);
        boolean twoTone = labelled(lines);
        int y = 1;
        for (String line : lines) {
            int x = module.isCentered() ? (width - textWidth(line, s.font())) / 2 : 0;
            if (twoTone) {
                // The caption is muted and the value is primary, as every other element on the HUD
                // draws them. Only worth doing for a single line: a multi-line module has already
                // decided what each of its rows says.
                String head = module.hudLabel() + ": ";
                text(g, head, x, y, s.mutedColor(), s);
                text(g, line.substring(Math.min(head.length(), line.length())),
                        x + textWidth(head, s.font()), y, s.textColor(), s);
            } else {
                text(g, line, x, y, s.textColor(), s);
            }
            y += s.lineHeight();
        }
    }
}
