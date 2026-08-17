/*
 * Ported from Inventory Buttons (https://github.com/afranz29/Inventory-Buttons),
 * Copyright (C) 2026 Panda/afranz29, licensed under the LGPLv3 - itself a port of the inventory
 * button editor from NotEnoughUpdates (Moulberry and contributors). This file stays under the
 * LGPLv3, and is a deliberate pixel-for-pixel port: the layout numbers, colours and behaviour are
 * the upstream mod's and are not to be "tidied" into the rest of this mod's styling.
 */

package dev.diego.diegoaddons.gui;

import dev.diego.diegoaddons.DiegoAddonsV2Client;
import dev.diego.diegoaddons.config.InvButton;
import dev.diego.diegoaddons.module.modules.InventoryButtonsModule;
import dev.diego.diegoaddons.util.HypixelSkulls;
import dev.diego.diegoaddons.util.InvButtons;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The button editor: an inventory drawn in the middle, the buttons on it, and a panel to set one up.
 *
 * <p>Click a button to select it, click it again to open its panel, drag it anywhere, backspace to
 * delete it, right-click empty space to add one. <b>S</b> toggles grid snapping for this session.
 *
 * <p>Snapping is two grids, not one. Inside the inventory rectangle a button lands on one of the
 * thirteen fixed slots the vanilla screen leaves free - the armour column, the crafting square, the
 * offhand, the hotbar row - because those are the only places inside the window a button can sit
 * without covering a slot. Outside it, buttons tile on a 20px pitch off whichever edge they are
 * nearest. With snapping off the outside is free-form but the inside still magnets to those slots,
 * so a button dropped over the armour column does not end up one pixel out.
 */
public final class InvButtonEditor extends Screen {

    private static final Identifier INVENTORY =
            Identifier.withDefaultNamespace("textures/gui/container/inventory.png");
    private static final Identifier BUTTONS =
            Identifier.fromNamespaceAndPath(DiegoAddonsV2Client.MOD_ID, "textures/invbuttons/buttons.png");
    private static final Identifier INFO =
            Identifier.fromNamespaceAndPath(DiegoAddonsV2Client.MOD_ID, "textures/invbuttons/info.png");

    private static final int SHEET_W = 90;
    private static final int SHEET_H = 36;

    private static final int BUTTON_SIZE = 18;
    private static final int OUTER_PADDING = 2;
    private static final int OUTER_GRID = BUTTON_SIZE + OUTER_PADDING;
    private static final int TOP_BOTTOM_START_X = 8;

    /** The places inside the inventory window that are not a slot. */
    private static final List<Point> FREE_SLOTS = List.of(
            new Point(25, 8), new Point(57, 8), new Point(25, 60), new Point(57, 60),
            new Point(97, 17), new Point(115, 17), new Point(97, 35), new Point(115, 35),
            new Point(153, 27),
            new Point(98, 61), new Point(116, 61), new Point(134, 61), new Point(152, 61));

    /** SkyBlock warp heads worth having on a button, by the name the search matches. */
    private static final Map<String, String> SKULL_ICONS = new HashMap<>();

    static {
        SKULL_ICONS.put("Personal Bank", "skull:e36e94f6c34a35465fce4a90f2e25976389eb9709a12273574ff70fd4daa6852");
        SKULL_ICONS.put("Skyblock Hub", "skull:d7cc6687423d0570d556ac53e0676cb563bbdd9717cd8269bdebed6f6d4e7bf8");
        SKULL_ICONS.put("Private Island", "skull:c9c8881e42915a9d29bb61a16fb26d059913204d265df5b439b3d792acd56");
        SKULL_ICONS.put("Castle", "skull:f4559d75464b2e40a518e4de8e6cf3085f0a3ca0b1b7012614c4cd96fed60378");
        SKULL_ICONS.put("Sirius Shack", "skull:7ab83858ebc8ee85c3e54ab13aabfcc1ef2ad446d6a900e471c3f33b78906a5b");
        SKULL_ICONS.put("Crypts", "skull:25d2f31ba162fe6272e831aed17f53213db6fa1c4cbe4fc827f3963cc98b9");
        SKULL_ICONS.put("Spiders Den", "skull:c754318a3376f470e481dfcd6c83a59aa690ad4b4dd7577fdad1c2ef08d8aee6");
        SKULL_ICONS.put("Top Of The Nest", "skull:9d7e3b19ac4f3dee9c5677c135333b9d35a7f568b63d1ef4ada4b068b5a25");
        SKULL_ICONS.put("The End", "skull:7840b87d52271d2a755dedc82877e0ed3df67dcc42ea479ec146176b02779a5");
        SKULL_ICONS.put("The End Dragons Nest", "skull:a1cd6d2d03f135e7c6b5d6cdae1b3a68743db4eb749faf7341e9fb347aa283b");
        SKULL_ICONS.put("The Park", "skull:a221f813dacee0fef8c59f76894dbb26415478d9ddfc44c2e708a6d3b7549b");
        SKULL_ICONS.put("The Park Jungle", "skull:79ca3540621c1c79c32bf42438708ff1f5f7d0af9b14a074731107edfeb691c");
        SKULL_ICONS.put("The Park Howling Cave", "skull:1832d53997b451635c9cf9004b0f22bb3d99ab5a093942b5b5f6bb4e4de47065");
        SKULL_ICONS.put("Gold Mines", "skull:73bc965d579c3c6039f0a17eb7c2e6faf538c7a5de8e60ec7a719360d0a857a9");
        SKULL_ICONS.put("Deep Caverns", "skull:569a1f114151b4521373f34bc14c2963a5011cdc25a6554c48c708cd96ebfc");
        SKULL_ICONS.put("The Barn", "skull:4d3a6bd98ac1833c664c4909ff8d2dc62ce887bdcf3cc5b3848651ae5af6b");
        SKULL_ICONS.put("Mushroom Desert", "skull:6b20b23c1aa2be0270f016b4c90d6ee6b8330a17cfef87869d6ad60b2ffbf3b5");
        SKULL_ICONS.put("Dungeon Hub", "skull:9b56895b9659896ad647f58599238af532d46db9c1b0389b8bbeb70999dab33d");
        SKULL_ICONS.put("Dwarven Mines", "skull:51539dddf9ed255ece6348193cd75012c82c93aec381f05572cecf7379711b3b");
        SKULL_ICONS.put("HOTM Heart Of The Mountain", "skull:86f06eaa3004aeed09b3d5b45d976de584e691c0e9cade133635de93d23b9edb");
        SKULL_ICONS.put("Bazaar Dude", "skull:c232e3820897429157619b0ee099fec0628f602fff12b695de54aef11d923ad7");
        SKULL_ICONS.put("Museum", "skull:438cf3f8e54afc3b3f91d20a49f324dca1486007fe545399055524c17941f4dc");
        SKULL_ICONS.put("Crystal Hollows", "skull:21dbe30b027acbceb612563bd877cd7ebb719ea6ed1399027dcee58bb9049d4a");
        SKULL_ICONS.put("Dwarven Forge", "skull:5cbd9f5ec1ed007259996491e69ff649a3106cf920227b1bb3a71ee7a89863f");
        SKULL_ICONS.put("Forgotton Skull", "skull:6becc645f129c8bc2faa4d8145481fab11ad2ee75749d628dcd999aa94e7");
        SKULL_ICONS.put("Crystal Nucleus", "skull:34d42f9c461cee1997b67bf3610c6411bf852b9e5db607bbf626527cfb42912c");
        SKULL_ICONS.put("Void Sepulture", "skull:eb07594e2df273921a77c101d0bfdfa1115abed5b9b2029eb496ceba9bdbb4b3");
        SKULL_ICONS.put("Crimson Isle", "skull:c3687e25c632bce8aa61e0d64c24e694c3eea629ea944f4cf30dcfb4fbce071");
        SKULL_ICONS.put("Trapper Den", "skull:6102f82148461ced1f7b62e326eb2db3a94a33cba81d4281452af4d8aeca4991");
        SKULL_ICONS.put("Arachne Sanctuary", "skull:35e248da2e108f09813a6b848a0fcef111300978180eda41d3d1a7a8e4dba3c3");
        SKULL_ICONS.put("Garden", "skull:f4880d2c1e7b86e87522e20882656f45bafd42f94932b2c5e0d6ecaa490cb4c");
        SKULL_ICONS.put("Winter", "skull:6dd663136cafa11806fdbca6b596afd85166b4ec02142c8d5ac8941d89ab7");
        SKULL_ICONS.put("Wizard Tower", "skull:838564e28aba98301dbda5fafd86d1da4e2eaeef12ea94dcf440b883e559311c");
        SKULL_ICONS.put("Dwarven Mines Base Camp", "skull:2461ec3bd654f62ca9a393a32629e21b4e497c877d3f3380bcf2db0e20fc0244");
    }

    /** The five tabs over the icon list. */
    private enum Filter {
        ALL(Items.BOOK),
        ITEMS(Items.DIAMOND_SWORD),
        BLOCKS(Items.BEDROCK),
        SKULLS(Items.SKELETON_SKULL),
        MISC(Items.BUCKET);

        final ItemStack icon;

        Filter(Item item) {
            this.icon = new ItemStack(item);
        }
    }

    /** Something the icon list can draw and that a button can be set to. */
    private interface Icon {
        void render(GuiGraphicsExtractor g, int x, int y);

        String name();

        /** What gets written into the button's {@code itemId}. */
        String configId();
    }

    private record StackIcon(ItemStack stack) implements Icon {
        @Override
        public void render(GuiGraphicsExtractor g, int x, int y) {
            g.fakeItem(stack, x, y);
        }

        @Override
        public String name() {
            return stack.getHoverName().getString();
        }

        @Override
        public String configId() {
            if (stack.getItem() == Items.PLAYER_HEAD) {
                String named = SKULL_ICONS.get(name());
                if (named != null) {
                    return named;
                }
            }
            return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        }
    }

    private record TextureIcon(String name, Identifier texture) implements Icon {
        @Override
        public void render(GuiGraphicsExtractor g, int x, int y) {
            g.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0f, 0f, 16, 16, 16, 16, 16, 16,
                    0xFFFFFFFF);
        }

        @Override
        public String configId() {
            return texture.toString();
        }
    }

    private record SkyblockIcon(HypixelSkulls.Skull skull) implements Icon {
        @Override
        public void render(GuiGraphicsExtractor g, int x, int y) {
            g.fakeItem(skull.icon(), x, y);
        }

        @Override
        public String name() {
            return skull.name();
        }

        @Override
        public String configId() {
            return skull.configId();
        }
    }

    private final Screen parent;

    private final int xSize = 176;
    private final int ySize = 166;
    private int guiLeft;
    private int guiTop;

    private final int editorWidth = 150;
    private final int editorHeight = 224;
    private int editorLeft;
    private int editorTop;

    private int dragOffsetX;
    private int dragOffsetY;
    private boolean dragging;
    private boolean editorOpen;
    private boolean infoOpen;
    private boolean savePanelOpen;

    private EditBox commandField;
    private EditBox searchField;
    private EditBox skullField;
    private EditBox profileField;

    private InvButton editing;

    private Filter filter = Filter.ALL;
    private final List<Icon> icons = new ArrayList<>();
    private final Lerp scroll = new Lerp();

    private String status = "";
    private long statusUntil;

    private boolean gridSnap;

    public InvButtonEditor(Screen parent) {
        super(Component.literal("Inventory Buttons"));
        this.parent = parent;
        InventoryButtonsModule module = InventoryButtonsModule.INSTANCE;
        this.gridSnap = module != null && module.gridSnap();
    }

    @Override
    protected void init() {
        guiLeft = (width - xSize) / 2;
        guiTop = (height - ySize) / 2;

        // The SkyBlock heads are only wanted once somebody is actually picking an icon.
        HypixelSkulls.load();

        commandField = new EditBox(font, 0, 0, editorWidth - 14, 16, Component.literal("Command"));
        commandField.setMaxLength(256);
        commandField.setResponder(typed -> {
            if (editing == null) {
                return;
            }
            // A command is a command: the slash is put back the moment it is removed, so a button
            // can never be saved with something that would be sent as chat.
            String text = typed;
            if (text.isEmpty()) {
                text = "/";
                commandField.setValue(text);
                commandField.moveCursorTo(1, false);
            } else if (!text.startsWith("/")) {
                text = "/" + text.replace("/", "");
                commandField.setValue(text);
                commandField.moveCursorTo(text.length(), false);
            }
            editing.command = text;
        });

        searchField = new EditBox(font, 0, 0, editorWidth - 14, 16, Component.literal("Icon"));
        searchField.setMaxLength(256);
        searchField.setResponder(this::search);

        skullField = new EditBox(font, 0, 0, editorWidth - 14, 16, Component.literal("Skull ID"));
        skullField.setMaxLength(512);
        skullField.setResponder(typed -> {
            if (editing != null && !typed.isEmpty()) {
                editing.itemId = typed;
            }
        });

        profileField = new EditBox(font, 0, 0, 140, 20, Component.literal("Profile Name"));
        profileField.setMaxLength(32);

        search("");
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        // Deliberately not super: the editor draws over whatever is behind it without dimming, the
        // way the mod it is ported from does.
        guiLeft = (width - xSize) / 2;
        guiTop = (height - ySize) / 2;

        g.blit(RenderPipelines.GUI_TEXTURED, INVENTORY, guiLeft, guiTop, 0f, 0f,
                xSize, ySize, xSize, ySize, 256, 256, 0xFFFFFFFF);

        Minecraft mc = Minecraft.getInstance();
        for (InvButton button : InvButtons.buttons()) {
            int x = guiLeft + button.x + (button.anchorRight ? xSize : 0);
            int y = guiTop + button.y + (button.anchorBottom ? ySize : 0);

            if (button == editing) {
                g.fill(x, y, x + 18, y + 18, 0x8000FF00);
                InvButtonsOverlay.border(g, x, y, 18, 18, 0xFFFFFFFF);
            } else {
                g.blit(RenderPipelines.GUI_TEXTURED, BUTTONS, x, y,
                        button.backgroundIndex * 18f, 18f, 18, 18, 18, 18, SHEET_W, SHEET_H,
                        0xFFFFFFFF);
            }
            InvButtonsOverlay.drawIcon(g, mc, button, x, y);
        }

        if (editing != null && editorOpen) {
            positionEditor();
            renderEditorPanel(g, mouseX, mouseY, partial);
        }

        if (editing == null) {
            g.centeredText(font, "Click to select/drag, Click again to edit", width / 2, 10, 0xFFFFFFFF);
            g.centeredText(font, "Backspace while selected to delete", width / 2, 22, 0xFFAAAAAA);
            g.centeredText(font, "Right Click empty space to add new", width / 2, 34, 0xFFAAAAAA);
        }

        g.text(font, "Grid Snap (S): " + (gridSnap ? "ON" : "OFF"), 5, height - 15,
                gridSnap ? 0xFF55FF55 : 0xFFAAAAAA, true);

        renderIoButtons(g, mouseX, mouseY);

        if (infoOpen) {
            renderInfoPanel(g, mouseX, mouseY);
        }
        if (savePanelOpen) {
            renderSavePanel(g, mouseX, mouseY, partial);
        }
    }

    private void renderIoButtons(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int x = 10;
        int y = 10;

        ioButton(g, x, y, 100, "Save as Profile", mouseX, mouseY);
        ioButton(g, x, y + 25, 50, "Export", mouseX, mouseY);
        ioButton(g, x, y + 50, 50, "Import", mouseX, mouseY);

        if (System.currentTimeMillis() < statusUntil) {
            g.text(font, status, x + 105, y + 6, 0xFF55FF55, true);
        }
    }

    private void ioButton(GuiGraphicsExtractor g, int x, int y, int w, String label,
                          int mouseX, int mouseY) {
        boolean hover = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + 20;
        g.fill(x, y, x + w, y + 20, hover ? 0xFF606060 : 0xFF404040);
        InvButtonsOverlay.border(g, x, y, w, 20, 0xFFFFFFFF);
        g.centeredText(font, label, x + w / 2, y + 6, 0xFFFFFFFF);
    }

    private void renderSavePanel(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        g.fillGradient(0, 0, width, height, 0xAA000000, 0xAA000000);

        int panelW = 200;
        int panelH = 100;
        int panelX = (width - panelW) / 2;
        int panelY = (height - panelH) / 2;

        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xFF202020);
        InvButtonsOverlay.border(g, panelX, panelY, panelW, panelH, 0xFF505050);
        g.centeredText(font, "Profile Name", panelX + panelW / 2, panelY + 10, 0xFFFFFFFF);

        int closeX = panelX + panelW - 17;
        int closeY = panelY + 5;
        boolean hoverClose = mouseX >= closeX && mouseX <= closeX + 12
                && mouseY >= closeY && mouseY <= closeY + 12;
        g.fill(closeX, closeY, closeX + 12, closeY + 12, hoverClose ? 0xFFFF0000 : 0xFFCC0000);
        g.centeredText(font, "x", closeX + 6, closeY + 2, 0xFFFFFFFF);

        profileField.setX(panelX + 30);
        profileField.setY(panelY + 35);
        profileField.extractRenderState(g, mouseX, mouseY, partial);

        int btnX = panelX + (panelW - 60) / 2;
        int btnY = panelY + 65;
        boolean hoverSave = mouseX >= btnX && mouseX <= btnX + 60 && mouseY >= btnY && mouseY <= btnY + 20;
        g.fill(btnX, btnY, btnX + 60, btnY + 20, hoverSave ? 0xFF408040 : 0xFF206020);
        InvButtonsOverlay.border(g, btnX, btnY, 60, 20, 0xFFFFFFFF);
        g.centeredText(font, "Save", btnX + 30, btnY + 6, 0xFFFFFFFF);
    }

    private void renderEditorPanel(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        g.fill(editorLeft, editorTop, editorLeft + editorWidth, editorTop + editorHeight, 0xFF202020);
        InvButtonsOverlay.border(g, editorLeft, editorTop, editorWidth, editorHeight, 0xFF505050);

        g.text(font, "Command", editorLeft + 7, editorTop + 7, 0xFFA0A0A0, false);
        commandField.setX(editorLeft + 7);
        commandField.setY(editorTop + 19);
        commandField.extractRenderState(g, mouseX, mouseY, partial);

        g.text(font, "Background Style", editorLeft + 7, editorTop + 40, 0xFFA0A0A0, false);
        for (int i = 0; i < 5; i++) {
            int bx = editorLeft + 7 + i * 20;
            int by = editorTop + 52;
            g.blit(RenderPipelines.GUI_TEXTURED, BUTTONS, bx, by, i * 18f, 0f, 18, 18, 18, 18,
                    SHEET_W, SHEET_H, 0xFFFFFFFF);
            if (editing.backgroundIndex == i) {
                InvButtonsOverlay.border(g, bx - 1, by - 1, 20, 20, 0xFF00FF00);
            }
        }

        int filterY = editorTop + 75;
        int tabWidth = (editorWidth - 14) / Filter.values().length;
        for (int i = 0; i < Filter.values().length; i++) {
            Filter mode = Filter.values()[i];
            int bx = editorLeft + 7 + i * tabWidth;
            boolean active = filter == mode;
            g.fill(bx, filterY, bx + tabWidth, filterY + 20, active ? 0xFF606060 : 0xFF303030);
            InvButtonsOverlay.border(g, bx, filterY, tabWidth, 20, active ? 0xFFFFFFFF : 0xFF505050);
            g.fakeItem(mode.icon, bx + (tabWidth - 16) / 2, filterY + 2);
        }

        g.text(font, "Search Icon", editorLeft + 7, editorTop + 100, 0xFFA0A0A0, false);
        searchField.setX(editorLeft + 7);
        searchField.setY(editorTop + 112);
        searchField.extractRenderState(g, mouseX, mouseY, partial);

        if (filter == Filter.SKULLS) {
            String label = "Add Skull by ID";
            int titleX = editorLeft + 7;
            int titleY = editorTop + 135;
            g.text(font, label, titleX, titleY, 0xFFA0A0A0, false);

            int infoX = titleX + font.width(label) + 5;
            int infoY = titleY - 1;
            g.blit(RenderPipelines.GUI_TEXTURED, INFO, infoX, infoY, 0f, 0f, 10, 10, 10, 10, 10, 10,
                    0xFFFFFFFF);
            if (mouseX >= infoX && mouseX < infoX + 10 && mouseY >= infoY && mouseY < infoY + 10) {
                g.fill(infoX, infoY, infoX + 10, infoY + 10, 0x40FFFFFF);
            }

            skullField.setX(editorLeft + 7);
            skullField.setY(editorTop + 147);
            skullField.extractRenderState(g, mouseX, mouseY, partial);
        }

        renderIconList(g, mouseX, mouseY, listTop(), listHeight());
    }

    /** The skull list is shorter because the "add by id" field sits above it. */
    private int listTop() {
        return editorTop + (filter == Filter.SKULLS ? 170 : 135);
    }

    private int listHeight() {
        return filter == Filter.SKULLS ? 47 : 82;
    }

    private void renderInfoPanel(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int panelW = 150;
        int panelH = 190;
        int panelX = 10;
        int panelY = (height - panelH) / 2;

        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xFF202020);
        InvButtonsOverlay.border(g, panelX, panelY, panelW, panelH, 0xFF505050);

        int closeX = panelX + panelW - 14;
        int closeY = panelY + 4;
        boolean hover = mouseX >= closeX && mouseX <= closeX + 10 && mouseY >= closeY && mouseY <= closeY + 10;
        g.fill(closeX, closeY, closeX + 10, closeY + 10, hover ? 0xFFFF0000 : 0xFFCC0000);
        g.centeredText(font, "x", closeX + 5, closeY + 1, 0xFFFFFFFF);

        int textX = panelX + 8;
        int textY = panelY + 8;
        g.text(font, Component.literal("How to find Skull IDs")
                .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD), textX, textY, 0xFFFFFFFF, true);
        textY += 18;

        String[] steps = {
                "1. Go to a site like minecraft-heads.com",
                "2. Find and select a head.",
                "3. Look for the 'Texture URL' or 'Minecraft-URL' section.",
                "4. Copy only the long alphanumeric string at the very end of the URL.",
                "5. Paste it into the 'Add Skull ID' field preceded by 'skull:'.",
                "   Example: skull:a6cc4...",
        };
        for (String step : steps) {
            for (FormattedCharSequence line : font.split(Component.literal(step), panelW - 16)) {
                g.text(font, line, textX, textY, 0xFFA0A0A0, false);
                textY += font.lineHeight + 2;
            }
            textY += 4;
        }
    }

    private void renderIconList(GuiGraphicsExtractor g, int mouseX, int mouseY, int listY, int listH) {
        int listX = editorLeft + 7;
        int listW = editorWidth - 14;

        g.fill(listX, listY, listX + listW, listY + listH, 0xFF101010);
        g.enableScissor(listX, listY, listX + listW, listY + listH);

        scroll.tick();
        int cols = 6;
        int offset = scroll.value();
        int first = offset / 20 * cols;
        int last = Math.min(first + 42, icons.size());

        Icon hovered = null;
        for (int i = first; i < last; i++) {
            Icon icon = icons.get(i);
            int ix = listX + 2 + (i - first) % cols * 20;
            int iy = listY + 2 + (i - first) / cols * 20 - offset % 20;
            icon.render(g, ix, iy);
            if (mouseX >= ix && mouseX < ix + 18 && mouseY >= iy && mouseY < iy + 18) {
                g.fill(ix, iy, ix + 18, iy + 18, 0x40FFFFFF);
                hovered = icon;
            }
        }
        g.disableScissor();

        if (hovered != null) {
            g.setTooltipForNextFrame(font, Component.literal(hovered.name()), mouseX, mouseY);
        }

        int rows = (int) Math.ceil(icons.size() / (double) cols);
        int visibleRows = listH / 20;
        if (rows > visibleRows) {
            float ratio = Math.min(1f, offset / (float) ((rows - visibleRows) * 20));
            int barH = Math.max(10, (int) (listH * (visibleRows / (float) rows)));
            int barY = listY + (int) ((listH - barH) * ratio);
            g.fill(listX + listW - 2, barY, listX + listW, barY + barH, 0xFF808080);
        }
    }

    /** Keeps the panel beside the button being edited, and on screen. */
    private void positionEditor() {
        if (editing == null) {
            return;
        }
        int btnX = guiLeft + editing.x + (editing.anchorRight ? xSize : 0);
        int btnY = guiTop + editing.y + (editing.anchorBottom ? ySize : 0);

        editorLeft = btnX + 25;
        editorTop = btnY - 20;
        if (editorLeft + editorWidth > width) {
            editorLeft = btnX - editorWidth - 5;
        }
        if (editorTop + editorHeight > height) {
            editorTop = height - editorHeight - 5;
        }
        if (editorTop < 0) {
            editorTop = 5;
        }
    }

    // ------------------------------------------------------------------ the icon search

    private void search(String query) {
        icons.clear();
        String needle = query.toLowerCase(java.util.Locale.ROOT).trim();

        if (filter == Filter.ALL || filter == Filter.SKULLS) {
            for (Map.Entry<String, String> entry : SKULL_ICONS.entrySet()) {
                if (entry.getKey().toLowerCase(java.util.Locale.ROOT).contains(needle)) {
                    ItemStack head = InvButtons.skull(entry.getValue());
                    head.set(DataComponents.CUSTOM_NAME, Component.literal(entry.getKey()));
                    icons.add(new StackIcon(head));
                }
            }
            for (HypixelSkulls.Skull skull : HypixelSkulls.all()) {
                if (skull.name().toLowerCase(java.util.Locale.ROOT).contains(needle)) {
                    icons.add(new SkyblockIcon(skull));
                }
            }
        }

        if (filter == Filter.ALL || filter == Filter.MISC) {
            for (Map.Entry<String, Identifier> entry : InvButtons.CUSTOM_TEXTURES.entrySet()) {
                if (entry.getKey().toLowerCase(java.util.Locale.ROOT).contains(needle)) {
                    icons.add(new TextureIcon(entry.getKey(), entry.getValue()));
                }
            }
        }

        if (filter == Filter.ALL || filter == Filter.ITEMS || filter == Filter.BLOCKS) {
            for (Item item : BuiltInRegistries.ITEM) {
                boolean block = item instanceof BlockItem;
                if (filter == Filter.BLOCKS && !block) {
                    continue;
                }
                if (filter == Filter.ITEMS && block) {
                    continue;
                }
                ItemStack stack = new ItemStack(item);
                Identifier id = BuiltInRegistries.ITEM.getKey(item);
                if (id.toString().contains(needle)
                        || stack.getHoverName().getString().toLowerCase(java.util.Locale.ROOT)
                        .contains(needle)) {
                    icons.add(new StackIcon(stack));
                    // A search that matches half the registry is not a list anyone scrolls; the cap
                    // only applies once something has been typed, so the unfiltered list is whole.
                    if (icons.size() > 500 && !needle.isEmpty()) {
                        break;
                    }
                }
            }
        }

        icons.sort(Comparator
                .comparingInt((Icon i) -> i.name().toLowerCase(java.util.Locale.ROOT)
                        .startsWith(needle) ? 0 : 1)
                .thenComparingInt(i -> i instanceof SkyblockIcon ? 1 : 0)
                .thenComparing(i -> i.name().toLowerCase(java.util.Locale.ROOT)));
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();

        if (savePanelOpen) {
            return savePanelClicked(event, doubleClick, mouseX, mouseY);
        }

        int x = 10;
        int y = 10;
        if (inside(mouseX, mouseY, x, y, 100, 20)) {
            savePanelOpen = true;
            profileField.setFocused(true);
            return true;
        }
        if (inside(mouseX, mouseY, x, y + 25, 50, 20)) {
            Minecraft.getInstance().keyboardHandler.setClipboard(InvButtons.exportToClipboard());
            setStatus("Exported to Clipboard!");
            return true;
        }
        if (inside(mouseX, mouseY, x, y + 50, 50, 20)) {
            boolean ok = InvButtons.importFromClipboard(
                    Minecraft.getInstance().keyboardHandler.getClipboard());
            setStatus(ok ? "Imported Successfully!" : "Invalid Clipboard!");
            if (ok) {
                editing = null;
                editorOpen = false;
            }
            return true;
        }

        if (infoOpen) {
            int panelW = 150;
            int panelH = 190;
            int panelX = 10;
            int panelY = (height - panelH) / 2;
            if (inside(mouseX, mouseY, panelX + panelW - 14, panelY + 4, 10, 10)) {
                infoOpen = false;
                return true;
            }
            if (inside(mouseX, mouseY, panelX, panelY, panelW, panelH)) {
                return true;
            }
        }

        if (editing != null && editorOpen && panelClicked(event, doubleClick, mouseX, mouseY)) {
            return true;
        }

        for (InvButton button : InvButtons.buttons()) {
            int bx = guiLeft + button.x + (button.anchorRight ? xSize : 0);
            int by = guiTop + button.y + (button.anchorBottom ? ySize : 0);
            if (!inside(mouseX, mouseY, bx, by, 18, 18)) {
                continue;
            }
            if (editing != button) {
                editing = button;
                editorOpen = false;
                infoOpen = false;
                unfocusFields();
            } else if (!editorOpen) {
                openPanelFor(button);
            }
            dragging = true;
            dragOffsetX = (int) mouseX - bx;
            dragOffsetY = (int) mouseY - by;
            return true;
        }

        if (event.button() == 1) {
            InvButton added = new InvButton((int) mouseX - guiLeft, (int) mouseY - guiTop,
                    "/cmd", "minecraft:stone");
            InvButtons.buttons().add(added);
            editing = added;
            editorOpen = true;
            commandField.setValue(added.command);
            commandField.setFocused(true);
            dragging = true;
            dragOffsetX = 9;
            dragOffsetY = 9;
            filter = Filter.ALL;
            search("");
            infoOpen = false;
            return true;
        }

        editing = null;
        dragging = false;
        editorOpen = false;
        infoOpen = false;
        return super.mouseClicked(event, doubleClick);
    }

    /** Second click on an already-selected button: open its panel, filled in from it. */
    private void openPanelFor(InvButton button) {
        editorOpen = true;
        if (!button.command.startsWith("/")) {
            button.command = "/" + button.command;
        }
        commandField.setValue(button.command);
        commandField.moveCursorTo(button.command.length(), false);
        commandField.setFocused(true);
        searchField.setFocused(false);
        skullField.setFocused(false);
        skullField.setValue(button.itemId != null && button.itemId.startsWith("skull:")
                ? button.itemId : "");
        filter = Filter.ALL;
        search(searchField.getValue());
    }

    private boolean savePanelClicked(MouseButtonEvent event, boolean doubleClick,
                                     double mouseX, double mouseY) {
        int panelW = 200;
        int panelH = 100;
        int panelX = (width - panelW) / 2;
        int panelY = (height - panelH) / 2;

        if (inside(mouseX, mouseY, panelX + panelW - 17, panelY + 5, 12, 12)) {
            savePanelOpen = false;
            profileField.setFocused(false);
            return true;
        }
        boolean onField = profileField.mouseClicked(event, doubleClick);
        profileField.setFocused(onField);
        if (onField) {
            return true;
        }
        if (inside(mouseX, mouseY, panelX + (panelW - 60) / 2, panelY + 65, 60, 20)) {
            saveProfile();
        }
        // Everything else inside the panel, and outside it too - the panel is modal.
        return true;
    }

    private void saveProfile() {
        String name = profileField.getValue().trim();
        if (name.isEmpty()) {
            return;
        }
        InvButtons.saveProfile(name);
        setStatus("Saved: " + name);
        savePanelOpen = false;
        profileField.setValue("");
    }

    /** A click inside the edit panel. Returns false only when it missed the panel entirely. */
    private boolean panelClicked(MouseButtonEvent event, boolean doubleClick,
                                 double mouseX, double mouseY) {
        positionEditor();
        if (!inside(mouseX, mouseY, editorLeft, editorTop, editorWidth, editorHeight)) {
            return false;
        }

        int commandY = editorTop + 19;
        int searchY = editorTop + 112;
        boolean onCommand = inside(mouseX, mouseY, editorLeft + 7, commandY,
                commandField.getWidth(), commandField.getHeight());
        boolean onSearch = inside(mouseX, mouseY, editorLeft + 7, searchY,
                searchField.getWidth(), searchField.getHeight());
        boolean onSkull = false;

        if (filter == Filter.SKULLS) {
            onSkull = inside(mouseX, mouseY, editorLeft + 7, editorTop + 147,
                    skullField.getWidth(), skullField.getHeight());
            int infoX = editorLeft + 7 + font.width("Add Skull by ID") + 5;
            if (inside(mouseX, mouseY, infoX, editorTop + 134, 10, 10)) {
                infoOpen = !infoOpen;
                return true;
            }
        }

        commandField.setFocused(onCommand);
        searchField.setFocused(onSearch);
        skullField.setFocused(onSkull);
        if (onCommand) {
            commandField.mouseClicked(event, doubleClick);
        }
        if (onSearch) {
            searchField.mouseClicked(event, doubleClick);
        }
        if (onSkull) {
            skullField.mouseClicked(event, doubleClick);
        }

        if (mouseY >= editorTop + 52 && mouseY <= editorTop + 70) {
            for (int i = 0; i < 5; i++) {
                int bx = editorLeft + 7 + i * 20;
                if (mouseX >= bx && mouseX <= bx + 18) {
                    editing.backgroundIndex = i;
                    return true;
                }
            }
        }

        int filterY = editorTop + 75;
        if (mouseY >= filterY && mouseY <= filterY + 20) {
            int tabWidth = (editorWidth - 14) / Filter.values().length;
            int index = (int) ((mouseX - (editorLeft + 7)) / tabWidth);
            if (index >= 0 && index < Filter.values().length) {
                filter = Filter.values()[index];
                search(searchField.getValue());
                scroll.target(0);
                return true;
            }
        }

        if (mouseY >= listTop() && mouseY <= listTop() + listHeight()) {
            pickIcon(mouseX, mouseY);
        }
        return true;
    }

    private void pickIcon(double mouseX, double mouseY) {
        int listX = editorLeft + 7;
        int offset = scroll.value();
        int cols = 6;
        int col = ((int) mouseX - listX - 2) / 20;
        int row = ((int) mouseY - listTop() + offset % 20) / 20;
        if (col < 0 || col >= cols || row < 0) {
            return;
        }
        int index = offset / 20 * cols + row * cols + col;
        if (index < 0 || index >= icons.size()) {
            return;
        }
        String id = icons.get(index).configId();
        editing.itemId = id;
        if (id.startsWith("skull:")) {
            skullField.setValue(id);
        }
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        dragging = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (!dragging || editing == null) {
            return super.mouseDragged(event, dragX, dragY);
        }
        int absoluteX = (int) event.x() - dragOffsetX - guiLeft;
        int absoluteY = (int) event.y() - dragOffsetY - guiTop;

        Point snapped = gridSnap ? snapToGrid(absoluteX, absoluteY) : snapToFreeSlot(absoluteX, absoluteY);
        int proposedX = snapped.x - (editing.anchorRight ? xSize : 0);
        int proposedY = snapped.y - (editing.anchorBottom ? ySize : 0);

        if (!overlaps(proposedX, proposedY)) {
            editing.x = proposedX;
            editing.y = proposedY;
            positionEditor();
        }
        return true;
    }

    /** Snapping on: the free slots inside the window, the 20px tiling outside it. */
    private Point snapToGrid(int absoluteX, int absoluteY) {
        boolean outsideX = absoluteX < 0 || absoluteX > xSize - BUTTON_SIZE;
        boolean outsideY = absoluteY < 0 || absoluteY > ySize - BUTTON_SIZE;

        if (!outsideX && !outsideY) {
            return nearestFreeSlot(absoluteX, absoluteY);
        }

        int x = absoluteX;
        int y = absoluteY;
        if (absoluteX < 0) {
            x = -OUTER_PADDING - BUTTON_SIZE + (absoluteX + OUTER_PADDING) / OUTER_GRID * OUTER_GRID;
        } else if (absoluteX > xSize - BUTTON_SIZE) {
            x = xSize + OUTER_PADDING + (absoluteX - xSize + OUTER_PADDING) / OUTER_GRID * OUTER_GRID;
        } else if (outsideY) {
            x = TOP_BOTTOM_START_X
                    + Math.round((absoluteX - TOP_BOTTOM_START_X) / (float) OUTER_GRID) * OUTER_GRID;
        }

        if (absoluteY < 0) {
            y = -OUTER_PADDING - BUTTON_SIZE + (absoluteY + OUTER_PADDING) / OUTER_GRID * OUTER_GRID;
        } else if (absoluteY > ySize - BUTTON_SIZE) {
            y = ySize + OUTER_PADDING + (absoluteY - ySize + OUTER_PADDING) / OUTER_GRID * OUTER_GRID;
        } else if (outsideX) {
            y = OUTER_PADDING + Math.round((absoluteY - OUTER_PADDING) / (float) OUTER_GRID) * OUTER_GRID;
        }
        return new Point(x, y);
    }

    /**
     * Snapping off: free anywhere, except over the slots.
     *
     * <p>The two zones are where the inventory's own slots are - the hotbar and main grid across the
     * bottom, the armour column down the left. A button dropped there would sit on top of a slot, so
     * it magnets to the nearest gap instead.
     */
    private Point snapToFreeSlot(int absoluteX, int absoluteY) {
        boolean overBottom = absoluteX + BUTTON_SIZE > 0 && absoluteX < 176
                && absoluteY + BUTTON_SIZE > 83 && absoluteY < 166;
        boolean overLeft = absoluteX + BUTTON_SIZE > 0 && absoluteX < 26
                && absoluteY + BUTTON_SIZE > 7 && absoluteY < 83;
        if (overBottom || overLeft) {
            return nearestFreeSlot(absoluteX, absoluteY);
        }
        return new Point(absoluteX, absoluteY);
    }

    private static Point nearestFreeSlot(int x, int y) {
        Point best = null;
        double closest = Double.MAX_VALUE;
        for (Point p : FREE_SLOTS) {
            double distance = Math.pow(x - p.x, 2) + Math.pow(y - p.y, 2);
            if (distance < closest) {
                closest = distance;
                best = p;
            }
        }
        return best == null ? new Point(x, y) : best;
    }

    /** Buttons may not be stacked; a move that would overlap another one is simply not taken. */
    private boolean overlaps(int proposedX, int proposedY) {
        int x = proposedX + (editing.anchorRight ? xSize : 0);
        int y = proposedY + (editing.anchorBottom ? ySize : 0);
        for (InvButton other : InvButtons.buttons()) {
            if (other == editing) {
                continue;
            }
            int ox = other.x + (other.anchorRight ? xSize : 0);
            int oy = other.y + (other.anchorBottom ? ySize : 0);
            if (Math.abs(ox - x) < BUTTON_SIZE && Math.abs(oy - y) < BUTTON_SIZE) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (editing != null && editorOpen && mouseX >= editorLeft && mouseX <= editorLeft + editorWidth) {
            int rows = (int) Math.ceil(icons.size() / 6.0);
            int visibleRows = listHeight() / 20;
            int max = Math.max(0, (rows - visibleRows) * 20);
            scroll.target(Math.clamp(scroll.target() + (int) (-vertical * 10), 0, max));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        boolean typing = (editorOpen && (commandField.isFocused() || searchField.isFocused()
                || skullField.isFocused()))
                || (savePanelOpen && profileField.isFocused());

        if (event.key() == GLFW.GLFW_KEY_S && !typing) {
            gridSnap = !gridSnap;
            return true;
        }

        if (savePanelOpen) {
            if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
                savePanelOpen = false;
                profileField.setFocused(false);
                return true;
            }
            if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
                saveProfile();
                return true;
            }
            return profileField.keyPressed(event) || super.keyPressed(event);
        }

        if (editing != null) {
            if (editorOpen) {
                if (commandField.isFocused()) {
                    // The leading slash is not deletable, so backspace at the front does nothing
                    // rather than emptying the box and having the responder put it back.
                    if (event.key() == GLFW.GLFW_KEY_BACKSPACE) {
                        String text = commandField.getValue();
                        if (text.equals("/") || (commandField.getCursorPosition() <= 1
                                && commandField.getHighlighted().isEmpty())) {
                            return true;
                        }
                    }
                    return commandField.keyPressed(event);
                }
                if (searchField.isFocused()) {
                    return searchField.keyPressed(event);
                }
                if (skullField.isFocused()) {
                    return skullField.keyPressed(event);
                }
            }
            if ((event.key() == GLFW.GLFW_KEY_DELETE || event.key() == GLFW.GLFW_KEY_BACKSPACE)
                    && !typing) {
                InvButtons.buttons().remove(editing);
                editing = null;
                dragging = false;
                editorOpen = false;
                infoOpen = false;
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (savePanelOpen) {
            return profileField.charTyped(event) || super.charTyped(event);
        }
        if (editing != null && editorOpen) {
            if (commandField.isFocused() && commandField.charTyped(event)) {
                return true;
            }
            if (searchField.isFocused() && searchField.charTyped(event)) {
                return true;
            }
            if (skullField.isFocused() && skullField.charTyped(event)) {
                return true;
            }
        }
        return super.charTyped(event);
    }

    @Override
    public void onClose() {
        InvButtons.save();
        Minecraft mc = Minecraft.getInstance();
        if (parent != null) {
            mc.setScreen(parent);
        } else {
            super.onClose();
        }
    }

    private void unfocusFields() {
        commandField.setFocused(false);
        searchField.setFocused(false);
        skullField.setFocused(false);
    }

    private void setStatus(String text) {
        status = text;
        statusUntil = System.currentTimeMillis() + 3000;
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    /** The icon list's scroll, eased so a wheel notch glides rather than jumps. */
    private static final class Lerp {
        private int value;
        private int target;

        void tick() {
            if (value == target) {
                return;
            }
            int difference = target - value;
            int step = difference / 5;
            value += step == 0 ? (difference > 0 ? 1 : -1) : step;
        }

        int value() {
            return value;
        }

        int target() {
            return target;
        }

        void target(int t) {
            target = t;
        }
    }
}
