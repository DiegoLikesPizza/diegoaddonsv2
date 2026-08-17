package dev.diego.diegoaddons.command;

import dev.diego.diegoaddons.DiegoAddonsV2Client;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.diego.diegoaddons.config.MiningRoute;
import dev.diego.diegoaddons.util.CustomEsp;
import dev.diego.diegoaddons.util.CustomImages;
import dev.diego.diegoaddons.util.IgnoreList;
import dev.diego.diegoaddons.util.MiningRoutes;
import dev.diego.diegoaddons.util.PortalImages;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Supplier;

/**
 * Client-side commands for the mod: {@code /da} and {@code /diego} open the main menu, with
 * sub-commands for the editors and the block list.
 *
 * <p>These never reach the server - they are handled entirely on the client by Fabric's client
 * command API, so they work on any server without it seeing an unknown command.
 */
public final class DiegoCommands {
    /** One line of {@code /da help}. */
    private record Help(String usage, String description) {
    }

    /**
     * The help text. Kept beside the registrations below so adding a command and forgetting to
     * document it is a visible omission rather than a silent one.
     */
    private static final List<Help> HELP = List.of(
            new Help("", "Open the DiegoAddons menu"),
            new Help("help", "Show this list"),
            new Help("hud", "Open the HUD editor"),
            new Help("play <player> [ttt|c4|bj|bs]",
                    "Challenge a DiegoAddons user - noughts and crosses, connect four, blackjack, battleships"),
            new Help("accept", "Accept the game you were invited to"),
            new Help("decline", "Decline the invitation"),
            new Help("resign", "Give up the running game"),
            new Help("game", "Reopen the board"),
            new Help("update", "Check for a new version of the mod"),
            new Help("buttons", "Open the inventory button editor"),
            new Help("words", "Open the word replacement list"),
            new Help("hotkeys", "Open the command hotkey list"),
            new Help("blocked", "Open the blocked player list"),
            new Help("block <player> [reason]", "Block a player, optionally with a reason"),
            new Help("unblock <player>", "Unblock a player"),
            new Help("esp add <text>", "Highlight mobs whose name contains this"),
            new Help("esp list", "Show the highlight terms"),
            new Help("esp remove <text>", "Stop highlighting that term"),
            new Help("route new <name>", "Start a new mining route and show it"),
            new Help("route add", "Add your position to the shown route"),
            new Help("route undo", "Remove the last point of the shown route"),
            new Help("route show <name>", "Draw a saved route"),
            new Help("route hide", "Stop drawing the route"),
            new Help("route list", "List your mining routes"),
            new Help("route delete <name>", "Delete a mining route"),
            new Help("portal <file>", "Put an image on the portal you are looking at"),
            new Help("portal", "Say which portal you are looking at and what is on it"),
            new Help("portal clear", "Take the image off that portal"),
            new Help("portal clear all", "Take every portal image off"),
            new Help("portal list", "List the portals you have given an image"),
            new Help("images", "List the images you have dropped in, and re-read them"));

    private DiegoCommands() {
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, ctx) -> {
            root(dispatcher, "da");
            root(dispatcher, "diego");
        });
    }

    /** Registers one command root, so both names get the same sub-commands. */
    private static void root(CommandDispatcher<FabricClientCommandSource> dispatcher, String name) {
        LiteralArgumentBuilder<FabricClientCommandSource> cmd = ClientCommands.literal(name)
                .executes(c -> openMenu())
                .then(ClientCommands.literal("help")
                        .executes(c -> help(c.getSource(), name)))
                .then(ClientCommands.literal("hud")
                        .executes(c -> run(() -> DiegoAddonsV2Client.CONFIG.openHudEditor())))
                .then(ClientCommands.literal("play")
                        .then(ClientCommands.argument("player", StringArgumentType.word())
                                .executes(c -> run(() -> dev.diego.diegoaddons.util.Minigames.invite(
                                        StringArgumentType.getString(c, "player"), null)))
                                .then(ClientCommands.argument("game", StringArgumentType.word())
                                        .suggests((c, b) -> {
                                            dev.diego.diegoaddons.util.Minigames.GAMES.keySet()
                                                    .forEach(b::suggest);
                                            return b.buildFuture();
                                        })
                                        .executes(c -> run(() ->
                                                dev.diego.diegoaddons.util.Minigames.invite(
                                                        StringArgumentType.getString(c, "player"),
                                                        StringArgumentType.getString(c, "game")))))))
                .then(ClientCommands.literal("accept")
                        .executes(c -> run(dev.diego.diegoaddons.util.Minigames::accept)))
                .then(ClientCommands.literal("decline")
                        .executes(c -> run(dev.diego.diegoaddons.util.Minigames::decline)))
                .then(ClientCommands.literal("resign")
                        .executes(c -> run(dev.diego.diegoaddons.util.Minigames::resign)))
                .then(ClientCommands.literal("game")
                        .executes(c -> run(dev.diego.diegoaddons.gui.GameScreen::open)))
                .then(ClientCommands.literal("update")
                        .executes(c -> update(c.getSource())))
                .then(ClientCommands.literal("buttons")
                        .executes(c -> open(() ->
                                new dev.diego.diegoaddons.gui.InvButtonEditor(null))))
                .then(ClientCommands.literal("words")
                        .executes(c -> openList("misc.words")))
                .then(ClientCommands.literal("hotkeys")
                        .executes(c -> openList("misc.hotkeys")))
                .then(ClientCommands.literal("blocked")
                        .executes(c -> openList("misc.blocked")))
                .then(ClientCommands.literal("block")
                        .then(ClientCommands.argument("player", StringArgumentType.word())
                                .executes(c -> block(c.getSource(), StringArgumentType.getString(c, "player"), ""))
                                .then(ClientCommands.argument("reason", StringArgumentType.greedyString())
                                        .executes(c -> block(c.getSource(),
                                                StringArgumentType.getString(c, "player"),
                                                StringArgumentType.getString(c, "reason"))))))
                .then(ClientCommands.literal("esp")
                        .then(ClientCommands.literal("list")
                                .executes(c -> espList(c.getSource())))
                        .then(ClientCommands.literal("add")
                                .then(ClientCommands.argument("text", StringArgumentType.greedyString())
                                        .executes(c -> espAdd(c.getSource(),
                                                StringArgumentType.getString(c, "text")))))
                        .then(ClientCommands.literal("remove")
                                .then(ClientCommands.argument("text", StringArgumentType.greedyString())
                                        .executes(c -> espRemove(c.getSource(),
                                                StringArgumentType.getString(c, "text"))))))
                .then(ClientCommands.literal("route")
                        .then(ClientCommands.literal("new")
                                .then(ClientCommands.argument("name", StringArgumentType.greedyString())
                                        .executes(c -> routeNew(c.getSource(),
                                                StringArgumentType.getString(c, "name")))))
                        .then(ClientCommands.literal("add")
                                .executes(c -> routeAdd(c.getSource())))
                        .then(ClientCommands.literal("undo")
                                .executes(c -> routeUndo(c.getSource())))
                        .then(ClientCommands.literal("show")
                                .then(ClientCommands.argument("name", StringArgumentType.greedyString())
                                        .executes(c -> routeShow(c.getSource(),
                                                StringArgumentType.getString(c, "name")))))
                        .then(ClientCommands.literal("hide")
                                .executes(c -> routeHide(c.getSource())))
                        .then(ClientCommands.literal("list")
                                .executes(c -> routeList(c.getSource())))
                        .then(ClientCommands.literal("delete")
                                .then(ClientCommands.argument("name", StringArgumentType.greedyString())
                                        .executes(c -> routeDelete(c.getSource(),
                                                StringArgumentType.getString(c, "name"))))))
                .then(ClientCommands.literal("portal")
                        .executes(c -> portalStatus(c.getSource()))
                        .then(ClientCommands.literal("list")
                                .executes(c -> portalList(c.getSource())))
                        .then(ClientCommands.literal("clear")
                                .executes(c -> portalSet(c.getSource(), ""))
                                .then(ClientCommands.literal("all")
                                        .executes(c -> portalClearAll(c.getSource()))))
                        .then(ClientCommands.argument("file", StringArgumentType.greedyString())
                                .suggests((c, b) -> {
                                    dev.diego.diegoaddons.util.CustomImages.names().forEach(b::suggest);
                                    return b.buildFuture();
                                })
                                .executes(c -> portalSet(c.getSource(),
                                        StringArgumentType.getString(c, "file")))))
                .then(ClientCommands.literal("images")
                        .executes(c -> images(c.getSource())))
                .then(ClientCommands.literal("unblock")
                        .then(ClientCommands.argument("player", StringArgumentType.word())
                                .executes(c -> unblock(c.getSource(),
                                        StringArgumentType.getString(c, "player")))));
        dispatcher.register(cmd);
    }

    /** Prints every command, using whichever root name was typed so the examples are copyable. */
    private static int help(FabricClientCommandSource source, String root) {
        source.sendFeedback(Component.literal("§b§lDiegoAddons §7- commands"));
        for (Help h : HELP) {
            String usage = h.usage().isEmpty() ? "/" + root : "/" + root + " " + h.usage();
            source.sendFeedback(Component.literal("  §e" + usage + " §8- §7" + h.description()));
        }
        source.sendFeedback(Component.literal("§7Also available as §e/diego§7, and §e\\§7 opens the menu."));
        return 1;
    }

    /**
     * Asks for a version check by hand.
     *
     * <p>Works with the Auto Update module switched off, because typing the command is the asking -
     * but with it off the check only ever reports, never downloads. What the module's mode setting
     * decides is what happens <i>without</i> being asked.
     */
    private static int update(FabricClientCommandSource source) {
        var module = dev.diego.diegoaddons.module.modules.AutoUpdateModule.INSTANCE;
        boolean on = module != null && module.isEnabled();
        source.sendFeedback(Component.literal("§b[DiegoAddons] §fChecking for a new version… §7(you are on "
                + dev.diego.diegoaddons.util.Updater.currentVersion() + ")"));
        if (on) {
            module.checkNow();
        } else {
            dev.diego.diegoaddons.util.Updater.check(false, false, false, true, true);
        }
        return 1;
    }

    private static int espAdd(FabricClientCommandSource source, String text) {
        boolean added = CustomEsp.add(text);
        source.sendFeedback(Component.literal(added
                ? "§b[DiegoAddons] §fNow highlighting §e" + text
                : "§b[DiegoAddons] §e" + text + "§f is already on the list."));
        return 1;
    }

    private static int espRemove(FabricClientCommandSource source, String text) {
        boolean removed = CustomEsp.remove(text);
        source.sendFeedback(Component.literal(removed
                ? "§b[DiegoAddons] §fNo longer highlighting §e" + text
                : "§b[DiegoAddons] §e" + text + "§f was not on the list."));
        return 1;
    }

    /** Lists the terms, since they are otherwise only visible by their effect in the world. */
    private static int espList(FabricClientCommandSource source) {
        List<String> terms = CustomEsp.all();
        if (terms.isEmpty()) {
            source.sendFeedback(Component.literal(
                    "§b[DiegoAddons] §fNo highlight terms. Add one with §e/da esp add <text>§f."));
            return 1;
        }
        source.sendFeedback(Component.literal("§b[DiegoAddons] §fHighlighting " + terms.size() + ":"));
        for (String t : terms) {
            source.sendFeedback(Component.literal("  §7- §e" + t));
        }
        return 1;
    }

    private static int block(FabricClientCommandSource source, String player, String reason) {
        IgnoreList.block(player, reason);
        source.sendFeedback(Component.literal("§b[DiegoAddons] §fBlocked §e" + player
                + (reason.isBlank() ? "" : " §7(" + reason + ")")));
        return 1;
    }

    private static int unblock(FabricClientCommandSource source, String player) {
        boolean removed = IgnoreList.unblock(player);
        source.sendFeedback(Component.literal(removed
                ? "§b[DiegoAddons] §fUnblocked §e" + player
                : "§b[DiegoAddons] §e" + player + "§f was not blocked."));
        return 1;
    }

    private static int routeNew(FabricClientCommandSource source, String name) {
        boolean ok = MiningRoutes.create(name);
        source.sendFeedback(Component.literal(ok
                ? "§b[DiegoAddons] §fStarted route §e" + name.trim() + "§f. Add points with §e/da route add§f."
                : "§b[DiegoAddons] §fA route named §e" + name.trim() + "§f already exists."));
        return 1;
    }

    private static int routeAdd(FabricClientCommandSource source) {
        String active = MiningRoutes.active();
        if (active.isEmpty()) {
            source.sendFeedback(Component.literal(
                    "§b[DiegoAddons] §fNo route is being shown. Start one with §e/da route new <name>§f."));
            return 1;
        }
        var player = Minecraft.getInstance().player;
        if (player == null) {
            return 1;
        }
        // The block the player is standing on, not the exact decimal position, so the waypoint is a
        // clean block to walk to.
        MiningRoutes.addPoint(active, player.getBlockX(), player.getBlockY(), player.getBlockZ());
        MiningRoute r = MiningRoutes.get(active);
        source.sendFeedback(Component.literal("§b[DiegoAddons] §fAdded point §e" + r.points.size()
                + "§f to §e" + active + "§f."));
        return 1;
    }

    private static int routeUndo(FabricClientCommandSource source) {
        String active = MiningRoutes.active();
        boolean ok = !active.isEmpty() && MiningRoutes.undo(active);
        source.sendFeedback(Component.literal(ok
                ? "§b[DiegoAddons] §fRemoved the last point of §e" + active + "§f."
                : "§b[DiegoAddons] §fNothing to undo."));
        return 1;
    }

    private static int routeShow(FabricClientCommandSource source, String name) {
        if (MiningRoutes.get(name) == null) {
            source.sendFeedback(Component.literal("§b[DiegoAddons] §fNo route named §e" + name.trim() + "§f."));
            return 1;
        }
        MiningRoutes.setActive(MiningRoutes.get(name).name);
        source.sendFeedback(Component.literal("§b[DiegoAddons] §fNow showing §e" + name.trim() + "§f."));
        return 1;
    }

    private static int routeHide(FabricClientCommandSource source) {
        MiningRoutes.clearActive();
        source.sendFeedback(Component.literal("§b[DiegoAddons] §fHid the route."));
        return 1;
    }

    private static int routeList(FabricClientCommandSource source) {
        List<String> names = MiningRoutes.names();
        if (names.isEmpty()) {
            source.sendFeedback(Component.literal(
                    "§b[DiegoAddons] §fNo mining routes. Make one with §e/da route new <name>§f."));
            return 1;
        }
        source.sendFeedback(Component.literal("§b[DiegoAddons] §fRoutes:"));
        for (String n : names) {
            MiningRoute r = MiningRoutes.get(n);
            String mark = MiningRoutes.active().equalsIgnoreCase(n) ? " §a(shown)" : "";
            source.sendFeedback(Component.literal("  §7- §e" + n + " §7(" + r.points.size() + " pts)" + mark));
        }
        return 1;
    }

    private static int routeDelete(FabricClientCommandSource source, String name) {
        boolean ok = MiningRoutes.delete(name);
        source.sendFeedback(Component.literal(ok
                ? "§b[DiegoAddons] §fDeleted §e" + name.trim() + "§f."
                : "§b[DiegoAddons] §fNo route named §e" + name.trim() + "§f."));
        return 1;
    }

    /**
     * The portal in front of you, or a line saying why there is none.
     *
     * <p>The module has to be on for any of this: the portals it knows about are the ones its own
     * scan found, and with it off nothing has been scanned. Saying so is worth a line - "no portal
     * found" while standing in one would otherwise read as the feature being broken.
     */
    private static PortalImages.Pane aimedPortal(FabricClientCommandSource source) {
        var module = dev.diego.diegoaddons.module.modules.PortalImagesModule.INSTANCE;
        if (module == null || !module.isEnabled()) {
            source.sendFeedback(Component.literal(
                    "§b[DiegoAddons] §fTurn §ePortal Images§f on first §7(Render category)§f."));
            return null;
        }
        PortalImages.Pane pane = PortalImages.looking();
        if (pane == null) {
            source.sendFeedback(Component.literal(
                    "§b[DiegoAddons] §fNo portal in front of you. Stand facing one and try again."));
        }
        return pane;
    }

    /** Assigns an image to the portal being looked at; a blank file takes the image off. */
    private static int portalSet(FabricClientCommandSource source, String file) {
        PortalImages.Pane pane = aimedPortal(source);
        if (pane == null) {
            return 1;
        }
        if (file.isBlank()) {
            PortalImages.assign(pane, "");
            source.sendFeedback(Component.literal(
                    "§b[DiegoAddons] §fTook the image off the portal at §e" + pane.key() + "§f."));
            return 1;
        }
        PortalImages.assign(pane, file);
        boolean exists = CustomImages.get(file.trim()) != null;
        source.sendFeedback(Component.literal("§b[DiegoAddons] §fPut §e" + file.trim()
                + "§f on the portal at §e" + pane.key() + "§f."
                + (exists ? "" : " §cThat file is not in the images folder yet.")));
        return 1;
    }

    private static int portalStatus(FabricClientCommandSource source) {
        PortalImages.Pane pane = aimedPortal(source);
        if (pane == null) {
            return 1;
        }
        var module = dev.diego.diegoaddons.module.modules.PortalImagesModule.INSTANCE;
        String assigned = PortalImages.assignment(pane.key());
        source.sendFeedback(Component.literal("§b[DiegoAddons] §fPortal at §e" + pane.key()
                + "§f shows §e" + (assigned != null ? assigned : module.defaultImage() + " §7(the default)")
                + "§f. §7" + PortalImages.panes().size() + " portal(s) in range."));
        return 1;
    }

    private static int portalList(FabricClientCommandSource source) {
        var list = dev.diego.diegoaddons.config.ConfigManager.get().portalImages;
        if (list.isEmpty()) {
            source.sendFeedback(Component.literal("§b[DiegoAddons] §fNo portal has its own image yet. "
                    + "Stand in front of one and type §e/da portal <file>§f."));
            return 1;
        }
        source.sendFeedback(Component.literal("§b[DiegoAddons] §fPortal images:"));
        for (var p : list) {
            source.sendFeedback(Component.literal("  §7- §e" + p.key + " §8-> §e" + p.file));
        }
        return 1;
    }

    private static int portalClearAll(FabricClientCommandSource source) {
        int n = dev.diego.diegoaddons.config.ConfigManager.get().portalImages.size();
        PortalImages.clearAssignments();
        source.sendFeedback(Component.literal(
                "§b[DiegoAddons] §fCleared §e" + n + "§f portal image(s)."));
        return 1;
    }

    /** Lists the PNGs in the images folder and re-reads them, so an edited file takes effect. */
    private static int images(FabricClientCommandSource source) {
        Minecraft.getInstance().execute(CustomImages::reload);
        List<String> names = CustomImages.names();
        source.sendFeedback(Component.literal("§b[DiegoAddons] §fImages in §e"
                + CustomImages.folder() + "§f:"));
        if (names.isEmpty()) {
            source.sendFeedback(Component.literal("  §7- none yet; drop a .png in that folder"));
            return 1;
        }
        for (String n : names) {
            source.sendFeedback(Component.literal("  §7- §e" + n));
        }
        source.sendFeedback(Component.literal("§7Re-read from disk."));
        return 1;
    }

    /**
     * Opens a screen once the command has finished running. Setting it directly would fight with the
     * chat screen closing itself immediately afterwards, which would drop us back to the game.
     */
    /** Runs something on the client thread once the command has finished. */
    private static int run(Runnable action) {
        Minecraft.getInstance().execute(action);
        return 1;
    }

    /**
     * Opens one list's editor straight from a command.
     *
     * <p>Falls back to the settings menu rather than doing nothing, so a renamed id shows the place
     * the list lives instead of a command that silently fails.
     */
    private static int openList(String id) {
        return run(() -> {
            if (!DiegoAddonsV2Client.CONFIG.openList(id)) {
                DiegoAddonsV2Client.CONFIG.open();
            }
        });
    }

    private static int open(Supplier<Screen> screen) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> mc.setScreen(screen.get()));
        return 1;
    }

    /** Opens the settings menu, which configlib owns and draws from the module list. */
    private static int openMenu() {
        Minecraft.getInstance().execute(
                () -> dev.diego.diegoaddons.DiegoAddonsV2Client.CONFIG.open());
        return 1;
    }
}
