package dev.diego.diegoaddons.gui;

import dev.diego.diegoaddons.config.ConfigManager;
import dev.diego.diegoaddons.mixin.ChatComponentAccessor;
import dev.diego.diegoaddons.module.modules.ChatModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Searches the chat backlog. Opened with Ctrl+F, it lists every message containing the query,
 * newest first. <b>Left-click</b> jumps the chat to that message, <b>right-click</b> copies it.
 *
 * <p>The search reads the untrimmed message list rather than the wrapped display lines, so a hit is
 * a whole message. Pair this with the Unlimited Chat History feature and the backlog worth searching
 * is the entire session rather than the last hundred lines.
 */
public class ChatSearchScreen extends Screen {
    private static final int ROW_H = 12;
    private static final int PAD = 10;

    private final Screen parent;

    private EditBox query;
    /** A hit: the message itself, so it can be found in the chat, plus its plain text. */
    private record Hit(GuiMessage message, String text) {
    }

    private final List<Hit> results = new ArrayList<>();
    private int scroll;
    private int copied = -1;      // index of the row flashed as copied
    private long copiedAt;

    private int panelX, panelY, panelW, panelH, listTop, rows;

    public ChatSearchScreen(Screen parent) {
        super(Component.literal("Chat Search"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        panelW = Math.min(420, width - 40);
        panelH = Math.min(260, height - 40);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
        listTop = panelY + 46;
        rows = Math.max(1, (panelY + panelH - PAD - listTop) / ROW_H);

        String previous = query != null ? query.getValue() : "";
        query = new EditBox(font, panelX + PAD, panelY + 20, panelW - PAD * 2, 16,
                Component.literal("Search"));
        query.setMaxLength(128);
        query.setHint(Component.literal("Search chat…"));
        query.setValue(previous);
        query.setResponder(v -> {
            scroll = 0;
            refresh();
        });
        addRenderableWidget(query);
        setInitialFocus(query);
        refresh();
    }

    /** Recollects matching messages, newest first. */
    private void refresh() {
        results.clear();
        String q = query.getValue();
        if (q.isBlank() || minecraft == null) {
            return;
        }
        boolean cs = ChatModule.INSTANCE != null && ChatModule.INSTANCE.caseSensitive();
        String needle = cs ? q : q.toLowerCase(Locale.ROOT);
        List<GuiMessage> all = ((ChatComponentAccessor) minecraft.gui.getChat()).diego$allMessages();
        for (GuiMessage m : all) {
            String plain = m.content().getString().replaceAll("§.", "");
            String hay = cs ? plain : plain.toLowerCase(Locale.ROOT);
            if (hay.contains(needle)) {
                results.add(new Hit(m, plain));
            }
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        Theme t = Themes.current();
        boolean sm = ConfigManager.get().smoothCorners;
        g.fill(0, 0, width, height, t.overlay());

        UiRender.dropShadow(g, panelX, panelY, panelW, panelH, 10, t.shadow(), 10, 5);
        UiRender.fillRounded(g, panelX, panelY, panelW, panelH, 10, t.surface(), sm);
        UiRender.strokeRounded(g, panelX, panelY, panelW, panelH, 10, t.border(), sm);
        UiRender.text(g, font, "CHAT SEARCH", Fonts.SMALL, panelX + PAD, panelY + 8, t.textFaint());

        String count = results.isEmpty() ? "" : results.size() + " match" + (results.size() == 1 ? "" : "es");
        UiRender.textRight(g, font, count, Fonts.SMALL, panelX + panelW - PAD, panelY + 8, t.textMuted());

        if (query.getValue().isBlank()) {
            UiRender.text(g, font, "Type to search the chat backlog.", Fonts.SMALL,
                    panelX + PAD, listTop + 4, t.textFaint());
        } else if (results.isEmpty()) {
            UiRender.text(g, font, "No matches.", Fonts.SMALL, panelX + PAD, listTop + 4, t.textFaint());
        }

        int end = Math.min(results.size(), scroll + rows);
        for (int i = scroll; i < end; i++) {
            int ry = listTop + (i - scroll) * ROW_H;
            boolean hover = UiRender.inside(mouseX, mouseY, panelX + PAD, ry, panelW - PAD * 2, ROW_H);
            if (hover) {
                UiRender.fillRounded(g, panelX + PAD - 3, ry - 1, panelW - PAD * 2 + 6, ROW_H, 3,
                        t.surfaceAlt(), sm);
            }
            boolean flash = i == copied && System.currentTimeMillis() - copiedAt < 700;
            String line = trim(results.get(i).text(), panelW - PAD * 2 - 6);
            UiRender.text(g, font, line, Fonts.SMALL, panelX + PAD, ry + 1,
                    flash ? t.accent() : (hover ? t.text() : t.textMuted()));
        }

        if (!results.isEmpty()) {
            UiRender.text(g, font, "left-click: jump · right-click: copy", Fonts.SMALL,
                    panelX + PAD, panelY + panelH - 12, t.textFaint());
        }
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        // On top of our own scrim, so the confirmation is not dimmed by it.
        dev.diego.diegoaddons.util.Toasts.render(g);
    }

    /**
     * Scrolls the chat so the given message is on screen, then leaves the search for the chat.
     *
     * <p>The chat scrolls in wrapped display lines, not messages, so the message's first line has to
     * be located in that list - a long message occupies several lines and its index there is not its
     * index among messages.
     */
    private void jumpTo(GuiMessage message) {
        ChatComponentAccessor acc = (ChatComponentAccessor) minecraft.gui.getChat();
        List<GuiMessage.Line> lines = acc.diego$trimmedMessages();
        int target = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).parent() == message) {
                target = i;
            }
        }
        if (target < 0) {
            // Trimmed away since the search ran - the backlog still has it, the display does not.
            dev.diego.diegoaddons.util.Toasts.show("Not in view", "Message scrolled out of the chat");
            return;
        }
        int max = Math.max(0, lines.size() - minecraft.gui.getChat().getLinesPerPage());
        acc.diego$setChatScrollbarPos(Math.min(target, max));
        minecraft.setScreen(parent instanceof ChatScreen ? parent : new ChatScreen("", false));
    }

    /** Cuts a message to the panel width, since results are single-line rows. */
    private String trim(String s, int maxWidth) {
        if (Fonts.width(font, s, Fonts.SMALL) <= maxWidth) {
            return s;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            sb.append(s.charAt(i));
            if (Fonts.width(font, sb + "…", Fonts.SMALL) > maxWidth) {
                sb.setLength(Math.max(0, sb.length() - 1));
                break;
            }
        }
        return sb + "…";
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        int end = Math.min(results.size(), scroll + rows);
        for (int i = scroll; i < end; i++) {
            int ry = listTop + (i - scroll) * ROW_H;
            if (!UiRender.inside(event.x(), event.y(), panelX + PAD, ry, panelW - PAD * 2, ROW_H)) {
                continue;
            }
            Hit hit = results.get(i);
            if (event.button() == 1) {
                minecraft.keyboardHandler.setClipboard(hit.text());
                copied = i;
                copiedAt = System.currentTimeMillis();
                dev.diego.diegoaddons.util.Toasts.show("Copied to clipboard", hit.text());
            } else if (event.button() == 0) {
                jumpTo(hit.message());
            }
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (results.size() > rows) {
            int max = results.size() - rows;
            scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(dy)));
            return true;
        }
        return super.mouseScrolled(mx, my, dx, dy);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
