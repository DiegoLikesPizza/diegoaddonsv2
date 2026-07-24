package dev.diego.diegoaddons.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.diego.diegoaddons.module.modules.AnimationsModule;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Backs the size and position part of the {@link AnimationsModule}: offsets and scales the item in
 * first person.
 *
 * <p>The transform is wrapped in its own push/pop pair rather than applied in place. Both hands are
 * drawn through this method from a shared pose, so leaking the transform past the first call would
 * apply it twice to the off-hand. The pop runs on every return path, not just the last one.
 */
@Mixin(ItemInHandRenderer.class)
public class ItemInHandRendererMixin {
    private static final String RENDER_ARM =
            "renderArmWithItem(Lnet/minecraft/client/player/AbstractClientPlayer;FFLnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V";

    @Inject(method = RENDER_ARM, at = @At("HEAD"))
    private void diego$applyTransform(AbstractClientPlayer player, float partialTick, float pitch,
                                      InteractionHand hand, float swingProgress, ItemStack stack,
                                      float equipProgress, PoseStack pose,
                                      SubmitNodeCollector collector, int light, CallbackInfo ci) {
        pose.pushPose();
        AnimationsModule mod = AnimationsModule.INSTANCE;
        if (mod == null || !mod.isEnabled()) {
            return;
        }
        pose.translate(mod.x(), mod.y(), mod.z());
        float s = mod.scale();
        if (s != 1.0f) {
            pose.scale(s, s, s);
        }
    }

    @Inject(method = RENDER_ARM, at = @At("RETURN"))
    private void diego$restoreTransform(AbstractClientPlayer player, float partialTick, float pitch,
                                        InteractionHand hand, float swingProgress, ItemStack stack,
                                        float equipProgress, PoseStack pose,
                                        SubmitNodeCollector collector, int light, CallbackInfo ci) {
        pose.popPose();
    }
}
