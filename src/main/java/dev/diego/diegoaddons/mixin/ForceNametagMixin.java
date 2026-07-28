package dev.diego.diegoaddons.mixin;

import dev.diego.diegoaddons.module.modules.ForceNametagModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Backs the {@link ForceNametagModule}: the game hides a player's name plate while they are invisible
 * or sneaking. This forces it back on for exactly those two cases, so a hidden player (often a
 * SkyBlock NPC drawn as a player) still shows their tag. Everything else is left to the game.
 */
@Mixin(AvatarRenderer.class)
public class ForceNametagMixin {
    @Inject(method = "shouldShowName(Lnet/minecraft/world/entity/Avatar;D)Z", at = @At("HEAD"), cancellable = true)
    private void diego$forceName(Avatar entity, double distanceSq, CallbackInfoReturnable<Boolean> cir) {
        ForceNametagModule mod = ForceNametagModule.INSTANCE;
        if (mod == null || !mod.isEnabled()) {
            return;
        }
        if (entity.isInvisible() || entity.isCrouching()) {
            cir.setReturnValue(true);
            return;
        }
        // Your own tag is never drawn, because in first person you would be reading it from behind.
        // In third person you are looking at yourself, so there is a view to put it over.
        Minecraft mc = Minecraft.getInstance();
        if (mod.showOwn() && entity == mc.player && !mc.options.getCameraType().isFirstPerson()) {
            cir.setReturnValue(true);
        }
    }
}
