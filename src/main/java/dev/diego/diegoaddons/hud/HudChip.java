package dev.diego.diegoaddons.hud;

import com.render.api.gui.ContainerComponent;
import com.render.api.gui.layout.GuiDisplay;
import com.render.api.gui.TextComponent;
import com.render.api.gui.layout.GuiAlignment;
import com.render.api.gui.layout.GuiFlexDirection;
import com.render.api.gui.layout.GuiLength;
import dev.diego.diegoaddons.gui.Theme;
import dev.diego.diegoaddons.gui.Themes;
import dev.diego.diegoaddons.module.HudModule;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

/**
 * The default HUD element: a rounded chip of one or more text rows, the RenderLib rebuild of what
 * {@code ModuleManager.drawTextChipLocal} used to draw imperatively.
 *
 * <p>Retained, so the tree is built once and only touched when something it shows actually changes -
 * {@link #update} returns early on an unchanged frame, which is most of them. The chip is sized to
 * the widest row with digits normalised, so a clock or an FPS counter doesn't twitch as its value
 * changes.
 */
public class HudChip {
    protected static final float PAD_X = 8f;
    public static final float PAD_Y = 5f;
    public static final float TEXT_PX = 10f;
    /** Breathing room between stacked rows; the old chip renderer used a 12px stride for 10px text. */
    public static final float ROW_GAP = 2f;

    protected final HudModule module;
    protected final ContainerComponent root;

    private final List<TextComponent> rows = new ArrayList<>();
    private List<String> lastLines = List.of();
    private int lastColor;
    private boolean lastCentered;
    private String lastTheme = "";

    public HudChip(HudModule module, ContainerComponent root) {
        this.module = module;
        this.root = root;
        root.display(GuiDisplay.FLEX)
                .flexDirection(GuiFlexDirection.COLUMN)
                .alignItems(GuiAlignment.START)
                .rowGap(GuiLength.pixels(ROW_GAP))
                .gap(ROW_GAP)
                .padding(PAD_Y, PAD_X)
                .cornerRadius(7f)
                .borderWidth(1f);
        applyTheme();
    }

    /** True when the element has something to show this frame. */
    public boolean update(Minecraft mc) {
        List<String> lines = module.hudLines(mc);
        if (lines.isEmpty()) {
            return false;
        }
        int color = module.color();
        boolean centered = module.isCentered();
        String theme = Themes.current().name();
        if (lines.equals(lastLines) && color == lastColor && centered == lastCentered
                && theme.equals(lastTheme)) {
            return true;   // nothing changed; leave the retained tree alone
        }
        if (!theme.equals(lastTheme)) {
            applyTheme();
        }
        lastLines = List.copyOf(lines);
        lastColor = color;
        lastCentered = centered;
        lastTheme = theme;
        rebuild(lines, color, centered);
        return true;
    }

    private void rebuild(List<String> lines, int color, boolean centered) {
        float width = 0f;
        for (String line : lines) {
            width = Math.max(width, HudText.steadyWidth(line, TEXT_PX));
        }
        root.width(width + PAD_X * 2f);
        root.alignItems(centered ? GuiAlignment.CENTER : GuiAlignment.START);

        while (rows.size() > lines.size()) {
            root.remove(rows.remove(rows.size() - 1));
        }
        while (rows.size() < lines.size()) {
            TextComponent row = new TextComponent().font(HudText.MEDIUM).textScalePixels(TEXT_PX);
            rows.add(row);
            root.add(row);
        }
        for (int i = 0; i < lines.size(); i++) {
            rows.get(i).text(lines.get(i)).color(color).width(width);
        }
    }

    protected void applyTheme() {
        Theme t = Themes.current();
        root.backgroundColor((0xCC << 24) | (t.surface() & 0x00FFFFFF))
                .borderColor(Theme.withAlpha(t.border(), 0.9f));
    }
}
