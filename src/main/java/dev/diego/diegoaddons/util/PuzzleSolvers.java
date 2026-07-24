package dev.diego.diegoaddons.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.diego.diegoaddons.DiegoAddonsV2Client;
import dev.diego.diegoaddons.module.modules.PuzzleSolversModule;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
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

    /** The NPC whose line only a truth-teller could say is the one holding the reward. */
    private static void weirdos(PuzzleSolversModule mod, String msg) {
        var m = NPC_LINE.matcher(msg);
        if (!m.matches()) {
            return;
        }
        String npc = m.group(1).trim();
        String said = m.group(2).trim();
        for (Pattern p : WEIRDOS_TRUTH) {
            if (p.matcher(said).matches()) {
                announce(mod, "Weirdos: reward is in " + npc + "'s chest");
                return;
            }
        }
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

    /** Clears per-dungeon state. */
    public static void reset() {
        currentAnswers = null;
        lastAnnounced = "";
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
