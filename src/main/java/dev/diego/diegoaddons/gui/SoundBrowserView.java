package dev.diego.diegoaddons.gui;

import com.render.api.gui.ButtonComponent;
import com.render.api.gui.ContainerComponent;
import com.render.api.gui.ScrollContainerComponent;
import com.render.api.gui.layout.GuiAlignment;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Picks a sound from every sound the game knows about.
 *
 * <p>The list is the sound registry itself rather than a hand-written selection, so anything a
 * resource pack or a game update adds is there the moment it exists - which is the point. A fixed
 * list of five notes is always missing the one you wanted.
 *
 * <p>A row plays its sound when clicked and is chosen with the button beside it, so you can hear
 * what you are picking before you commit to hearing it for the rest of the run.
 */
public class SoundBrowserView extends DiegoView {
    private static final float PANEL_W = 900f;
    private static final float PANEL_H = 720f;
    private static final float ROW_H = 34f;
    /** Rows built at once. The registry runs to thousands; a search is how you find one anyway. */
    private static final int MAX_ROWS = 60;

    private final String current;
    private final Consumer<String> onPick;
    private final List<String> all = new ArrayList<>();

    private ScrollContainerComponent list;
    private com.render.api.gui.TextComponent countLabel;
    private String query = "";

    public SoundBrowserView(String current, Consumer<String> onPick) {
        super("Sounds", PANEL_W, PANEL_H);
        this.current = current;
        this.onPick = onPick;
        for (Identifier id : BuiltInRegistries.SOUND_EVENT.keySet()) {
            all.add(id.toString());
        }
        all.sort(String::compareTo);
    }

    @Override
    protected void content(float width, float height) {
        ContainerComponent body = column(width, 12f).height(height).padding(PAD);
        float inner = width - PAD * 2f;

        ContainerComponent top = row(inner, 12f).height(36f)
                .justifyContent(GuiAlignment.SPACE_BETWEEN);
        top.add(field(query, "Search sounds...", 320f, s -> {
            query = s;
            refresh();
        }));
        countLabel = GuiText.label("", t.textFaint(), 13f).width(320f)
                .textAlignment(com.render.api.gui.GuiTextAlignment.RIGHT);
        top.add(textBox(countLabel, 320f, 36f));
        body.add(top);

        body.add(textBox(GuiText.label("Now: " + current, t.textMuted(), 13f), inner, 24f));

        list = new ScrollContainerComponent();
        list.size(inner, height - PAD * 2f - 36f - 24f - 24f);
        asColumn(list, inner, 6f);
        // Without this the rows are laid out past the bottom of the box instead of
        // scrolling inside it - which reads as every row drawn on top of the last.
        list.overflowY(com.render.api.gui.GuiOverflowMode.AUTO);
        body.add(list);
        panel.add(body);
        refresh();
    }

    /** Rebuilds the visible rows for the current search. */
    private void refresh() {
        list.clearChildren();
        String q = query.trim().toLowerCase(Locale.ROOT);
        int matched = 0;
        int built = 0;
        for (String id : all) {
            if (!q.isEmpty() && !id.toLowerCase(Locale.ROOT).contains(q)) {
                continue;
            }
            matched++;
            if (built < MAX_ROWS) {
                list.add(soundRow(id));
                built++;
            }
        }
        countLabel.text(matched + " of " + all.size()
                + (matched > built ? "  ·  showing " + built + ", keep typing" : ""));
    }

    private ContainerComponent soundRow(String id) {
        float inner = PANEL_W - PAD * 4f;
        ContainerComponent r = row(inner, 10f).height(ROW_H)
                .justifyContent(GuiAlignment.SPACE_BETWEEN);

        ButtonComponent hear = clickable(t.surfaceAlt(), () -> play(id));
        asRow(hear, inner - 110f, 0f).height(ROW_H).cornerRadius(8f).padding(0f, 12f)
                .borderWidth(1f).borderColor(GuiColors.of(t.border()));
        hear.add(GuiText.label(id, id.equals(current) ? t.accent() : t.text(), 13f));
        r.add(hear);

        ButtonComponent use = clickable(t.accent(), () -> {
            onPick.accept(id);
            close();
        });
        asRow(use, 100f, 0f).height(ROW_H).cornerRadius(8f)
                .justifyContent(GuiAlignment.CENTER);
        use.add(GuiText.label("Use", t.accentText(), 13f));
        r.add(use);
        return r;
    }

    /** Plays a sound by id, so it can be heard before it is chosen. */
    private void play(String id) {
        Minecraft mc = Minecraft.getInstance();
        Identifier key = Identifier.tryParse(id);
        if (mc.player == null || key == null) {
            return;
        }
        mc.player.playSound(SoundEvent.createVariableRangeEvent(key), 1.0f, 1.0f);
    }
}
