package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.HudModule;
import dev.diego.diegoaddons.util.Garden;
import dev.diego.diegoaddons.util.Pests;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The pest traps: how many are out, which are full, and which have run out of bait.
 *
 * <p>Both failure states are silent and both stop the trap earning anything - a full trap catches
 * nothing more until it is emptied, and one without bait catches nothing at all. Hypixel prints all
 * three numbers into the tab list; this puts them where you will see them, and says so once when a
 * trap goes bad rather than leaving you to notice.
 *
 * <p>Needs the <b>Pest Traps</b> widget in {@code /widget}, the same requirement the pest cooldown
 * has and for the same reason - see {@link Pests}.
 */
public class PestTrapsModule extends HudModule {
    public static PestTrapsModule INSTANCE;

    private final BooleanSetting hideWhenFine =
            new BooleanSetting(this, "hideWhenFine", "Hide while every trap is fine", false);
    private final BooleanSetting warnFull =
            new BooleanSetting(this, "warnFull", "Warn when a trap fills", true);
    private final BooleanSetting warnBait =
            new BooleanSetting(this, "warnBait", "Warn when a trap runs out of bait", true);
    private final BooleanSetting warnTitle =
            new BooleanSetting(this, "warnTitle", "Warning as a title", true);
    private final BooleanSetting warnChat =
            new BooleanSetting(this, "warnChat", "Warning in chat", true);
    private final BooleanSetting warnSound =
            new BooleanSetting(this, "warnSound", "Play a sound", true);

    /** Traps already announced, so a state that persists for an hour is said once and not per tick. */
    private final Set<Integer> announcedFull = new HashSet<>();
    private final Set<Integer> announcedBait = new HashSet<>();

    public PestTrapsModule() {
        super("pesttraps", Category.GARDEN, "Pest Traps",
                "Trap count, full traps and empty bait, with a warning when one goes bad.");
        settings.add(hideWhenFine);
        settings.add(warnFull);
        settings.add(warnBait);
        settings.add(warnTitle);
        settings.add(warnChat);
        settings.add(warnSound);
        INSTANCE = this;
    }

    @Override
    protected String label() {
        return "Traps";
    }

    @Override
    protected String value(Minecraft mc) {
        return null;   // up to three lines; see hudLines
    }

    @Override
    protected String sampleValue() {
        return "2/3";
    }

    @Override
    public List<String> hudLines(Minecraft mc) {
        if (!Pests.inGarden() || Garden.trapsPlaced() < 0) {
            return List.of();
        }
        boolean trouble = !Garden.fullTraps().isEmpty() || !Garden.noBaitTraps().isEmpty();
        if (hideWhenFine.get() && !trouble) {
            return List.of();
        }
        List<String> out = new ArrayList<>(3);
        String count = Garden.trapsPlaced() + "/" + Garden.trapsMax();
        out.add(showLabel() ? "Traps: " + count : count);
        if (!Garden.fullTraps().isEmpty()) {
            out.add("Full: " + numbers(Garden.fullTraps()));
        }
        if (!Garden.noBaitTraps().isEmpty()) {
            out.add("No bait: " + numbers(Garden.noBaitTraps()));
        }
        return out;
    }

    private static String numbers(Set<Integer> traps) {
        return traps.stream().map(n -> "#" + n).collect(Collectors.joining(", "));
    }

    @Override
    public void onClientTick(Minecraft mc) {
        if (mc.player == null || !Pests.inGarden() || Garden.trapsPlaced() < 0) {
            return;
        }
        if (warnFull.get()) {
            announce(mc, Garden.fullTraps(), announcedFull, "are full", "TRAPS FULL");
        }
        if (warnBait.get()) {
            announce(mc, Garden.noBaitTraps(), announcedBait, "are out of bait", "TRAPS EMPTY");
        }
        // A trap that has been emptied or re-baited drops out, which is what lets it warn again.
        announcedFull.retainAll(Garden.fullTraps());
        announcedBait.retainAll(Garden.noBaitTraps());
    }

    /**
     * Announces the traps that have just gone bad, on the same three channels as the pest timer's
     * swap warning - title, chat and sound.
     *
     * <p>Batched into one warning rather than one per trap: three traps filling within a second of
     * each other are one thing that happened, and three titles in a row would show you only the
     * last. Each trap is still marked individually, so a fourth filling later warns on its own.
     */
    private void announce(Minecraft mc, Set<Integer> traps, Set<Integer> already, String what,
                          String headline) {
        List<Integer> fresh = new ArrayList<>();
        for (int trap : traps) {
            if (already.add(trap)) {
                fresh.add(trap);
            }
        }
        if (fresh.isEmpty()) {
            return;
        }
        String which = numbers(new java.util.LinkedHashSet<>(fresh));
        String detail = (fresh.size() == 1 ? "Trap " : "Traps ") + which + " " + what;

        if (warnTitle.get() && mc.gui != null) {
            mc.gui.setTitle(Component.literal("§6§l" + headline));
            mc.gui.setSubtitle(Component.literal("§e" + detail));
        }
        if (warnChat.get() && mc.gui != null) {
            mc.gui.getChat().addClientSystemMessage(
                    Component.literal("§b[DiegoAddons] §f" + detail + "."));
        }
        if (warnSound.get() && mc.player != null) {
            mc.player.playSound(SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(), 1.0f, 0.8f);
        }
    }
}
