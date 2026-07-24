package dev.diego.diegoaddons.util;

import dev.diego.diegoaddons.module.modules.EtherwarpModule;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/**
 * Shows where an etherwarp would take you while you hold sneak with an etherwarp item.
 *
 * <p>Green means the warp would succeed, red that it would not. The check mirrors what the ability
 * needs: a solid block you are looking at within range, and <b>two blocks of clear space above it</b>
 * for you to stand in. That last part is what usually fails, and it is invisible until you try.
 *
 * <p>The item is recognised by its lore mentioning the ability rather than by name, so any item that
 * has it - transmission tuners aside - lights up.
 */
public final class EtherwarpHelper {
    /** Vanilla etherwarp reach before tuners; the highlight is about the block, not the exact cap. */
    private static final double RANGE = 57.0;
    private static final double EDGE = 0.05;
    private static final int OK = 0xFF00FF00;
    private static final int BAD = 0xFFFF3333;

    private static BlockPos target;
    private static boolean valid;

    private EtherwarpHelper() {
    }

    /** The block currently aimed at, or null when the helper is not active. */
    public static BlockPos target() {
        return target;
    }

    public static boolean valid() {
        return valid;
    }

    public static void reset() {
        target = null;
        valid = false;
    }

    /** Called every client tick while the module is on. */
    public static void tick(Minecraft mc) {
        EtherwarpModule mod = EtherwarpModule.INSTANCE;
        target = null;
        valid = false;
        if (mod == null || !mod.isEnabled() || mc.player == null || mc.level == null) {
            return;
        }
        if (!mc.player.isShiftKeyDown() || !holdsEtherwarpItem(mc)) {
            return;
        }

        Vec3 eye = mc.player.getEyePosition();
        Vec3 end = eye.add(mc.player.getLookAngle().scale(RANGE));
        BlockHitResult hit = mc.level.clip(new ClipContext(
                eye, end, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, mc.player));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        target = hit.getBlockPos();
        valid = hasHeadroom(mc, target);

        WorldRender.thickBox(new AABB(target), valid ? OK : BAD, EDGE, true);
    }

    /** The warp puts you on top of the block, so the two blocks above it have to be free. */
    private static boolean hasHeadroom(Minecraft mc, BlockPos pos) {
        for (int dy = 1; dy <= 2; dy++) {
            BlockState above = mc.level.getBlockState(pos.above(dy));
            if (!above.getCollisionShape(mc.level, pos.above(dy)).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** True when the held item carries the ability, read from its lore. */
    private static boolean holdsEtherwarpItem(Minecraft mc) {
        ItemStack held = mc.player.getMainHandItem();
        if (held.isEmpty()) {
            return false;
        }
        var lore = held.get(net.minecraft.core.component.DataComponents.LORE);
        if (lore == null) {
            return false;
        }
        for (var line : lore.lines()) {
            if (LegacyText.strip(line.getString()).toLowerCase(Locale.ROOT).contains("etherwarp")) {
                return true;
            }
        }
        return false;
    }
}
