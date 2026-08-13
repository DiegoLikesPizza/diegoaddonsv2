package dev.diego.diegoaddons.mixin;

import dev.diego.diegoaddons.util.Invisibug;
import dev.diego.diegoaddons.util.SparkleParticles;
import dev.diego.diegoaddons.util.TpsTracker;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Two read-only taps on the packet stream: the server's time packets, which
 * {@link TpsTracker} turns into a tick-rate estimate, and its particle packets, which are the only
 * way to find an {@link Invisibug}. Both observe and neither changes what vanilla does with the
 * packet.
 */
@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {
    @Inject(method = "handleSetTime", at = @At("HEAD"))
    private void diego$onSetTime(ClientboundSetTimePacket packet, CallbackInfo ci) {
        TpsTracker.onTimePacket(packet.gameTime());
    }

    /**
     * At RETURN rather than HEAD, and that is not a style choice.
     *
     * <p>A packet handler is entered first on the network thread, where vanilla's
     * {@code ensureRunningOnSameThread} throws to reschedule it - so a HEAD injection would run off
     * the client thread and touch the level from there. Nothing reaches a return until the second,
     * scheduled call, which is on the client thread where reading entities is safe. RETURN rather
     * than TAIL because the method has two exits and TAIL would only take the last one.
     */
    @Inject(method = "handleParticleEvent", at = @At("RETURN"))
    private void diego$onParticle(ClientboundLevelParticlesPacket packet, CallbackInfo ci) {
        Invisibug.onParticle(packet.getParticle(), packet.getX(), packet.getY(), packet.getZ());
        // Two features read the same packets and they are on different islands, so neither can be
        // folded into the other; both check their own module first and cost a boolean when off.
        SparkleParticles.onParticle(packet.getParticle(), packet.getX(), packet.getY(), packet.getZ());
    }
}
