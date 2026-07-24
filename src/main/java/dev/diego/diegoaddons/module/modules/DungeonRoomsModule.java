package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.HudModule;
import dev.diego.diegoaddons.util.DungeonRooms;
import net.minecraft.client.Minecraft;

/**
 * Shows which dungeon room you are standing in and what kind it is, read from the dungeon map.
 *
 * <p>Hides itself outside a dungeon and in the boss room, where there is no room grid to speak of.
 */
public class DungeonRoomsModule extends HudModule {
    private final BooleanSetting showTile =
            new BooleanSetting(this, "tile", "Show grid position", false);

    public DungeonRoomsModule() {
        super("dungeonrooms", "Dungeon Rooms", "Shows the dungeon room you are standing in.");
        settings.add(showTile);
    }

    @Override
    public void onClientTick(Minecraft mc) {
        DungeonRooms.tick(mc);
    }

    @Override
    protected String label() {
        return "Room";
    }

    @Override
    protected String value(Minecraft mc) {
        DungeonRooms.RoomType room = DungeonRooms.currentRoom();
        if (room == null) {
            return null;   // not in a room worth naming - the chip hides itself
        }
        if (!showTile.get()) {
            return room.display;
        }
        return room.display + " (" + DungeonRooms.tileX() + ", " + DungeonRooms.tileZ() + ")";
    }

    @Override
    protected String sampleValue() {
        return "Puzzle";
    }
}
