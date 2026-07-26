package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.Module;
import net.minecraft.client.Minecraft;

/**
 * ESP for dungeon wither/blood doors and their keys. Each is its own toggle; see
 * {@link dev.diego.diegoaddons.util.DoorKeyEsp} for how they are found.
 */
public class DoorKeyEspModule extends Module {
    public static DoorKeyEspModule INSTANCE;

    private final BooleanSetting witherDoors =
            new BooleanSetting(this, "witherDoors", "Wither doors", true);
    private final BooleanSetting bloodDoors =
            new BooleanSetting(this, "bloodDoors", "Blood door", true);
    private final BooleanSetting witherKey =
            new BooleanSetting(this, "witherKey", "Wither key", true);
    private final BooleanSetting bloodKey =
            new BooleanSetting(this, "bloodKey", "Blood key", true);

    public DoorKeyEspModule() {
        super("doorkeyesp", Category.DUNGEONS, "Door & Key ESP",
                "Highlight wither/blood doors and their keys.");
        settings.add(witherDoors);
        settings.add(bloodDoors);
        settings.add(witherKey);
        settings.add(bloodKey);
        INSTANCE = this;
    }

    public boolean witherDoors() {
        return witherDoors.get();
    }

    public boolean bloodDoors() {
        return bloodDoors.get();
    }

    public boolean witherKey() {
        return witherKey.get();
    }

    public boolean bloodKey() {
        return bloodKey.get();
    }

    @Override
    public void onClientTick(Minecraft mc) {
        dev.diego.diegoaddons.util.DoorKeyEsp.tick(mc);
    }
}
