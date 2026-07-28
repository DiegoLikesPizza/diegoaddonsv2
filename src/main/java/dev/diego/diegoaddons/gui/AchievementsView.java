package dev.diego.diegoaddons.gui;

import com.render.api.gui.ButtonComponent;
import com.render.api.gui.ContainerComponent;
import com.render.api.gui.GuiOverflowMode;
import com.render.api.gui.ScrollContainerComponent;
import com.render.api.gui.layout.GuiAlignment;
import dev.diego.diegoaddons.config.Achievement;
import dev.diego.diegoaddons.config.ConfigManager;
import dev.diego.diegoaddons.util.Achievements;
import dev.diego.diegoaddons.util.SkyblockProfile;
import net.minecraft.client.Minecraft;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * Your achievements: several hundred shipped with the mod, plus whatever you have written yourself.
 *
 * <p>At this size the list is nothing without a way through it, so the controls come before the
 * content: a search box, a category step, and a filter for the ones you have not got yet. Only a
 * page of rows is built at a time - five hundred retained rows is five hundred sets of components
 * whether or not any of them are on screen.
 *
 * <p>The strip along the top says what the mod can currently see of your profile, because every
 * condition is judged from those readings and a wrong one should be visible rather than inferred
 * from something failing to unlock.
 */
public class AchievementsView extends DiegoView {
    private static final float PANEL_W = 1000f;
    private static final float PANEL_H = 700f;
    private static final float ROW_H = 52f;
    private static final int PAGE = 40;

    private static final SimpleDateFormat WHEN = new SimpleDateFormat("d MMM yyyy");
    private static final String[] SHOW = {"All", "Locked", "Unlocked"};

    private ScrollContainerComponent list;
    private String search = "";
    private String category = "All";
    private int show = 0;
    private int page = 0;
    private String newName = "";

    public AchievementsView() {
        super("Achievements", PANEL_W, PANEL_H);
    }

    @Override
    protected void content(float width, float height) {
        ContainerComponent body = column(width, 10f).height(height).padding(PAD);
        float inner = width - PAD * 2f;

        body.add(textBox(GuiText.label(status(), t.textMuted(), 13f), inner, 20f));

        // Search, category, and which of them to show.
        ContainerComponent controls = row(inner, 10f).height(34f);
        controls.add(field(search, "Search", inner - 170f - 110f - 130f - 30f, s -> {
            search = s;
            page = 0;
            refill(inner);
        }));
        controls.add(step(categories(), category, 170f, v -> {
            category = v;
            page = 0;
        }));
        controls.add(step(List.of(SHOW), SHOW[show], 110f, v -> {
            for (int i = 0; i < SHOW.length; i++) {
                if (SHOW[i].equals(v)) {
                    show = i;
                }
            }
            page = 0;
        }));
        ButtonComponent mine = clickable(t.surface(), () -> {
            category = "Custom";
            page = 0;
            rebuildView();
        });
        asRow(mine, 130f, 0f).height(34f).cornerRadius(9f).justifyContent(GuiAlignment.CENTER)
                .borderWidth(1f).borderColor(GuiColors.of(t.border()));
        mine.add(GuiText.label("Mine", t.text(), 13f));
        controls.add(mine);
        body.add(controls);

        // Writing your own, which is still the point of the feature.
        ContainerComponent add = row(inner, 10f).height(34f);
        add.add(field(newName, "Write your own…", inner - 128f, s -> newName = s));
        ButtonComponent addBtn = clickable(t.accent(), () -> {
            String name = newName.trim();
            if (!name.isEmpty()) {
                Achievement a = new Achievement(Achievements.newId(), name);
                Achievements.all().add(a);
                ConfigManager.save();
                newName = "";
                Minecraft.getInstance().execute(() -> new AchievementEditView(a).open());
            }
        });
        asRow(addBtn, 118f, 0f).height(34f).cornerRadius(9f).justifyContent(GuiAlignment.CENTER);
        addBtn.add(GuiText.label("Add", t.accentText(), 14f));
        add.add(addBtn);
        body.add(add);

        list = new ScrollContainerComponent();
        list.size(inner, height - PAD * 2f - 20f - 34f - 34f - 34f - 40f);
        asColumn(list, inner, 6f);
        list.overflowY(GuiOverflowMode.AUTO);
        body.add(list);

        body.add(pager(inner));
        panel.add(body);
        refill(inner);
    }

    /** What the mod can currently see, and how far through the list you are. */
    private String status() {
        Minecraft mc = Minecraft.getInstance();
        int total = Achievements.everything().size();
        int done = ConfigManager.get().achievementUnlocks.size();
        String profile = SkyblockProfile.name(mc);
        String where = profile.isEmpty()
                ? "Not on a SkyBlock profile"
                : "On " + profile + "  ·  " + SkyblockProfile.gamemode(mc)
                        + (SkyblockProfile.level(mc) > 0 ? "  ·  level " + SkyblockProfile.level(mc) : "");
        return done + " of " + total + " unlocked   ·   " + where;
    }

    private List<String> categories() {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        out.add("All");
        for (Achievement a : Achievements.everything()) {
            out.add(a.category);
        }
        return new ArrayList<>(out);
    }

    private void rebuildView() {
        panel.clearChildren();
        build();
    }

    /** Everything matching the current search, category and lock filter. */
    private List<Achievement> filtered() {
        String q = search.trim().toLowerCase(Locale.ROOT);
        List<Achievement> out = new ArrayList<>();
        for (Achievement a : Achievements.everything()) {
            if (!"All".equals(category) && !category.equals(a.category)) {
                continue;
            }
            boolean done = Achievements.isUnlocked(a);
            if (show == 1 && done || show == 2 && !done) {
                continue;
            }
            if (!q.isEmpty()
                    && !a.name.toLowerCase(Locale.ROOT).contains(q)
                    && !a.description.toLowerCase(Locale.ROOT).contains(q)
                    && !a.category.toLowerCase(Locale.ROOT).contains(q)) {
                continue;
            }
            out.add(a);
        }
        return out;
    }

    private void refill(float inner) {
        list.clearChildren();
        List<Achievement> all = filtered();
        if (all.isEmpty()) {
            list.add(textBox(GuiText.label("Nothing matches.", t.textFaint(), 13f), inner, 24f));
            return;
        }
        int from = Math.min(page * PAGE, Math.max(0, all.size() - 1));
        int to = Math.min(from + PAGE, all.size());
        for (Achievement a : all.subList(from, to)) {
            list.add(rowFor(a, inner));
        }
    }

    private ContainerComponent rowFor(Achievement a, float inner) {
        boolean done = Achievements.isUnlocked(a);
        boolean on = Achievements.isOn(a);
        ContainerComponent r = row(inner - 24f, 10f).height(ROW_H).flexShrink(0f).padding(0f, 12f)
                .cornerRadius(10f).backgroundColor(GuiColors.of(t.surfaceAlt()))
                .borderWidth(1f).borderColor(GuiColors.of(done ? t.accent() : t.border()))
                .justifyContent(GuiAlignment.SPACE_BETWEEN);

        ContainerComponent text = column(0f, 2f).height(ROW_H).flexGrow(1f)
                .justifyContent(GuiAlignment.CENTER);
        text.add(GuiText.label((done ? "✦ " : "") + a.name,
                done ? t.text() : (on ? t.textMuted() : t.textFaint()), 14f));
        text.add(GuiText.label(subtitle(a, done), done ? t.accent() : t.textFaint(), 11f));
        r.add(text);

        r.add(textBox(GuiText.label(a.category, t.textFaint(), 11f), 110f, ROW_H));

        ButtonComponent toggle = clickable(t.surface(), () -> {
            Achievements.setOn(a, !on);
            rebuildView();
        });
        asRow(toggle, 62f, 0f).height(26f).cornerRadius(8f).justifyContent(GuiAlignment.CENTER)
                .borderWidth(1f).borderColor(GuiColors.of(t.border()));
        toggle.add(GuiText.label(on ? "On" : "Off", on ? t.text() : t.textFaint(), 12f));
        r.add(toggle);

        ButtonComponent edit = clickable(t.surface(),
                () -> Minecraft.getInstance().execute(() -> new AchievementEditView(a).open()));
        asRow(edit, 70f, 0f).height(26f).cornerRadius(8f).justifyContent(GuiAlignment.CENTER)
                .borderWidth(1f).borderColor(GuiColors.of(t.border()));
        edit.add(GuiText.label(a.builtin ? "Trigger" : "Edit", t.text(), 12f));
        r.add(edit);

        if (!a.builtin) {
            ButtonComponent remove = clickable(t.surface(), () -> {
                Achievements.all().remove(a);
                ConfigManager.get().achievementUnlocks.remove(a.id);
                ConfigManager.save();
                rebuildView();
            });
            asRow(remove, 80f, 0f).height(26f).cornerRadius(8f).justifyContent(GuiAlignment.CENTER)
                    .borderWidth(1f).borderColor(GuiColors.of(t.border()));
            remove.add(GuiText.label("Remove", t.textMuted(), 12f));
            r.add(remove);
        } else {
            r.add(textBox(GuiText.label("", t.textFaint(), 11f), 80f, ROW_H));
        }
        return r;
    }

    /** Either when it was earned, or how far along it is. */
    private String subtitle(Achievement a, boolean done) {
        if (done) {
            return "Unlocked " + WHEN.format(new Date(Achievements.unlockedAt(a)));
        }
        if (a.counted()) {
            return Achievements.counter(a.counter) + " / " + a.threshold
                    + (a.description.isBlank() ? "" : "  ·  " + a.description);
        }
        if (!a.isComplete()) {
            return "No trigger yet - open it and say what unlocks it";
        }
        if (!Achievements.isOn(a)) {
            return "Off";
        }
        return a.description.isBlank() ? "Chat: " + Achievements.pattern(a) : a.description;
    }

    private ContainerComponent pager(float inner) {
        int total = filtered().size();
        int pages = Math.max(1, (total + PAGE - 1) / PAGE);
        ContainerComponent bar = row(inner, 10f).height(30f).justifyContent(GuiAlignment.CENTER);

        ButtonComponent prev = clickable(t.surface(), () -> {
            if (page > 0) {
                page--;
                rebuildView();
            }
        });
        asRow(prev, 90f, 0f).height(28f).cornerRadius(8f).justifyContent(GuiAlignment.CENTER)
                .borderWidth(1f).borderColor(GuiColors.of(t.border()));
        prev.add(GuiText.label("Previous", page > 0 ? t.text() : t.textFaint(), 12f));
        bar.add(prev);

        bar.add(textBox(GuiText.label(
                total + " shown  ·  page " + (page + 1) + " of " + pages, t.textFaint(), 12f),
                240f, 30f));

        final int last = pages - 1;
        ButtonComponent next = clickable(t.surface(), () -> {
            if (page < last) {
                page++;
                rebuildView();
            }
        });
        asRow(next, 90f, 0f).height(28f).cornerRadius(8f).justifyContent(GuiAlignment.CENTER)
                .borderWidth(1f).borderColor(GuiColors.of(t.border()));
        next.add(GuiText.label("Next", page < last ? t.text() : t.textFaint(), 12f));
        bar.add(next);
        return bar;
    }

    /** A button that steps through a list of values - the category picker, mostly. */
    private ButtonComponent step(List<String> values, String current, float width,
                                 java.util.function.Consumer<String> set) {
        int index = Math.max(0, values.indexOf(current));
        final String next = values.get((index + 1) % values.size());
        ButtonComponent b = clickable(t.surface(), () -> {
            set.accept(next);
            rebuildView();
        });
        asRow(b, width, 0f).height(34f).cornerRadius(9f).justifyContent(GuiAlignment.CENTER)
                .borderWidth(1f).borderColor(GuiColors.of(t.border()));
        b.add(GuiText.label(current, t.text(), 12f));
        return b;
    }
}
