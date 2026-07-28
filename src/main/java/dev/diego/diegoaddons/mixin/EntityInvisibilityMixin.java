package dev.diego.diegoaddons.mixin;

import dev.diego.diegoaddons.module.modules.ShowHiddenMobsModule;
import dev.diego.diegoaddons.util.DungeonState;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Backs {@link ShowHiddenMobsModule}: answers "no" when something asks whether one of these mobs is
 * invisible, which puts it back on screen with no drawing of our own.
 *
 * <p>Deliberately narrow. Your own player is never touched - turning your own invisibility off would
 * be a lie about your own state - and the local player is the one entity whose invisibility you can
 * always account for anyway.
 */
@Mixin(Entity.class)
public class EntityInvisibilityMixin {
    @Inject(method = "isInvisible", at = @At("HEAD"), cancellable = true)
    private void diego$revealHiddenMobs(CallbackInfoReturnable<Boolean> cir) {
        ShowHiddenMobsModule mod = ShowHiddenMobsModule.INSTANCE;
        if (mod == null || !mod.isEnabled()) {
            return;
        }
        Entity self = (Entity) (Object) this;
        Minecraft mc = Minecraft.getInstance();
        if (self == mc.player || !(self instanceof LivingEntity)) {
            return;
        }
        if (self instanceof Player && !mod.includePlayers()) {
            return;   // Shadow Assassins are player entities; some people would rather not see NPCs
        }
        if (mod.dungeonsOnly() && !DungeonState.inDungeons()) {
            return;
        }
        cir.setReturnValue(false);
    }
}
