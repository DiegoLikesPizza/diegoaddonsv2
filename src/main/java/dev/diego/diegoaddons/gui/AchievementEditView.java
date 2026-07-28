package dev.diego.diegoaddons.gui;

import com.render.api.gui.ButtonComponent;
import com.render.api.gui.ContainerComponent;
import com.render.api.gui.GuiOverflowMode;
import com.render.api.gui.ScrollContainerComponent;
import com.render.api.gui.layout.GuiAlignment;
import dev.diego.diegoaddons.config.Achievement;
import dev.diego.diegoaddons.config.ConfigManager;
import dev.diego.diegoaddons.util.Achievements;
import net.minecraft.client.Minecraft;

import java.util.List;

/**
 * One achievement: what it is called, and what unlocks it.
 *
 * <p>Two ways to say what unlocks it, and an achievement may use both. A chat trigger is something
 * that <em>happens</em> - a line Hypixel prints. A condition is something that <em>is the case</em>,
 * counted over every profile the mod has recorded, which is the only way "two profiles past level
 * 200" can be asked at all: no single profile knows that about itself.
 *
 * <p>Conditions are offered as fixed choices rather than a text box because a typed expression that
 * silently means nothing is worse than a smaller set that always means something.
 */
public class AchievementEditView extends DiegoView {
    private static final float PANEL_W = 940f;
    private static final float PANEL_H = 660f;
    private static final float ROW_H = 44f;

    private static final String[] STATS = {"level", "playtime", "idle"};
    private static final String[] STAT_NAMES = {"SkyBlock level", "Hours played", "Days since played"};
    private static final String[] MODES = {"any", "normal", "ironman", "stranded", "bingo"};
    private static final String[] MODE_NAMES = {"Any mode", "Normal", "Ironman", "Stranded", "Bingo"};
    private static final String[] COMPARATORS = {">=", "<=", "=="};
    private static final String[] COMPARATOR_NAMES = {"at least", "at most", "exactly"};

    private final Achievement a;
    private ScrollContainerComponent list;

    public AchievementEditView(Achievement achievement) {
        super("Achievement", PANEL_W, PANEL_H);
        this.a = achievement;
    }

    @Override
    protected void content(float width, float height) {
        ContainerComponent body = column(width, 10f).height(height).padding(PAD);
        float inner = width - PAD * 2f;

        if (a.builtin) {
            // A shipped achievement is not yours to rename - but its trigger is a guess about
            // Hypixel's wording, and that is exactly the part worth being able to correct.
            body.add(textBox(GuiText.label(a.name, t.text(), 18f), inner, 26f));
            body.add(textBox(GuiText.label(
                    a.description.isBlank() ? a.category : a.category + "  ·  " + a.description,
                    t.textMuted(), 12f), inner, 20f));
        } else {
            body.add(field(a.name, "Name", inner, s -> {
                a.name = s;
                ConfigManager.save();
            }));
            body.add(field(a.description, "Description (optional)", inner, s -> {
                a.description = s;
                ConfigManager.save();
            }));
        }

        body.add(textBox(GuiText.label(
                "Chat trigger - a line that unlocks it. * matches anything. Leave empty for none.",
                t.textFaint(), 12f), inner, 18f));
        body.add(field(Achievements.pattern(a), "You found a * Essence", inner,
                s -> Achievements.setPattern(a, s)));
        if (a.builtin && !a.excludes.isBlank()) {
            body.add(textBox(GuiText.label("Ignored when the line contains: " + a.excludes,
                    t.textFaint(), 11f), inner, 16f));
        }
        if (a.counted()) {
            body.add(textBox(GuiText.label(
                    "Counted: " + Achievements.counter(a.counter) + " of " + a.threshold
                            + " so far, shared with the other tiers of this one.",
                    t.textFaint(), 11f), inner, 16f));
        }

        ContainerComponent head = row(inner, 12f).height(28f)
                .justifyContent(GuiAlignment.SPACE_BETWEEN);
        head.add(textBox(GuiText.label("Conditions - all must hold, counted over every profile",
                t.textMuted(), 12f), 0f, 28f).flexGrow(1f));
        if (!a.builtin) {
            ButtonComponent addCond = clickable(t.accent(), () -> {
                a.conditions.add(new Achievement.Condition("level", ">=", 200));
                ConfigManager.save();
                rebuildView();
            });
            asRow(addCond, 150f, 0f).height(28f).cornerRadius(8f).justifyContent(GuiAlignment.CENTER);
            addCond.add(GuiText.label("Add condition", t.accentText(), 13f));
            head.add(addCond);
        }
        body.add(head);

        list = new ScrollContainerComponent();
        list.size(inner, height - PAD * 2f - 32f * 3f - 18f - 28f - 44f - 60f);
        asColumn(list, inner, 8f);
        list.overflowY(GuiOverflowMode.AUTO);
        body.add(list);

        body.add(footer(inner));
        panel.add(body);
        fill(inner);
    }

    /** Back, and a way to try the thing out without waiting for it to really happen. */
    private ContainerComponent footer(float inner) {
        ContainerComponent bar = row(inner, 10f).height(34f);

        ButtonComponent back = clickable(t.surface(),
                () -> Minecraft.getInstance().execute(() -> new AchievementsView().open()));
        asRow(back, 130f, 0f).height(34f).cornerRadius(9f).justifyContent(GuiAlignment.CENTER)
                .borderWidth(1f).borderColor(GuiColors.of(t.border()));
        back.add(GuiText.label("All achievements", t.text(), 13f));
        bar.add(back);

        boolean on = Achievements.isOn(a);
        ButtonComponent pause = clickable(on ? t.surface() : t.elevated(), () -> {
            Achievements.setOn(a, !on);
            rebuildView();
        });
        asRow(pause, 100f, 0f).height(34f).cornerRadius(9f).justifyContent(GuiAlignment.CENTER)
                .borderWidth(1f).borderColor(GuiColors.of(t.border()));
        pause.add(GuiText.label(on ? "Active" : "Paused", on ? t.text() : t.textFaint(), 13f));
        bar.add(pause);

        boolean done = Achievements.isUnlocked(a);
        ButtonComponent toggle = clickable(done ? t.surface() : t.accent(), () -> {
            if (done) {
                Achievements.relock(a);
            } else {
                Achievements.unlockManually(a);
            }
            rebuildView();
        });
        asRow(toggle, 150f, 0f).height(34f).cornerRadius(9f).justifyContent(GuiAlignment.CENTER)
                .borderWidth(1f).borderColor(GuiColors.of(t.border()));
        toggle.add(GuiText.label(done ? "Lock again" : "Unlock by hand",
                done ? t.textMuted() : t.accentText(), 13f));
        bar.add(toggle);
        return bar;
    }

    private void rebuildView() {
        panel.clearChildren();
        build();
    }

    private void fill(float inner) {
        list.clearChildren();
        if (a.conditions.isEmpty()) {
            list.add(textBox(GuiText.label(
                    a.chat.isBlank()
                            ? "No conditions. Add one, or give it a chat trigger above."
                            : "No conditions - the chat line alone unlocks it.",
                    t.textFaint(), 12f), inner, 24f));
            return;
        }
        for (Achievement.Condition c : List.copyOf(a.conditions)) {
            ContainerComponent r = row(inner - 24f, 8f).height(ROW_H).flexShrink(0f).padding(0f, 10f)
                    .cornerRadius(10f).backgroundColor(GuiColors.of(t.surfaceAlt()))
                    .borderWidth(1f).borderColor(GuiColors.of(t.border()));

            r.add(cycle(STATS, STAT_NAMES, c.stat, 168f, v -> c.stat = v));
            r.add(cycle(MODES, MODE_NAMES, c.gamemode, 120f, v -> c.gamemode = v));
            r.add(cycle(COMPARATORS, COMPARATOR_NAMES, c.comparator, 100f, v -> c.comparator = v));
            r.add(field(trim(c.value), "0", 90f, s -> {
                c.value = parse(s, c.value);
                ConfigManager.save();
            }));
            r.add(textBox(GuiText.label("on", t.textFaint(), 12f), 22f, ROW_H));
            r.add(field(String.valueOf(c.profiles), "1", 60f, s -> {
                c.profiles = Math.max(1, (int) parse(s, c.profiles));
                ConfigManager.save();
            }));
            r.add(textBox(GuiText.label(progress(c), t.textMuted(), 12f), 96f, ROW_H));

            if (!a.builtin) {
                ButtonComponent remove = clickable(t.surface(), () -> {
                    a.conditions.remove(c);
                    ConfigManager.save();
                    rebuildView();
                });
                asRow(remove, 78f, 0f).height(28f).cornerRadius(8f).justifyContent(GuiAlignment.CENTER)
                        .borderWidth(1f).borderColor(GuiColors.of(t.border()));
                remove.add(GuiText.label("Remove", t.textMuted(), 12f));
                r.add(remove);
            }
            list.add(r);
        }
    }

    /** How close this condition is, which is the whole reason the profile record is kept. */
    private String progress(Achievement.Condition c) {
        return Achievements.matching(c) + " / " + Math.max(1, c.profiles) + " profiles";
    }

    /** A button that steps through fixed choices, since a text box here could only be wrong. */
    private ButtonComponent cycle(String[] values, String[] names, String current, float width,
                                  java.util.function.Consumer<String> set) {
        int index = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(current)) {
                index = i;
            }
        }
        final int next = (index + 1) % values.length;
        ButtonComponent b = clickable(t.surface(), () -> {
            set.accept(values[next]);
            ConfigManager.save();
            rebuildView();
        });
        asRow(b, width, 0f).height(28f).cornerRadius(8f).justifyContent(GuiAlignment.CENTER)
                .borderWidth(1f).borderColor(GuiColors.of(t.border()));
        b.add(GuiText.label(names[index], t.text(), 12f));
        return b;
    }

    /** Whole numbers without the ".0" nobody typed. */
    private static String trim(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    private static double parse(String s, double fallback) {
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
