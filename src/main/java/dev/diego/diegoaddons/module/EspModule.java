package dev.diego.diegoaddons.module;

/**
 * A module that draws boxes around things, with the two settings all of them share: what shape to
 * draw and what colour to draw it in.
 *
 * <p>They were each hard-coding a colour and an outline before. Putting both here means a new ESP
 * feature gets the whole set for free, and - more to the point - that they all behave the same way,
 * rather than one having a colour picker and the next having whatever its author typed.
 *
 * <p>Subclasses add their own settings as usual; these two are added first, so they sit at the top
 * of the module's list where the shape and colour of the thing belong.
 */
public abstract class EspModule extends Module {
    /** The shapes an ESP can take. Indices match the cycle setting's options. */
    public static final int OUTLINE = 0;
    public static final int BOX = 1;
    public static final int SQUARE_2D = 2;
    /** The entity's own model outlined, rather than a box around it. */
    public static final int MODEL = 3;

    private final CycleSetting style = new CycleSetting(this, "espStyle", "Style", OUTLINE,
            "Box outline", "Filled box", "2D square", "Player outline");
    private final ColorSetting color;

    protected EspModule(String id, Category category, String name, String description,
                        int defaultColor) {
        super(id, category, name, description);
        this.color = new ColorSetting(this, "espColor", "Color", defaultColor);
        settings.add(style);
        settings.add(color);
    }

    /** Which shape to draw: {@link #OUTLINE}, {@link #BOX} or {@link #SQUARE_2D}. */
    public int espStyle() {
        return style.get();
    }

    public ColorSetting espColor() {
        return color;
    }

    /** The colour to draw in right now - what the old fixed {@code color()} used to return. */
    public int color() {
        return color.argb();
    }
}
