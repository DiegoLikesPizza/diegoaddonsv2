package dev.diego.diegoaddons.mixin;

import dev.diego.diegoaddons.util.ItemRarity;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Puts the {@link ItemRarity} colour on a hotbar slot <b>before</b> the item is drawn into it.
 *
 * <p>It used to be drawn from the mod's own HUD pass, which runs after the whole vanilla GUI - so on
 * the hotbar the fill and the circle landed on top of the item rather than behind it, while the same
 * two looked right in an open inventory. That was worked around by forcing the hotbar to the outline
 * style, which is not what "Filled" is supposed to mean.
 *
 * <p>The fix is to draw where the inventory draws: {@code extractSlot} is the one method vanilla
 * hands a slot's position and its stack just before submitting the item, so injecting at its head
 * puts the backing under the item with no ordering left to guess. It covers the offhand slot too,
 * which the old pass never reached.
 */
@Mixin(Gui.class)
public abstract class HotbarSlotRarityMixin {

    @Inject(method = "extractSlot", at = @At("HEAD"))
    private void diego$rarityUnderItem(GuiGraphicsExtractor g, int x, int y, DeltaTracker delta,
                                       Player player, ItemStack stack, int seed, CallbackInfo ci) {
        ItemRarity.renderHotbarSlot(g, x, y, stack);
    }
}
