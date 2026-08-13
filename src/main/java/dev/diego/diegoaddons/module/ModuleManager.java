package dev.diego.diegoaddons.module;

import dev.diego.diegoaddons.DiegoAddonsV2Client;
import dev.diego.diegoaddons.config.ConfigManager;
import dev.diego.diegoaddons.gui.Fonts;
import dev.diego.diegoaddons.gui.Theme;
import dev.diego.diegoaddons.gui.Themes;
import dev.diego.diegoaddons.gui.UiRender;
import dev.diego.diegoaddons.module.modules.AnimationsModule;
import dev.diego.diegoaddons.module.modules.ArmorHiderModule;
import dev.diego.diegoaddons.module.modules.AnnounceKickModule;
import dev.diego.diegoaddons.module.modules.AutoRequeueModule;
import dev.diego.diegoaddons.module.modules.AutoSprintModule;
import dev.diego.diegoaddons.module.modules.AutoUpdateModule;
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
import dev.diego.diegoaddons.module.modules.MinigamesModule;
import dev.diego.diegoaddons.module.modules.NoCursorResetModule;
import dev.diego.diegoaddons.module.modules.StorageOverlayModule;
import dev.diego.diegoaddons.module.modules.TitleScreenModule;
import dev.diego.diegoaddons.module.modules.SlayerBossHighlightModule;
import dev.diego.diegoaddons.module.modules.SlayerMinibossEspModule;
import dev.diego.diegoaddons.module.modules.VoidgloomSlayerModule;
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
        register(new StorageOverlayModule(), false);
        register(new NoCursorResetModule(), false);
        register(new MinigamesModule(), false);
        register(new dev.diego.diegoaddons.module.modules.PestTimerModule(), false);
        register(new dev.diego.diegoaddons.module.modules.PestEspModule(), false);
        register(new dev.diego.diegoaddons.module.modules.PlotBordersModule(), false);
        register(new dev.diego.diegoaddons.module.modules.SprayTimerModule(), false);
        register(new dev.diego.diegoaddons.module.modules.PestTrapsModule(), false);
        register(new dev.diego.diegoaddons.module.modules.JacobContestModule(), false);
        register(new dev.diego.diegoaddons.module.modules.VisitorHelperModule(), false);
        register(new dev.diego.diegoaddons.module.modules.FeastHudModule(), false);
        register(new dev.diego.diegoaddons.module.modules.FarmingSessionModule(), false);
        register(new dev.diego.diegoaddons.module.modules.LoadoutKeybindModule(), false);
        // Hunting. Order here is the order of the cards: the two that need finding before they can
        // be caught first, then the critters in the order the wiki groups them (water, ground).
        register(new dev.diego.diegoaddons.module.modules.InvisibugEspModule(), false);
        register(new dev.diego.diegoaddons.module.modules.CinderbatEspModule(), false);
        register(new dev.diego.diegoaddons.module.modules.MatchoEspModule(), false);
        register(new dev.diego.diegoaddons.module.modules.PufferEspModule(), false);
        register(new dev.diego.diegoaddons.module.modules.TurtleEspModule(), false);
        register(new dev.diego.diegoaddons.module.modules.DolphinEspModule(), false);
        register(new dev.diego.diegoaddons.module.modules.FeeshEspModule(), false);
        register(new dev.diego.diegoaddons.module.modules.AxolotlEspModule(), false);
        register(new dev.diego.diegoaddons.module.modules.FrogEspModule(), false);
        register(new dev.diego.diegoaddons.module.modules.PandaEspModule(), false);
        // Safari (the Critter Safari on Torrhus Canyon, SkyBlock 0.27).
        register(new dev.diego.diegoaddons.module.modules.CritterEspModule(), false);
        register(new dev.diego.diegoaddons.module.modules.SparklingCritterModule(), false);
        register(new dev.diego.diegoaddons.module.modules.FloorDropsEspModule(), false);
        register(new dev.diego.diegoaddons.module.modules.HideyhoFinderModule(), false);
        register(new dev.diego.diegoaddons.module.modules.ColdWarningModule(), false);
        register(new dev.diego.diegoaddons.module.modules.SafariItemsModule(), false);
        // Off by default like everything else, and for one more reason: it reaches the network and
        // replaces the mod's own jar, which is nobody's default.
        register(new AutoUpdateModule(), false);
        // Nothing is read or written here any more. The saved state arrives when configlib reads
        // its file, which happens after this method - the spec it reads into is built from what was
        // just registered - and the modules that came back on are woken by applyEnabled.

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

        // What a Safari quest item is for, under its own lore.
        net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback.EVENT.register(
                (stack, context, flag, lines) ->
                        dev.diego.diegoaddons.util.SafariItems.appendTooltip(stack, lines));

        // What a visitor's offer is worth, under the offer's own lore.
        net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback.EVENT.register(
                (stack, context, flag, lines) -> {
                    if (Minecraft.getInstance().screen instanceof AbstractContainerScreen<?> screen) {
                        dev.diego.diegoaddons.util.Visitors.appendTooltip(screen, stack, lines);
                    }
                });

        // Always-on dispatch hooks.
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            // The scoreboard and tab list are read from all over the mod, several times per tick.
            // They cannot change without a packet, so they are read once and held for the tick.
            dev.diego.diegoaddons.util.SkyblockLocation.invalidate();
            dev.diego.diegoaddons.util.SkyblockHud.tick(mc);
            dev.diego.diegoaddons.util.DungeonState.tick(mc);
            // Before anything reads the room grid: has the run changed under us?
            dev.diego.diegoaddons.util.DungeonRun.tick(mc);
            dev.diego.diegoaddons.util.SlayerState.tick(mc);
            dev.diego.diegoaddons.util.CrystalHollows.tick(mc);
            // The pest cooldown is read from the tab list, so it has to be read before the modules
            // that warn off it - and once, not once per Garden feature.
            dev.diego.diegoaddons.util.Pests.tick(mc);
            // Cold, before the module that warns off it - the module ticks in the loop below, and a
            // reading taken after it would always be a tick behind the warning that used it.
            dev.diego.diegoaddons.util.Cold.tick(mc);
            // After Pests: the plot borders follow its list of infested plots.
            dev.diego.diegoaddons.util.Garden.tick(mc);
            // Sacks are read from whatever menu is open, so this runs wherever you are.
            dev.diego.diegoaddons.util.Sacks.tick(mc);
            // Room detection is shared by every dungeon solver and the map, so it ticks here once -
            // not off the back of one solver, which left the others blind whenever it was disabled.
            dev.diego.diegoaddons.util.DungeonRooms.tick(mc);
            for (Module m : MODULES) {
                if (m.isEnabled()) {
                    tickSafely(m, mc);
                }
            }
            // Which hunting ESPs are on the right island is decided once here, so the entity pass
            // below can ask that instead of reading the tab list per module per entity. This also
            // draws the Invisibugs, which are not found in that pass at all.
            dev.diego.diegoaddons.util.Hunting.tick(mc);
            // Same arrangement for the Safari: whether its two features are drawing is decided once,
            // and the plate pass below asks that rather than the tab list.
            dev.diego.diegoaddons.util.SafariEsp.tick(mc);
            // The sparkling trail, which is found from particle packets rather than from entities -
            // so it draws itself here rather than in the plate pass below.
            dev.diego.diegoaddons.util.SparkleParticles.tick(mc);
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

        // A game's own whispers are read here and kept out of the chat, before anything else sees
        // them. Returning false hides the line - see GameLink, which decides whether it was ours.
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            if (overlay) {
                return true;
            }
            return !dev.diego.diegoaddons.util.GameLink.receive(
                    LegacyText.strip(message.getString()).trim());
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
                dev.diego.diegoaddons.util.Pests.onMessage(plain);
                dev.diego.diegoaddons.util.Garden.onMessage(plain);
                dev.diego.diegoaddons.util.HarvestFeast.onMessage(plain);
                dev.diego.diegoaddons.util.FarmingSession.onMessage(plain);
                dev.diego.diegoaddons.util.Hideyho.onMessage(plain);
                dev.diego.diegoaddons.module.modules.ColdWarningModule.onMessage(plain);
                PrinceMessageModule.onMessage(plain);
                AutoRequeueModule.onMessage(plain);
                AnnounceKickModule.onMessage(plain);
                // Autopet swaps the pet without any menu being opened, so chat is the only place
                // the HUD can learn about it.
                dev.diego.diegoaddons.util.SkyblockHud.onChat(plain);
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
                    // Inventory buttons are not drawn here any more - they are a screen
                    // extension (see InventoryButtonsExtension), which owns their hit testing too.
                    dev.diego.diegoaddons.util.LeapOverlay.render((AbstractContainerScreen<?>) scr, g);
                    dev.diego.diegoaddons.util.SlotLocks.keys((AbstractContainerScreen<?>) scr, mx, my);
                    Toasts.render(g);
                });
                // Deny the click to the menu when it lands on a locked slot. Our own buttons are
                // absent here on purpose: they are real widgets now, and a widget
                // consumes its own press, so the click never reaches the menu and needs no veto.
                //
                // The storage sheet goes first and takes every click while it is up: it is standing
                // in for the menu, and a press reaching the menu underneath would land on whatever
                // Hypixel happens to have at those coordinates rather than on what you can see.
                ScreenMouseEvents.allowMouseClick(screen).register((scr, ev) -> {
                    AbstractContainerScreen<?> container = (AbstractContainerScreen<?>) scr;
                    boolean shift = (ev.modifiers() & org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT) != 0;
                    if (dev.diego.diegoaddons.gui.StorageOverlay.mouseClicked(
                            container, ev.x(), ev.y(), ev.button(), shift)) {
                        return false;
                    }
                    return !dev.diego.diegoaddons.util.SlotLocks.locksClick(container, ev.x(), ev.y());
                });
                ScreenMouseEvents.allowMouseScroll(screen).register((scr, mx, my, hs, vs) ->
                        !dev.diego.diegoaddons.gui.StorageOverlay.mouseScrolled(
                                (AbstractContainerScreen<?>) scr, vs));
                // Typing goes to the sheet's search box while it is up, which is also why the keys
                // that would otherwise close the menu have to be asked about first.
                ScreenKeyboardEvents.allowCharType(screen).register((scr, event) ->
                        !dev.diego.diegoaddons.gui.StorageOverlay.charTyped(
                                (AbstractContainerScreen<?>) scr, (char) event.codepoint()));
                // Which storage icon was clicked, so the backpack menu that follows can be
                // attributed to the slot it came from. After the click rather than before: this
                // only observes, and the click itself is what opens the backpack.
                ScreenMouseEvents.afterMouseClick(screen).register((scr, ev, consumed) -> {
                    dev.diego.diegoaddons.util.StorageScanner.onContainerClick(
                            (AbstractContainerScreen<?>) scr, ev.x(), ev.y());
                    return false;
                });
                // Deny hotbar-swap / drop keys that would move a locked slot's item, and let the
                // storage sheet have the keys first - it takes every one of them while it is up,
                // because a key reaching the menu underneath acts on a slot the sheet has hidden.
                ScreenKeyboardEvents.allowKeyPress(screen).register((scr, event) -> {
                    AbstractContainerScreen<?> container = (AbstractContainerScreen<?>) scr;
                    if (dev.diego.diegoaddons.gui.StorageOverlay.keyPressed(container, event)) {
                        return false;
                    }
                    return !dev.diego.diegoaddons.util.SlotLocks.locksKey(container, event);
                });
            }
        });

        // Server-TPS estimate is derived from time packets; reset it when leaving a world.
        // The SkyBlock pet/equipment cache is per-profile, so clear it too.
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            TpsTracker.reset();
            dev.diego.diegoaddons.util.SkyblockHud.reset();
            // Writes out anything the last menus added, then forgets the profile - the next login
            // may well be a different one, and storage is per profile.
            dev.diego.diegoaddons.util.StorageData.reset();
            // A game cannot survive the connection that carried it, and there is nobody left to
            // tell - so it is dropped rather than resigned.
            dev.diego.diegoaddons.util.Minigames.reset();
            dev.diego.diegoaddons.util.IgnoreList.reset();
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
            // The armour stands an Invisibug was found through do not survive the world change,
            // and a remembered id would point at whatever entity is given it next.
            dev.diego.diegoaddons.util.Invisibug.clear();
            dev.diego.diegoaddons.util.SafariEsp.clear();
            dev.diego.diegoaddons.util.FloorDrops.clear();
            dev.diego.diegoaddons.util.SparkleParticles.clear();
            dev.diego.diegoaddons.util.Hideyho.reset();
            dev.diego.diegoaddons.util.Cold.reset();
            dev.diego.diegoaddons.util.WorldRender.clear();
            dev.diego.diegoaddons.util.EspDraw.clear();
            dev.diego.diegoaddons.util.EspWorld.clear();
            PartyCommands.reset();
        });

        DiegoAddonsV2Client.LOGGER.info("[DiegoAddons V2] {} modules registered", MODULES.size());
    }

    /**
     * @param defaultOn whether this module is on before anything has been saved. configlib only
     *                  calls the setter for options actually present in the file, so a module the
     *                  user has never touched simply keeps whatever is set here.
     */
    private static void register(Module m, boolean defaultOn) {
        MODULES.add(m);
        // Quietly: onEnable runs against a client that is still starting up. The enabled modules
        // are woken in one pass once the config has been read - see applyEnabled.
        m.setEnabledQuietly(defaultOn);
    }

    /**
     * Fires {@code onEnable} for everything that came back enabled.
     *
     * <p>Separate from loading because the two cannot be the same step: configlib pushes the saved
     * values in while the client is still being built, and a module's {@code onEnable} may touch
     * parts of it that do not exist yet. Reading first and waking second keeps that order honest.
     */
    public static void applyEnabled() {
        for (Module m : MODULES) {
            m.wake();
        }
    }

    /** Toggle a module on/off and persist the choice. Live. */
    public static void setEnabled(Module m, boolean enabled) {
        if (m.isEnabled() == enabled) {
            return;
        }
        // While the config is being read this is a restore, not a choice: set the flag and leave
        // the waking to applyEnabled, and do not write the file back out mid-load.
        if (ConfigManager.isLoading()) {
            m.setEnabledQuietly(enabled);
            return;
        }
        m.setEnabled(enabled);
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
        // Item Rarity is not here: its hotbar backing has to go *under* the item, so it is drawn
        // from HotbarSlotRarityMixin rather than from this pass, which runs after the whole GUI.
        dev.diego.diegoaddons.util.AbilityCooldown.renderHotbar(g, mc);
        dev.diego.diegoaddons.util.EspDraw.renderHud(g, mc);
        // The mining ability and hydration reminders, which are shouted across the middle of the
        // screen rather than placed - see CentreOverlay.
        dev.diego.diegoaddons.hud.CentreOverlay.render(g, mc);
        // Toasts, except on the screens that draw them themselves - those sit above the HUD (and
        // dim it), so drawing here too would show a faded ghost behind the crisp one.
        if (!(mc.screen instanceof AbstractContainerScreen<?>)) {
            Toasts.render(g);
        }
    }
}
