package dev.diego.diegoaddons.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.diego.diegoaddons.module.modules.ArmorHiderModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Second half of the {@link ArmorHiderModule}: hides head-slot items that are not armour models.
 *
 * <p>SkyBlock helmets are very often player heads rather than real armour, and those never reach the
 * humanoid armour layer - the game draws anything worn on the head through this layer instead. So
 * hiding armour alone left every skull helmet sitting on the player, which is what this fixes.
 *
 * <p>Restricted to players for the same reason as the armour layer: a pumpkin on a mob is not
 * somebody's armour.
 */
@Mixin(CustomHeadLayer.class)
public class CustomHeadLayerMixin {
    @Inject(
            method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;FF)V",
            at = @At("HEAD"),
            cancellable = true)
    private void diego$hideHeadItem(PoseStack pose, SubmitNodeCollector collector, int light,
                                    LivingEntityRenderState state, float limbSwing, float limbSwingAmount,
                                    CallbackInfo ci) {
        ArmorHiderModule mod = ArmorHiderModule.INSTANCE;
        if (mod == null || !mod.isEnabled()) {
            return;
        }
        if (!(state instanceof AvatarRenderState avatar)) {
            return;   // only players, not every mob wearing something on its head
        }
        Minecraft mc = Minecraft.getInstance();
        boolean self = mc.player != null && avatar.id == mc.player.getId();
        if (mod.hides(self)) {
            ci.cancel();
        }
    }
}
