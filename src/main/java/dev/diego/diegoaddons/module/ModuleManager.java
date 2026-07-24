package dev.diego.diegoaddons.module;

import dev.diego.diegoaddons.DiegoAddonsV2Client;
import dev.diego.diegoaddons.config.ConfigManager;
import dev.diego.diegoaddons.config.ModuleConfig;
import dev.diego.diegoaddons.gui.Fonts;
import dev.diego.diegoaddons.gui.Theme;
import dev.diego.diegoaddons.gui.Themes;
import dev.diego.diegoaddons.gui.UiRender;
import dev.diego.diegoaddons.module.modules.AnimationsModule;
import dev.diego.diegoaddons.module.modules.ArmorHiderModule;
import dev.diego.diegoaddons.module.modules.BetterIgnoreListModule;
import dev.diego.diegoaddons.module.modules.ChatCompactModule;
import dev.diego.diegoaddons.module.modules.ChatHistoryModule;
import dev.diego.diegoaddons.module.modules.ChatSearchModule;
import dev.diego.diegoaddons.module.modules.ClockModule;
import dev.diego.diegoaddons.module.modules.CommandHotkeysModule;
import dev.diego.diegoaddons.module.modules.CustomF5;
import dev.diego.diegoaddons.module.modules.HideEffectsModule;
import dev.diego.diegoaddons.module.modules.InventoryButtonsModule;
import dev.diego.diegoaddons.module.modules.InventoryHudModule;
import dev.diego.diegoaddons.module.modules.MusicDisplayModule;
import dev.diego.diegoaddons.module.modules.OldMasterStarsModule;
import dev.diego.diegoaddons.module.modules.PartyCommandsModule;
import dev.diego.diegoaddons.module.modules.PartyFinderModule;
import dev.diego.diegoaddons.module.modules.PuzzleSolversModule;
import dev.diego.diegoaddons.module.modules.PerformanceModule;
import dev.diego.diegoaddons.module.modules.ReplaceWordsModule;
import dev.diego.diegoaddons.module.modules.SkinChangerModule;
import dev.diego.diegoaddons.util.InventoryButtons;
import dev.diego.diegoaddons.util.IgnoreList;
import dev.diego.diegoaddons.util.LegacyText;
import dev.diego.diegoaddons.util.OldMasterStars;
import dev.diego.diegoaddons.util.PartyCommands;
import dev.diego.diegoaddons.util.PartyFinder;
import dev.diego.diegoaddons.util.PuzzleSolvers;
import dev.diego.diegoaddons.util.Toasts;
import dev.diego.diegoaddons.util.TpsTracker;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Owns the module registry and dispatches always-registered client-tick and HUD-render events to
 * the currently enabled modules. Because the event hooks are registered once and never removed,
 * enabling, disabling, and reloading modules is fully live - no registry churn, no restart.
 */
public final class ModuleManager {
    private static final List<Module> MODULES = new ArrayList<>();
    private static boolean initialised = false;

    private ModuleManager() {
    }

    public static List<Module> all() {
        return MODULES;
    }

    public static void init() {
        if (initialised) {
            return;
        }
        initialised = true;

        // Register modules with their default-on state (used only the first time this instance runs).
        // Everything ships disabled by default; the user enables what they want.
        register(new CustomF5(), false);
        register(new ArmorHiderModule(), false);
        register(new AnimationsModule(), false);
        register(new SkinChangerModule(), false);
        register(new PerformanceModule(), false);
        register(new ClockModule(), false);
        register(new InventoryHudModule(), false);
        register(new MusicDisplayModule(), false);
        register(new OldMasterStarsModule(), false);
        register(new InventoryButtonsModule(), false);
        register(new ChatHistoryModule(), false);
        register(new ChatSearchModule(), false);
        register(new ChatCompactModule(), false);
        register(new HideEffectsModule(), false);
        register(new BetterIgnoreListModule(), false);
        register(new ReplaceWordsModule(), false);
        register(new PartyCommandsModule(), false);
        register(new PartyFinderModule(), false);
        register(new PuzzleSolversModule(), false);
        register(new CommandHotkeysModule(), false);
        ConfigManager.save();

        // Apply persisted enabled states.
        for (Module m : MODULES) {
            m.setEnabled(ConfigManager.moduleConfig(m.id).enabled);
        }

        // Always-on dispatch hooks.
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            dev.diego.diegoaddons.util.SkyblockHud.tick(mc);
            for (Module m : MODULES) {
                if (m.isEnabled()) {
                    m.onClientTick(mc);
                }
            }
        });
        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath(DiegoAddonsV2Client.MOD_ID, "hud"),
                ModuleManager::renderHud);

        // Rewrite dungeon-star item names (line 0 = the name) to the old master-star style.
        ItemTooltipCallback.EVENT.register((stack, ctx, type, lines) -> {
            OldMasterStarsModule m = OldMasterStarsModule.INSTANCE;
            if (m == null || !m.isEnabled() || lines.isEmpty()) {
                return;
            }
            Component name = lines.get(0);
            Component converted = OldMasterStars.transform(name);
            if (converted != name) {
                lines.set(0, converted);
            }
        });

        // The word list is applied when text is drawn (see FontMixin), which covers item names and
        // lore along with everything else - so there is deliberately no tooltip hook for it here.

        // Watch system messages: blocked players joining the party, and party chat triggers.
        // Observing only; the message itself is left alone.
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) {
                String plain = LegacyText.strip(message.getString());
                IgnoreList.onMessage(plain);
                PartyCommands.onMessage(plain);
                PuzzleSolvers.onMessage(plain);
            }
        });

        // Inventory buttons and toasts, drawn after a container menu's own extract pass.
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (screen instanceof AbstractContainerScreen<?>) {
                // The party finder overlay draws with the background, so item tooltips stay on top
                // of it and the slot highlight sits behind the item rather than over it.
                ScreenEvents.afterBackground(screen).register((scr, g, mx, my, dt) ->
                        PartyFinder.render((AbstractContainerScreen<?>) scr, g));
                ScreenEvents.afterExtract(screen).register((scr, g, mx, my, dt) -> {
                    InventoryButtons.render((AbstractContainerScreen<?>) scr, g, mx, my);
                    Toasts.render(g);
                });
                // Deny the click to the menu when it lands on one of our buttons, so the button
                // press cannot also be read as a click on the slot behind it.
                ScreenMouseEvents.allowMouseClick(screen).register((scr, ev) -> {
                    AbstractContainerScreen<?> cs = (AbstractContainerScreen<?>) scr;
                    boolean ours = PartyFinder.click(cs, ev.x(), ev.y(), ev.button())
                            || InventoryButtons.click(cs, ev.x(), ev.y(), ev.button());
                    return !ours;
                });
            }
        });

        // Server-TPS estimate is derived from time packets; reset it when leaving a world.
        // The SkyBlock pet/equipment cache is per-profile, so clear it too.
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            TpsTracker.reset();
            dev.diego.diegoaddons.util.SkyblockHud.reset();
            dev.diego.diegoaddons.util.ChatCompactor.reset();
            PuzzleSolvers.reset();
            dev.diego.diegoaddons.util.BlazeSolver.reset();
            dev.diego.diegoaddons.util.DungeonRooms.reset();
            dev.diego.diegoaddons.util.BeamsSolver.reset();
            dev.diego.diegoaddons.util.WorldRender.clear();
            PartyCommands.reset();
        });

        DiegoAddonsV2Client.LOGGER.info("[DiegoAddons V2] {} modules registered", MODULES.size());
    }

    private static void register(Module m, boolean defaultOn) {
        MODULES.add(m);
        // Seed default settings the first time we ever see this module in this instance.
        if (!ConfigManager.get().modules.containsKey(m.id)) {
            ConfigManager.get().modules.put(m.id, new ModuleConfig(defaultOn));
        }
    }

    /** Toggle a module on/off and persist the choice. Live. */
    public static void setEnabled(Module m, boolean enabled) {
        m.setEnabled(enabled);
        ConfigManager.moduleConfig(m.id).enabled = enabled;
        ConfigManager.save();
    }

    public static void toggle(Module m) {
        setEnabled(m, !m.isEnabled());
    }

    /** Categories that have at least one registered module, in {@link Category} order. */
    public static List<Category> categories() {
        List<Category> out = new ArrayList<>();
        for (Category c : Category.values()) {
            for (Module m : MODULES) {
                if (m.category == c) {
                    out.add(c);
                    break;
                }
            }
        }
        return out;
    }

    public static List<Module> modulesIn(Category c) {
        List<Module> out = new ArrayList<>();
        for (Module m : MODULES) {
            if (m.category == c) {
                out.add(m);
            }
        }
        return out;
    }

    // --- HUD chip geometry + drawing, shared by the live HUD and the HUD editor ---

    private static final int CHIP_PAD_X = 8;
    private static final int CHIP_PAD_Y = 5;
    private static final int CHIP_LINE_H = Fonts.BODY_H;   // per-row height
    /** Height of a single-line chip - the default row stride when auto-stacking new modules. */
    public static final int CHIP_H = CHIP_LINE_H + CHIP_PAD_Y * 2;
    private static final int DEFAULT_X = 6;
    private static final int DEFAULT_GAP = 4;

    /** The enabled HUD modules, in registration order. */
    public static List<HudModule> enabledHudModules() {
        List<HudModule> out = new ArrayList<>();
        for (Module m : MODULES) {
            if (m.isEnabled() && m instanceof HudModule hud) {
                out.add(hud);
            }
        }
        return out;
    }

    public static int chipWidth(Font font, List<String> lines) {
        int w = 0;
        for (String line : lines) {
            w = Math.max(w, font.width(Fonts.t(normalizeDigits(font, line), Fonts.MEDIUM)));
        }
        return w + CHIP_PAD_X * 2;
    }

    /**
     * Replace every digit with the widest digit glyph so numeric HUD values (clock, FPS, coords)
     * keep a stable chip width instead of jittering as the digits change from frame to frame.
     */
    private static String normalizeDigits(Font font, String line) {
        char widest = '0';
        int max = -1;
        for (char c = '0'; c <= '9'; c++) {
            int cw = font.width(Fonts.t(String.valueOf(c), Fonts.MEDIUM));
            if (cw > max) {
                max = cw;
                widest = c;
            }
        }
        StringBuilder sb = new StringBuilder(line.length());
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            sb.append(c >= '0' && c <= '9' ? widest : c);
        }
        return sb.toString();
    }

    public static int chipHeight(List<String> lines) {
        return Math.max(1, lines.size()) * CHIP_LINE_H + CHIP_PAD_Y * 2;
    }

    /** Resolve a module's HUD X, falling back to a default stacked position by its index. */
    public static int hudX(HudModule hud) {
        int cx = ConfigManager.moduleConfig(hud.id).hudX;
        return cx >= 0 ? cx : DEFAULT_X;
    }

    public static int hudY(HudModule hud) {
        int cy = ConfigManager.moduleConfig(hud.id).hudY;
        if (cy >= 0) {
            return cy;
        }
        int slot = MODULES.indexOf(hud);
        return 6 + slot * (CHIP_H + DEFAULT_GAP);
    }

    public static void setHudPos(HudModule hud, int x, int y) {
        ModuleConfig cfg = ConfigManager.moduleConfig(hud.id);
        cfg.hudX = x;
        cfg.hudY = y;
        ConfigManager.save();
    }

    public static final float MIN_SCALE = 0.5f;
    public static final float MAX_SCALE = 3.0f;

    /** The chip scale multiplier for a module, clamped to the allowed range. */
    public static float hudScale(HudModule hud) {
        float s = ConfigManager.moduleConfig(hud.id).hudScale;
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, s <= 0f ? 1f : s));
    }

    public static void setHudScale(HudModule hud, float scale) {
        ConfigManager.moduleConfig(hud.id).hudScale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale));
        ConfigManager.save();
    }

    public static void resetHudPositions() {
        for (Module m : MODULES) {
            ModuleConfig cfg = ConfigManager.moduleConfig(m.id);
            cfg.hudX = -1;
            cfg.hudY = -1;
        }
        ConfigManager.save();
    }

    /** Draw the default text chip (one or more stacked lines) at the local origin (0, 0). */
    public static void drawTextChipLocal(GuiGraphicsExtractor g, Font font, Theme t, boolean smooth,
                                         HudModule hud, List<String> lines) {
        int w = chipWidth(font, lines);
        int h = chipHeight(lines);
        int bg = (0xCC << 24) | (t.surface() & 0x00FFFFFF);
        UiRender.fillRounded(g, 0, 0, w, h, 7, bg, smooth);
        UiRender.strokeRounded(g, 0, 0, w, h, 7, Theme.withAlpha(t.border(), 0.9f), smooth);
        for (int i = 0; i < lines.size(); i++) {
            int slotTop = CHIP_PAD_Y + i * CHIP_LINE_H;
            // Baseline-correct vertical centring (size 10 = Fonts.MEDIUM point-size).
            int ty = Fonts.centerTop(slotTop, CHIP_LINE_H, 10);
            // Centring uses the line's real width, not the digit-normalised one, so the slack the
            // normalisation leaves behind is split evenly instead of piling up on the right.
            int tx = CHIP_PAD_X;
            if (hud.isCentered()) {
                tx = (w - font.width(Fonts.t(lines.get(i), Fonts.MEDIUM))) / 2;
            }
            UiRender.text(g, font, lines.get(i), Fonts.MEDIUM, tx, ty, hud.color());
        }
    }

    /**
     * Draw a HUD element at (x, y). Used by both the live HUD and the editor: it sets up a scaled
     * pose (so the whole element grows/shrinks by {@link #hudScale(HudModule)} around its top-left
     * corner) and delegates the actual drawing to the module. Returns whether anything was drawn.
     */
    public static boolean drawElement(GuiGraphicsExtractor g, Font font, Theme t, boolean smooth,
                                      HudModule hud, Minecraft mc, int x, int y, boolean editor) {
        float s = hudScale(hud);
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(s);
        boolean drawn = hud.drawLocal(g, font, t, smooth, mc, editor);
        g.pose().popMatrix();
        return drawn;
    }

    private static void renderHud(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.screen instanceof dev.diego.diegoaddons.gui.HudEditorScreen) {
            return; // the editor draws its own elements
        }
        Theme t = Themes.current();
        boolean smooth = ConfigManager.get().smoothCorners;
        Font font = mc.font;
        for (HudModule hud : enabledHudModules()) {
            drawElement(g, font, t, smooth, hud, mc, hudX(hud), hudY(hud), false);
        }
        // Toasts, except on the screens that draw them themselves - those sit above the HUD (and
        // dim it), so drawing here too would show a faded ghost behind the crisp one.
        if (!(mc.screen instanceof dev.diego.diegoaddons.gui.ChatSearchScreen)
                && !(mc.screen instanceof AbstractContainerScreen<?>)) {
            Toasts.render(g);
        }
    }
}
