package dev.diego.diegoaddons.util;

import dev.diego.diegoaddons.config.ConfigManager;
import dev.diego.diegoaddons.config.InventoryButton;
import dev.diego.diegoaddons.gui.Theme;
import dev.diego.diegoaddons.gui.UiRender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
    /** Gap between two buttons, and the extra a gigantic one spans. */
    public static final int GAP = 2;
    public static final int BIG = SIZE * 2 + GAP;

    /** Drawn size of a button, depending on whether it is gigantic. */
    public static int size(InventoryButton b) {
        return b.gigantic ? BIG : SIZE;
    }

    /** Absolute X of a button on screen, resolved against the corner it is anchored to. */
    public static int screenX(InventoryButton b, int leftPos, int imageWidth) {
        return b.anchorRight ? leftPos + imageWidth + b.x : leftPos + b.x;
    }

    /** Absolute Y of a button on screen, resolved against the corner it is anchored to. */
    public static int screenY(InventoryButton b, int topPos, int imageHeight) {
        return b.anchorBottom ? topPos + imageHeight + b.y : topPos + b.y;
    }

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

    // --- drawing, for the editor ---
    //
    // The live buttons on a container screen are not drawn here: they are retained RenderLib
    // components owned by InventoryButtonsExtension, which also handles their presses. What is left
    // here is the shared model - the configured list, the icon cache and the corner geometry - plus
    // the one-button painter the editor still needs for its drag preview.

    /** Draws one button. {@code ghost} dims it, for the editor's not-yet-placed state. */
    public static void draw(GuiGraphicsExtractor g, Minecraft mc, Theme t, InventoryButton b,
                            int x, int y, boolean hover, boolean ghost) {
        boolean sm = ConfigManager.get().smoothCorners;
        int size = size(b);
        int fill = hover ? t.elevated() : t.surfaceAlt();
        UiRender.fillRounded(g, x, y, size, size, 4,
                ghost ? Theme.withAlpha(fill, 0.5f) : fill, sm);
        UiRender.strokeRounded(g, x, y, size, size, 4,
                hover ? t.accent() : Theme.withAlpha(t.border(), 0.9f), sm);
        // A gigantic button scales its icon with it, drawn around the cell centre.
        if (b.gigantic) {
            g.pose().pushMatrix();
            g.pose().translate(x + size / 2f, y + size / 2f);
            g.pose().scale(2f);
            g.item(icon(b.icon), -8, -8);
            g.pose().popMatrix();
        } else {
            g.item(icon(b.icon), x + 1, y + 1);
        }
    }

}
