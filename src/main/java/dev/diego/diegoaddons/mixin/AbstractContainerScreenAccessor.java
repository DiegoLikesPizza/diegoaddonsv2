package dev.diego.diegoaddons.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the container GUI's on-screen origin and size, so the inventory buttons can anchor
 * themselves to the menu's corners.
 */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
    @Accessor("leftPos")
    int diego$leftPos();

    @Accessor("topPos")
    int diego$topPos();

    @Accessor("imageWidth")
    int diego$imageWidth();

    @Accessor("imageHeight")
    int diego$imageHeight();
}
