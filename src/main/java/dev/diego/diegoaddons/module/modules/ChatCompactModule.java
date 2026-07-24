package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.Module;
import dev.diego.diegoaddons.module.NumberSetting;

/**
 * Collapses repeated chat messages into a single line with a grey counter, instead of letting the
 * same text scroll past ten times.
 *
 * <p>The window is how long a message stays eligible to be merged; after it passes, the same text
 * starts a fresh count rather than continuing an old one.
 */
public class ChatCompactModule extends Module {
    public static ChatCompactModule INSTANCE;

    /** 30 s to 5 min, in half-minute steps. */
    private final NumberSetting window =
            new NumberSetting(this, "window", "Merge window (s)", 60, 30, 300, 30);

    public ChatCompactModule() {
        super("chatcompact", Category.MISC, "Compact Chat",
                "Merge repeated messages into one line with a counter.");
        settings.add(window);
        INSTANCE = this;
    }

    public double windowSeconds() {
        return window.get();
    }
}
