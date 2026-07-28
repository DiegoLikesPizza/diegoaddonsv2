package dev.diego.diegoaddons.gui;

import com.render.api.gui.ButtonComponent;
import com.render.api.gui.ContainerComponent;
import com.render.api.gui.ScrollContainerComponent;
import com.render.api.gui.layout.GuiAlignment;
import dev.diego.diegoaddons.config.BlockedPlayer;
import dev.diego.diegoaddons.util.IgnoreList;

import java.util.List;

/**
 * The block list: who is blocked, and why.
 *
 * <p>The reason is worth keeping - "why did I block this person" is the question you have three
 * weeks later - so a row shows both, and blocking somebody already on the list updates their reason
 * rather than listing them twice.
 */
public class IgnoreListView extends DiegoView {
    private static final float PANEL_W = 900f;
    private static final float PANEL_H = 640f;
    private static final float ROW_H = 40f;

    private ScrollContainerComponent list;
    private String newName = "";
    private String newReason = "";

    public IgnoreListView() {
        super("Blocked players", PANEL_W, PANEL_H);
    }

    @Override
    protected void content(float width, float height) {
        ContainerComponent body = column(width, 12f).height(height).padding(PAD);
        float inner = width - PAD * 2f;

        ContainerComponent add = row(inner, 12f).height(36f);
        add.add(field(newName, "Player", 220f, s -> newName = s));
        add.add(field(newReason, "Reason (optional)", inner - 220f - 118f - 24f, s -> newReason = s));
        ButtonComponent block = clickable(t.accent(), () -> {
            if (IgnoreList.block(newName.trim(), newReason.trim())) {
                newName = "";
                newReason = "";
                rebuildView();
            }
        });
        asRow(block, 118f, 0f).height(36f).cornerRadius(9f).justifyContent(GuiAlignment.CENTER);
        block.add(GuiText.label("Block", t.accentText(), 14f));
        add.add(block);
        body.add(add);

        list = new ScrollContainerComponent();
        list.size(inner, height - PAD * 2f - 36f - 12f);
        asColumn(list, inner, 8f);
        body.add(list);
        panel.add(body);
        fill(inner);
    }

    private void rebuildView() {
        panel.clearChildren();
        build();
    }

    private void fill(float inner) {
        list.clearChildren();
        List<BlockedPlayer> all = IgnoreList.all();
        if (all.isEmpty()) {
            list.add(textBox(GuiText.label("Nobody blocked yet.", t.textFaint(), 13f), inner, 24f));
            return;
        }
        for (BlockedPlayer b : List.copyOf(all)) {
            ContainerComponent r = row(inner - 24f, 10f).height(ROW_H).padding(0f, 12f)
                    .cornerRadius(10f).backgroundColor(GuiColors.of(t.surfaceAlt()))
                    .borderWidth(1f).borderColor(GuiColors.of(t.border()))
                    .justifyContent(GuiAlignment.SPACE_BETWEEN);
            r.add(textBox(GuiText.label(b.name, t.text(), 15f), 180f, ROW_H));
            r.add(textBox(GuiText.label(b.reason == null || b.reason.isBlank() ? "-" : b.reason,
                    t.textMuted(), 13f), 0f, ROW_H).flexGrow(1f));
            ButtonComponent remove = clickable(t.surface(), () -> {
                IgnoreList.unblock(b.name);
                rebuildView();
            });
            asRow(remove, 100f, 0f).height(28f).cornerRadius(8f)
                    .justifyContent(GuiAlignment.CENTER)
                    .borderWidth(1f).borderColor(GuiColors.of(t.border()));
            remove.add(GuiText.label("Unblock", t.textMuted(), 13f));
            r.add(remove);
            list.add(r);
        }
    }
}
