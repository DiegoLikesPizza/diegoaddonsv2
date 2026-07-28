package dev.diego.diegoaddons.mixin;

import dev.diego.diegoaddons.module.modules.TitleScreenModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;

/**
 * Tidies the title screen instead of replacing it.
 *
 * <p>An earlier attempt drew a whole menu of its own, which RenderLib letterboxes on any window that
 * is not 16:9 - a strip of dead screen down each side. Vanilla's menu is fine; it just has a button
 * for a service you do not pay for and two icons you press once a year. So this changes the three
 * things worth changing and leaves the rest of the screen exactly as it is, at any aspect ratio.
 *
 * <p>The corner icons are found by type - they are the only sprite buttons on the screen - and
 * Realms by its translation key, so neither depends on what language the game is in.
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
        for (GuiEventListener child : new ArrayList<>(this.children())) {
            if (mod.hideCornerButtons() && child instanceof SpriteIconButton icon) {
                this.removeWidget(icon);
                continue;
            }
            if (mod.replaceRealms() && child instanceof Button button && isRealms(button)) {
                replaceWithHypixel(button, mod);
            }
        }
    }

    /** Swaps the Realms button for one that goes where you were actually going. */
    private void replaceWithHypixel(AbstractWidget realms, TitleScreenModule mod) {
        int x = realms.getX();
        int y = realms.getY();
        int width = realms.getWidth();
        int height = realms.getHeight();
        this.removeWidget(realms);
        this.addRenderableWidget(Button.builder(
                Component.literal(mod.buttonLabel()), b -> connect(mod.server()))
                .bounds(x, y, width, height)
                .build());
    }

    /** By translation key, so it is the Realms button in any language. */
    private static boolean isRealms(Button button) {
        return button.getMessage().getContents() instanceof TranslatableContents contents
                && "menu.online".equals(contents.getKey());
    }

    /** Straight to the server, the way the multiplayer list does it. */
    private static void connect(String address) {
        Minecraft mc = Minecraft.getInstance();
        String host = address == null || address.isBlank() ? "mc.hypixel.net" : address.trim();
        ServerData data = new ServerData("Hypixel", host, ServerData.Type.OTHER);
        ConnectScreen.startConnecting(mc.screen, mc, ServerAddress.parseString(host), data,
                false, null);
    }
}
