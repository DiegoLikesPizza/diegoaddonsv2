package dev.diego.diegoaddons.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Skips vanilla's accessibility onboarding screen - the one a brand-new instance opens with, asking
 * whether to keep the narrator hotkey - and turns off the two settings it covers: the narrator
 * hotkey and view bobbing.
 *
 * <p>Vanilla gates that screen on {@link Options#onboardAccessibility}, which is {@code true} until
 * the screen is dismissed, so it doubles as the "this instance has never been launched" flag. This
 * runs once per game: on a first launch it writes the two settings and clears the flag through
 * {@link Options#onboardingAccessibilityFinished()} (which saves options.txt), and on every later
 * launch it does nothing at all - the settings are the player's from then on.
 *
 * <p>Whether we get there before Minecraft opens the screen or after decides which half does the
 * work: clearing the flag early means the screen is never shown, and catching the screen itself
 * covers the case where it is already up.
 */
public final class AccessibilityDefaults {
    private static final Logger LOGGER = LoggerFactory.getLogger("DiegoAddonsV2");

    private static boolean handled;

    private AccessibilityDefaults() {
    }

    /** Call every client tick; does its work once and then costs a boolean check. */
    public static void tick(Minecraft mc) {
        if (handled) {
            return;
        }
        Options options = mc.options;
        if (options == null) {
            return;   // too early in start-up; try again next tick
        }

        boolean onboardingOpen = mc.screen instanceof AccessibilityOnboardingScreen;
        if (!options.onboardAccessibility && !onboardingOpen) {
            handled = true;   // not a first launch, leave the player's settings alone
            return;
        }

        handled = true;
        options.narratorHotkey().set(false);
        options.bobView().set(false);
        options.onboardingAccessibilityFinished();   // clears the flag and saves options.txt

        if (onboardingOpen) {
            mc.setScreen(new TitleScreen());
        }
        LOGGER.info("[DiegoAddons V2] First launch: skipped the accessibility onboarding screen, "
                + "turned off the narrator hotkey and view bobbing");
    }
}
