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
    /**
     * How far from you - or from the block you aimed at - the sound may be and still be taken as
     * this warp's.
     *
     * <p>Checking only your own position was why the etherwarp itself was missed while a plain
     * Instant Transmission was caught: the warp's sound is played where you land, which is the block
     * you aimed at, and the sound packet arrives before the move does. So at that moment you are
     * still standing at the far end of it - up to fifty-odd blocks away from its own noise.
     */
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
        boolean atPlayer =
                mc.player.distanceToSqr(packet.getX(), packet.getY(), packet.getZ()) <= NEAR * NEAR;
        boolean atTarget = false;
        var target = EtherwarpHelper.target();
        if (target != null) {
            double dx = packet.getX() - (target.getX() + 0.5);
            double dy = packet.getY() - (target.getY() + 1.0);
            double dz = packet.getZ() - (target.getZ() + 0.5);
            atTarget = dx * dx + dy * dy + dz * dz <= NEAR * NEAR;
        }
        if (!atPlayer && !atTarget) {
            return;
        }
        ci.cancel();
        mc.player.playSound(mod.chosenSound(), 1.0f, mod.soundPitch());
    }
}
