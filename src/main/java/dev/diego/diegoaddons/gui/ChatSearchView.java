package dev.diego.diegoaddons.gui;

import com.render.api.gui.ButtonComponent;
import com.render.api.gui.ContainerComponent;
import com.render.api.gui.ScrollContainerComponent;
import com.render.api.gui.layout.GuiAlignment;
import dev.diego.diegoaddons.mixin.ChatComponentAccessor;
import dev.diego.diegoaddons.util.Toasts;
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
        // A label with no width wraps at whatever the layout hands it, which here was nothing -
        // hence one character per line down the side of the screen.
        countLabel = GuiText.label("", t.textFaint(), 13f).width(320f)
                .textAlignment(com.render.api.gui.GuiTextAlignment.RIGHT);
        top.add(textBox(countLabel, 320f, 36f));
        body.add(top);
        body.add(textBox(GuiText.label(
                "Click a result to jump to it in chat, or Copy to put it on the clipboard.",
                t.textFaint(), 12f).width(inner), inner, 20f));

        list = new ScrollContainerComponent();
        list.size(inner, height - PAD * 2f - 36f - 20f - 24f);
        asColumn(list, inner, 4f);
        // Without this the rows are laid out past the bottom of the box instead of
        // scrolling inside it - which reads as every row drawn on top of the last.
        list.overflowY(com.render.api.gui.GuiOverflowMode.AUTO);
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

        List<GuiMessage> hits = new ArrayList<>();
        for (GuiMessage m : ((ChatComponentAccessor) mc.gui.getChat()).diego$allMessages()) {
            String plain = m.content().getString().replaceAll("§.", "");
            String hay = cs ? plain : plain.toLowerCase(Locale.ROOT);
            if (hay.contains(needle)) {
                hits.add(m);
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
            GuiMessage m = hits.get(i);
            list.add(hitRow(m, m.content().getString().replaceAll("§.", ""), inner - 24f));
        }
    }

    /**
     * One hit: the message, which jumps the chat to it, and a button that copies it.
     *
     * <p>Both used to hang off one row with left and right click. RenderLib's button reports a press
     * and not which press, so what was a hidden second action is a second button - which also makes
     * it discoverable without being told.
     */
    private ContainerComponent hitRow(GuiMessage message, String text, float inner) {
        ContainerComponent r = row(inner, 8f).height(ROW_H).flexShrink(0f);

        ButtonComponent jump = clickable(t.surfaceAlt(), () -> jumpTo(message));
        asRow(jump, inner - 96f, 0f).height(ROW_H).cornerRadius(8f).padding(0f, 12f)
                .borderWidth(1f).borderColor(GuiColors.of(t.border()));
        jump.add(GuiText.label(text.length() > 130 ? text.substring(0, 130) + "..." : text,
                t.text(), 13f));
        r.add(jump);

        ButtonComponent copy = clickable(t.surface(), () -> {
            Minecraft.getInstance().keyboardHandler.setClipboard(text);
            Toasts.show("Copied to clipboard", text);
        });
        asRow(copy, 88f, 0f).height(ROW_H).cornerRadius(8f)
                .justifyContent(com.render.api.gui.layout.GuiAlignment.CENTER)
                .borderWidth(1f).borderColor(GuiColors.of(t.border()));
        copy.add(GuiText.label("Copy", t.textMuted(), 13f));
        r.add(copy);
        return r;
    }

    /** Scrolls the chat to a message and drops you into it. */
    private void jumpTo(GuiMessage message) {
        Minecraft mc = Minecraft.getInstance();
        ChatComponentAccessor acc = (ChatComponentAccessor) mc.gui.getChat();
        List<GuiMessage.Line> lines = acc.diego$trimmedMessages();
        int target = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).parent() == message) {
                target = i;
            }
        }
        if (target < 0) {
            // Trimmed away since the search ran - the backlog still has it, the display does not.
            Toasts.show("Not in view", "That message has scrolled out of the chat");
            return;
        }
        int max = Math.max(0, lines.size() - mc.gui.getChat().getLinesPerPage());
        acc.diego$setChatScrollbarPos(Math.min(target, max));
        close();
        mc.setScreen(new net.minecraft.client.gui.screens.ChatScreen("", false));
    }
}
