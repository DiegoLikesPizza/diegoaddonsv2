package dev.diego.diegoaddons.util;

import dev.diego.diegoaddons.mixin.AbstractContainerScreenAccessor;
import dev.diego.diegoaddons.module.modules.WardrobeOverlayModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.ClientMannequin;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Draws a small player model wearing each stored armour set on top of the Hypixel SkyBlock wardrobe
 * menu. Each wardrobe column (a set) gets its own mannequin, built from whatever armour pieces sit
 * in that column, so you can see the sets at a glance instead of reading four separate rows.
 *
 * <p>Armour is identified by the item's {@link Equippable} component (so skull-based helmets count),
 * and the mannequins are reused across frames. See {@link WardrobeOverlayModule}.
 */
public final class WardrobeOverlay {
    private static final EquipmentSlot[] ARMOR = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };
    private static final int PLAYER_INV_SLOTS = 36; // 27 storage + 9 hotbar, always last in a chest menu
    private static final List<ClientMannequin> POOL = new ArrayList<>();

    private WardrobeOverlay() {
    }

    public static void render(AbstractContainerScreen<?> screen, GuiGraphicsExtractor g) {
        WardrobeOverlayModule mod = WardrobeOverlayModule.INSTANCE;
        if (mod == null || !mod.isEnabled()) {
            return;
        }
        if (!screen.getTitle().getString().contains("Wardrobe")) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }

        // Group armour pieces by their column (slot x), and track the armour rows' y-range.
        AbstractContainerMenu menu = screen.getMenu();
        List<Slot> slots = menu.slots;
        int chestCount = slots.size() - PLAYER_INV_SLOTS;
        Map<Integer, EnumMap<EquipmentSlot, ItemStack>> columns = new TreeMap<>();
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (int i = 0; i < chestCount && i < slots.size(); i++) {
            Slot s = slots.get(i);
            ItemStack st = s.getItem();
            if (st.isEmpty()) {
                continue;
            }
            Equippable eq = st.get(DataComponents.EQUIPPABLE);
            if (eq == null || !eq.slot().isArmor()) {
                continue;
            }
            columns.computeIfAbsent(s.x, k -> new EnumMap<>(EquipmentSlot.class)).put(eq.slot(), st);
            minY = Math.min(minY, s.y);
            maxY = Math.max(maxY, s.y);
        }
        if (columns.isEmpty()) {
            return;
        }

        int leftPos = ((AbstractContainerScreenAccessor) screen).diego$leftPos();
        int topPos = ((AbstractContainerScreenAccessor) screen).diego$topPos();
        int y1 = topPos + minY - 1;
        int y2 = topPos + maxY + 16 + 1; // +16 = slot height, so we span helmet..boots fully
        int size = (int) ((y2 - y1) * 0.55f);

        int idx = 0;
        for (Map.Entry<Integer, EnumMap<EquipmentSlot, ItemStack>> col : columns.entrySet()) {
            ClientMannequin m = mannequin(idx++, mc);
            if (m == null) {
                continue;
            }
            EnumMap<EquipmentSlot, ItemStack> set = col.getValue();
            for (EquipmentSlot es : ARMOR) {
                m.setItemSlot(es, set.getOrDefault(es, ItemStack.EMPTY));
            }
            // Box is one column wide (no overlap with neighbours); arms may clip slightly.
            int cxAbs = leftPos + col.getKey() + 8;
            int bx1 = cxAbs - 9;
            int bx2 = cxAbs + 9;
            float cx = (bx1 + bx2) / 2f;
            float cy = (y1 + y2) / 2f;

            RenderContext.wardrobePreview = true;
            try {
                InventoryScreen.extractEntityInInventoryFollowsMouse(g, bx1, y1, bx2, y2, size, 0.0f, cx, cy, m);
            } catch (Throwable ignored) {
                // Rendering a detached mannequin can be finicky; never let it break the GUI.
            } finally {
                RenderContext.wardrobePreview = false;
            }
        }
    }

    /** A reusable mannequin for column {@code i}, recreated if the world changed. */
    private static ClientMannequin mannequin(int i, Minecraft mc) {
        while (POOL.size() <= i) {
            POOL.add(null);
        }
        ClientMannequin m = POOL.get(i);
        if (m == null || m.level() != mc.level) {
            try {
                m = new ClientMannequin(mc.level, mc.playerSkinRenderCache());
            } catch (Throwable t) {
                return null;
            }
            POOL.set(i, m);
        }
        return m;
    }
}
