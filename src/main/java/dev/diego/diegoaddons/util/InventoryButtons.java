package dev.diego.diegoaddons.util;

import dev.diego.diegoaddons.config.ConfigManager;
import dev.diego.diegoaddons.config.InventoryButton;
import dev.diego.diegoaddons.gui.Fonts;
import dev.diego.diegoaddons.gui.Theme;
import dev.diego.diegoaddons.gui.Themes;
import dev.diego.diegoaddons.gui.UiRender;
import dev.diego.diegoaddons.mixin.AbstractContainerScreenAccessor;
import dev.diego.diegoaddons.module.modules.InventoryButtonsModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import com.google.common.collect.LinkedHashMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User-defined shortcut buttons drawn beside any container GUI: click one and it runs its command.
 * Positions are stored relative to the GUI's corner, so the buttons follow the menu rather than the
 * screen. Configured in {@code InventoryButtonsScreen}.
 *
 * <p>This is a from-scratch implementation of the idea popularised by NotEnoughUpdates' inventory
 * buttons. No code or artwork is taken from NEU or its ports - the buttons are drawn with this mod's
 * own themed renderer and use ordinary item icons.
 */
public final class InventoryButtons {
    /** Drawn size of a button, matching a vanilla slot so it sits naturally beside the GUI. */
    public static final int SIZE = 18;

    private static final Map<String, ItemStack> ICONS = new HashMap<>();

    private InventoryButtons() {
    }

    /** The configured buttons (never null). */
    public static List<InventoryButton> all() {
        if (ConfigManager.get().inventoryButtons == null) {
            ConfigManager.get().inventoryButtons = new java.util.ArrayList<>();
        }
        return ConfigManager.get().inventoryButtons;
    }

    /**
     * The icon for a button, cached. Either a plain item id, or {@code skull:<texture>} for a custom
     * player head - the same shorthand SkyBlock-oriented mods use, where the texture is the hash or
     * full URL from textures.minecraft.net. Falls back to a barrier so a bad id is visible.
     */
    public static ItemStack icon(String id) {
        return ICONS.computeIfAbsent(id == null ? "" : id, key -> {
            try {
                if (key.startsWith("skull:")) {
                    return skull(key.substring(6));
                }
                Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(key));
                if (item != null && item != Items.AIR) {
                    return new ItemStack(item);
                }
            } catch (Exception ignored) {
                // An unparseable or unknown id just falls through to the placeholder below.
            }
            return new ItemStack(Items.BARRIER);
        });
    }

    /**
     * A player head wearing the given texture. Minecraft wants the profile's texture property as
     * base64 of the JSON the session server would return, so the hash is wrapped back up into that
     * shape here.
     */
    private static ItemStack skull(String texture) {
        String url = texture.startsWith("http") ? texture
                : "http://textures.minecraft.net/texture/" + texture;
        String json = "{\"textures\":{\"SKIN\":{\"url\":\"" + url + "\"}}}";
        String encoded = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));

        PropertyMap props = new PropertyMap(LinkedHashMultimap.create());
        props.put("textures", new Property("textures", encoded));
        // A fixed UUID keeps the profile stable; the name is irrelevant for rendering.
        GameProfile profile = new GameProfile(
                UUID.nameUUIDFromBytes(texture.getBytes(StandardCharsets.UTF_8)), "DiegoIcon", props);

        ItemStack head = new ItemStack(Items.PLAYER_HEAD);
        head.set(DataComponents.PROFILE, ResolvableProfile.createResolved(profile));
        return head;
    }

    /** Forget resolved icons, so an edited id is picked up immediately. */
    public static void invalidateIcons() {
        ICONS.clear();
    }

    // --- live drawing / clicking on real container screens ---

    public static void render(AbstractContainerScreen<?> screen, GuiGraphicsExtractor g, int mouseX, int mouseY) {
        InventoryButtonsModule mod = InventoryButtonsModule.INSTANCE;
        if (mod == null || !mod.isEnabled() || !visible(mod)) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Theme t = Themes.current();
        int leftPos = ((AbstractContainerScreenAccessor) screen).diego$leftPos();
        int topPos = ((AbstractContainerScreenAccessor) screen).diego$topPos();

        InventoryButton hovered = null;
        for (InventoryButton b : all()) {
            int x = leftPos + b.x;
            int y = topPos + b.y;
            boolean hover = UiRender.inside(mouseX, mouseY, x, y, SIZE, SIZE);
            draw(g, mc, t, b, x, y, hover, false);
            if (hover) {
                hovered = b;
            }
        }
        if (hovered != null && mod.showTooltips() && !hovered.command.isBlank()) {
            tooltip(g, mc, t, "/" + hovered.command, mouseX, mouseY);
        }
    }

    /** Draws one button. {@code ghost} dims it, for the editor's not-yet-placed state. */
    public static void draw(GuiGraphicsExtractor g, Minecraft mc, Theme t, InventoryButton b,
                            int x, int y, boolean hover, boolean ghost) {
        boolean sm = ConfigManager.get().smoothCorners;
        int fill = hover ? t.elevated() : t.surfaceAlt();
        UiRender.fillRounded(g, x, y, SIZE, SIZE, 4,
                ghost ? Theme.withAlpha(fill, 0.5f) : fill, sm);
        UiRender.strokeRounded(g, x, y, SIZE, SIZE, 4,
                hover ? t.accent() : Theme.withAlpha(t.border(), 0.9f), sm);
        g.item(icon(b.icon), x + 1, y + 1);
    }

    /** A themed one-line tooltip, so the buttons explain themselves on hover. */
    private static void tooltip(GuiGraphicsExtractor g, Minecraft mc, Theme t, String text, int mx, int my) {
        boolean sm = ConfigManager.get().smoothCorners;
        int w = Fonts.width(mc.font, text, Fonts.MEDIUM) + 12;
        int h = 16;
        int x = Math.max(2, Math.min(mx + 10, mc.getWindow().getGuiScaledWidth() - w - 2));
        int y = Math.max(2, my - h - 4);
        UiRender.fillRounded(g, x, y, w, h, 5, (0xEE << 24) | (t.surface() & 0x00FFFFFF), sm);
        UiRender.strokeRounded(g, x, y, w, h, 5, Theme.withAlpha(t.border(), 0.9f), sm);
        UiRender.text(g, mc.font, text, Fonts.MEDIUM, x + 6, y + 4, t.text());
    }

    /**
     * Handles a click on a container screen.
     *
     * @return true when a button was hit, in which case the screen must not also process the click
     */
    public static boolean click(AbstractContainerScreen<?> screen, double mouseX, double mouseY, int button) {
        InventoryButtonsModule mod = InventoryButtonsModule.INSTANCE;
        if (mod == null || !mod.isEnabled() || button != 0 || !visible(mod)) {
            return false;
        }
        int leftPos = ((AbstractContainerScreenAccessor) screen).diego$leftPos();
        int topPos = ((AbstractContainerScreenAccessor) screen).diego$topPos();
        for (InventoryButton b : all()) {
            if (UiRender.inside(mouseX, mouseY, leftPos + b.x, topPos + b.y, SIZE, SIZE)) {
                run(b);
                return true;
            }
        }
        return false;
    }

    /**
     * Runs a button's command. The menu is closed first: most of these commands open another menu,
     * and the server will not replace a container the client still thinks is open.
     */
    private static void run(InventoryButton b) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || b.command == null || b.command.isBlank()) {
            return;
        }
        String cmd = b.command.startsWith("/") ? b.command.substring(1) : b.command;
        mc.player.closeContainer();
        mc.player.connection.sendCommand(cmd);
    }

    private static boolean visible(InventoryButtonsModule mod) {
        Minecraft mc = Minecraft.getInstance();
        if (mod.hideInCreative() && mc.player != null && mc.player.isCreative()) {
            return false;
        }
        return !all().isEmpty();
    }
}
