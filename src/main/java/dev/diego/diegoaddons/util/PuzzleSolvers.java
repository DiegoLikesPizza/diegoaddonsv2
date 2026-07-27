package dev.diego.diegoaddons.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.diego.diegoaddons.DiegoAddonsV2Client;
import dev.diego.diegoaddons.module.modules.PuzzleSolversModule;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Solvers for the dungeon puzzles that can be answered from chat alone.
 *
 * <p>Both puzzles here are decided by what the NPCs say, so the solver reads the messages and tells
 * you the answer in chat. That deliberately needs neither 3-D world rendering nor knowing which room
 * you are in - the two things every other dungeon puzzle solver depends on.
 *
 * <ul>
 *   <li><b>Quiz (Oruo)</b> - the question is matched against a table of answers, then the ⓐ/ⓑ/ⓒ
 *       option whose text is a known answer is called out.</li>
 *   <li><b>Three Weirdos</b> - each NPC's line is either one only a truth-teller would say or one a
 *       liar would; the NPC who said a truthful line is the one with the reward.</li>
 * </ul>
 *
 * <p>The answer table and the two line lists follow Odin (BSD-3-Clause, © odtheking and
 * contributors); see the credits in the README.
 */
public final class PuzzleSolvers {
    /** Lines only the NPC holding the reward can truthfully say. */
    private static final Pattern[] WEIRDOS_TRUTH = {
            Pattern.compile("The reward is not in my chest!"),
            Pattern.compile("At least one of them is lying, and the reward is not in .+'s chest\\.?"),
            Pattern.compile("My chest doesn't have the reward\\. We are all telling the truth\\.?"),
            Pattern.compile("My chest has the reward and I'm telling the truth!"),
            Pattern.compile("The reward isn't in any of our chests\\.?"),
            Pattern.compile("Both of them are telling the truth\\. Also, .+ has the reward in their chest\\.?"),
    };

    /** Lines a lying NPC says - the chest each one names (or its own) is not the reward. */
    private static final Pattern[] WEIRDOS_LIE = {
            Pattern.compile("One of us is telling the truth!"),
            Pattern.compile("They are both telling the truth\\. The reward isn't in .+'s chest\\.?"),
            Pattern.compile("We are all telling the truth!"),
            Pattern.compile(".+ is telling the truth and the reward is in his chest\\.?"),
            Pattern.compile("My chest doesn't have the reward\\. At least one of the others is telling the truth!"),
            Pattern.compile("One of the others is lying\\.?"),
            Pattern.compile("They are both telling the truth, the reward is in .+'s chest\\.?"),
            Pattern.compile("They are both lying, the reward is in my chest!"),
            Pattern.compile("The reward is in my chest\\.?"),
            Pattern.compile("The reward is not in my chest\\. They are both lying\\.?"),
            Pattern.compile(".+ is telling the truth\\.?"),
            Pattern.compile("My chest has the reward\\.?"),
    };

    /** The three answer buttons in the Quiz room, relative to the room, in ⓐ/ⓑ/ⓒ order. */
    private static final BlockPos[] QUIZ_BUTTONS = {
            new BlockPos(20, 70, 6), new BlockPos(15, 70, 9), new BlockPos(10, 70, 6),
    };

    private static final int GREEN = 0xFF00FF00;
    private static final int RED = 0xFFFF3333;
    private static final double EDGE = 0.06;

    /** ⓐ/ⓑ/ⓒ index of the correct Quiz option, or -1 until it is known. */
    private static int correctOption = -1;
    /** Each spoken Weirdo, by name -> {NPC x, NPC z, 1 if it holds the reward else 0}. The chest is
     * worked out from this every tick, so it is right even before the room's rotation had resolved
     * when the NPC first spoke. */
    private static final Map<String, double[]> weirdosNpc = new LinkedHashMap<>();

    /** "[NPC] Name: message" - how the puzzle NPCs speak. */
    private static final Pattern NPC_LINE = Pattern.compile("^\\[NPC] ([^:]+): (.+)$");
    /** "[STATUE] Oruo the Omniscient: ..." */
    private static final String ORUO = "[STATUE] Oruo the Omniscient:";
    /** The three option markers Oruo lists answers with. */
    private static final char[] OPTIONS = {'ⓐ', 'ⓑ', 'ⓒ'};

    private static Map<String, List<String>> answers;
    /** Answers for the question currently being asked, or null between questions. */
    private static List<String> currentAnswers;
    /** Guard so the same solution is not announced twice. */
    private static String lastAnnounced = "";

    private PuzzleSolvers() {
    }

    /** Loads the answer table on first use. */
    private static Map<String, List<String>> answers() {
        if (answers != null) {
            return answers;
        }
        answers = new HashMap<>();
        Identifier id = Identifier.fromNamespaceAndPath(DiegoAddonsV2Client.MOD_ID, "puzzles/quiz_answers.json");
        try {
            var resource = Minecraft.getInstance().getResourceManager().getResource(id);
            if (resource.isPresent()) {
                try (InputStream in = resource.get().open();
                     InputStreamReader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
                    for (var e : root.entrySet()) {
                        List<String> list = new ArrayList<>();
                        for (JsonElement v : e.getValue().getAsJsonArray()) {
                            list.add(v.getAsString());
                        }
                        answers.put(e.getKey(), list);
                    }
                }
            }
        } catch (Exception e) {
            DiegoAddonsV2Client.LOGGER.warn("[DiegoAddons] Could not load quiz answers: {}", e.toString());
        }
        return answers;
    }

    /** Called for every incoming system message. */
    public static void onMessage(String plain) {
        PuzzleSolversModule mod = PuzzleSolversModule.INSTANCE;
        if (mod == null || !mod.isEnabled()) {
            return;
        }
        String msg = plain.trim();
        if (mod.quiz()) {
            quiz(mod, msg);
        }
        if (mod.weirdos()) {
            weirdos(mod, msg);
        }
    }

    /**
     * Oruo asks a question, then lists three options. The question sets which answers are correct;
     * the option carrying one of them is the one to click.
     */
    private static void quiz(PuzzleSolversModule mod, String msg) {
        if (msg.startsWith(ORUO) && msg.endsWith("correctly!")) {
            currentAnswers = null;   // moved on to the next question
            correctOption = -1;
            return;
        }

        // An option line, e.g. "ⓑ Elle" - only useful once the question has been seen.
        if (!msg.isEmpty() && currentAnswers != null) {
            for (int i = 0; i < OPTIONS.length; i++) {
                if (msg.charAt(0) != OPTIONS[i]) {
                    continue;
                }
                for (String answer : currentAnswers) {
                    if (msg.endsWith(answer)) {
                        correctOption = i;
                        announce(mod, "Quiz: " + OPTIONS[i] + " " + answer);
                        return;
                    }
                }
                return;
            }
        }

        for (var e : answers().entrySet()) {
            if (msg.contains(e.getKey())) {
                currentAnswers = e.getValue();
                return;
            }
        }
    }

    /**
     * The NPC whose line only a truth-teller could say is the one holding the reward; the ones whose
     * line marks a chest empty are wrong. Each NPC's chest is found from where the NPC is standing.
     */
    private static void weirdos(PuzzleSolversModule mod, String msg) {
        var m = NPC_LINE.matcher(msg);
        if (!m.matches()) {
            return;
        }
        String npc = m.group(1).trim();
        String said = m.group(2).trim();

        boolean truth = matchesAny(WEIRDOS_TRUTH, said);
        boolean lie = !truth && matchesAny(WEIRDOS_LIE, said);
        // Record every weirdo that speaks while in the room, not only the ones whose exact line is in
        // the tables - otherwise a phrasing we don't have leaves that chest unmarked. Outside the room
        // we still require a known line, so stray [NPC] chatter elsewhere is never picked up.
        boolean inRoom = "Three Weirdos".equals(DungeonRooms.currentRoomName());
        if (!truth && !lie && !inRoom) {
            return;
        }
        double[] pos = npcPos(npc);
        if (pos == null) {
            return;
        }
        weirdosNpc.put(npc, new double[]{pos[0], pos[1], truth ? 1 : 0});
        if (truth) {
            announce(mod, "Weirdos: reward is in " + npc + "'s chest");
        }
    }

    /** Whether the reward chest has been pinned down, so highlights are meaningful. */
    private static boolean weirdosSolved() {
        for (double[] v : weirdosNpc.values()) {
            if (v[2] == 1) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesAny(Pattern[] patterns, String s) {
        for (Pattern p : patterns) {
            if (p.matcher(s).matches()) {
                return true;
            }
        }
        return false;
    }

    /** The world x/z of the armour-stand NPC with this name, or null if it isn't loaded. */
    private static double[] npcPos(String npc) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return null;
        }
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof ArmorStand && e.hasCustomName()
                    && LegacyText.strip(e.getCustomName().getString()).trim().equals(npc)) {
                return new double[]{e.getX(), e.getZ()};
            }
        }
        return null;
    }

    /** The chest block tied to a Weirdo NPC standing at (x, z), turned to the room's rotation. */
    private static BlockPos weirdosChest(double x, double z) {
        BlockPos rel = DungeonRooms.toRecorded(new BlockPos((int) x - 1, 69, (int) z - 1));
        return rel == null ? null : DungeonRooms.toWorld(rel.offset(1, 0, 0));
    }

    /** Prints a solution once, either to yourself or to the party. */
    private static void announce(PuzzleSolversModule mod, String text) {
        if (text.equalsIgnoreCase(lastAnnounced)) {
            return;
        }
        lastAnnounced = text;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui != null) {
            mc.gui.getChat().addClientSystemMessage(Component.literal("§b[DiegoAddons] §a" + text));
        }
        if (mod.announceToParty() && mc.player != null) {
            mc.player.connection.sendCommand("pc " + text);
        }
    }

    /**
     * Draws the world highlights each tick: the correct Quiz answer's block green, and in Three
     * Weirdos the reward chest green with the ruled-out chests red. Chat still calls the answer out;
     * this is the same answer, placed on the block you actually click.
     */
    public static void tick(Minecraft mc) {
        PuzzleSolversModule mod = PuzzleSolversModule.INSTANCE;
        if (mod == null || !mod.isEnabled() || mc.level == null) {
            return;
        }
        String room = DungeonRooms.currentRoomName();
        if (mod.quiz() && "Quiz".equals(room) && correctOption >= 0 && correctOption < QUIZ_BUTTONS.length) {
            BlockPos w = DungeonRooms.toWorld(QUIZ_BUTTONS[correctOption]);
            if (w != null) {
                // The button block, plus the block it sits on, so both read as the answer.
                WorldRender.thickBox(new AABB(w.getX(), w.getY() - 1, w.getZ(),
                        w.getX() + 1, w.getY() + 1, w.getZ() + 1), GREEN, EDGE, true);
            }
        }
        // Only highlight once the reward chest is known: the green one is the answer and every other
        // recorded weirdo chest is ruled out red. Until then the chat line is the only output.
        if (mod.weirdos() && "Three Weirdos".equals(room) && weirdosSolved()) {
            for (double[] v : weirdosNpc.values()) {
                BlockPos chest = weirdosChest(v[0], v[1]);
                if (chest != null) {
                    WorldRender.thickBox(new AABB(chest), v[2] == 1 ? GREEN : RED, EDGE, true);
                }
            }
        }
    }

    /** Clears per-dungeon state. */
    public static void reset() {
        currentAnswers = null;
        lastAnnounced = "";
        correctOption = -1;
        weirdosNpc.clear();
    }

    /**
     * The colour an Oruo answer line should be tinted - green for the right answer, red for a wrong
     * one - or 0 to leave it alone. Used to recolour the options in chat as Oruo lists them.
     */
    public static int quizOptionColor(String plain) {
        PuzzleSolversModule mod = PuzzleSolversModule.INSTANCE;
        if (mod == null || !mod.isEnabled() || !mod.quiz() || currentAnswers == null || plain.isEmpty()) {
            return 0;
        }
        char c = plain.charAt(0);
        if (c != 'ⓐ' && c != 'ⓑ' && c != 'ⓒ') {
            return 0;
        }
        for (String answer : currentAnswers) {
            if (plain.endsWith(answer)) {
                return 0x55FF55;   // correct
            }
        }
        return 0xFF5555;   // wrong
    }

    /** Exposed so the module can report how many answers are known. */
    public static int knownQuestions() {
        return answers().size();
    }

    /** Lower-cased helper kept for future solvers that need loose matching. */
    static String norm(String s) {
        return s.toLowerCase(Locale.ROOT).trim();
    }
}
