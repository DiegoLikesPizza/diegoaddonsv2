package dev.diego.diegoaddons.config;

import com.google.gson.reflect.TypeToken;
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
import dev.diego.diegoaddons.gui.Themes;

import java.util.List;
import java.util.Set;

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
 * <p><b>configlib owns storage.</b> Every option here is persistent, and the settings no longer keep
 * a copy of anything: an option reads and writes the setting object itself, and configlib decides
 * when that reaches the file. {@link ConfigManager} is now only the state that is not a setting,
 * plus the one call that asks for a write.
 */
public final class ModuleSpec {

    /**
     * Modules drawn in the Client category rather than the one they are registered under.
     *
     * <p>A module's {@link Category} is where it lives in the code and what the commands list it
     * under; this is only about which page it is drawn on. Kept as ids so the two never have to be
     * kept in step - a module named here that no longer exists costs nothing.
     */
    private static final java.util.Set<String> IN_CLIENT = java.util.Set.of("autoupdate");

    private ModuleSpec() {
    }

    /** Builds the spec from whatever is currently registered. */
    public static ConfigSpec build() {
        SpecBuilder b = SpecBuilder.create();
        // Before the first category, and that is not cosmetic: the builder qualifies every id with
        // whichever category is open, so declaring these last would key them under the last category
        // registered - and adding a category later would silently move every one of them, which
        // reads back as the whole config having reset itself.
        state(b);
        appearance(b);
        for (Category c : ModuleManager.categories()) {
            b.category(c.name().toLowerCase(java.util.Locale.ROOT), c.display, "");
            for (Module m : ModuleManager.modulesIn(c)) {
                // Auto Update is registered under Misc but drawn in Client, beside the rest of what
                // the mod does to itself rather than to the game - see appearance().
                if (!IN_CLIENT.contains(m.id)) {
                    module(b, m);
                }
            }
        }
        return b.build();
    }

    /**
     * Everything in {@link AddonConfig} - the state that is not a module setting.
     *
     * <p>Declared hidden rather than as options: these are either drawn by the mod's own screens
     * (the theme picker, the list editors) or never drawn at all (a one-time notice having been
     * shown, the last seen pet). A hidden option is persisted and nothing more, which is exactly
     * what each of these wants.
     *
     * <p>The setter takes {@code Object} because that is configlib's hidden-accessor contract; the
     * cast is safe because the type handed to {@code hidden} is the one Gson parses it back as.
     */
    @SuppressWarnings("unchecked")
    private static void state(SpecBuilder b) {
        AddonConfig c = ConfigManager.get();
        // theme, customAccent, accentColor and smoothCorners are declared in appearance(...) as
        // real rows, so they are not hidden here - one value, one declaration, one writer.
        b.hidden("introShown", boolean.class, () -> c.introShown, v -> c.introShown = (Boolean) v);
        b.hidden("legacyImported", boolean.class,
                () -> c.legacyImported, v -> c.legacyImported = (Boolean) v);
        b.hidden("sbHintShown", boolean.class, () -> c.sbHintShown, v -> c.sbHintShown = (Boolean) v);
        b.hidden("gfsSeeded", boolean.class, () -> c.gfsSeeded, v -> c.gfsSeeded = (Boolean) v);
        b.hidden("activeMiningRoute", String.class,
                () -> c.activeMiningRoute, v -> c.activeMiningRoute = (String) v);
        b.hidden("savedEquipment", String[].class,
                () -> c.savedEquipment, v -> c.savedEquipment = (String[]) v);
        b.hidden("savedPet", String.class, () -> c.savedPet, v -> c.savedPet = (String) v);
        b.hidden("feastCrops", String.class, () -> c.feastCrops, v -> c.feastCrops = (String) v);
        b.hidden("feastSeasoning", int.class,
                () -> c.feastSeasoning, v -> c.feastSeasoning = (Integer) v);
        b.hidden("feastGrand", boolean.class, () -> c.feastGrand, v -> c.feastGrand = (Boolean) v);
        b.hidden("feastReadAt", long.class, () -> c.feastReadAt, v -> c.feastReadAt = (Long) v);
        b.hidden("feastSeasonEnd", long.class,
                () -> c.feastSeasonEnd, v -> c.feastSeasonEnd = (Long) v);
        b.hidden("sackCounts", java.util.Map.class,
                new TypeToken<java.util.Map<String, Integer>>() { }.getType(),
                () -> c.sackCounts, v -> c.sackCounts = (java.util.Map<String, Integer>) v);
        b.hidden("sackProfile", String.class, () -> c.sackProfile, v -> c.sackProfile = (String) v);
        b.hidden("sacksReadAt", long.class, () -> c.sacksReadAt, v -> c.sacksReadAt = (Long) v);

        // The list-editor modules' contents. The full generic type is given so Gson rebuilds the
        // records inside rather than handing back a list of maps.
        b.hidden("blockedPlayers", List.class, new TypeToken<List<BlockedPlayer>>() { }.getType(),
                () -> c.blockedPlayers, v -> c.blockedPlayers = (List<BlockedPlayer>) v);
        b.hidden("wordReplacements", List.class, new TypeToken<List<WordReplacement>>() { }.getType(),
                () -> c.wordReplacements, v -> c.wordReplacements = (List<WordReplacement>) v);
        b.hidden("commandHotkeys", List.class, new TypeToken<List<CommandHotkey>>() { }.getType(),
                () -> c.commandHotkeys, v -> c.commandHotkeys = (List<CommandHotkey>) v);
        b.hidden("loadoutKeys", List.class, new TypeToken<List<LoadoutKey>>() { }.getType(),
                () -> c.loadoutKeys, v -> c.loadoutKeys = (List<LoadoutKey>) v);
        b.hidden("miningRoutes", List.class, new TypeToken<List<MiningRoute>>() { }.getType(),
                () -> c.miningRoutes, v -> c.miningRoutes = (List<MiningRoute>) v);
        b.hidden("gfsItems", List.class, new TypeToken<List<GfsItem>>() { }.getType(),
                () -> c.gfsItems, v -> c.gfsItems = (List<GfsItem>) v);
        b.hidden("espTerms", List.class, new TypeToken<List<String>>() { }.getType(),
                () -> c.espTerms, v -> c.espTerms = (List<String>) v);
        b.hidden("lockedSlots", Set.class, new TypeToken<Set<Integer>>() { }.getType(),
                () -> c.lockedSlots, v -> c.lockedSlots = (Set<Integer>) v);

        // The Player HUD's section order. Hidden rather than an option because configlib already
        // draws it - as an OrderOption with its own screen (see ListSpecs); this is only where the
        // result is kept.
        var player = dev.diego.diegoaddons.module.modules.PlayerHudModule.INSTANCE;
        if (player != null) {
            b.hidden("playerhud.sectionOrder", String.class,
                    player::savedOrder, v -> player.setSavedOrder((String) v));
        }
    }

    /**
     * The mod itself rather than what it does - how it looks, and how it keeps itself up to date.
     *
     * <p>Declared before the module categories so it leads the sidebar: it is the first thing
     * anyone opening a settings menu goes looking for, and it was until now not reachable at all -
     * the five themes existed and drove the whole HUD, but nothing anywhere could select one.
     *
     * <p>Its id stays {@code appearance} although it is now called Client. The builder qualifies
     * every option id with the category it was declared in, so renaming the id would re-key every
     * setting under it and hand everyone their theme back at the default. The name is what is shown;
     * the id is storage, and storage has no reason to change because a label did.
     */
    private static void appearance(SpecBuilder b) {
        AddonConfig c = ConfigManager.get();
        b.category("appearance", "Client", "The mod itself");

        b.section("Theme", "The palette the HUD, the menu and the toasts all draw from", 0);
        b.choice("theme", "Theme", "",
                Themes::currentIndex, Themes::selectIndex, Themes.names(), false);
        b.toggle("customAccent", "Custom accent colour",
                "Use your own accent instead of the theme's", () -> c.customAccent, v -> {
                    c.customAccent = v;
                    ConfigManager.save();
                });
        b.color("accentColor", "Accent colour", "", () -> c.accentColor, v -> {
            c.accentColor = v;
            ConfigManager.save();
        }, false).when(() -> c.customAccent, false);

        b.section("Rendering", "", 1);
        b.toggle("smoothCorners", "Smooth corners",
                "Anti-alias rounded corners with a soft edge pixel",
                () -> c.smoothCorners, v -> {
                    c.smoothCorners = v;
                    ConfigManager.save();
                });

        // Keeping the mod current is the mod acting on itself, which is what this page is for. It
        // is still a module - it ticks, it has a switch, /da lists it under Misc - only its card is
        // here. ModFiles.migrate() carries its saved settings across from the old key.
        for (Module m : ModuleManager.all()) {
            if (IN_CLIENT.contains(m.id)) {
                module(b, m);
            }
        }
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
                m::isEnabled, v -> ModuleManager.setEnabled(m, v));
        // Where it sits on screen, for anything that draws one. Declared before the settings so
        // the placement row leads, which is what you came to the card for.
        if (m instanceof dev.diego.diegoaddons.module.HudModule hud && hud.placeable()) {
            dev.diego.diegoaddons.hud.HudElements.declare(b, hud);
        }
        // Any editable list this module owns, before its settings - the list is the feature, the
        // settings only adjust it.
        ListSpecs.declare(b, m.id);
        for (Setting s : m.settings()) {
            // A row an element cannot act on is left out rather than drawn dead - the inventory
            // grid and the player model have no text for a text colour to reach.
            if (m instanceof dev.diego.diegoaddons.module.HudModule hud && hud.isUselessSetting(s)) {
                continue;
            }
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
        // The per-element appearance rows only mean anything while that element's override is on.
        java.util.function.BooleanSupplier when =
                m instanceof dev.diego.diegoaddons.module.HudModule hud && hud.isStyleDetail(s)
                        ? hud::customStyleOn
                        : null;
        switch (s) {
            case BooleanSetting bs ->
                    b.toggle(id, s.name, "", bs::get, bs::set);

            case NumberSetting ns ->
                    b.slider(id, s.name, "", ns::get, ns::set,
                            ns.min, ns.max, ns.step, ns.decimals, "", false);

            case CycleSetting cs ->
                    b.choice(id, s.name, "", cs::get, cs::set, cs.options,
                            segmented(cs.options.length));

            case KeybindSetting ks ->
                    b.keybind(id, s.name, "", ks::get, ks::set, false);

            // Two different controls behind one setting type. A value that has to exist somewhere
            // else - a sound, an item - opens the picker that knows about them, and is a button. A
            // value that is just words is a text box in the row, which is what it always wanted to
            // be and only ever went through a screen because there was nowhere else to type.
            case StringSetting ss -> {
                if (ss.hasChooser()) {
                    b.action(id, s.name, "", ss.get(), false, ss::choose);
                    // An action row is a button, and a button carries no value for configlib to
                    // store - so the value the chooser picks is declared separately. Without this
                    // the Secret Chime sound would be chosen, used, and forgotten at the restart.
                    b.hidden(id, String.class, ss::get, v -> ss.set((String) v));
                } else {
                    b.text(id, s.name, "", ss::get, ss::set, "", 128, 200);
                }
            }

            case ActionSetting as ->
                    b.action(id, s.name, "", as.action, false, as::run);

            // One ColorSetting is a mode plus two colours, which configlib has no single row for.
            // Split into the three it does have; the second colour only exists for a gradient, so
            // it is hidden the rest of the time rather than sitting there doing nothing.
            case ColorSetting col -> {
                b.choice(id + ".mode", s.name, "", col::mode, col::setMode,
                        ColorSetting.MODES, segmented(ColorSetting.MODES.length));
                if (when != null) {
                    b.when(when, false);
                }
                b.color(id + ".a", s.name + " colour", "",
                        col::colorA, col::setColorA, true);
                if (when != null) {
                    b.when(when, false);
                }
                // The second colour needs both conditions: it exists for a gradient, and if this
                // colour belongs to an element override it also needs that override to be on.
                b.color(id + ".b", s.name + " fade to", "",
                        col::colorB, col::setColorB, true)
                        .when(when == null
                                ? () -> col.mode() == ColorSetting.GRADIENT
                                : () -> when.getAsBoolean() && col.mode() == ColorSetting.GRADIENT,
                                false);
            }

            default -> {
                // A setting type added later shows up as a read-only row rather than vanishing
                // silently from the menu.
                b.action(id, s.name, "Unsupported setting type", "", false, () -> {
                });
            }
        }
        // Applies to whatever was added last, which for a colour is its "fade to" row - the other
        // two are conditioned inside the branch, where each is still the last thing added.
        if (when != null && !(s instanceof ColorSetting)) {
            b.when(when, false);
        }
    }
}
