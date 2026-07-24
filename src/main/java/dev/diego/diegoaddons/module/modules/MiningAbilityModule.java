package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.HudModule;
import dev.diego.diegoaddons.util.MiningAbility;
import net.minecraft.client.Minecraft;

/**
 * Reminds you that a mining ability is off cooldown, so a drill's ability is not left unused for
 * half a session.
 *
 * <p>The cooldown is taken from the game's own "is now available" message rather than timed, so it
 * cannot drift out of step with the server. See {@link MiningAbility}.
 */
public class MiningAbilityModule extends HudModule {
    public static MiningAbilityModule INSTANCE;

    private final BooleanSetting chime =
            new BooleanSetting(this, "chime", "Play a sound", true);
    private final BooleanSetting onlyReady =
            new BooleanSetting(this, "onlyReady", "Only show when ready", true);

    public MiningAbilityModule() {
        super("miningability", Category.MINING, "Mining Ability Reminder",
                "Tells you when your mining ability is up.");
        settings.add(chime);
        settings.add(onlyReady);
        INSTANCE = this;
    }

    public boolean chime() {
        return chime.get();
    }

    @Override
    public void onClientTick(Minecraft mc) {
        MiningAbility.tick(mc);
    }

    @Override
    protected String label() {
        return "Ability";
    }

    @Override
    protected String value(Minecraft mc) {
        String name = MiningAbility.abilityName();
        if (name == null) {
            return null;
        }
        if (MiningAbility.isReady()) {
            return name + " ready";
        }
        return onlyReady.get() ? null : name + " " + MiningAbility.secondsLeft() + "s";
    }

    @Override
    protected String sampleValue() {
        return "Pickobulus ready";
    }
}
