package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.Module;
import dev.diego.diegoaddons.module.NumberSetting;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;

/**
 * Tweaks the F5 (third-person) camera: skipping the front view, how far the camera sits behind you,
 * and whether it is allowed through blocks.
 *
 * <p>See {@code CameraMixin} for the last two - both hang off the same vanilla method, the one that
 * shortens the camera distance so it does not end up inside a wall.
 */
public class CustomF5 extends Module {
    public static CustomF5 INSTANCE;

    private final BooleanSetting skipFront =
            new BooleanSetting(this, "skipFront", "Skip front view", true);
    /** Off by default so the vanilla camera-distance attribute keeps working untouched. */
    private final BooleanSetting customDistance =
            new BooleanSetting(this, "customDistance", "Custom distance", false);
    private final NumberSetting distance =
            new NumberSetting(this, "distance", "Distance", 4.0, 1.0, 16.0, 0.5);
    /** Lets the camera pass through blocks instead of being pulled in against them. */
    private final BooleanSetting cameraClip =
            new BooleanSetting(this, "cameraClip", "Camera clip", false);

    public CustomF5() {
        super("customf5", Category.RENDER, "CustomF5", "Tweak the F5 third-person camera.");
        settings.add(skipFront);
        settings.add(customDistance);
        settings.add(distance);
        settings.add(cameraClip);
        INSTANCE = this;
    }

    public boolean customDistance() {
        return customDistance.get();
    }

    public float distance() {
        return (float) distance.get();
    }

    public boolean cameraClip() {
        return cameraClip.get();
    }

    @Override
    public void onClientTick(Minecraft mc) {
        if (skipFront.get() && mc.options.getCameraType() == CameraType.THIRD_PERSON_FRONT) {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
        }
    }
}
