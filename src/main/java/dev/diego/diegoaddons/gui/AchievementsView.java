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
import java.util.Date;
import java.util.List;

/**
 * Your achievements: what you have written, and which of them you have earned.
 *
 * <p>The strip along the top says which profile the mod currently thinks you are on and what it has
 * read off it. That is there because every condition is judged from those readings - if the level is
 * wrong, an achievement about levels will be wrong too, and it should be possible to see that at a
 * glance rather than by waiting for something not to unlock.
 */
public class AchievementsView extends DiegoView {
    private static final float PANEL_W = 940f;
    private static final float PANEL_H = 660f;
    private static final float ROW_H = 56f;

    private static final SimpleDateFormat WHEN = new SimpleDateFormat("d MMM yyyy");

    private ScrollContainerComponent list;
    private String newName = "";

    public AchievementsView() {
        super("Achievements", PANEL_W, PANEL_H);
    }

    @Override
    protected void content(float width, float height) {
        ContainerComponent body = column(width, 12f).height(height).padding(PAD);
        float inner = width - PAD * 2f;

        body.add(textBox(GuiText.label(status(), t.textMuted(), 13f), inner, 20f));

        ContainerComponent add = row(inner, 12f).height(36f);
        add.add(field(newName, "Quit an ironman inside a day", inner - 130f, s -> newName = s));
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
        asRow(addBtn, 118f, 0f).height(36f).cornerRadius(9f).justifyContent(GuiAlignment.CENTER);
        addBtn.add(GuiText.label("Add", t.accentText(), 14f));
        add.add(addBtn);
        body.add(add);

        list = new ScrollContainerComponent();
        list.size(inner, height - PAD * 2f - 36f - 20f - 24f);
        asColumn(list, inner, 8f);
        list.overflowY(GuiOverflowMode.AUTO);
        body.add(list);
        panel.add(body);
        fill(inner);
    }

    /** What the mod can currently see, in one line. */
    private String status() {
        Minecraft mc = Minecraft.getInstance();
        String profile = SkyblockProfile.name(mc);
        if (profile.isEmpty()) {
            return "Not on a SkyBlock profile - nothing is being recorded right now.";
        }
        int level = SkyblockProfile.level(mc);
        return "On " + profile + "  ·  " + SkyblockProfile.gamemode(mc)
                + (level > 0 ? "  ·  level " + level : "  ·  level unknown")
                + "  ·  " + ConfigManager.get().profileStats.size() + " profile(s) recorded";
    }

    private void rebuildView() {
        panel.clearChildren();
        build();
    }

    private void fill(float inner) {
        list.clearChildren();
        List<Achievement> all = Achievements.all();
        if (all.isEmpty()) {
            list.add(textBox(GuiText.label("Nothing yet. Name one above and say what unlocks it.",
                    t.textFaint(), 13f), inner, 24f));
            return;
        }
        for (Achievement a : List.copyOf(all)) {
            boolean done = Achievements.isUnlocked(a);
            ContainerComponent r = row(inner - 24f, 10f).height(ROW_H).flexShrink(0f).padding(0f, 12f)
                    .cornerRadius(10f).backgroundColor(GuiColors.of(t.surfaceAlt()))
                    .borderWidth(1f).borderColor(GuiColors.of(done ? t.accent() : t.border()))
                    .justifyContent(GuiAlignment.SPACE_BETWEEN);

            ContainerComponent text = column(0f, 2f).height(ROW_H).flexGrow(1f)
                    .justifyContent(GuiAlignment.CENTER);
            text.add(GuiText.label((done ? "✦ " : "") + a.name,
                    done ? t.text() : t.textMuted(), 15f));
            text.add(GuiText.label(subtitle(a, done), done ? t.accent() : t.textFaint(), 12f));
            r.add(text);

            ButtonComponent edit = clickable(t.surface(),
                    () -> Minecraft.getInstance().execute(() -> new AchievementEditView(a).open()));
            asRow(edit, 84f, 0f).height(28f).cornerRadius(8f).justifyContent(GuiAlignment.CENTER)
                    .borderWidth(1f).borderColor(GuiColors.of(t.border()));
            edit.add(GuiText.label("Edit", t.text(), 13f));
            r.add(edit);

            ButtonComponent remove = clickable(t.surface(), () -> {
                Achievements.all().remove(a);
                ConfigManager.get().achievementUnlocks.remove(a.id);
                ConfigManager.save();
                rebuildView();
            });
            asRow(remove, 90f, 0f).height(28f).cornerRadius(8f).justifyContent(GuiAlignment.CENTER)
                    .borderWidth(1f).borderColor(GuiColors.of(t.border()));
            remove.add(GuiText.label("Remove", t.textMuted(), 13f));
            r.add(remove);
            list.add(r);
        }
    }

    /** Either when it was earned, or what it is still waiting for. */
    private String subtitle(Achievement a, boolean done) {
        if (done) {
            return "Unlocked " + WHEN.format(new Date(Achievements.unlockedAt(a)));
        }
        if (!a.isComplete()) {
            return "No trigger yet - open it and say what unlocks it";
        }
        if (!a.enabled) {
            return "Paused";
        }
        if (!a.description.isBlank()) {
            return a.description;
        }
        return a.chat.isBlank()
                ? a.conditions.size() + " condition(s)"
                : "Chat: " + a.chat;
    }
}
