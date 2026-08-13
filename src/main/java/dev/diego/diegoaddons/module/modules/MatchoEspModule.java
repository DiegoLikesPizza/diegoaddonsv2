package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.HuntingEspModule;

import java.util.Locale;

/**
 * Boxes Matchos - the humanoid mob that appears on the Crimson Isle after the Blazing Volcano erupts.
 *
 * <p>The only hunting mob here that is a SkyBlock <i>mob</i> rather than an animal: it is a costumed
 * entity wearing a name plate, so it is found the way every other plated mob in the mod is - see
 * {@link dev.diego.diegoaddons.util.EntityEsp}. The plate reads something like
 * {@code [Lv100] Matcho 750k/750k}, so the name is matched by {@code contains} rather than by
 * equality, and case-insensitively for the same reason the dungeon minibosses are.
 */
public class MatchoEspModule extends HuntingEspModule {
    public MatchoEspModule() {
        super("matchoesp", "Matcho ESP", "Box Matchos on the Crimson Isle.",
                0xFFFFEB3B, "the Crimson Isle", CRIMSON);
    }

    @Override
    public boolean matchesPlate(String plate) {
        return plate.toLowerCase(Locale.ROOT).contains("matcho");
    }
}
