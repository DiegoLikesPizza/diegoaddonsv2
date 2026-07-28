package dev.diego.diegoaddons.module.modules;

import com.mojang.blaze3d.platform.Window;
import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.Module;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;

/**
 * Fills the screen without going exclusive fullscreen: the window loses its border and is sized to
 * the monitor it is on.
 *
 * <p>Minecraft's own fullscreen takes the display, which is why alt-tabbing out of it costs a mode
 * switch and a second of black screen, and why anything you have on a second monitor is fighting for
 * the same one. Borderless is an ordinary window that happens to cover everything - alt-tab is
 * instant, overlays draw over it, and a second screen carries on as normal.
 *
 * <p>Switching it off puts the window back exactly where it was, which is why the size and position
 * are recorded on the way in rather than guessed at on the way out.
 */
public class BorderlessFullscreenModule extends Module {
    public static BorderlessFullscreenModule INSTANCE;

    private final dev.diego.diegoaddons.module.BooleanSetting keepRunning =
            new dev.diego.diegoaddons.module.BooleanSetting(this, "keepRunning",
                    "Keep playing when unfocused", true);

    /** Whether the mouse was grabbed when focus went, so it can be taken back when focus returns. */
    private boolean wasGrabbed;
    private boolean wasFocused = true;

    /** Where the window was before it was made borderless, so it can be put back. */
    private int savedX;
    private int savedY;
    private int savedWidth;
    private int savedHeight;
    private boolean saved;

    public BorderlessFullscreenModule() {
        super("borderless", Category.RENDER, "Borderless Fullscreen",
                "Fill the screen with a borderless window instead of exclusive fullscreen.");
        settings.add(keepRunning);
        INSTANCE = this;
    }

    /** Whether losing focus should be a non-event rather than a pause. */
    public boolean keepRunning() {
        return keepRunning.get();
    }

    /**
     * Hands the mouse back to the desktop the moment focus goes, and takes it again when it returns.
     *
     * <p>Suppressing the pause menu is only half of "stop taking my input": the cursor is still
     * captured, so the Start menu opens behind a game that is quietly eating the mouse. Vanilla only
     * lets go because the pause screen opens - with no pause screen, nothing lets go.
     */
    @Override
    public void onClientTick(Minecraft mc) {
        if (mc.getWindow() == null) {
            return;
        }
        if (pending != 0 && mc.screen == null) {
            int what = pending;
            pending = 0;
            if (what > 0) {
                apply();
            } else {
                restore();
            }
        }
        if (!keepRunning.get()) {
            return;
        }
        boolean focused = mc.getWindow().isFocused();
        if (focused == wasFocused) {
            return;
        }
        wasFocused = focused;
        if (!focused) {
            wasGrabbed = mc.mouseHandler.isMouseGrabbed();
            if (wasGrabbed) {
                mc.mouseHandler.releaseMouse();
            }
        } else if (wasGrabbed && mc.screen == null && mc.level != null) {
            mc.mouseHandler.grabMouse();
            wasGrabbed = false;
        }
    }

    /**
     * The window change waits for the screen to close.
     *
     * <p>You switch this on from the menu, and resizing the window out from under an open screen is
     * what crashed the game: the screen is mid-layout, its scroll containers ask to be clipped to a
     * box that no longer exists, and the clip is null. So the change is queued and applied on the
     * first tick with nothing open - which is a moment later, when you have closed the menu to go
     * and look at it anyway.
     */
    private int pending;   // +1 apply, -1 restore, 0 nothing

    @Override
    protected void onEnable() {
        pending = 1;
    }

    @Override
    protected void onDisable() {
        pending = -1;
        // A module that is off does not tick, so this one cannot wait for a quiet moment.
        flushPending();
    }

    /** Drops the border and grows the window to the monitor it is currently on. */
    private void apply() {
        Minecraft mc = Minecraft.getInstance();
        Window window = mc.getWindow();
        long handle = window.handle();

        // Exclusive fullscreen and borderless are two answers to the same question; leaving both on
        // would leave the window owning a display it is also trying to be an ordinary window on.
        if (window.isFullscreen()) {
            window.toggleFullScreen();
        }

        remember(handle);
        long monitor = monitorFor(handle);
        GLFWVidMode mode = GLFW.glfwGetVideoMode(monitor);
        if (mode == null) {
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer mx = stack.mallocInt(1);
            IntBuffer my = stack.mallocInt(1);
            GLFW.glfwGetMonitorPos(monitor, mx, my);
            GLFW.glfwSetWindowAttrib(handle, GLFW.GLFW_DECORATED, GLFW.GLFW_FALSE);
            GLFW.glfwSetWindowMonitor(handle, 0L, mx.get(0), my.get(0),
                    mode.width(), mode.height(), GLFW.GLFW_DONT_CARE);
        }
    }

    /** Puts the border and the old geometry back. */
    /** Runs the queued change now - used on disable, since a disabled module stops ticking. */
    public void flushPending() {
        if (pending == 0) {
            return;
        }
        int what = pending;
        pending = 0;
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            mc.execute(what > 0 ? this::apply : this::restore);
        }
    }

    private void restore() {
        Minecraft mc = Minecraft.getInstance();
        long handle = mc.getWindow().handle();
        GLFW.glfwSetWindowAttrib(handle, GLFW.GLFW_DECORATED, GLFW.GLFW_TRUE);
        if (saved) {
            GLFW.glfwSetWindowMonitor(handle, 0L, savedX, savedY, savedWidth, savedHeight,
                    GLFW.GLFW_DONT_CARE);
            saved = false;
        }
    }

    private void remember(long handle) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer x = stack.mallocInt(1);
            IntBuffer y = stack.mallocInt(1);
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            GLFW.glfwGetWindowPos(handle, x, y);
            GLFW.glfwGetWindowSize(handle, w, h);
            savedX = x.get(0);
            savedY = y.get(0);
            savedWidth = w.get(0);
            savedHeight = h.get(0);
            saved = true;
        }
    }

    /**
     * The monitor the window is mostly on, rather than always the primary one.
     *
     * <p>GLFW only tells you a window's monitor when it owns one in fullscreen, so for a windowed
     * one it has to be worked out: the monitor whose area the window's centre falls inside.
     */
    private static long monitorFor(long handle) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer wx = stack.mallocInt(1);
            IntBuffer wy = stack.mallocInt(1);
            IntBuffer ww = stack.mallocInt(1);
            IntBuffer wh = stack.mallocInt(1);
            GLFW.glfwGetWindowPos(handle, wx, wy);
            GLFW.glfwGetWindowSize(handle, ww, wh);
            int centreX = wx.get(0) + ww.get(0) / 2;
            int centreY = wy.get(0) + wh.get(0) / 2;

            var monitors = GLFW.glfwGetMonitors();
            if (monitors != null) {
                for (int i = 0; i < monitors.limit(); i++) {
                    long monitor = monitors.get(i);
                    GLFWVidMode mode = GLFW.glfwGetVideoMode(monitor);
                    if (mode == null) {
                        continue;
                    }
                    IntBuffer mx = stack.mallocInt(1);
                    IntBuffer my = stack.mallocInt(1);
                    GLFW.glfwGetMonitorPos(monitor, mx, my);
                    if (centreX >= mx.get(0) && centreX < mx.get(0) + mode.width()
                            && centreY >= my.get(0) && centreY < my.get(0) + mode.height()) {
                        return monitor;
                    }
                }
            }
        }
        return GLFW.glfwGetPrimaryMonitor();
    }
}
