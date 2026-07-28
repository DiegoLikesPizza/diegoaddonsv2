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
import dev.diego.diegoaddons.module.modules.AnnounceKickModule;
import dev.diego.diegoaddons.module.modules.AutoRequeueModule;
import dev.diego.diegoaddons.module.modules.AutoSprintModule;
import dev.diego.diegoaddons.module.modules.ForceNametagModule;
import dev.diego.diegoaddons.module.modules.FullbrightModule;
import dev.diego.diegoaddons.module.modules.HydrationReminderModule;
import dev.diego.diegoaddons.module.modules.ItemRarityModule;
import dev.diego.diegoaddons.module.modules.AutoGfsModule;
import dev.diego.diegoaddons.module.modules.BetterIgnoreListModule;
import dev.diego.diegoaddons.module.modules.BorderlessFullscreenModule;
import dev.diego.diegoaddons.module.modules.ChatModule;
import dev.diego.diegoaddons.module.modules.ClockModule;
import dev.diego.diegoaddons.module.modules.CommandHotkeysModule;
import dev.diego.diegoaddons.module.modules.CustomEspModule;
import dev.diego.diegoaddons.module.modules.StarredMobEspModule;
import dev.diego.diegoaddons.module.modules.SecretChimeModule;
import dev.diego.diegoaddons.module.modules.ChestSolverModule;
import dev.diego.diegoaddons.module.modules.CrystalHollowsMapModule;
import dev.diego.diegoaddons.module.modules.CustomF5;
import dev.diego.diegoaddons.module.modules.DoorKeyEspModule;
import dev.diego.diegoaddons.module.modules.DungeonMapModule;
import dev.diego.diegoaddons.module.modules.EtherwarpModule;
import dev.diego.diegoaddons.module.modules.GrottoFinderModule;
import dev.diego.diegoaddons.module.modules.MimicMessageModule;
import dev.diego.diegoaddons.module.modules.MiningRoutesModule;
import dev.diego.diegoaddons.module.modules.PrinceMessageModule;
import dev.diego.diegoaddons.module.modules.StructureFinderModule;
import dev.diego.diegoaddons.module.modules.HideEffectsModule;
import dev.diego.diegoaddons.module.modules.InventoryButtonsModule;
import dev.diego.diegoaddons.module.modules.InventoryHudModule;
import dev.diego.diegoaddons.module.modules.MiningAbilityModule;
import dev.diego.diegoaddons.module.modules.MusicDisplayModule;
import dev.diego.diegoaddons.module.modules.OldMasterStarsModule;
import dev.diego.diegoaddons.module.modules.PartyCommandsModule;
import dev.diego.diegoaddons.module.modules.PartyFinderModule;
import dev.diego.diegoaddons.module.modules.PuzzleSolversModule;
import dev.diego.diegoaddons.module.modules.PerformanceModule;
import dev.diego.diegoaddons.module.modules.ReplaceWordsModule;
import dev.diego.diegoaddons.module.modules.SkinChangerModule;
import dev.diego.diegoaddons.module.modules.AbilityCooldownModule;
import dev.diego.diegoaddons.module.modules.AutoCloseChestModule;
import dev.diego.diegoaddons.module.modules.BatEspModule;
import dev.diego.diegoaddons.module.modules.DungeonMinibossEspModule;
import dev.diego.diegoaddons.module.modules.FishingRareAlertModule;
import dev.diego.diegoaddons.module.modules.LeapOverlayModule;
import dev.diego.diegoaddons.module.modules.CustomScoreboardModule;
import dev.diego.diegoaddons.module.modules.PlayerHudModule;
import dev.diego.diegoaddons.module.modules.PetHudModule;
import dev.diego.diegoaddons.module.modules.PlayerEspModule;
import dev.diego.diegoaddons.module.modules.ShowHiddenMobsModule;
import dev.diego.diegoaddons.module.modules.SlotLockModule;
import dev.diego.diegoaddons.module.modules.TitleScreenModule;
import dev.diego.diegoaddons.module.modules.SlayerBossHighlightModule;
import dev.diego.diegoaddons.module.modules.SlayerMinibossEspModule;
import dev.diego.diegoaddons.module.modules.VoidgloomSlayerModule;
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
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
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
        register(new FullbrightModule(), false);
        register(new ForceNametagModule(), false);
        register(new ArmorHiderModule(), false);
        register(new AutoSprintModule(), false);
        register(new ItemRarityModule(), false);
        register(new AutoRequeueModule(), false);
        register(new AnnounceKickModule(), false);
        register(new AnimationsModule(), false);
        register(new SkinChangerModule(), false);
        register(new PerformanceModule(), false);
        register(new ClockModule(), false);
        register(new InventoryHudModule(), false);
        register(new MusicDisplayModule(), false);
        register(new OldMasterStarsModule(), false);
        register(new InventoryButtonsModule(), false);
        register(new ChatModule(), false);
        register(new ShowHiddenMobsModule(), false);
        register(new BorderlessFullscreenModule(), false);
        register(new TitleScreenModule(), true);
        register(new HydrationReminderModule(), false);
        register(new PlayerHudModule(), false);
        register(new PetHudModule(), false);
        register(new HideEffectsModule(), false);
        register(new BetterIgnoreListModule(), false);
        register(new ReplaceWordsModule(), false);
        register(new PartyCommandsModule(), false);
        register(new PartyFinderModule(), false);
        register(new PuzzleSolversModule(), false);
        register(new DungeonMapModule(), false);
        register(new DoorKeyEspModule(), false);
        register(new MimicMessageModule(), false);
        register(new PrinceMessageModule(), false);
        register(new StarredMobEspModule(), false);
        register(new SecretChimeModule(), false);
        register(new CustomEspModule(), false);
        register(new EtherwarpModule(), false);
        register(new MiningAbilityModule(), false);
        register(new AutoGfsModule(), false);
        register(new CrystalHollowsMapModule(), false);
        register(new StructureFinderModule(), false);
        register(new GrottoFinderModule(), false);
        register(new ChestSolverModule(), false);
        register(new MiningRoutesModule(), false);
        register(new CommandHotkeysModule(), false);
        register(new SlayerBossHighlightModule(), false);
        register(new SlayerMinibossEspModule(), false);
        register(new VoidgloomSlayerModule(), false);
        register(new BatEspModule(), false);
        register(new DungeonMinibossEspModule(), false);
        register(new FishingRareAlertModule(), false);
        register(new AutoCloseChestModule(), false);
        register(new AbilityCooldownModule(), false);
        register(new LeapOverlayModule(), false);
        register(new PlayerEspModule(), false);
        register(new CustomScoreboardModule(), false);
        register(new SlotLockModule(), false);
        ConfigManager.save();

        // Apply persisted enabled states.
        for (Module m : MODULES) {
            m.setEnabled(ConfigManager.moduleConfig(m.id).enabled);
        }

        // A secret is usually a block you clicked: the chime listens for that rather than for the
        // counter catching up with it.
        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (level.isClientSide()) {
                dev.diego.diegoaddons.util.SecretChime.onUseBlock(
                        Minecraft.getInstance(), hit.getBlockPos());
            }
            return net.minecraft.world.InteractionResult.PASS;
        });

        // What a party is short of, at the bottom of the tooltip you are already reading.
        net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback.EVENT.register(
                (stack, context, flag, lines) -> PartyFinder.appendTooltip(stack, lines));

        // Always-on dispatch hooks.
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            dev.diego.diegoaddons.util.SkyblockHud.tick(mc);
            dev.diego.diegoaddons.util.DungeonState.tick(mc);
            // Before anything reads the room grid: has the run changed under us?
            dev.diego.diegoaddons.util.DungeonRun.tick(mc);
            dev.diego.diegoaddons.util.SlayerState.tick(mc);
            dev.diego.diegoaddons.util.CrystalHollows.tick(mc);
            // Room detection is shared by every dungeon solver and the map, so it ticks here once -
            // not off the back of one solver, which left the others blind whenever it was disabled.
            dev.diego.diegoaddons.util.DungeonRooms.tick(mc);
            for (Module m : MODULES) {
                if (m.isEnabled()) {
                    tickSafely(m, mc);
                }
            }
            // Both ESP features read the same name plates, so they share a single pass.
            dev.diego.diegoaddons.util.EntityEsp.tick(mc);
            // Promote everything submitted this tick to the frames until the next one, so world
            // boxes stay solid instead of flickering between ticks.
            dev.diego.diegoaddons.util.WorldRender.flip();
            dev.diego.diegoaddons.util.EspDraw.flip();
            dev.diego.diegoaddons.util.EspWorld.flip();
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

        // Tick a boulder push off the list once the player has actually made it.
        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (level.isClientSide()) {
                dev.diego.diegoaddons.util.BoulderSolver.onInteract(hit.getBlockPos());
                dev.diego.diegoaddons.util.WaterSolver.onInteract(hit.getBlockPos());
            }
            return net.minecraft.world.InteractionResult.PASS;
        });

        // Watch system messages: blocked players joining the party, and party chat triggers.
        // Observing only; the message itself is left alone.
        // Recolour Oruo's answer options in chat: the correct one green, the wrong ones red.
        ClientReceiveMessageEvents.MODIFY_GAME.register((message, overlay) -> {
            if (overlay) {
                return message;
            }
            String plain = LegacyText.strip(message.getString()).trim();
            int color = PuzzleSolvers.quizOptionColor(plain);
            return color != 0 ? Component.literal(plain).withColor(color) : message;
        });

        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) {
                String plain = LegacyText.strip(message.getString());
                IgnoreList.onMessage(plain);
                PartyCommands.onMessage(plain);
                PuzzleSolvers.onMessage(plain);
                dev.diego.diegoaddons.util.MiningAbility.onMessage(plain);
                dev.diego.diegoaddons.util.DungeonState.onMessage(plain);
                dev.diego.diegoaddons.util.FishingAlerts.onMessage(plain);
                PrinceMessageModule.onMessage(plain);
                AutoRequeueModule.onMessage(plain);
            }
        });

        // Inventory buttons and toasts, drawn after a container menu's own extract pass.
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (screen instanceof AbstractContainerScreen<?>) {
                // The party finder overlay draws with the background, so item tooltips stay on top
                // of it and the slot highlight sits behind the item rather than over it.
                ScreenEvents.afterBackground(screen).register((scr, g, mx, my, dt) -> {
                    dev.diego.diegoaddons.util.ItemRarity.render((AbstractContainerScreen<?>) scr, g);
                    // With the background rather than after everything: drawn last it sat on
                    // top of item tooltips.
                    dev.diego.diegoaddons.util.SlotLocks.render((AbstractContainerScreen<?>) scr, g);
                    PartyFinder.render((AbstractContainerScreen<?>) scr, g);
                });
                ScreenEvents.afterExtract(screen).register((scr, g, mx, my, dt) -> {
                    // Inventory buttons are not drawn here any more - they are a RenderLib screen
                    // extension (see InventoryButtonsExtension), which owns their hit testing too.
                    dev.diego.diegoaddons.util.LeapOverlay.render((AbstractContainerScreen<?>) scr, g);
                    dev.diego.diegoaddons.util.SlotLocks.keys((AbstractContainerScreen<?>) scr, mx, my);
                    Toasts.render(g);
                });
                // Deny the click to the menu when it lands on a locked slot. Our own buttons are
                // absent here on purpose: they are RenderLib components now, and a RenderLib button
                // consumes its own press, so the click never reaches the menu and needs no veto.
                ScreenMouseEvents.allowMouseClick(screen).register((scr, ev) ->
                        !dev.diego.diegoaddons.util.SlotLocks.locksClick(
                                (AbstractContainerScreen<?>) scr, ev.x(), ev.y()));
                // Deny hotbar-swap / drop keys that would move a locked slot's item.
                ScreenKeyboardEvents.allowKeyPress(screen).register((scr, event) ->
                        !dev.diego.diegoaddons.util.SlotLocks.locksKey((AbstractContainerScreen<?>) scr, event));
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
            dev.diego.diegoaddons.util.DungeonRun.clear();
            dev.diego.diegoaddons.util.DungeonRun.forget();
            dev.diego.diegoaddons.util.DungeonState.reset();
            dev.diego.diegoaddons.util.SlayerState.reset();
            dev.diego.diegoaddons.util.VoidgloomSlayer.reset();
            dev.diego.diegoaddons.util.DungeonMapData.reset();
            dev.diego.diegoaddons.util.CrystalHollows.reset();
            if (MimicMessageModule.INSTANCE != null) {
                MimicMessageModule.INSTANCE.resetRun();
            }
            dev.diego.diegoaddons.util.BeamsSolver.reset();
            dev.diego.diegoaddons.util.BoulderSolver.reset();
            dev.diego.diegoaddons.util.IceFillSolver.reset();
            dev.diego.diegoaddons.util.WaterSolver.reset();
            dev.diego.diegoaddons.util.TpMazeSolver.reset();
            dev.diego.diegoaddons.util.TicTacToeSolver.reset();
            dev.diego.diegoaddons.util.SecretChime.reset();
            dev.diego.diegoaddons.util.MiningAbility.reset();
            dev.diego.diegoaddons.util.AutoGfs.reset();
            dev.diego.diegoaddons.util.EtherwarpHelper.reset();
            dev.diego.diegoaddons.util.WorldRender.clear();
            dev.diego.diegoaddons.util.EspDraw.clear();
            dev.diego.diegoaddons.util.EspWorld.clear();
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

    // --- HUD helpers still used elsewhere --------------------------------------------------------

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

    /** Modules already told off for throwing, so a broken one says so once and not sixty times a second. */
    private static final java.util.Set<String> FAILED = new java.util.HashSet<>();

    /**
     * Ticks one module, and keeps its failure to itself.
     *
     * <p>The dispatch used to be a plain loop, so anything thrown by one module ended the tick for
     * every module after it - a whole run of silent solvers with nothing in the log to say why, and
     * the cause depending on registration order. A module that throws is now reported once, by name,
     * and everything else carries on.
     */
    private static void tickSafely(Module m, Minecraft mc) {
        try {
            m.onClientTick(mc);
        } catch (RuntimeException | LinkageError e) {
            if (FAILED.add(m.id)) {
                DiegoAddonsV2Client.LOGGER.error("[DiegoAddons] {} threw while ticking and was left running; "
                        + "the rest of the modules are unaffected", m.id, e);
            }
        }
    }

    private static void renderHud(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) {
            return;
        }
        Theme t = Themes.current();
        boolean smooth = ConfigManager.get().smoothCorners;
        Font font = mc.font;
        dev.diego.diegoaddons.util.ItemRarity.renderHotbar(g, mc);
        dev.diego.diegoaddons.util.AbilityCooldown.renderHotbar(g, mc);
        dev.diego.diegoaddons.util.EspDraw.renderHud(g, mc);
        // Toasts, except on the screens that draw them themselves - those sit above the HUD (and
        // dim it), so drawing here too would show a faded ghost behind the crisp one.
        if (!(mc.screen instanceof AbstractContainerScreen<?>)) {
            Toasts.render(g);
        }
    }
}
