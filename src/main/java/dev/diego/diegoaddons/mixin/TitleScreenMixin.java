package dev.diego.diegoaddons.mixin;

import dev.diego.diegoaddons.module.modules.TitleScreenModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;

/**
 * Tidies the vanilla title screen instead of replacing it.
 *
 * <p>Two changes, and nothing else touched, so the screen still works at any aspect ratio: Realms
 * becomes a button that goes where you were actually going, and a DiegoAddons button opens the
 * start-screen options - including the switch for configlib's own menu, which has to be reachable
 * from here or it could be turned on and never off.
 *
 * <p>Realms is found by its translation key rather than its position or label, so it is the Realms
 * button in any language.
 *
 * <p>The corner icons are left alone. Hiding them was once a setting; with the setting gone,
 * removing the accessibility button unconditionally and offering no way back is not a default worth
 * having.
 */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
    protected TitleScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void diego$tidyMenu(CallbackInfo ci) {
        TitleScreenModule mod = TitleScreenModule.INSTANCE;
        if (mod == null || !mod.isEnabled()) {
            return;
        }
        boolean replaced = false;
        for (GuiEventListener child : new ArrayList<>(this.children())) {
            if (child instanceof Button button && isRealms(button)) {
                replaceWithHypixel(button);
                replaced = true;
            }
        }
        if (!replaced) {
            // Said out loud rather than swallowed: the button is found by its translation key, and
            // a key that changes between versions would otherwise look like the feature quietly
            // doing nothing.
            dev.diego.diegoaddons.DiegoAddonsV2Client.LOGGER.warn(
                    "[DiegoAddons V2] No Realms button found on the title screen - nothing to"
                            + " replace. The 'menu.online' key may have changed.");
        }
        diego$addSettingsButton();
    }

    /**
     * A way into the mod's start-screen options from the vanilla title screen.
     *
     * <p>Needed because the options live on configlib's own menu, which you cannot reach until that
     * menu is switched on - and the switch is one of the options. Without this the setting can only
     * ever be turned on, never back off from the screen it affects.
     */
    private void diego$addSettingsButton() {
        var handle = dev.diego.diegoaddons.DiegoAddonsV2Client.CONFIG;
        if (handle == null) {
            return;
        }
        this.addRenderableWidget(Button.builder(
                        Component.literal("DiegoAddons"),
                        b -> Minecraft.getInstance().setScreen(
                                new dev.diego.configlib.menu.MenuCustomizeScreen(handle, this)))
                .bounds(this.width - 106, this.height - 26, 100, 20)
                .build());
    }

    /**
     * Swaps the Realms button for one that goes where you were actually going.
     *
     * <p>Explicitly re-enabled: vanilla greys the Realms button out when the service is
     * unreachable or the account has no subscription, and inheriting that would give a button that
     * looks right and cannot be pressed.
     */
    private void replaceWithHypixel(AbstractWidget realms) {
        int x = realms.getX();
        int y = realms.getY();
        int width = realms.getWidth();
        int height = realms.getHeight();
        this.removeWidget(realms);
        Button hypixel = Button.builder(
                Component.literal(dev.diego.diegoaddons.util.Hypixel.LABEL),
                b -> dev.diego.diegoaddons.util.Hypixel.connect())
                .bounds(x, y, width, height)
                .build();
        hypixel.active = true;
        hypixel.visible = true;
        this.addRenderableWidget(hypixel);
    }

    /** By translation key, so it is the Realms button in any language. */
    private static boolean isRealms(Button button) {
        return button.getMessage().getContents() instanceof TranslatableContents contents
                && "menu.online".equals(contents.getKey());
    }
}
