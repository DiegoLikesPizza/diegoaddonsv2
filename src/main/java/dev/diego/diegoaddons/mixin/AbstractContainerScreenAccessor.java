package dev.diego.diegoaddons.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes the container GUI's on-screen origin and size, so the wardrobe overlay can place its
 * mannequins and the equipment overlay can put its card beside the menu, plus the protected
 * slot-click entry point the wardrobe keybinds use.
 */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
    @Accessor("leftPos")
    int diego$leftPos();

    @Accessor("topPos")
    int diego$topPos();

    @Accessor("imageWidth")
    int diego$imageWidth();

    /**
     * The screen's own click handler. Going through this rather than crafting a packet means a
     * keybind press behaves exactly like the user clicking that slot themselves.
     */
    @Invoker("slotClicked")
    void diego$slotClicked(Slot slot, int slotId, int button, ContainerInput input);
}
