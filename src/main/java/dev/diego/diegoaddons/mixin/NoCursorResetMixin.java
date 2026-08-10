package dev.diego.diegoaddons.mixin;

import dev.diego.diegoaddons.module.modules.NoCursorResetModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Backs {@link NoCursorResetModule}: puts the cursor back where it was instead of in the middle of
 * the window when the mouse is released.
 *
 * <p>Both halves of the problem are in vanilla's own two methods. {@code grabMouse} - which runs
 * the moment a screen closes, even for the single frame between one chest menu and the next -
 * overwrites {@code xpos}/{@code ypos} with the centre of the window. {@code releaseMouse} then
 * moves the real cursor to whatever those now say, which is why every menu that opens another one
 * throws your hand back to the middle.
 *
 * <p>So the position is taken at the top of {@code grabMouse}, before vanilla can overwrite it, and
 * put back in {@code releaseMouse} in place of the centre. Nothing else about either method changes:
 * the mouse is still released, the cursor still becomes visible, and with nothing yet remembered
 * (the first grab of a session) vanilla is left to do exactly what it always did.
 */
@Mixin(MouseHandler.class)
public class NoCursorResetMixin {

    @Shadow
    private double xpos;

    @Shadow
    private double ypos;

    @Shadow
    private boolean mouseGrabbed;

    @Shadow
    @org.spongepowered.asm.mixin.Final
    private Minecraft minecraft;

    /** The last position the cursor had while a screen was up, or -1 when there is none yet. */
    private double diego$keptX = -1;
    private double diego$keptY = -1;

    @Inject(method = "grabMouse", at = @At("HEAD"))
    private void diego$rememberCursor(CallbackInfo ci) {
        // Only when this call is actually going to grab: vanilla returns early if the window is not
        // focused or the mouse is already held, and in neither case are xpos/ypos about to be lost.
        if (NoCursorResetModule.on() && !mouseGrabbed && minecraft.isWindowActive()) {
            diego$keptX = xpos;
            diego$keptY = ypos;
        }
    }

    @Inject(method = "releaseMouse", at = @At("HEAD"), cancellable = true)
    private void diego$restoreCursor(CallbackInfo ci) {
        if (!NoCursorResetModule.on() || !mouseGrabbed || diego$keptX < 0 || diego$keptY < 0) {
            return;
        }
        // Clamped, because the window may have been resized while the mouse was held - a remembered
        // position outside it would put the cursor somewhere unreachable.
        double x = Math.clamp(diego$keptX, 0, minecraft.getWindow().getScreenWidth());
        double y = Math.clamp(diego$keptY, 0, minecraft.getWindow().getScreenHeight());
        mouseGrabbed = false;
        xpos = x;
        ypos = y;

        // Not through InputConstants.grabOrReleaseMouse, and that is the whole fix. It sets the
        // cursor position *first* and the input mode second - and leaving GLFW_CURSOR_DISABLED is
        // exactly when GLFW puts the cursor back where it was when the cursor was disabled, which
        // vanilla had just set to the centre of the window. So the position went in, GLFW overwrote
        // it a line later, and the cursor landed in the middle anyway - the bug this module exists
        // to fix, reproduced faithfully inside the fix.
        //
        // Mode first, position second: then GLFW does its restore and ours is what lands last.
        long handle = minecraft.getWindow().handle();
        GLFW.glfwSetInputMode(handle, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
        GLFW.glfwSetCursorPos(handle, x, y);
        ci.cancel();
    }
}
