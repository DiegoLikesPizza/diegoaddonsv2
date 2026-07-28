package dev.diego.diegoaddons.gui;

import com.render.api.gui.ButtonComponent;
import com.render.api.gui.ContainerComponent;
import com.render.api.gui.ScrollContainerComponent;
import com.render.api.gui.layout.GuiAlignment;
import dev.diego.diegoaddons.mixin.ChatComponentAccessor;
import dev.diego.diegoaddons.module.modules.ChatModule;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Searches the chat backlog - everything the scrollback still holds, which with unlimited history on
 * is the whole session.
 *
 * <p>A result copies to the clipboard when clicked, since finding the message is rarely the end of
 * it: you found it to paste it somewhere.
 */
public class ChatSearchView extends DiegoView {
    private static final float PANEL_W = 1100f;
    private static final float PANEL_H = 700f;
    private static final float ROW_H = 30f;
    /** Rows built at once; a session's chat can run to thousands and the search is how you narrow it. */
    private static final int MAX_ROWS = 80;

    private ScrollContainerComponent list;
    private com.render.api.gui.TextComponent countLabel;
    private String query = "";

    public ChatSearchView() {
        super("Chat search", PANEL_W, PANEL_H);
    }

    @Override
    protected void content(float width, float height) {
        ContainerComponent body = column(width, 12f).height(height).padding(PAD);
        float inner = width - PAD * 2f;

        ContainerComponent top = row(inner, 12f).height(36f)
                .justifyContent(GuiAlignment.SPACE_BETWEEN);
        top.add(field(query, "Search chat...", 420f, s -> {
            query = s;
            refresh(inner);
        }));
        countLabel = GuiText.label("", t.textFaint(), 13f);
        top.add(textBox(countLabel, 0f, 36f));
        body.add(top);

        list = new ScrollContainerComponent();
        list.size(inner, height - PAD * 2f - 36f - 12f);
        asColumn(list, inner, 4f);
        body.add(list);
        panel.add(body);
        refresh(inner);
    }

    /** Recollects matching messages, newest first. */
    private void refresh(float inner) {
        list.clearChildren();
        Minecraft mc = Minecraft.getInstance();
        if (query.isBlank() || mc.gui == null) {
            countLabel.text("");
            list.add(textBox(GuiText.label("Type to search the chat backlog.", t.textFaint(), 13f),
                    inner, 24f));
            return;
        }
        boolean cs = ChatModule.INSTANCE != null && ChatModule.INSTANCE.caseSensitive();
        String needle = cs ? query : query.toLowerCase(Locale.ROOT);

        List<String> hits = new ArrayList<>();
        for (GuiMessage m : ((ChatComponentAccessor) mc.gui.getChat()).diego$allMessages()) {
            String plain = m.content().getString().replaceAll("§.", "");
            String hay = cs ? plain : plain.toLowerCase(Locale.ROOT);
            if (hay.contains(needle)) {
                hits.add(plain);
            }
        }
        countLabel.text(hits.isEmpty() ? "no matches"
                : hits.size() + " match" + (hits.size() == 1 ? "" : "es")
                        + (hits.size() > MAX_ROWS ? "  ·  showing " + MAX_ROWS : ""));
        if (hits.isEmpty()) {
            list.add(textBox(GuiText.label("No matches.", t.textFaint(), 13f), inner, 24f));
            return;
        }
        int shown = 0;
        for (int i = hits.size() - 1; i >= 0 && shown < MAX_ROWS; i--, shown++) {
            list.add(hitRow(hits.get(i), inner - 24f));
        }
    }

    private ContainerComponent hitRow(String text, float inner) {
        ButtonComponent b = clickable(t.surfaceAlt(), () -> {
            Minecraft.getInstance().keyboardHandler.setClipboard(text);
        });
        asRow(b, inner, 0f).height(ROW_H).cornerRadius(8f).padding(0f, 12f)
                .borderWidth(1f).borderColor(GuiColors.of(t.border()));
        b.add(GuiText.label(text.length() > 140 ? text.substring(0, 140) + "..." : text,
                t.text(), 13f));
        return b;
    }
}
