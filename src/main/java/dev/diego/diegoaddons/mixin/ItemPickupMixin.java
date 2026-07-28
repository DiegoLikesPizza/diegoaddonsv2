package dev.diego.diegoaddons.mixin;

import dev.diego.diegoaddons.util.SecretChime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tells the {@link SecretChime} when the player picks an item up off the floor - one of the ways a
 * dungeon secret announces itself, and one the secret counter is slow to report.
 *
 * <p>Only the local player's pickups count: everybody's are broadcast, and a chime for somebody
 * else's secret is a chime for something you did not do.
 */
@Mixin(ClientPacketListener.class)
public class ItemPickupMixin {
    @Inject(method = "handleTakeItemEntity", at = @At("TAIL"))
    private void diego$secretPickup(ClientboundTakeItemEntityPacket packet, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && packet.getPlayerId() == mc.player.getId()) {
            SecretChime.onPickup(mc);
        }
    }
}
