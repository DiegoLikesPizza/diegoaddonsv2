package dev.diego.diegoaddons.gui;

import dev.diego.configlib.gui.widget.SearchBox;
import dev.diego.configlib.render.Fonts;
import dev.diego.configlib.render.Theme;
import dev.diego.configlib.render.Ui;
import dev.diego.diegoaddons.DiegoAddonsV2Client;
import dev.diego.diegoaddons.mixin.ChatComponentAccessor;
import dev.diego.diegoaddons.module.modules.ChatModule;
import dev.diego.diegoaddons.util.LegacyText;
import dev.diego.diegoaddons.util.Toasts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Searches the chat backlog - everything the scrollback still holds, which with unlimited history on
 * is a great deal more than the hundred vanilla keeps.
 *
 * <p>Drawn on configlib's own layer rather than being a config screen, because it is not one: there
 * is nothing here to configure. It borrows the library's {@code Ui}, {@code Fonts} and search box so
 * it looks like everything else, and owns its own layout.
 *
 * <p>A hit does one of two things - jumps the chat to that line, or copies the text. Both are the
 * reason to search in the first place.
 */
public final class ChatSearchScreen extends Screen {

    private static final int PANEL_W = 780;
    private static final int HEADER_H = 104;
    private static final int ROW_H = 34;
    private static final int PAD = 24;
    private static final int COPY_W = 78;

    /** More than this and the list stops being worth scrolling; refine the query instead. */
    private static final int MAX_HITS = 400;

    private final SearchBox search = new SearchBox("Search chat...");
    private final List<GuiMessage> hits = new ArrayList<>();

    private float scroll;
    private int panelX;
    private int panelY;
    private int panelH;

    public ChatSearchScreen() {
        super(Component.literal("Chat search"));
    }

    @Override
    protected void init() {
        panelH = Math.min(Ui.u(height) - 80, 660);
        panelX = (Ui.u(width) - PANEL_W) / 2;
        panelY = (Ui.u(height) - panelH) / 2;
        search.onChange(this::refresh);
        search.focus();
        refresh();
    }

    /**
     * Re-runs the query over the backlog.
     *
     * <p>Done on change rather than per frame: the backlog can be thousands of messages and each one
     * is stripped of its colour codes to match against, which is not work to repeat sixty times a
     * second for a string that has not altered.
     */
    private void refresh() {
        hits.clear();
        scroll = 0f;
        String query = search.value().trim();
        Minecraft mc = Minecraft.getInstance();
        if (query.isEmpty() || mc.gui == null) {
            return;
        }
        ChatModule mod = ChatModule.INSTANCE;
        boolean caseSensitive = mod != null && mod.caseSensitive();
        String needle = caseSensitive ? query : query.toLowerCase(Locale.ROOT);

        for (GuiMessage m : ((ChatComponentAccessor) mc.gui.getChat()).diego$allMessages()) {
            String plain = LegacyText.strip(m.content().getString());
            String hay = caseSensitive ? plain : plain.toLowerCase(Locale.ROOT);
            if (hay.contains(needle)) {
                hits.add(m);
                if (hits.size() >= MAX_HITS) {
                    break;
                }
            }
        }
    }

    private int listTop() {
        return panelY + HEADER_H;
    }

    private int listHeight() {
        return panelH - HEADER_H - PAD;
    }

    private int maxScroll() {
        return Math.max(0, hits.size() * ROW_H - listHeight());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractRenderState(g, mouseX, mouseY, partial);
        Theme t = DiegoAddonsV2Client.CONFIG.theme();
        int mx = Ui.u(mouseX);
        int my = Ui.u(mouseY);

        Ui.beginHiRes(g);
        Ui.roundRect(g, panelX, panelY, PANEL_W, panelH, 18, t.surface());
        Ui.roundOutline(g, panelX, panelY, PANEL_W, panelH, 18, 1, t.stroke());

        Fonts.drawFitted(g, font, "Chat search", panelX + PAD, panelY + 12, 30,
                PANEL_W - PAD * 2, t.text());
        Fonts.drawTruncated(g, font, "Click a result to jump to it, or Copy to take the text.",
                panelX + PAD, panelY + 44, PANEL_W - PAD * 2, Fonts.UI_SMALL, t.textDim());

        search.bounds(panelX + PAD, panelY + 62, PANEL_W - PAD * 2, 32);
        search.render(g, t, mx, my);

        int top = listTop();
        int h = listHeight();

        if (hits.isEmpty()) {
            String msg = search.value().isBlank()
                    ? "Type to search the chat backlog."
                    : "Nothing in the backlog matches that.";
            Fonts.drawCentered(g, font, msg, panelX + PANEL_W / 2, top + h / 2,
                    Fonts.UI_BODY, t.textFaint());
            Ui.endHiRes(g);
            return;
        }

        Ui.pushClip(g, panelX, top, PANEL_W, h);
        int offset = Math.round(scroll);
        int first = Math.max(0, offset / ROW_H);
        int last = Math.min(hits.size(), first + h / ROW_H + 2);
        for (int i = first; i < last; i++) {
            drawHit(g, t, i, top + i * ROW_H - offset, mx, my);
        }
        Ui.popClip(g);

        if (hits.size() >= MAX_HITS) {
            // Said rather than silently truncated, so a missing result is explained.
            Fonts.drawRight(g, font, "First " + MAX_HITS + " matches", panelX + PANEL_W - PAD,
                    panelY + panelH - 18, Fonts.UI_SMALL, t.textFaint());
        }
        Ui.endHiRes(g);
    }

    private void drawHit(GuiGraphicsExtractor g, Theme t, int index, int y, int mx, int my) {
        int rx = panelX + PAD;
        int rw = PANEL_W - PAD * 2;
        int jumpW = rw - COPY_W - 8;
        boolean overJump = Ui.hovered(mx, my, rx, y, jumpW, ROW_H - 4);
        boolean overCopy = Ui.hovered(mx, my, rx + jumpW + 8, y, COPY_W, ROW_H - 4);

        Ui.roundRect(g, rx, y, jumpW, ROW_H - 4, 8, overJump ? t.cardHover() : t.card());
        Fonts.drawTruncated(g, font, LegacyText.strip(hits.get(index).content().getString()),
                rx + 10, Fonts.centerY(y, ROW_H - 4, Fonts.BODY_SZ), jumpW - 20,
                Fonts.UI_BODY, overJump ? t.text() : t.textDim());

        Ui.roundRect(g, rx + jumpW + 8, y, COPY_W, ROW_H - 4, 8,
                overCopy ? t.cardHover() : t.controlOff());
        Fonts.drawCentered(g, font, "Copy", rx + jumpW + 8 + COPY_W / 2,
                Fonts.centerY(y, ROW_H - 4, Fonts.BODY_SZ), Fonts.UI_BODY, t.textDim());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int mx = Ui.u(event.x());
        int my = Ui.u(event.y());
        if (search.mouseClicked(mx, my, event.button())) {
            return true;
        }

        int rx = panelX + PAD;
        int rw = PANEL_W - PAD * 2;
        int jumpW = rw - COPY_W - 8;
        int top = listTop();
        int offset = Math.round(scroll);
        for (int i = 0; i < hits.size(); i++) {
            int y = top + i * ROW_H - offset;
            if (y + ROW_H < top || y > top + listHeight()) {
                continue;
            }
            if (Ui.hovered(mx, my, rx, y, jumpW, ROW_H - 4)) {
                jumpTo(hits.get(i));
                return true;
            }
            if (Ui.hovered(mx, my, rx + jumpW + 8, y, COPY_W, ROW_H - 4)) {
                String text = LegacyText.strip(hits.get(i).content().getString());
                Minecraft.getInstance().keyboardHandler.setClipboard(text);
                Toasts.show("Copied to clipboard", text);
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    /** Scrolls the chat to a message and drops you into the chat screen looking at it. */
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
            // Trimmed away since the search ran: the backlog still has it, the display does not.
            Toasts.show("Not in view", "That message has scrolled out of the chat");
            return;
        }
        int max = Math.max(0, lines.size() - mc.gui.getChat().getLinesPerPage());
        acc.diego$setChatScrollbarPos(Math.min(target, max));
        mc.setScreen(new net.minecraft.client.gui.screens.ChatScreen("", false));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double sx, double sy) {
        scroll = Math.clamp(scroll - (float) sy * ROW_H * 2, 0f, maxScroll());
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (search.keyPressed(event.key(), event.modifiers())) {
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        return search.charTyped((char) event.codepoint()) || super.charTyped(event);
    }
}
