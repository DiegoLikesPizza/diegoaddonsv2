package dev.diego.diegoaddons.util;

import dev.diego.diegoaddons.module.modules.SecretChimeModule;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Chimes when you get a dungeon secret.
 *
 * <p>It used to watch the secret counter and chime when it went up. That number comes from the tab
 * list, which updates when Hypixel feels like it: the sound landed a second or more after the thing
 * that earned it, or not at all. A sound that arrives late is worse than none - it tells you about a
 * moment that has already passed.
 *
 * <p>So it listens for the acts themselves instead, which the client knows the instant they happen:
 * opening a secret chest, taking a wither essence, pulling a lever, and picking an item up off the
 * floor. The count is still watched as a backstop for the kinds it cannot see, with a short window
 * that stops one secret chiming twice.
 */
public final class SecretChime {
    /** How long after an act its counter tick is taken to be the same secret. */
    private static final long DOUBLE_MS = 1500;

    private static int lastCount = -1;
    private static long lastChime;

    private SecretChime() {
    }

    public static void reset() {
        lastCount = -1;
        lastChime = 0;
    }

    /** Called every client tick: the backstop for secrets no interaction announces. */
    public static void tick(Minecraft mc) {
        SecretChimeModule mod = SecretChimeModule.INSTANCE;
        if (mod == null || !mod.isEnabled() || mc.player == null) {
            return;
        }
        int count = DungeonState.inDungeons() ? DungeonState.secretsFound() : -1;
        if (count < 0) {
            lastCount = -1;   // left the dungeon; start fresh next time
            return;
        }
        if (lastCount >= 0 && count > lastCount) {
            chime(mc);
        }
        lastCount = count;
    }

    /**
     * Called when the player interacts with a block. Chimes for the ones a secret is made of.
     *
     * <p>The block is checked rather than the outcome, because the outcome arrives from the server
     * and the point is to be immediate. A chest that turns out to be empty still chimes, which is
     * the same thing the room already told you by having a chest in it.
     */
    public static void onUseBlock(Minecraft mc, BlockPos pos) {
        SecretChimeModule mod = SecretChimeModule.INSTANCE;
        if (mod == null || !mod.isEnabled() || !mod.onInteract()
                || mc.level == null || !DungeonState.inDungeons()) {
            return;
        }
        BlockState state = mc.level.getBlockState(pos);
        if (state.is(Blocks.CHEST) || state.is(Blocks.TRAPPED_CHEST) || state.is(Blocks.LEVER)
                || state.getBlock() instanceof net.minecraft.world.level.block.SkullBlock
                || state.getBlock() instanceof net.minecraft.world.level.block.WallSkullBlock) {
            chime(mc);
        }
    }

    /** Called when the player picks an item up off the floor. */
    public static void onPickup(Minecraft mc) {
        SecretChimeModule mod = SecretChimeModule.INSTANCE;
        if (mod == null || !mod.isEnabled() || !mod.onPickup() || !DungeonState.inDungeons()) {
            return;
        }
        chime(mc);
    }

    /** Plays the chosen sound, unless something else just did. */
    private static void chime(Minecraft mc) {
        SecretChimeModule mod = SecretChimeModule.INSTANCE;
        long now = System.currentTimeMillis();
        if (mc.player == null || now - lastChime < DOUBLE_MS) {
            return;
        }
        lastChime = now;
        mc.player.playSound(mod.chosenSound(), 1.0f, mod.pitch());
    }
}
