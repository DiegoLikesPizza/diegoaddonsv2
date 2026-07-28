package dev.diego.diegoaddons.hud;

import com.render.api.gui.ContainerComponent;
import com.render.api.gui.GuiTextAlignment;
import com.render.api.gui.TextComponent;
import dev.diego.diegoaddons.gui.GuiColors;
import dev.diego.diegoaddons.gui.Themes;
import dev.diego.diegoaddons.module.modules.CustomScoreboardModule;
import dev.diego.diegoaddons.util.CustomScoreboard;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The replacement SkyBlock sidebar, as a HUD element like everything else.
 *
 * <p>It used to draw itself straight into the HUD pass, pinned to the right edge - the one element
 * of the mod's that could not be moved, scaled or faded with the others. Here it is placed like any
 * of them.
 *
 * <p>Lines keep their own colours: SkyBlock says a great deal with them (a red timer, a green
 * counter), and re-colouring them to the theme would throw that away.
 */
public class ScoreboardElement extends HudElement {
    private static final float TITLE_PX = 10f;
    private static final float LINE_PX = 9f;
    private static final float TITLE_H = 13f;
    private static final float LINE_H = 11f;

    private final CustomScoreboardModule board;
    private final List<TextComponent> rows = new ArrayList<>();
    private TextComponent titleLabel;
    private ContainerComponent titleRow;

    private String lastShape = "";

    public ScoreboardElement(CustomScoreboardModule module, ContainerComponent root) {
        super(module, root);
        this.board = module;
    }

    @Override
    public boolean update(Minecraft mc) {
        Component title = CustomScoreboard.title(mc);
        List<Component> lines = CustomScoreboard.lines(mc);
        if (title == null || lines.isEmpty()) {
            return false;   // not on a server with a sidebar; nothing to stand in for
        }

        // The tree is rebuilt when the shape changes - a line appearing or going, or the widest
        // line growing. The text itself is swapped in place every tick, which is most ticks.
        String shape = lines.size() + "|" + widest(title, lines) + "|" + board.background();
        if (themeChanged() || !shape.equals(lastShape)) {
            lastShape = shape;
            rebuild(title, lines);
            return true;
        }
        titleLabel.text(title);
        for (int i = 0; i < rows.size() && i < lines.size(); i++) {
            rows.get(i).text(lines.get(i));
        }
        return true;
    }

    private void rebuild(Component title, List<Component> lines) {
        root.clearChildren();
        rows.clear();

        float width = widest(title, lines) + PAD_X * 2f;
        asColumn(root, width, 1f).padding(PAD_Y, PAD_X);
        if (board.background()) {
            applyBackground(root, 7f);
        } else {
            root.backgroundColor(GuiColors.of(0x00000000)).borderWidth(0f);
        }

        titleLabel = new TextComponent().text(title).font(MEDIUM).textScalePixels(TITLE_PX)
                .lineHeight(LINE_HEIGHT).textAlignment(GuiTextAlignment.CENTER)
                .color(GuiColors.of(Themes.current().accent()))
                .width(width - PAD_X * 2f);
        titleRow = textRow(titleLabel, width - PAD_X * 2f, TITLE_H);
        root.add(titleRow);

        for (Component line : lines) {
            TextComponent label = new TextComponent().text(line).font(MEDIUM)
                    .textScalePixels(LINE_PX).lineHeight(LINE_HEIGHT)
                    .color(GuiColors.of(Themes.current().text()))
                    .width(width - PAD_X * 2f);
            rows.add(label);
            root.add(textRow(label, width - PAD_X * 2f, LINE_H));
        }
        sizeRoot(width, PAD_Y * 2f + TITLE_H + lines.size() * LINE_H + lines.size());
    }

    /** How wide the widest of the title and the lines renders. */
    private static float widest(Component title, List<Component> lines) {
        float w = width(title.getString(), TITLE_PX);
        for (Component line : lines) {
            w = Math.max(w, width(line.getString(), LINE_PX));
        }
        return w;
    }
}
