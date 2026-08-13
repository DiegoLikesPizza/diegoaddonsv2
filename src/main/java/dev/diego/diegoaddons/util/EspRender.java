package dev.diego.diegoaddons.util;

import dev.diego.diegoaddons.module.ColorSetting;
import dev.diego.diegoaddons.module.EspModule;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

/**
 * Draws an ESP in whichever shape and colour its module is set to, so every ESP feature reaches for
 * one call and they all end up looking the same.
 *
 * <p>A gradient runs <b>up</b> whatever is drawn, and it is one gradient rather than a stack of
 * slices pretending to be one: see {@link EspWorld}, which carries the fade on the vertex colours so
 * the GPU interpolates it.
 */
public final class EspRender {
    private static final double EDGE = 0.05;

    private EspRender() {
    }

    /** Draws {@code box} the way {@code module} is set to draw it. */
    public static void draw(AABB box, EspModule module) {
        drawImpl(null, box, module, module.espColor().argb());
    }

    /** As above, for a feature whose colour means something of its own (a slayer tier). */
    public static void draw(AABB box, EspModule module, int argb) {
        drawImpl(null, box, module, argb);
    }

    /** Both at once: an entity to outline, and a colour that is not the module's own. */
    public static void draw(Entity entity, AABB box, EspModule module, int argb) {
        drawImpl(entity, box, module, argb);
    }

    /**
     * Draws around an entity, which is what lets the model itself be outlined.
     *
     * <p>Without an entity the model style has nothing to outline, so it falls back to the box - a
     * name plate hovering over a mob identifies the mob, not an entity we can outline.
     */
    public static void draw(Entity entity, AABB box, EspModule module) {
        drawImpl(entity, box, module, module.espColor().argb());
    }

    private static void drawImpl(Entity entity, AABB box, EspModule module, int argb) {
        ColorSetting colour = module.espColor();
        boolean fade = colour.mode() != ColorSetting.SINGLE;
        int top = fade ? colour.argbAt(1f) : argb;
        int bottom = fade ? colour.argbAt(0f) : argb;

        switch (module.espStyle()) {
            case EspModule.BOX -> {
                if (fade) {
                    EspWorld.fillFade(box, translucent(bottom), translucent(top));
                } else {
                    EspWorld.fill(box, translucent(argb));
                }
            }
            case EspModule.SQUARE_2D -> EspDraw.square2d(box, argb);
            case EspModule.HIGHLIGHT -> {
                // Deliberately more opaque than the see-through fill: that one is translucent so you
                // can still make out the mob behind it, and here there is nothing behind it to see -
                // if it is drawn at all, you are already looking straight at the thing.
                if (fade) {
                    EspWorld.fillOccludedFade(box, highlight(bottom), highlight(top));
                } else {
                    EspWorld.fillOccluded(box, highlight(argb));
                }
            }
            case EspModule.MODEL -> {
                if (entity != null) {
                    EspWorld.outlineModel(entity, argb);
                } else if (fade) {
                    EspWorld.outlineFade(box, bottom, top, EDGE);
                } else {
                    EspWorld.outline(box, argb, EDGE);
                }
            }
            default -> {
                if (fade) {
                    EspWorld.outlineFade(box, bottom, top, EDGE);
                } else {
                    EspWorld.outline(box, argb, EDGE);
                }
            }
        }
    }

    /** A filled ESP at full alpha hides the thing it is pointing at. */
    private static int translucent(int argb) {
        return (argb & 0x00FFFFFF) | (0x66 << 24);
    }

    /** The highlight's alpha: heavier than a see-through fill, still not opaque. */
    private static int highlight(int argb) {
        return (argb & 0x00FFFFFF) | (0xAA << 24);
    }
}
