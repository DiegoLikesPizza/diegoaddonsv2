package dev.diego.diegoaddons.module.modules;

import dev.diego.configlib.hud.HudWidget;
import dev.diego.diegoaddons.config.ConfigManager;
import dev.diego.diegoaddons.gui.Fonts;
import dev.diego.diegoaddons.gui.Theme;
import dev.diego.diegoaddons.gui.Themes;
import dev.diego.diegoaddons.gui.UiRender;
import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.HudModule;
import dev.diego.diegoaddons.util.SkyblockHud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

/**
 * The active SkyBlock pet on the HUD: its icon, name and level.
 *
 * <p>Its own element rather than a column of the inventory HUD - a pet is a glance and an inventory
 * is a read, and switching one off should not move the other.
 */
public class PetHudModule extends HudModule {
    public static PetHudModule INSTANCE;

    private final BooleanSetting hideWithout =
            new BooleanSetting(this, "hideWithout", "Hide when no pet is out", false);

    public PetHudModule() {
        super("pethud", "Pet HUD", "Show your active pet on the HUD.", false);
        settings.add(hideWithout);
        INSTANCE = this;
    }

    /** The plate is the shared "Background plate" appearance row now, not a toggle of its own. */
    public boolean showBackground() {
        return style().plate();
    }

    public boolean hideWithoutPet() {
        return hideWithout.get();
    }

    /** The pet's level line, shared with the element. */
    public String levelText(SkyblockHud.PetInfo info) {
        String lvl = info.level() >= 0 ? "Lvl " + info.level() : "Lvl ?";
        return info.xp() == null ? lvl : lvl + "  " + info.xp();
    }

    @Override
    protected String label() {
        return "Pet";
    }

    @Override
    protected String value(Minecraft mc) {
        return null;   // drawn by its own element
    }

    // --- the HUD element ------------------------------------------------------------------------

    private static final int PAD_X = 8;
    private static final int PAD_Y = 5;
    /** The icon is drawn at double an item's natural 16px, which is what makes it read as a portrait. */
    private static final int ITEM = 32;
    private static final int GAP = 2;
    private static final int MIN_W = 64;

    /** The card's text for this frame, measured. */
    private record Card(String name, String level, int colour, int width) {
    }

    /** Measured in {@code width()} and drawn in {@code render()}; see the note on the scoreboard. */
    private Card frame;

    @Override
    public HudWidget hudWidget() {
        return new HudWidget() {
            @Override
            public int width() {
                frame = read(Minecraft.getInstance());
                return (frame == null ? MIN_W : frame.width()) + PAD_X * 2;
            }

            @Override
            public int height() {
                return PAD_Y * 2 + ITEM + GAP + Fonts.BODY_H + Fonts.SMALL_H;
            }

            @Override
            public boolean shouldRender() {
                Minecraft mc = Minecraft.getInstance();
                return mc.player != null && (SkyblockHud.petInfo() != null || !hideWithoutPet());
            }

            @Override
            public void render(GuiGraphicsExtractor g) {
                paint(g, frame);
            }

            /** Outside SkyBlock there is no pet to read, so the card stands in as an empty one. */
            @Override
            public void renderPreview(GuiGraphicsExtractor g) {
                frame = read(Minecraft.getInstance());
                paint(g, frame);
            }
        };
    }

    /** The pet's name and level line, widened to whatever they actually say. */
    private Card read(Minecraft mc) {
        Font font = mc.font;
        if (font == null) {
            return null;
        }
        SkyblockHud.PetInfo info = SkyblockHud.petInfo();
        Theme t = Themes.current();
        String name = info == null ? "No pet" : info.name();
        String level = info == null ? "" : levelText(info);
        int w = Math.max(MIN_W, Math.max(Fonts.width(font, name, Fonts.MEDIUM),
                Fonts.width(font, level, Fonts.SMALL)));
        return new Card(name, level, info == null ? t.textFaint() : info.colour(), w);
    }

    private void paint(GuiGraphicsExtractor g, Card card) {
        if (card == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        if (font == null) {
            return;
        }
        Theme t = Themes.current();
        int inner = card.width();
        int w = inner + PAD_X * 2;
        int h = PAD_Y * 2 + ITEM + GAP + Fonts.BODY_H + Fonts.SMALL_H;

        dev.diego.diegoaddons.hud.HudElements.panel(g, this, w, h, 8,
                ConfigManager.get().smoothCorners);

        ItemStack stack = SkyblockHud.pet();
        if (!stack.isEmpty()) {
            // fakeItem draws at an item's natural 16px, so the pose carries the doubling rather than
            // the call - there is no size argument to pass.
            g.pose().pushMatrix();
            g.pose().translate(PAD_X + (inner - ITEM) / 2f, (float) PAD_Y);
            g.pose().scale(2f, 2f);
            g.fakeItem(stack, 0, 0);
            g.pose().popMatrix();
        }

        int cx = PAD_X + inner / 2;
        int y = PAD_Y + ITEM + GAP;
        UiRender.textCentered(g, font, card.name(), Fonts.MEDIUM, cx, y, card.colour());
        if (!card.level().isEmpty()) {
            UiRender.textCentered(g, font, card.level(), Fonts.SMALL, cx, y + Fonts.BODY_H,
                    style().mutedColor());
        }
    }
}
