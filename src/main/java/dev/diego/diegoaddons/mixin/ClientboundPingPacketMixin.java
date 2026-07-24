package dev.diego.diegoaddons.mixin;

import dev.diego.diegoaddons.util.ServerTicks;
import net.minecraft.network.protocol.common.ClientboundPingPacket;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Counts server ticks. Hypixel sends a ping with a non-zero id once per server tick, which is the
 * only server-paced signal a client reliably gets, so {@link ServerTicks} is driven from here rather
 * than from the client's own tick loop.
 *
 * <p>The zero-id ping is the real latency ping and is ignored, matching how this is done elsewhere.
 */
@Mixin(ClientCommonPacketListenerImpl.class)
public class ClientboundPingPacketMixin {
    @Inject(method = "handlePing", at = @At("HEAD"))
    private void diego$countTick(ClientboundPingPacket packet, CallbackInfo ci) {
        if (packet.getId() != 0) {
            ServerTicks.increment();
        }
    }
}
