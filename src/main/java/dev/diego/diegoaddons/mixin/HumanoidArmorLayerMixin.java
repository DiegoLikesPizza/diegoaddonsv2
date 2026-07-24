package dev.diego.diegoaddons.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.diego.diegoaddons.module.modules.ArmorHiderModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Backs the {@link ArmorHiderModule}: cancels the humanoid armour layer for players so they render
 * without their worn armour. Only players are affected ({@link AvatarRenderState}); other armoured
 * mobs are left untouched. Self vs. others is decided from the render state's entity id.
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
        if (mod.hides(self)) {
            ci.cancel();
        }
    }
}
