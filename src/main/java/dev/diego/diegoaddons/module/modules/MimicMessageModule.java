package dev.diego.diegoaddons.module.modules;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.Module;
import dev.diego.diegoaddons.util.DungeonState;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;

import java.util.Base64;

/**
 * Alerts you the moment the <b>Mimic</b> is killed on floors 6 and 7.
 *
 * <p>The Mimic replaces one secret chest with a disguised baby zombie; killing it is worth +2 bonus
 * dungeon score. There is no chat line for it, so it is found by the mob itself: a baby zombie
 * wearing the Mimic's fixed head skin. That skin's texture hash is constant, so the head's profile
 * is matched against it - the same signal the established mods use. The kill fires once per run,
 * even on an instant kill, because the disguised zombie is caught the first frame it appears dead.
 *
 * <p>The kill is also flagged on {@link DungeonState} to keep the score readout correct.
 */
public class MimicMessageModule extends Module {
    public static MimicMessageModule INSTANCE;

    /** The Mimic head's texture hash (the tail of its textures.minecraft.net URL). */
    private static final String TEXTURE = "e19c12543bc7792605ef68e1f8749ae8f2a381d9085d4d4b780ba1282d3597a0";

    private final BooleanSetting sound =
            new BooleanSetting(this, "sound", "Play a sound", true);
    private final BooleanSetting announceParty =
            new BooleanSetting(this, "announce", "Announce in party chat", false);

    private Entity mimic;         // the disguised zombie once spotted, held so its removal is seen
    private boolean firedThisRun; // one alert per dungeon run

    public MimicMessageModule() {
        super("mimicmessage", Category.DUNGEONS, "Mimic Message",
                "Tells you when the Mimic is killed on floors 6 and 7 (+2 bonus score).");
        settings.add(sound);
        settings.add(announceParty);
        INSTANCE = this;
    }

    /** Clear per-run state when leaving the world. */
    public void resetRun() {
        mimic = null;
        firedThisRun = false;
    }

    @Override
    public void onClientTick(Minecraft mc) {
        if (firedThisRun || mc.level == null || !DungeonState.inDungeons() || !DungeonState.mimicFloor()) {
            return;
        }
        // Find (or re-confirm) the disguised zombie. Holding the reference means a later removal is
        // still visible even after it drops out of the render list.
        if (mimic == null || mimic.isRemoved()) {
            for (Entity e : mc.level.entitiesForRendering()) {
                if (e instanceof Zombie z && z.isBaby() && isMimicHead(z.getItemBySlot(EquipmentSlot.HEAD))) {
                    mimic = z;
                    break;
                }
            }
        }
        if (mimic != null && (mimic.isRemoved() || (mimic instanceof Zombie z && z.isDeadOrDying()))) {
            fire(mc);
        }
    }

    private void fire(Minecraft mc) {
        firedThisRun = true;
        DungeonState.setMimicKilled();
        if (mc.player == null) {
            return;
        }
        if (sound.get()) {
            mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, 0.7f);
        }
        if (mc.gui != null) {
            mc.gui.getChat().addClientSystemMessage(
                    Component.literal("§b[DiegoAddons] §6Mimic killed! §7(+2 bonus score)"));
        }
        if (announceParty.get()) {
            mc.player.connection.sendCommand("pc Mimic Killed!");
        }
    }

    /** True if this head item is a player skull carrying the Mimic's texture. */
    private static boolean isMimicHead(ItemStack head) {
        if (head == null || head.isEmpty()) {
            return false;
        }
        ResolvableProfile rp = head.get(DataComponents.PROFILE);
        if (rp == null) {
            return false;
        }
        GameProfile profile = rp.partialProfile();
        if (profile == null) {
            return false;
        }
        for (Property property : profile.properties().get("textures")) {
            try {
                String decoded = new String(Base64.getDecoder().decode(property.value()));
                if (decoded.contains(TEXTURE)) {
                    return true;
                }
            } catch (IllegalArgumentException ignored) {
                // not valid base64; not the head we're after
            }
        }
        return false;
    }
}
