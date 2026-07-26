package dev.diego.diegoaddons.hud;

import com.render.api.gui.ContainerComponent;
import com.render.api.gui.ImageComponent;
import com.render.api.gui.TextComponent;
import com.render.api.gui.layout.GuiAlignment;
import com.render.api.gui.layout.GuiDisplay;
import com.render.api.gui.layout.GuiFlexDirection;
import com.render.api.gui.layout.GuiLength;
import dev.diego.diegoaddons.gui.Theme;
import dev.diego.diegoaddons.gui.Themes;
import dev.diego.diegoaddons.module.modules.MusicDisplayModule;
import dev.diego.diegoaddons.util.CoverArt;
import dev.diego.diegoaddons.util.MediaWatcher;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The Music Display element: album cover beside a stack of text rows, with a progress bar under
 * them. Falls back to the plain chip layout when neither the cover nor the bar is switched on.
 *
 * <p>The cover and the bar are the only parts that move on their own, so the tree is rebuilt only
 * when its shape changes (rows appearing, the cover being toggled) while the bar's fill width and
 * the cover's texture are mutated in place every tick.
 */
public class MusicChip extends HudChip {
    private static final float GAP = 4f;
    private static final float BAR_H = 3f;

    private final MusicDisplayModule music;

    private ContainerComponent textColumn;
    private final List<TextComponent> rows = new ArrayList<>();
    private ImageComponent cover;
    private ContainerComponent coverPlaceholder;
    private ContainerComponent barTrack;
    private ContainerComponent barFill;

    private List<String> lastLines = List.of();
    private boolean lastCover;
    private boolean lastBar;
    private int lastColor;
    private String lastTheme = "";
    private Identifier lastArt;

    public MusicChip(MusicDisplayModule module, ContainerComponent root) {
        super(module, root);
        this.music = module;
    }

    @Override
    public boolean update(Minecraft mc) {
        if (!music.customLayout()) {
            return super.update(mc);   // plain text chip
        }
        List<String> lines = module.hudLines(mc);
        if (lines.isEmpty()) {
            return false;
        }

        boolean wantCover = music.showCover();
        boolean wantBar = music.showProgress();
        int color = module.color();
        String theme = Themes.current().name();

        boolean shapeChanged = !lines.equals(lastLines) || wantCover != lastCover
                || wantBar != lastBar || color != lastColor || !theme.equals(lastTheme);
        if (shapeChanged) {
            if (!theme.equals(lastTheme)) {
                applyTheme();
            }
            lastLines = List.copyOf(lines);
            lastCover = wantCover;
            lastBar = wantBar;
            lastColor = color;
            lastTheme = theme;
            rebuild(lines, wantCover, wantBar, color);
        }

        if (wantCover) {
            Identifier art = CoverArt.get(MediaWatcher.artist(), MediaWatcher.title());
            if (!Objects.equals(art, lastArt)) {
                lastArt = art;
                showArt(art);
            }
        }
        if (wantBar && barFill != null) {
            int duration = MediaWatcher.duration();
            float f = duration > 0 ? Math.min(1f, MediaWatcher.position() / (float) duration) : 0f;
            barFill.width(Math.max(0f, textWidth() * f));
        }
        return true;
    }

    private void rebuild(List<String> lines, boolean wantCover, boolean wantBar, int color) {
        root.clearChildren();
        rows.clear();
        cover = null;
        coverPlaceholder = null;
        barTrack = null;
        barFill = null;
        lastArt = null;

        root.display(GuiDisplay.FLEX)
                .flexDirection(GuiFlexDirection.ROW)
                .alignItems(GuiAlignment.CENTER)
                .columnGap(GuiLength.pixels(GAP))
                .gap(GAP)
                .padding(PAD_Y, PAD_X);

        float contentH = lines.size() * (ROW_H + ROW_GAP) + (wantBar ? GAP + BAR_H : 0f);
        float textW = textWidth();

        if (wantCover) {
            cover = new ImageComponent();
            cover.size(contentH, contentH).cornerRadius(3f).visible(false);
            coverPlaceholder = new ContainerComponent();
            coverPlaceholder.size(contentH, contentH).cornerRadius(3f)
                    .backgroundColor(Theme.withAlpha(Themes.current().textFaint(), 0.25f));
            root.add(cover);
            root.add(coverPlaceholder);
        }

        textColumn = new ContainerComponent();
        textColumn.display(GuiDisplay.FLEX)
                .flexDirection(GuiFlexDirection.COLUMN)
                .alignItems(GuiAlignment.START)
                .rowGap(GuiLength.pixels(ROW_GAP))
                .gap(ROW_GAP)
                .width(textW);
        for (String line : lines) {
            // Fixed-height box per row; text boxes left to size themselves collapse and overlap.
            ContainerComponent box = new ContainerComponent();
            box.size(textW, ROW_H).display(GuiDisplay.FLEX)
                    .flexDirection(GuiFlexDirection.ROW)
                    .alignItems(GuiAlignment.CENTER);
            TextComponent row = new TextComponent().text(line).color(color)
                    .font(HudText.MEDIUM).textScalePixels(TEXT_PX).width(textW);
            box.add(row);
            rows.add(row);
            textColumn.add(box);
        }

        if (wantBar) {
            barTrack = new ContainerComponent();
            barTrack.size(textW, BAR_H).cornerRadius(BAR_H / 2f)
                    .backgroundColor(Theme.withAlpha(Themes.current().textFaint(), 0.35f))
                    .display(GuiDisplay.FLEX)
                    .flexDirection(GuiFlexDirection.ROW)
                    .alignItems(GuiAlignment.START);
            barFill = new ContainerComponent();
            barFill.size(0f, BAR_H).cornerRadius(BAR_H / 2f)
                    .backgroundColor(module.color());
            barTrack.add(barFill);
            textColumn.add(barTrack);
        }

        root.width(textW + PAD_X * 2f + (wantCover ? contentH + GAP : 0f));
        root.add(textColumn);
    }

    /** The text block is at least 60px wide, so a short title doesn't give a stubby progress bar. */
    private float textWidth() {
        float w = 60f;
        for (String line : lastLines) {
            w = Math.max(w, HudText.width(line, TEXT_PX));
        }
        return w;
    }

    /** Swaps between the artwork and its placeholder as the online lookup lands. */
    private void showArt(Identifier art) {
        if (cover == null || coverPlaceholder == null) {
            return;
        }
        if (art != null) {
            cover.resource(art);
        }
        cover.visible(art != null);
        coverPlaceholder.visible(art == null);
    }
}
