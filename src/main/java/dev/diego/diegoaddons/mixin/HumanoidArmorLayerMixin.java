package dev.diego.diegoaddons.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.diego.diegoaddons.module.modules.ArmorHiderModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * Backs the {@link ArmorHiderModule}: cancels the humanoid armour layer for players so they render
 * without their worn armour. Only real players are affected - being drawn as a player is not enough,
 * because SkyBlock builds a great many of its mobs that way (see {@link #isRealPlayer}). Self vs.
 * others is decided from the render state's entity id.
 */
@Mixin(HumanoidArmorLayer.class)
public class HumanoidArmorLayerMixin {
    @Inject(
            method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/HumanoidRenderState;FF)V",
            at = @At("HEAD"),
            cancellable = true)
    private void diego$hideArmor(PoseStack pose, SubmitNodeCollector collector, int light,
                                 HumanoidRenderState state, float limbSwing, float limbSwingAmount,
                                 CallbackInfo ci) {
        ArmorHiderModule mod = ArmorHiderModule.INSTANCE;
        if (mod == null || !mod.isEnabled()) {
            return;
        }
        if (!(state instanceof AvatarRenderState avatar)) {
            return; // only hide armour on players, not on other humanoid mobs
        }
        Minecraft mc = Minecraft.getInstance();
        boolean self = mc.player != null && avatar.id == mc.player.getId();
        if (!self && !isRealPlayer(mc, avatar.id)) {
            return;   // a SkyBlock mob wearing a player model - its armour is how you recognise it
        }
        if (mod.hides(self)) {
            ci.cancel();
        }
    }

    /**
     * Whether an entity id belongs to a real player rather than one of SkyBlock's mobs, which are
     * very often player entities in armour - dungeon mobs, slayer bosses, half the island's NPCs.
     * Hiding their armour is not cosmetic there, it takes away how you tell them apart.
     *
     * <p>Two things separate them, and both are needed. A real account has a version-4 (random)
     * UUID; the server makes its mobs up with version-2 ones. And a real player has an entry in the
     * player list, which the mobs are not given.
     */
    private static boolean isRealPlayer(Minecraft mc, int entityId) {
        if (mc.level == null || mc.getConnection() == null) {
            return false;
        }
        if (!(mc.level.getEntity(entityId) instanceof Player player)) {
            return false;
        }
        UUID uuid = player.getUUID();
        return uuid.version() == 4 && mc.getConnection().getPlayerInfo(uuid) != null;
    }
}
