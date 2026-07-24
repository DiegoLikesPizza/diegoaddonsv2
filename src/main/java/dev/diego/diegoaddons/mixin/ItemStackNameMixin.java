package dev.diego.diegoaddons.mixin;

import dev.diego.diegoaddons.module.modules.ReplaceWordsModule;
import dev.diego.diegoaddons.util.WordReplacer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Locale;

/**
 * Runs the word replacements on an item's display name at the source.
 *
 * <p>Hooking {@code getHoverName} rather than the tooltip event is what makes the rename show up
 * <i>everywhere</i> the name appears - including the pop-up above the hotbar when you switch slots,
 * which never passes through the tooltip event and so kept showing the original.
 *
 * <p>In the friend list the real IGN is appended in brackets, so a renamed player is still
 * identifiable as an account rather than only as a nickname.
 */
@Mixin(ItemStack.class)
public class ItemStackNameMixin {
    @Inject(method = "getHoverName()Lnet/minecraft/network/chat/Component;", at = @At("RETURN"), cancellable = true)
    private void diego$replaceName(CallbackInfoReturnable<Component> cir) {
        ReplaceWordsModule mod = ReplaceWordsModule.INSTANCE;
        if (mod == null || !mod.isEnabled() || !mod.inItems()) {
            return;
        }
        Component name = cir.getReturnValue();
        Component out = mod.ignInFriendList() && inFriendList()
                ? WordReplacer.applyWithOriginal(name)
                : WordReplacer.apply(name);
        if (out != name) {
            cir.setReturnValue(out);
        }
    }

    /** Hypixel's friend list is a chest menu whose title carries "Friends". */
    private static boolean inFriendList() {
        Minecraft mc = Minecraft.getInstance();
        return mc.screen instanceof AbstractContainerScreen<?> s
                && s.getTitle().getString().replaceAll("§.", "").toLowerCase(Locale.ROOT).contains("friend");
    }
}
