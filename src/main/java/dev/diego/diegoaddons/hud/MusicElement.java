package dev.diego.diegoaddons.hud;

import com.render.api.gui.ContainerComponent;
import com.render.api.gui.ImageComponent;
import com.render.api.gui.TextComponent;
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
 * Music Display: album cover beside the track rows, with a progress bar under them. Without the
 * cover and the bar it is just the plain text chip, so that case is delegated.
 */
public class MusicElement extends HudElement {
    private static final float GAP = 4f;
    private static final float BAR_H = 3f;
    private static final float MIN_TEXT_W = 60f;

    private final MusicDisplayModule music;
    private final TextChipElement plain;

    private final List<TextComponent> labels = new ArrayList<>();
    private ImageComponent cover;
    private ContainerComponent coverBox;
    private ContainerComponent barFill;
    private float textWidth = MIN_TEXT_W;

    private List<String> lastLines = List.of();
    private boolean lastCover;
    private boolean lastBar;
    private int lastColor;
    private Identifier lastArt;
    private boolean plainMode = true;

    public MusicElement(MusicDisplayModule module, ContainerComponent root) {
        super(module, root);
        this.music = module;
        this.plain = new TextChipElement(module, root);
    }

    @Override
    public boolean update(Minecraft mc) {
        if (!music.customLayout()) {
            if (!plainMode) {
                plainMode = true;
                reset();
            }
            return plain.update(mc);
        }
        List<String> lines = module.hudLines(mc);
        if (lines.isEmpty()) {
            return false;
        }

        boolean wantCover = music.showCover();
        boolean wantBar = music.showProgress();
        int color = module.color();
        boolean recolour = themeChanged();
        if (plainMode || recolour || !lines.equals(lastLines)
                || wantCover != lastCover || wantBar != lastBar || color != lastColor) {
            plainMode = false;
            lastLines = List.copyOf(lines);
            lastCover = wantCover;
            lastBar = wantBar;
            lastColor = color;
            rebuild(lines, wantCover, wantBar, color);
        }

        if (wantCover) {
            Identifier art = CoverArt.get(MediaWatcher.artist(), MediaWatcher.title());
            if (!Objects.equals(art, lastArt)) {
                lastArt = art;
                if (art != null) {
                    cover.resource(art);
                }
                cover.visible(art != null);
                coverBox.visible(art == null);
            }
        }
        if (wantBar && barFill != null) {
            int duration = MediaWatcher.duration();
            float f = duration > 0 ? Math.min(1f, MediaWatcher.position() / (float) duration) : 0f;
            barFill.width(Math.max(0f, textWidth * f));
        }
        return true;
    }

    private void reset() {
        root.clearChildren();
        labels.clear();
        cover = null;
        coverBox = null;
        barFill = null;
        lastArt = null;
    }

    private void rebuild(List<String> lines, boolean wantCover, boolean wantBar, int color) {
        reset();
        Theme t = Themes.current();

        textWidth = MIN_TEXT_W;
        for (String line : lines) {
            textWidth = Math.max(textWidth, width(line, TEXT_PX));
        }
        float contentH = lines.size() * ROW_H + (wantBar ? GAP + BAR_H : 0f);

        asRow(root, textWidth + PAD_X * 2f + (wantCover ? contentH + GAP : 0f), GAP)
                .padding(PAD_Y, PAD_X);
        applyBackground(root, 7f);

        if (wantCover) {
            cover = new ImageComponent();
            cover.size(contentH, contentH).cornerRadius(3f).visible(false);
            coverBox = new ContainerComponent();
            coverBox.size(contentH, contentH).cornerRadius(3f)
                    .backgroundColor(Theme.withAlpha(t.textFaint(), 0.25f));
            root.add(cover);
            root.add(coverBox);
        }

        ContainerComponent column = column(textWidth, 0f);
        for (String line : lines) {
            TextComponent label = text(line, color, TEXT_PX).width(textWidth);
            labels.add(label);
            column.add(textRow(label, textWidth, ROW_H));
        }
        if (wantBar) {
            ContainerComponent track = row(textWidth, 0f);
            track.height(BAR_H).cornerRadius(BAR_H / 2f)
                    .backgroundColor(Theme.withAlpha(t.textFaint(), 0.35f));
            barFill = new ContainerComponent();
            barFill.size(0f, BAR_H).cornerRadius(BAR_H / 2f).backgroundColor(color);
            track.add(barFill);
            column.add(track);
        }
        root.add(column);
    }
}
