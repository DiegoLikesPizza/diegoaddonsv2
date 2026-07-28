package dev.diego.diegoaddons.gui;

import com.render.api.gui.ButtonComponent;
import com.render.api.gui.ContainerComponent;
import com.render.api.gui.GuiGradient;
import com.render.api.gui.layout.GuiAlignment;
import dev.diego.diegoaddons.DiegoAddonsV2Client;
import dev.diego.diegoaddons.config.ConfigManager;

/**
 * The one-time welcome: what the mod is on the left, the one choice worth making up front on the
 * right.
 *
 * <p>Two columns rather than one list. The left says what this is and what it is for and asks
 * nothing; the right is the only thing a first run actually needs to decide - what it should look
 * like - with the two ways out beneath it. A welcome that opens with a form is a worse welcome than
 * one that opens with a sentence.
 *
 * <p>No title bar: a screen that is its own front page says what it is by its content, and a close
 * button would be a third way out beside the two it already offers.
 */
public class IntroView extends DiegoView {
    private static final float PANEL_W = 1440f;
    private static final float PANEL_H = 790f;
    private static final float LEFT_W = 860f;
    private static final float RIGHT_W = PANEL_W - LEFT_W;
    private static final float SIDE_PAD = 56f;
    private static final float RIGHT_PAD = 48f;

    private boolean finished;

    public IntroView() {
        super("Welcome", PANEL_W, PANEL_H);
    }

    @Override
    protected boolean showHeader() {
        return false;
    }

    private void finish() {
        if (!finished) {
            finished = true;
            ConfigManager.get().introShown = true;
            ConfigManager.save();
        }
    }

    @Override
    public void onClose() {
        finish();
        super.onClose();
    }

    @Override
    protected void content(float width, float height) {
        ContainerComponent body = row(width, 0f).height(height).alignItems(GuiAlignment.STRETCH);
        body.add(left(height));
        body.add(right(height));
        panel.add(body);
    }

    // --- what this is -------------------------------------------------------------------------------

    private ContainerComponent left(float height) {
        float inner = LEFT_W - SIDE_PAD * 2f;
        ContainerComponent col = column(LEFT_W, 0f).height(height).padding(SIDE_PAD);

        col.add(brand());
        col.add(spacer(inner, 26f));

        String headline = "Your SkyBlock tools, finally in one place.";
        col.add(textBox(GuiText.paragraph(headline, t.text(), 38f, inner).font(GuiText.TITLE),
                inner, GuiText.paragraphHeight(headline, 38f, inner)));
        col.add(spacer(inner, 20f));

        String blurb = "A focused workspace for combat, mining, dungeons, inventories and everyday "
                + "quality-of-life features - designed to stay out of your way while you play.";
        col.add(textBox(GuiText.paragraph(blurb, t.textMuted(), 15f, inner),
                inner, GuiText.paragraphHeight(blurb, 15f, inner)));
        col.add(spacer(inner, 26f));

        col.add(point("01", "Clear controls",
                "Searchable categories and settings that open under the module they belong to.", inner));
        col.add(spacer(inner, 10f));
        col.add(point("02", "A HUD you can trust",
                "Every element is placed, scaled and faded from one screen, and stays where you put it.", inner));
        col.add(spacer(inner, 10f));
        col.add(point("03", "Built for SkyBlock",
                "Dungeon and Crystal Hollows maps, puzzle solvers, and the information you actually read.", inner));

        // Pushes the footnote to the bottom of the column, wherever the copy above it ends.
        ContainerComponent gap = column(inner, 0f);
        gap.flexGrow(1f);
        col.add(gap);
        col.add(textBox(GuiText.label("You can change every visual choice later.", t.textFaint(), 12f),
                inner, 20f));
        return col;
    }

    /** The mark, the name, and which version of it this is. */
    private ContainerComponent brand() {
        ContainerComponent r = row(0f, 14f).height(52f);
        ContainerComponent mark = row(52f, 0f);
        mark.height(52f).cornerRadius(15f).justifyContent(GuiAlignment.CENTER)
                .backgroundColor(GuiColors.of(t.accent()))
                .gradient(new GuiGradient()
                        .startColor(GuiColors.of(t.accent()))
                        .endColor(GuiColors.of(t.accentTo())));
        mark.add(GuiText.label("D", t.accentText(), 26f));
        r.add(mark);

        ContainerComponent names = column(0f, 2f).height(52f)
                .justifyContent(GuiAlignment.CENTER);
        names.add(GuiText.label("DiegoAddons", t.text(), 22f));
        names.add(GuiText.label("VERSION 2", t.accent(), 10f));
        r.add(names);
        return r;
    }

    /** One numbered point: what it is, and one line on why it matters. */
    private ContainerComponent point(String number, String title, String detail, float width) {
        ContainerComponent card = row(width, 14f).height(70f).padding(0f, 16f)
                .cornerRadius(12f)
                .backgroundColor(GuiColors.of(t.surfaceAlt()))
                .borderWidth(1f).borderColor(GuiColors.of(t.border()));

        ContainerComponent chip = row(36f, 0f);
        chip.height(36f).cornerRadius(10f).justifyContent(GuiAlignment.CENTER)
                .backgroundColor(GuiColors.of(Theme.withAlpha(t.accent(), 0.22f)));
        chip.add(GuiText.label(number, t.accent(), 12f));
        card.add(chip);

        float textW = width - 36f - 14f - 32f;
        ContainerComponent lines = column(textW, 3f).height(70f)
                .justifyContent(GuiAlignment.CENTER);
        lines.add(GuiText.label(title, t.text(), 15f));
        lines.add(GuiText.paragraph(detail, t.textMuted(), 12f, textW));
        card.add(lines);
        return card;
    }

    // --- the one choice worth making now ------------------------------------------------------------

    private ContainerComponent right(float height) {
        float inner = RIGHT_W - RIGHT_PAD * 2f;
        ContainerComponent col = column(RIGHT_W, 0f).height(height).padding(RIGHT_PAD)
                .backgroundColor(GuiColors.of(t.surfaceAlt()));

        col.add(textBox(GuiText.label("MAKE IT YOURS", t.textFaint(), 11f), inner, 18f));
        col.add(spacer(inner, 10f));
        col.add(textBox(GuiText.label("Choose a starting theme", t.text(), 26f), inner, 34f));
        col.add(spacer(inner, 10f));

        String sub = "The interface and the HUD share the same palette. Switch again at any time.";
        col.add(textBox(GuiText.paragraph(sub, t.textMuted(), 13f, inner),
                inner, GuiText.paragraphHeight(sub, 13f, inner)));
        col.add(spacer(inner, 18f));

        for (Theme th : Themes.ALL) {
            col.add(themeRow(th, inner));
            col.add(spacer(inner, 6f));
        }

        col.add(spacer(inner, 14f));
        col.add(shortcut(inner));

        ContainerComponent gap = column(inner, 0f);
        gap.flexGrow(1f);
        col.add(gap);

        ButtonComponent go = clickable(t.accent(), () -> {
            finish();
            close();
        });
        asRow(go, inner, 0f).height(54f).cornerRadius(12f).justifyContent(GuiAlignment.CENTER);
        go.add(GuiText.label("Get started", t.accentText(), 15f));
        col.add(go);
        col.add(spacer(inner, 10f));

        ButtonComponent settings = clickable(t.surface(), () -> {
            finish();
            close();
            new DiegoClickGuiView().open();
        });
        asRow(settings, inner, 0f).height(54f).cornerRadius(12f).justifyContent(GuiAlignment.CENTER)
                .borderWidth(1f).borderColor(GuiColors.of(t.border()));
        settings.add(GuiText.label("Open settings now", t.text(), 15f));
        col.add(settings);
        return col;
    }

    /** One theme: its colour, its name, and whether it is the one in use. */
    private ButtonComponent themeRow(Theme th, float width) {
        boolean current = th.name().equals(t.name());
        ButtonComponent b = clickable(current ? t.elevated() : t.surface(), () -> {
            Themes.select(th);
            panel.clearChildren();
            build();   // the whole screen is the preview, so the whole screen redraws
        });
        asRow(b, width, 12f).height(38f).cornerRadius(9f).padding(0f, 12f)
                .justifyContent(GuiAlignment.SPACE_BETWEEN)
                .borderWidth(current ? 2f : 1f)
                .borderColor(GuiColors.of(current ? t.accent() : t.border()));

        ContainerComponent leftSide = row(0f, 12f).height(38f);
        ContainerComponent dot = new ContainerComponent();
        dot.size(20f, 20f).cornerRadius(10f)
                .backgroundColor(GuiColors.of(th.accent()))
                .gradient(new GuiGradient()
                        .startColor(GuiColors.of(th.accent()))
                        .endColor(GuiColors.of(th.accentTo())));
        leftSide.add(dot);
        leftSide.add(GuiText.label(th.name(), current ? t.text() : t.textMuted(), 14f));
        b.add(leftSide);

        if (current) {
            b.add(GuiText.label("SELECTED", t.accent(), 10f));
        }
        return b;
    }

    /** The keybind, stated once rather than left to be discovered. */
    private ContainerComponent shortcut(float width) {
        ContainerComponent card = column(width, 8f).padding(14f, 16f).cornerRadius(12f)
                .backgroundColor(GuiColors.of(t.surface()))
                .borderWidth(1f).borderColor(GuiColors.of(t.border()));
        card.add(textBox(GuiText.label("OPEN THE MENU IN GAME", t.textFaint(), 10f),
                width - 32f, 16f));

        ContainerComponent r = row(width - 32f, 10f).height(34f)
                .justifyContent(GuiAlignment.SPACE_BETWEEN);
        r.add(textBox(GuiText.label("Keyboard shortcut", t.text(), 14f), 0f, 34f).flexGrow(1f));

        String key = DiegoAddonsV2Client.OPEN_MENU.getTranslatedKeyMessage().getString();
        ContainerComponent chip = row(0f, 0f);
        chip.height(30f).padding(0f, 12f).cornerRadius(8f)
                .justifyContent(GuiAlignment.CENTER)
                .backgroundColor(GuiColors.of(t.surfaceAlt()))
                .borderWidth(1f).borderColor(GuiColors.of(t.border()));
        chip.add(GuiText.label(key, t.text(), 13f));
        r.add(chip);
        card.add(r);
        return card;
    }

    /** Vertical space, which a gap on the column cannot give you between only some of its rows. */
    private static ContainerComponent spacer(float width, float height) {
        ContainerComponent c = new ContainerComponent();
        c.size(width, height);
        return c;
    }
}
