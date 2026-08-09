package dev.diego.diegoaddons.util;

import dev.diego.diegoaddons.module.modules.DoorKeyEspModule;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Highlights the dungeon's <b>wither and blood doors</b> and their <b>keys</b>.
 *
 * <p>Doors sit at the openings between rooms: the block in that opening is a coal block for a wither
 * door and red terracotta for a blood door, so the whole 6x6 grid's openings are probed for those.
 * Keys are the floating markers named "Wither Key" / "Blood Key"; each is boxed and given a tracer
 * from your eyes so you can grab it fast. Only runs in a dungeon.
 */
public final class DoorKeyEsp {
    private static final int DOOR_Y = 69;
    private static final double EDGE = 0.05;
    private static final double KEY_RANGE = 48.0;

    // Filled rather than outlined, and in the doors' own colours: a wither door is black and a
    // blood door is red, which is how they read in the world, so the ESP says the same thing louder
    // rather than recolouring it into something you then have to translate.
    private static final int WITHER_DOOR = 0x66000000;
    private static final int BLOOD_DOOR = 0x66FF0000;
    private static final int WITHER_KEY = 0xFFE0E0E0;
    private static final int BLOOD_KEY = 0xFFFF3030;

    private DoorKeyEsp() {
    }

    /** Called every client tick while the module is on. */
    public static void tick(Minecraft mc) {
        DoorKeyEspModule mod = DoorKeyEspModule.INSTANCE;
        if (mod == null || !mod.isEnabled() || mc.level == null || mc.player == null
                || !DungeonState.inDungeons()) {
            return;
        }
        if (mod.witherDoors() || mod.bloodDoors()) {
            doors(mc, mod);
        }
        if (mod.witherKey() || mod.bloodKey()) {
            keys(mc, mod);
        }
    }

    /** Probes every opening in the 6x6 room grid for a wither or blood door. */
    private static void doors(Minecraft mc, DoorKeyEspModule mod) {
        // Openings between horizontally-adjacent rooms: 16 blocks east of a room centre.
        for (int tz = 0; tz < 6; tz++) {
            for (int tx = 0; tx < 5; tx++) {
                door(mc, mod, tx * 32 - 169, tz * 32 - 185, true);
            }
        }
        // Openings between vertically-adjacent rooms: 16 blocks south of a room centre.
        for (int tx = 0; tx < 6; tx++) {
            for (int tz = 0; tz < 5; tz++) {
                door(mc, mod, tx * 32 - 185, tz * 32 - 169, false);
            }
        }
    }

    private static void door(Minecraft mc, DoorKeyEspModule mod, int x, int z, boolean horizontal) {
        Block block = mc.level.getBlockState(new BlockPos(x, DOOR_Y, z)).getBlock();
        int color;
        if (block == Blocks.COAL_BLOCK && mod.witherDoors()) {
            color = WITHER_DOOR;
        } else if (block == Blocks.RED_TERRACOTTA && mod.bloodDoors()) {
            color = BLOOD_DOOR;
        } else {
            return;
        }
        // A door is a 3-wide, ~4-tall opening; box the whole opening, oriented to the wall it is in.
        AABB box = horizontal
                ? new AABB(x, DOOR_Y, z - 1, x + 1, DOOR_Y + 4, z + 2)
                : new AABB(x - 1, DOOR_Y, z, x + 2, DOOR_Y + 4, z + 1);
        WorldRender.filledBox(box, color, true);
    }

    private static void keys(Minecraft mc, DoorKeyEspModule mod) {
        AABB area = mc.player.getBoundingBox().inflate(KEY_RANGE);
        Vec3 eye = mc.player.getEyePosition();
        for (Entity e : mc.level.getEntities(mc.player, area)) {
            if (!(e instanceof ArmorStand) || !e.hasCustomName()) {
                continue;
            }
            String name = LegacyText.strip(e.getCustomName().getString()).trim();
            int color;
            if (name.equals("Wither Key") && mod.witherKey()) {
                color = WITHER_KEY;
            } else if (name.equals("Blood Key") && mod.bloodKey()) {
                color = BLOOD_KEY;
            } else {
                continue;
            }
            AABB box = new AABB(e.getX() - 0.4, e.getY() + 0.8, e.getZ() - 0.4,
                    e.getX() + 0.4, e.getY() + 1.6, e.getZ() + 0.4);
            WorldRender.thickBox(box, color, EDGE, true);
            WorldRender.line(eye, e.position().add(0, 1.2, 0), color);
        }
    }
}
