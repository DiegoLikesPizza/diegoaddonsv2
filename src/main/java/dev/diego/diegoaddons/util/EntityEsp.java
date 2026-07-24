package dev.diego.diegoaddons.util;

import dev.diego.diegoaddons.module.modules.CustomEspModule;
import dev.diego.diegoaddons.module.modules.StarredMobEspModule;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Boxes drawn around entities: dungeon starred mobs, and anything the user has named themselves.
 *
 * <p>SkyBlock mobs are ordinary vanilla entities wearing a name plate, so both features work the
 * same way - find the plate, then box the mob it belongs to. A plate is an armour stand riding the
 * mob, which is why the box is dropped back down to where the body actually is.
 */
public final class EntityEsp {
    /** The marker SkyBlock puts on dungeon starred mobs. */
    private static final String STAR = "\u272F";
    private static final double RANGE = 64.0;
    private static final double EDGE = 0.05;

    private EntityEsp() {
    }

    /** Called every client tick while either feature is on. */
    public static void tick(Minecraft mc) {
        StarredMobEspModule starred = StarredMobEspModule.INSTANCE;
        CustomEspModule custom = CustomEspModule.INSTANCE;
        boolean doStarred = starred != null && starred.isEnabled();
        boolean doCustom = custom != null && custom.isEnabled() && !CustomEsp.all().isEmpty();
        if (!doStarred && !doCustom || mc.player == null || mc.level == null) {
            return;
        }

        AABB area = mc.player.getBoundingBox().inflate(RANGE);
        for (Entity e : mc.level.getEntities(mc.player, area)) {
            if (!(e instanceof ArmorStand stand) || !stand.hasCustomName()) {
                continue;
            }
            String name = LegacyText.strip(stand.getCustomName().getString());

            if (doStarred && name.contains(STAR) && !starred.hideDead(name)) {
                box(stand, starred.color());
            } else if (doCustom) {
                String match = CustomEsp.match(name);
                if (match != null) {
                    box(stand, custom.color());
                }
            }
        }
    }

    /** The plate floats above its mob, so the box is placed on the body below it. */
    private static void box(ArmorStand stand, int color) {
        AABB box = new AABB(
                stand.getX() - 0.45, stand.getY() - 2.1, stand.getZ() - 0.45,
                stand.getX() + 0.45, stand.getY() - 0.3, stand.getZ() + 0.45);
        WorldRender.thickBox(box, color, EDGE, true);
    }
}
