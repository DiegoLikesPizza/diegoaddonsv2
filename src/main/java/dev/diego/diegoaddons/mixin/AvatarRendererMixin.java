package dev.diego.diegoaddons.mixin;

import dev.diego.diegoaddons.module.modules.SkinChangerModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Backs the {@link SkinChangerModule}: rewrites the body-skin texture a player is drawn with. The
 * player being rendered is identified from the render state's entity id (self vs. others, and the
 * name used to look up a per-player skin file).
 */
@Mixin(AvatarRenderer.class)
public class AvatarRendererMixin {
    @Inject(
            method = "getTextureLocation(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)Lnet/minecraft/resources/Identifier;",
            at = @At("RETURN"),
            cancellable = true)
    private void diego$replaceSkin(AvatarRenderState state, CallbackInfoReturnable<Identifier> cir) {
        if (dev.diego.diegoaddons.util.RenderContext.wardrobePreview) {
            return; // leave the wardrobe preview mannequins with their default skin
        }
        SkinChangerModule mod = SkinChangerModule.INSTANCE;
        if (mod == null || !mod.isEnabled()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        boolean self = mc.player != null && state.id == mc.player.getId();
        String name = null;
        if (mc.level != null && mc.level.getEntity(state.id) instanceof Player p) {
            name = p.getGameProfile().name();
        }
        Identifier repl = mod.skinFor(self, name);
        if (repl != null) {
            cir.setReturnValue(repl);
        }
    }
}
