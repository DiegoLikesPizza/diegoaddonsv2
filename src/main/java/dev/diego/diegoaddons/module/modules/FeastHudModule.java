package dev.diego.diegoaddons.module.modules;

import dev.diego.configlib.hud.HudWidget;
import dev.diego.diegoaddons.config.ConfigManager;
import dev.diego.diegoaddons.gui.Fonts;
import dev.diego.diegoaddons.hud.HudElements;
import dev.diego.diegoaddons.hud.HudSlots;
import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.HudModule;
import dev.diego.diegoaddons.util.Garden;
import dev.diego.diegoaddons.util.HarvestFeast;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

/**
 * The Harvest Feast, while it is running and never otherwise.
 *
 * <p>Seasoning towards the next milestone, and the four crops that are in season. Both are worth
 * having on screen for the same reason: seasoning only drops from <b>in-season</b> crops, at 1 in
 * 2,500, so the two numbers together are the answer to "am I farming the right thing, and is it
 * getting anywhere".
 *
 * <p>It shows itself only during the event - that is the point of it, and it is why the element
 * hides rather than showing zeroes out of season. See {@link HarvestFeast} for where the numbers
 * come from and why the crops carry an age.
 */
public class FeastHudModule extends HudModule {
    public static FeastHudModule INSTANCE;

    private final BooleanSetting showCrops =
            new BooleanSetting(this, "showCrops", "Show the crops in season", true);
    private final BooleanSetting showTier =
            new BooleanSetting(this, "showTier", "Show the milestone number", true);
    private final BooleanSetting alwaysShow =
            new BooleanSetting(this, "alwaysShow", "Show even when the event is over", false);
    private final BooleanSetting debug =
            new BooleanSetting(this, "debug", "Debug scan (log)", false);

    public FeastHudModule() {
        super("feasthud", Category.GARDEN, "Feast HUD",
                "Seasoning milestones and the crops in season, during the Harvest Feast.");
        settings.add(showCrops);
        settings.add(showTier);
        settings.add(alwaysShow);
        settings.add(debug);
        INSTANCE = this;
    }

    public boolean debugScan() {
        return debug.get();
    }

    @Override
    protected String label() {
        return "Feast";
    }

    @Override
    protected String value(Minecraft mc) {
        return null;   // several lines; see hudLines
    }

    @Override
    protected String sampleValue() {
        return "37 / 75 seasoning";
    }

    @Override
    public void onClientTick(Minecraft mc) {
        HarvestFeast.tick(mc);
    }

    /** Whether the element should be on screen at all this frame. */
    private boolean visible() {
        return HarvestFeast.running() || alwaysShow.get();
    }

    /** The text above the icons: seasoning towards the next milestone, and which one that is. */
    private List<String> textLines() {
        List<String> out = new ArrayList<>(2);
        // Nothing has been read yet: say what to do about it rather than show an empty card. The
        // seasoning total lives in Ted's menu and nowhere else, so there is one answer.
        if (HarvestFeast.readAt() == 0) {
            out.add(showLabel() ? "Feast: open Feast Chef Ted" : "Open Feast Chef Ted");
            return out;
        }
        int next = HarvestFeast.nextMilestone();
        String progress = next < 0
                ? HarvestFeast.seasoning() + " seasoning - all milestones done"
                : HarvestFeast.seasoning() + " / " + next + " seasoning";
        out.add(showLabel() ? "Feast: " + progress : progress);
        if (showTier.get() && next >= 0) {
            out.add("Milestone " + (HarvestFeast.tierReached() + 1)
                    + "/" + HarvestFeast.totalMilestones()
                    + (HarvestFeast.grand() ? " (Grand)" : ""));
        }
        return out;
    }

    /** The crops to draw as icons, empty when there are none or they are switched off. */
    private List<String> iconCrops() {
        return showCrops.get() ? HarvestFeast.crops() : List.of();
    }

    /**
     * The line beside the icons: how long these four have left.
     *
     * <p>Stale is said in words rather than by leaving the icons out. The four rotate every SkyBlock
     * month, and last month's are a guess you should be able to see is a guess - but they are also
     * all there is until you next open Ted.
     */
    private String seasonText() {
        if (HarvestFeast.stale()) {
            return "old - reopen Ted";
        }
        long left = HarvestFeast.msLeftInSeason();
        return left < 0 ? "" : clock(left);
    }

    /**
     * {@code 3:20:15} - hours, minutes and seconds, zero-padded.
     *
     * <p>A season runs to over ten hours, and "620m 15s" is a number you have to convert in your
     * head before it means anything. The shared {@code Garden.time} shape is right for a two-minute
     * pest cooldown and wrong here.
     */
    private static String clock(long ms) {
        long total = Math.max(0, ms) / 1000;
        return String.format(java.util.Locale.ROOT, "%d:%02d:%02d",
                total / 3600, (total % 3600) / 60, total % 60);
    }

    // --- the element ------------------------------------------------------------------------------

    private static final int PAD_X = 8;
    private static final int PAD_Y = 5;
    private static final int ICON = 16;
    private static final int ICON_GAP = 3;
    private static final int ROW_GAP = 3;

    @Override
    public HudWidget hudWidget() {
        return new HudWidget() {
            @Override
            public int width() {
                Minecraft mc = Minecraft.getInstance();
                Font font = mc.font;
                if (font == null) {
                    return 0;
                }
                int w = 0;
                for (String line : textLines()) {
                    w = Math.max(w, Fonts.width(font, line, Fonts.MEDIUM));
                }
                w = Math.max(w, iconRowWidth(font));
                return w + PAD_X * 2;
            }

            @Override
            public int height() {
                int rows = textLines().size();
                int h = PAD_Y * 2 + rows * Fonts.BODY_H;
                if (!iconCrops().isEmpty()) {
                    h += ROW_GAP + ICON;
                }
                return h;
            }

            @Override
            public boolean shouldRender() {
                return visible();
            }

            @Override
            public void render(GuiGraphicsExtractor g) {
                paint(g);
            }

            /** Out of season there is nothing to read, so the editor draws the card as it will look. */
            @Override
            public void renderPreview(GuiGraphicsExtractor g) {
                paint(g);
            }
        };
    }

    /** Icons side by side, then the countdown after them. */
    private int iconRowWidth(Font font) {
        List<String> crops = iconCrops();
        if (crops.isEmpty()) {
            return 0;
        }
        int w = crops.size() * (ICON + ICON_GAP);
        String time = seasonText();
        return time.isEmpty() ? w - ICON_GAP : w + Fonts.width(font, time, Fonts.SMALL);
    }

    private void paint(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        if (font == null) {
            return;
        }
        List<String> lines = textLines();
        List<String> crops = iconCrops();
        int inner = Math.max(iconRowWidth(font), lines.stream()
                .mapToInt(l -> Fonts.width(font, l, Fonts.MEDIUM)).max().orElse(0));
        int w = inner + PAD_X * 2;
        int h = PAD_Y * 2 + lines.size() * Fonts.BODY_H + (crops.isEmpty() ? 0 : ROW_GAP + ICON);

        HudElements.panel(g, this, w, h, 7, ConfigManager.get().smoothCorners);

        int y = PAD_Y;
        for (String line : lines) {
            g.text(font, Fonts.t(line, Fonts.MEDIUM), PAD_X, y, style().textColor(), false);
            y += Fonts.BODY_H;
        }
        if (crops.isEmpty()) {
            return;
        }
        y += ROW_GAP;
        int x = PAD_X;
        for (String crop : crops) {
            HudSlots.item(g, font, HarvestFeast.icon(crop), x, y, ICON);
            x += ICON + ICON_GAP;
        }
        String time = seasonText();
        if (!time.isEmpty()) {
            // Beside the icons rather than under them: the row reads as "these four, for this long".
            g.text(font, Fonts.t(time, Fonts.SMALL), x,
                    y + (ICON - Fonts.SMALL_VH) / 2, style().textColor(), false);
        }
    }
}
