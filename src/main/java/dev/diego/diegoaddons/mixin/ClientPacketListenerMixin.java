package dev.diego.diegoaddons.mixin;

import dev.diego.diegoaddons.util.TpsTracker;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Timestamps each server time packet so {@link TpsTracker} can estimate the server's tick rate.
 * The injection is at HEAD and only reads the packet's game-tick counter, so it never interferes
 * with vanilla time handling.
 */
@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {
    @Inject(method = "handleSetTime", at = @At("HEAD"))
    private void diego$onSetTime(ClientboundSetTimePacket packet, CallbackInfo ci) {
        TpsTracker.onTimePacket(packet.gameTime());
    }
}
