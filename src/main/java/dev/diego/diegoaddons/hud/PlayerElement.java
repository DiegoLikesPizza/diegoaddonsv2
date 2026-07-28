package dev.diego.diegoaddons.hud;

import com.render.api.gui.ContainerComponent;
import com.render.api.gui.EntityModelComponent;
import com.render.api.gui.layout.GuiAlignment;
import dev.diego.diegoaddons.gui.GuiColors;
import dev.diego.diegoaddons.module.modules.PlayerHudModule;
import net.minecraft.client.Minecraft;

/**
 * Your own character, as a HUD element of its own.
 *
 * <p>It was a column of the inventory HUD, which meant it could only be where the inventory was and
 * only as tall as the grid beside it. On its own it can sit anywhere and be any size - which is the
 * point of it: it is there to be looked at, not to be read.
 *
 * <p>The framing numbers are RenderLib's, and worth restating because both were wrong once: the
 * preview is scaled in pixels <em>per block</em>, so the entity's height is a factor and not a
 * divisor; and {@code yPivot} nudges an already-centred entity rather than doing the centring.
 */
public class PlayerElement extends HudElement {
    private static final float ASPECT = 0.7f;          // vanilla's 49x70 inventory preview
    private static final float FILL = 0.8f;            // share of the box height the body takes
    private static final float RENDERLIB_FIT = 0.625f; // the constant in RenderLib's scale
    private static final float BB_HEIGHT = 1.8f;
    private static final float PIVOT = 0.0625f;
    private static final float FACING = 180f;          // the mannequin spawns facing away

    private final PlayerHudModule player;
    private EntityModelComponent model;
    private String lastShape = "";

    public PlayerElement(PlayerHudModule module, ContainerComponent root) {
        super(module, root);
        this.player = module;
    }

    @Override
    public boolean update(Minecraft mc) {
        if (mc.player == null) {
            return false;
        }
        String shape = player.height() + "|" + player.showBackground();
        if (themeChanged() || !shape.equals(lastShape)) {
            lastShape = shape;
            rebuild(mc);
        }
        return true;
    }

    private void rebuild(Minecraft mc) {
        root.clearChildren();
        float height = player.height();
        float width = height * ASPECT;

        asRow(root, width + PAD_X * 2f, 0f).padding(PAD_Y, PAD_X)
                .justifyContent(GuiAlignment.CENTER);
        if (player.showBackground()) {
            applyBackground(root, 8f);
        } else {
            root.backgroundColor(GuiColors.of(0x00000000)).borderWidth(0f);
        }

        model = new EntityModelComponent();
        model.playerUuid(mc.player.getUUID());
        model.size(width, height);
        model.zoom(FILL * height / (RENDERLIB_FIT * BB_HEIGHT * Math.min(width, height)));
        model.yPivot(PIVOT);
        model.yRotation(FACING);
        root.add(model);
        sizeRoot(width + PAD_X * 2f, height + PAD_Y * 2f);
    }
}
