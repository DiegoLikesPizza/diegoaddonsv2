package dev.diego.diegoaddons.module;

/** A group shown in the left column of the ClickGUI. Order here is the display order. */
public enum Category {
    RENDER("Render"),
    HUD("HUD"),
    DUNGEONS("Dungeons"),
    MINING("Mining"),
    FORAGING("Foraging"),
    FISHING("Fishing"),
    SLAYER("Slayer"),
    MISC("Misc");

    public final String display;

    Category(String display) {
        this.display = display;
    }
}
