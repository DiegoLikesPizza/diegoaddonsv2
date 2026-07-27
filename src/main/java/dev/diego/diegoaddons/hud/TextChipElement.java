package dev.diego.diegoaddons.hud;

import com.render.api.gui.ContainerComponent;
import com.render.api.gui.TextComponent;
import com.render.api.gui.GuiTextAlignment;
import com.render.api.gui.layout.GuiAlignment;
import dev.diego.diegoaddons.module.HudModule;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

/**
 * The default HUD element: a rounded chip of one or more text rows.
 *
 * <p>Rows are boxes of a fixed height holding a measured label, which is what keeps several lines
 * from landing on top of each other. The chip is sized to its widest row with digits counted at
 * their widest, so a clock or an FPS readout keeps a steady width as its value changes.
 */
public class TextChipElement extends HudElement {
    private final List<TextComponent> labels = new ArrayList<>();
    private final List<ContainerComponent> rows = new ArrayList<>();

    private List<String> lastLines = List.of();
    private int lastColor;
    private boolean lastCentered;

    public TextChipElement(HudModule module, ContainerComponent root) {
        super(module, root);
        asColumn(root, 0f, 0f).padding(PAD_Y, PAD_X);
        applyBackground(root, 7f);
        themeChanged();
    }

    @Override
    public boolean update(Minecraft mc) {
        List<String> lines = module.hudLines(mc);
        if (lines.isEmpty()) {
            return false;
        }
        int color = module.color();
        boolean centered = module.isCentered();
        boolean recolour = themeChanged();
        if (!recolour && lines.equals(lastLines) && color == lastColor && centered == lastCentered) {
            return true;   // nothing moved this tick
        }
        if (recolour) {
            applyBackground(root, 7f);
        }
        lastLines = List.copyOf(lines);
        lastColor = color;
        lastCentered = centered;
        rebuild(lines, color, centered);
        return true;
    }

    private void rebuild(List<String> lines, int color, boolean centered) {
        float width = 0f;
        for (String line : lines) {
            width = Math.max(width, steadyWidth(line, TEXT_PX));
        }
        root.width(width + PAD_X * 2f);
        root.alignItems(centered ? GuiAlignment.CENTER : GuiAlignment.START);

        while (rows.size() > lines.size()) {
            labels.remove(labels.size() - 1);
            root.remove(rows.remove(rows.size() - 1));
        }
        while (rows.size() < lines.size()) {
            TextComponent label = text("", color, TEXT_PX);
            ContainerComponent box = textRow(label, width, ROW_H);
            labels.add(label);
            rows.add(box);
            root.add(box);
        }
        for (int i = 0; i < lines.size(); i++) {
            rows.get(i).width(width);
            rows.get(i).justifyContent(centered ? GuiAlignment.CENTER : GuiAlignment.START);
            // The label is as wide as the whole chip - which is the digit-normalised width, wider
            // than the text itself - so centring has to happen inside the label, not just around it.
            labels.get(i).text(lines.get(i)).color(color).width(width)
                    // The line box has to be the row's height, or the glyphs sit against its top.
                    .lineHeight(ROW_H)
                    .textAlignment(centered ? GuiTextAlignment.CENTER : GuiTextAlignment.LEFT);
        }
    }
}
