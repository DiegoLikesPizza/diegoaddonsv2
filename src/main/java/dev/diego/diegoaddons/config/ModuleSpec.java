package dev.diego.diegoaddons.config;

import dev.diego.configlib.core.ConfigSpec;
import dev.diego.configlib.core.SpecBuilder;
import dev.diego.diegoaddons.module.ActionSetting;
import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.ColorSetting;
import dev.diego.diegoaddons.module.CycleSetting;
import dev.diego.diegoaddons.module.KeybindSetting;
import dev.diego.diegoaddons.module.Module;
import dev.diego.diegoaddons.module.ModuleManager;
import dev.diego.diegoaddons.module.NumberSetting;
import dev.diego.diegoaddons.module.Setting;
import dev.diego.diegoaddons.module.StringSetting;

/**
 * Describes the module list to configlib.
 *
 * <p>The modules are built at runtime and their settings are objects of their own, so there is no
 * annotated class to reflect. This walks what {@link ModuleManager} registered and declares it
 * through {@link SpecBuilder} instead: one configlib category per {@link Category}, one section per
 * module, one option per {@link Setting}.
 *
 * <p><b>Nothing is copied.</b> Every option reads and writes through the setting it was built from,
 * so the modules stay the single source of truth and there is no second copy to keep in step.
 *
 * <p><b>Nothing is saved by configlib yet.</b> Every option is marked
 * {@link SpecBuilder#notPersisted()}, because the settings already persist themselves through
 * {@link ConfigManager} and two writers over one file is how a config gets destroyed. configlib
 * takes over storage when {@code ConfigManager} goes, not before.
 */
public final class ModuleSpec {

    private ModuleSpec() {
    }

    /** Builds the spec from whatever is currently registered. */
    public static ConfigSpec build() {
        SpecBuilder b = SpecBuilder.create();
        for (Category c : ModuleManager.categories()) {
            b.category(c.name().toLowerCase(java.util.Locale.ROOT), c.display, "");
            for (Module m : ModuleManager.modulesIn(c)) {
                module(b, m);
            }
        }
        return b.build();
    }

    /**
     * One module: a section named for it, carrying its on/off switch, then its own settings.
     *
     * <p>The switch is attached to the section rather than declared as its first option, so it is
     * drawn on the module's card. A module is a thing you turn on, and the switch belongs where the
     * module is - as a row inside it, it repeated the card's own title only to say "Enabled", and
     * turning a module on meant opening it first.
     */
    private static void module(SpecBuilder b, Module m) {
        b.section(m.name, m.description, 0);
        b.sectionToggle(m.id, m.name, m.description,
                m::isEnabled, v -> ModuleManager.setEnabled(m, v)).notPersisted();
        // Where it sits on screen, for anything that draws one. Declared before the settings so
        // the placement row leads, which is what you came to the card for.
        if (m instanceof dev.diego.diegoaddons.module.HudModule hud) {
            dev.diego.diegoaddons.hud.HudElements.declare(b, hud);
        }
        // Any editable list this module owns, before its settings - the list is the feature, the
        // settings only adjust it.
        ListSpecs.declare(b, m.id);
        for (Setting s : m.settings()) {
            setting(b, m, s);
        }
    }

    /**
     * Whether a choice is drawn as buttons side by side rather than as a dropdown.
     *
     * <p>Only for a straight either/or. Beyond two, the segments are narrow enough that the labels
     * start truncating and the row turns into a wall of half-words - which is what a dropdown is
     * for. configlib's own note on the flag says two or three; two is where it actually still reads.
     */
    private static boolean segmented(int choices) {
        return choices <= 2;
    }

    private static void setting(SpecBuilder b, Module m, Setting s) {
        String id = m.id + "." + s.key;
        switch (s) {
            case BooleanSetting bs ->
                    b.toggle(id, s.name, "", bs::get, bs::set).notPersisted();

            case NumberSetting ns ->
                    b.slider(id, s.name, "", ns::get, ns::set,
                            ns.min, ns.max, ns.step, ns.decimals, "", false).notPersisted();

            case CycleSetting cs ->
                    b.choice(id, s.name, "", cs::get, cs::set, cs.options,
                            segmented(cs.options.length)).notPersisted();

            case KeybindSetting ks ->
                    b.keybind(id, s.name, "", ks::get, ks::set, false).notPersisted();

            // Two different controls behind one setting type. A value that has to exist somewhere
            // else - a sound, an item - opens the picker that knows about them, and is a button. A
            // value that is just words is a text box in the row, which is what it always wanted to
            // be and only ever went through a screen because there was nowhere else to type.
            case StringSetting ss -> {
                if (ss.hasChooser()) {
                    b.action(id, s.name, "", ss.get(), false, ss::choose).notPersisted();
                } else {
                    b.text(id, s.name, "", ss::get, ss::set, "", 128, 200).notPersisted();
                }
            }

            case ActionSetting as ->
                    b.action(id, s.name, "", as.action, false, as::run).notPersisted();

            // One ColorSetting is a mode plus two colours, which configlib has no single row for.
            // Split into the three it does have; the second colour only exists for a gradient, so
            // it is hidden the rest of the time rather than sitting there doing nothing.
            case ColorSetting col -> {
                b.choice(id + ".mode", s.name, "", col::mode, col::setMode,
                        ColorSetting.MODES, segmented(ColorSetting.MODES.length)).notPersisted();
                b.color(id + ".a", s.name + " colour", "",
                        col::colorA, col::setColorA, true).notPersisted();
                b.color(id + ".b", s.name + " fade to", "",
                        col::colorB, col::setColorB, true).notPersisted()
                        .when(() -> col.mode() == ColorSetting.GRADIENT, false);
            }

            default -> {
                // A setting type added later shows up as a read-only row rather than vanishing
                // silently from the menu.
                b.action(id, s.name, "Unsupported setting type", "", false, () -> {
                }).notPersisted();
            }
        }
    }
}
