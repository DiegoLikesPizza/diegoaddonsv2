package dev.diego.diegoaddons.module.modules;

import com.mojang.blaze3d.platform.InputConstants;
import dev.diego.diegoaddons.gui.ChatSearchScreen;
import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.Module;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * Ctrl+F searches the chat backlog, the way it works everywhere else.
 *
 * <p>The combo is polled straight from the window rather than bound as a key mapping, because it has
 * to work <i>while the chat screen is open</i> - which is exactly when you want it, and precisely
 * where vanilla key mappings do not fire.
 */
public class ChatSearchModule extends Module {
    public static ChatSearchModule INSTANCE;

    private final BooleanSetting caseSensitive =
            new BooleanSetting(this, "caseSensitive", "Case sensitive", false);
    private final BooleanSetting ctrlClickCopy =
            new BooleanSetting(this, "ctrlClickCopy", "Ctrl+click to copy", true);

    private boolean wasDown;

    public ChatSearchModule() {
        super("chatsearch", Category.MISC, "Chat Search",
                "Ctrl+F searches your chat; Ctrl+click a message to copy it.");
        settings.add(caseSensitive);
        settings.add(ctrlClickCopy);
        INSTANCE = this;
    }

    public boolean caseSensitive() {
        return caseSensitive.get();
    }

    public boolean ctrlClickCopy() {
        return ctrlClickCopy.get();
    }

    @Override
    public void onClientTick(Minecraft mc) {
        if (mc.getWindow() == null) {
            return;
        }
        boolean ctrl = InputConstants.isKeyDown(mc.getWindow(), GLFW.GLFW_KEY_LEFT_CONTROL)
                || InputConstants.isKeyDown(mc.getWindow(), GLFW.GLFW_KEY_RIGHT_CONTROL);
        boolean down = ctrl && InputConstants.isKeyDown(mc.getWindow(), GLFW.GLFW_KEY_F);
        boolean pressed = down && !wasDown;
        wasDown = down;

        // Don't reopen on top of the search itself, and don't steal the combo from other mods' menus.
        if (pressed && !(mc.screen instanceof ChatSearchScreen) && mc.player != null) {
            mc.setScreen(new ChatSearchScreen(mc.screen));
        }
    }
}
