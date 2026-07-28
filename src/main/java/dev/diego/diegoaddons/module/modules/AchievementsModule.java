package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.gui.AchievementsView;
import dev.diego.diegoaddons.module.ActionSetting;
import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.Module;
import dev.diego.diegoaddons.util.Achievements;
import net.minecraft.client.Minecraft;

/**
 * Achievements you write yourself: a name, and what has to happen or be true for it to unlock.
 *
 * <p>Deliberately ships empty. The point of these is the ones nobody else would think to write -
 * "make an ironman just to quit inside a day" - and a list of milestones somebody at Anthropic
 * guessed at would only be in the way of that.
 *
 * <p>Unlocks are per account and permanent - relocking one is something you have to ask for. The
 * per-profile record they are judged from is only written while this module is on, so time played
 * with it switched off is time the mod cannot claim to have seen.
 */
public class AchievementsModule extends Module {
    public static AchievementsModule INSTANCE;

    private final BooleanSetting toast =
            new BooleanSetting(this, "toast", "Toast on unlock", true);
    private final BooleanSetting chat =
            new BooleanSetting(this, "chat", "Announce in chat", false);
    private final ActionSetting open =
            new ActionSetting(this, "open", "Your achievements", "Open", AchievementsModule::openView);

    public AchievementsModule() {
        super("achievements", Category.MISC, "Custom Achievements",
                "Write your own achievements and unlock them as you play.");
        settings.add(toast);
        settings.add(chat);
        settings.add(open);
        INSTANCE = this;
    }

    public boolean showToast() {
        return toast.get();
    }

    public boolean announceInChat() {
        return chat.get();
    }

    private static void openView() {
        Minecraft.getInstance().execute(() -> new AchievementsView().open());
    }

    @Override
    public void onClientTick(Minecraft mc) {
        Achievements.tick(mc);
    }
}
