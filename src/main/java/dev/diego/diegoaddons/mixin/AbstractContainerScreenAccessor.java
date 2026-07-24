package dev.diego.diegoaddons.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the container GUI's on-screen origin and size, so the wardrobe overlay can place its
 * mannequins and the equipment overlay can put its card beside the menu.
 */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
    @Accessor("leftPos")
    int diego$leftPos();

    @Accessor("topPos")
    int diego$topPos();

    @Accessor("imageWidth")
    int diego$imageWidth();
}
