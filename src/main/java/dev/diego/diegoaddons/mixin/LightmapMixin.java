package dev.diego.diegoaddons.mixin;

import dev.diego.diegoaddons.module.modules.FullbrightModule;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Backs {@link FullbrightModule} by lighting the lightmap itself.
 *
 * <p>Driving the brightness slider was never going to do this. That value reaches the lightmap
 * shader as {@code BrightnessFactor}, which only mixes the finished colour towards a slightly
 * lifted curve - and the option refuses anything above {@code 1.0} anyway, so the setting was
 * already at its ceiling while a cave stayed a cave.
 *
 * <p>What the shader actually starts from is the ambient colour: every texel begins at
 * {@code max(AmbientColor, nightVision)} before sky and block light are added on top. Setting that
 * to white means every light level resolves fully lit, which is what fullbright means. The darkness
 * effect and the boss overlay are cleared as well, since both subtract from the finished colour and
 * would otherwise still be able to dim the world.
 *
 * <p>The lightmap is a 16x16 texture that is only re-uploaded when it is marked dirty, so the flag
 * is forced while this is on - and once more on the way out, or the world would stay lit after the
 * module was switched off.
 */
@Mixin(LightmapRenderStateExtractor.class)
public class LightmapMixin {
    @Inject(method = "extract", at = @At("RETURN"))
    private void diego$fullbright(LightmapRenderState state, float partialTick, CallbackInfo ci) {
        FullbrightModule mod = FullbrightModule.INSTANCE;
        if (mod == null) {
            return;
        }
        if (!mod.isEnabled()) {
            if (FullbrightModule.consumeDirty()) {
                state.needsUpdate = true;   // one last refresh, to put the dark back
            }
            return;
        }
        // At full strength this is plain white, which is what fullbright means. Below that the
        // world's own ambient is mixed back in, so a cave is lit but still reads as a cave.
        float strength = mod.strength();
        state.ambientColor = strength >= 1f
                ? LightmapRenderStateExtractor.WHITE
                : new org.joml.Vector3f(state.ambientColor)
                        .lerp(LightmapRenderStateExtractor.WHITE, strength);
        if (mod.removeDarkness()) {
            state.darknessEffectScale = 0f;
        }
        if (mod.removeBossDim()) {
            state.bossOverlayWorldDarkening = 0f;
        }
        state.needsUpdate = true;
    }
}
