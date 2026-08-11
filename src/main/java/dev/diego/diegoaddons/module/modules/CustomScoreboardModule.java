package dev.diego.diegoaddons.module.modules;

import dev.diego.configlib.hud.HudWidget;
import dev.diego.diegoaddons.DiegoAddonsV2Client;
import dev.diego.diegoaddons.config.ConfigManager;
import dev.diego.diegoaddons.gui.Fonts;
import dev.diego.diegoaddons.gui.Theme;
import dev.diego.diegoaddons.gui.Themes;
import dev.diego.diegoaddons.gui.UiRender;
import dev.diego.diegoaddons.module.ActionSetting;
import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.HudModule;
import dev.diego.diegoaddons.module.StringSetting;
import dev.diego.diegoaddons.util.CustomScoreboard;
import dev.diego.diegoaddons.util.GlyphProbe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Replaces the vanilla SkyBlock sidebar with a clean themed panel (no red score numbers). The vanilla
 * sidebar is cancelled by {@code ScoreboardSidebarMixin}; the drawing lives in
 * {@link dev.diego.diegoaddons.util.CustomScoreboard}.
 */
public class CustomScoreboardModule extends HudModule {
    public static CustomScoreboardModule INSTANCE;

    private final BooleanSetting hideServerId =
            new BooleanSetting(this, "hideServerId", "Hide the server id", true);
    private final BooleanSetting hideUrl =
            new BooleanSetting(this, "hideUrl", "Hide the Hypixel URL", true);
    private final BooleanSetting hideDate =
            new BooleanSetting(this, "hideDate", "Hide the date line", false);
    private final BooleanSetting showBank =
            new BooleanSetting(this, "showBank", "Show bank balance", false);
    private final StringSetting title =
            new StringSetting(this, "title", "Custom title", "", null);
    private final StringSetting top =
            new StringSetting(this, "top", "Text at the top", "", null);
    private final StringSetting bottom =
            new StringSetting(this, "bottom", "Text at the bottom", "", null);
    private final ActionSetting symbolReport =
            new ActionSetting(this, "symbols", "Symbol report (log)", "Log",
                    CustomScoreboardModule::logSymbols);

    public CustomScoreboardModule() {
        super("customscoreboard", Category.RENDER, "Custom Scoreboard",
                "Re-style the sidebar: themed panel, no red numbers, and only the lines you want.",
                false);
        settings.add(hideServerId);
        settings.add(hideUrl);
        settings.add(hideDate);
        settings.add(showBank);
        settings.add(title);
        settings.add(top);
        settings.add(bottom);
        settings.add(symbolReport);
        INSTANCE = this;
    }

    /** The plate is the shared "Background plate" appearance row now, not a toggle of its own. */
    public boolean background() {
        return style().plate();
    }

    public boolean hideServerId() {
        return hideServerId.get();
    }

    public boolean hideUrl() {
        return hideUrl.get();
    }

    public boolean hideDate() {
        return hideDate.get();
    }

    public boolean showBank() {
        return showBank.get();
    }

    /** A title of your own, or blank to keep the server's. */
    public String customTitle() {
        return title.get();
    }

    public String topText() {
        return top.get();
    }

    public String bottomText() {
        return bottom.get();
    }

    /** Whether the line at {@code index} of {@code total} is one of yours rather than the server's. */
    public boolean isCustomLine(int index, int total) {
        if (!topText().isBlank() && index == 0) {
            return true;
        }
        return !bottomText().isBlank() && index == total - 1;
    }

    /**
     * Dumps the sidebar a character at a time, and for every character that is not plain ASCII says
     * whether the mod's face has it and whether the game's own face has it.
     *
     * <p>2.5.3 gave every one of the mod's font definitions the vanilla fallbacks, which should mean
     * its coverage is exactly the game's. Symbols that still come out as a box are therefore one of
     * two things, and they need opposite fixes: <b>missing here but not in vanilla</b> is a fallback
     * that did not take, ours to fix; <b>missing in both</b> is a character the game does not have at
     * all, which no font definition can conjure - it needs the resource pack that ships it.
     */
    private static void logSymbols() {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        if (font == null) {
            return;
        }
        Component title = CustomScoreboard.title(mc);
        List<Component> lines = CustomScoreboard.lines(mc);
        if (title == null && lines.isEmpty()) {
            report(mc, "No sidebar to read - stand on SkyBlock and try again.");
            return;
        }

        Logger log = DiegoAddonsV2Client.LOGGER;
        log.info("[scoreboard symbols] --- report ---");
        List<Component> all = new ArrayList<>();
        if (title != null) {
            all.add(title);
        }
        all.addAll(lines);

        int ours = 0;
        int both = 0;
        for (Component c : all) {
            String plain = c.getString();
            log.info("[scoreboard symbols] line: {}", plain);
            for (int i = 0; i < plain.length(); ) {
                int cp = plain.codePointAt(i);
                i += Character.charCount(cp);
                // ASCII is what Poppins is for; a box there would be a different bug entirely.
                if (cp < 0x80 || cp == '§') {
                    continue;
                }
                boolean missingHere = GlyphProbe.missing(font, Fonts.MEDIUM_FACE, cp);
                boolean missingVanilla = GlyphProbe.missingInVanilla(font, cp);
                if (missingHere && missingVanilla) {
                    both++;
                } else if (missingHere) {
                    ours++;
                }
                log.info("[scoreboard symbols]   U+{} '{}' mod={} vanilla={}",
                        String.format("%04X", cp), new String(Character.toChars(cp)),
                        missingHere ? "MISSING" : "ok", missingVanilla ? "MISSING" : "ok");
            }
        }
        log.info("[scoreboard symbols] --- {} missing here only, {} missing everywhere ---", ours, both);
        report(mc, "Symbol report in the log: " + ours + " missing in the mod's font only, "
                + both + " missing in the game's font too.");
    }

    private static void report(Minecraft mc, String text) {
        if (mc.gui != null) {
            mc.gui.getChat().addClientSystemMessage(Component.literal("§b[DiegoAddons] §f" + text));
        }
    }

    @Override
    protected String label() {
        return "Scoreboard";
    }

    @Override
    protected String value(net.minecraft.client.Minecraft mc) {
        return null;   // drawn by its own element, not as a text chip
    }

    // --- the HUD element ------------------------------------------------------------------------

    private static final int PAD_X = 8;
    private static final int PAD_Y = 5;
    private static final int TITLE_H = 13;
    private static final int ROW_H = 11;

    /** What the editor shows when there is no sidebar to read - a lobby, a menu, single player. */
    private static final List<String> SAMPLE = List.of("§7Hub", "§aPurse: §61.2M", "§aGems: §d4,821");

    /** The sidebar as measured text, re-faced but keeping the server's colours. */
    private record Board(Component title, List<Component> lines, int width) {
    }

    /**
     * Held between {@code width()} and {@code render()} within a frame. configlib asks for the size
     * first and draws second, and both need the same measured text; re-reading the scoreboard in
     * between would measure one thing and draw another on the frame a line appears.
     */
    private Board frame;

    @Override
    public HudWidget hudWidget() {
        return new HudWidget() {
            @Override
            public int width() {
                frame = read(Minecraft.getInstance(), false);
                return frame == null ? 0 : frame.width() + PAD_X * 2;
            }

            @Override
            public int height() {
                return frame == null ? 0 : PAD_Y * 2 + TITLE_H + frame.lines().size() * ROW_H;
            }

            /** No sidebar on this server, or none yet - leave the screen alone rather than draw a shell. */
            @Override
            public boolean shouldRender() {
                return read(Minecraft.getInstance(), false) != null;
            }

            @Override
            public void render(GuiGraphicsExtractor g) {
                paint(g, frame);
            }

            @Override
            public void renderPreview(GuiGraphicsExtractor g) {
                Board b = read(Minecraft.getInstance(), true);
                frame = b;
                paint(g, b);
            }
        };
    }

    /**
     * Reads and measures the sidebar, or returns null when there is nothing to show.
     *
     * @param sample when true, stands a fixed example in for a missing sidebar so the editor has
     *               something to drag
     */
    private Board read(Minecraft mc, boolean sample) {
        Font font = mc.font;
        if (font == null) {
            return null;
        }
        Component rawTitle = CustomScoreboard.title(mc);
        List<Component> rawLines = CustomScoreboard.lines(mc);
        if (rawTitle == null || rawLines.isEmpty()) {
            if (!sample) {
                return null;
            }
            rawTitle = Component.literal("SKYBLOCK");
            rawLines = SAMPLE.stream().map(s -> (Component) Component.literal(s.replace('&', '§'))).toList();
        }

        Component title = Fonts.reface(rawTitle, Fonts.MEDIUM_FACE);
        List<Component> lines = new ArrayList<>(rawLines.size());
        int w = font.width(title);
        for (Component raw : rawLines) {
            Component line = Fonts.reface(raw, Fonts.MEDIUM_FACE);
            lines.add(line);
            w = Math.max(w, font.width(line));
        }
        return new Board(title, lines, w);
    }

    private void paint(GuiGraphicsExtractor g, Board b) {
        if (b == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        if (font == null) {
            return;
        }
        Theme t = Themes.current();
        int inner = b.width();
        int w = inner + PAD_X * 2;
        int h = PAD_Y * 2 + TITLE_H + b.lines().size() * ROW_H;

        dev.diego.diegoaddons.hud.HudElements.panel(g, this, w, h, 7,
                ConfigManager.get().smoothCorners);

        g.text(font, b.title(), PAD_X + (inner - font.width(b.title())) / 2, PAD_Y + 2,
                style().textColor(), false);

        int y = PAD_Y + TITLE_H;
        List<Component> lines = b.lines();
        for (int i = 0; i < lines.size(); i++) {
            Component line = lines.get(i);
            // Your own lines are headings rather than readings, so they are centred like the title;
            // the server's stay left, where a column of numbers belongs.
            int x = isCustomLine(i, lines.size())
                    ? PAD_X + (inner - font.width(line)) / 2
                    : PAD_X;
            // The colour passed here is only the fallback: any run of the line that set its own
            // colour keeps it, which is the whole point of re-facing rather than re-styling.
            g.text(font, line, x, y + 1, style().textColor(), false);
            y += ROW_H;
        }
    }
}
