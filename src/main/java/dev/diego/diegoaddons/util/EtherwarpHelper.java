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
    /**
     * When a warp was last lined up, in client ticks. The sound swap needs to know that a warp was
     * actually about to happen: the teleport sound arrives from the server a moment after the aim
     * is released, by which time nothing is being aimed at any more.
     */
    private static long armedAt = Long.MIN_VALUE;
    private static long ticks;
    /**
     * How long after aiming a valid warp its sound is still taken to be ours. Kept short on purpose:
     * a second was long enough to sneak, stand up, and fire a plain Instant Transmission - which
     * then got the etherwarp's sound.
     */
    private static final long ARMED_TICKS = 5;

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
        armedAt = Long.MIN_VALUE;
    }

    /** Whether a valid warp was lined up just now - see {@link #armedAt}. */
    public static boolean armedRecently() {
        return ticks - armedAt <= ARMED_TICKS;
    }

    /** Called every client tick while the module is on. */
    public static void tick(Minecraft mc) {
        EtherwarpModule mod = EtherwarpModule.INSTANCE;
        ticks++;
        target = null;
        valid = false;
        if (mod == null || !mod.isEnabled() || mc.player == null || mc.level == null) {
            return;
        }
        boolean sneaking = mc.player.isShiftKeyDown();
        boolean holding = holdsEtherwarpItem(mc);
        if (!sneaking || !holding) {
            debug(mc, mod, "sneaking=" + sneaking + " etherwarpItem=" + holding
                    + " held=" + mc.player.getMainHandItem().getHoverName().getString());
            return;
        }

        Vec3 eye = mc.player.getEyePosition();
        Vec3 end = eye.add(mc.player.getLookAngle().scale(RANGE));
        BlockHitResult hit = mc.level.clip(new ClipContext(
                eye, end, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, mc.player));
        if (hit.getType() != HitResult.Type.BLOCK) {
            debug(mc, mod, "aiming at nothing within " + (int) RANGE + " blocks");
            return;
        }
        target = hit.getBlockPos();
        valid = hasHeadroom(mc, target);
        if (valid) {
            armedAt = ticks;
        }

        debug(mc, mod, "target=" + target.toShortString() + " valid=" + valid
                + " highlight=" + mod.highlight());
        if (mod.highlight()) {
            WorldRender.thickBox(new AABB(target), valid ? OK : BAD, EDGE, true);
        }
    }

    /** Says what the helper is seeing, at most once a second, while the debug option is on. */
    private static void debug(Minecraft mc, EtherwarpModule mod, String what) {
        if (!mod.debug() || ticks - lastSaid < 20 || mc.gui == null) {
            return;
        }
        lastSaid = ticks;
        mc.gui.getChat().addClientSystemMessage(
                net.minecraft.network.chat.Component.literal("§b[Etherwarp] §f" + what));
    }

    private static long lastSaid = Long.MIN_VALUE;

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
