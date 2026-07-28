package dev.diego.diegoaddons.mixin;

import dev.diego.diegoaddons.module.modules.EtherwarpModule;
import dev.diego.diegoaddons.util.EtherwarpHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.sounds.SoundEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Swaps the noise an etherwarp makes for the one picked in {@link EtherwarpModule}.
 *
 * <p>The warp is the server's doing, so what arrives is an ordinary sound packet: an enderman
 * teleport, at the place you landed. It is caught here, before the client plays it, and answered
 * with the chosen sound instead.
 *
 * <p>Enderman teleports are not rare, so three things have to line up before one is taken to be
 * yours: it has to land within a couple of blocks of you, you have to have been aiming a warp in the
 * last moment ({@link EtherwarpHelper#armedRecently()}), and the option has to be on. Anything else
 * is left alone - an endermen dying next to you still sounds like one.
 */
@Mixin(ClientPacketListener.class)
public class EtherwarpSoundMixin {
    /** How far from the landing spot the sound may be and still be taken as this warp's. */
    private static final double NEAR = 3.0;

    @Inject(method = "handleSoundEvent", at = @At("HEAD"), cancellable = true)
    private void diego$replaceWarpSound(ClientboundSoundPacket packet, CallbackInfo ci) {
        EtherwarpModule mod = EtherwarpModule.INSTANCE;
        if (mod == null || !mod.isEnabled() || !mod.replacesSound()) {
            return;
        }
        if (packet.getSound().value() != SoundEvents.ENDERMAN_TELEPORT) {
            return;
        }
        if (!EtherwarpHelper.armedRecently()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        if (mc.player.distanceToSqr(packet.getX(), packet.getY(), packet.getZ()) > NEAR * NEAR) {
            return;
        }
        ci.cancel();
        mc.player.playSound(mod.chosenSound(), 1.0f, mod.soundPitch());
    }
}
