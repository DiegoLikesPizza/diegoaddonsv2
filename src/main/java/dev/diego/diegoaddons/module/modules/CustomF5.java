package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.Module;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;

/**
 * Tweaks the F5 (third-person) camera. With "Skip front view" on, cycling the perspective skips the
 * front-facing camera entirely - as soon as the game enters it, we snap back to first person.
 */
public class CustomF5 extends Module {
    private final BooleanSetting skipFront = new BooleanSetting(this, "skipFront", "Skip front view", true);

    public CustomF5() {
        super("customf5", Category.RENDER, "CustomF5", "Tweak the F5 third-person camera.");
        settings.add(skipFront);
    }

    @Override
    public void onClientTick(Minecraft mc) {
        if (skipFront.get() && mc.options.getCameraType() == CameraType.THIRD_PERSON_FRONT) {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
        }
    }
}
