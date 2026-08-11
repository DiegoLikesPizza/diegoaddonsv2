package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.EspModule;
import dev.diego.diegoaddons.util.EntityEsp;
import net.minecraft.client.Minecraft;

import java.util.Locale;

/**
 * Boxes the pests in the Garden.
 *
 * <p>Pests are the one Garden mob you have to find before you can do anything about it: they are
 * small, they sit inside the crop you are farming, and only a vacuum can touch them. Finding them by
 * name plate rather than by entity type is what {@link EntityEsp} does for every other mob in the
 * mod - see {@link dev.diego.diegoaddons.util.Pests#pestOnPlate}.
 */
public class PestEspModule extends EspModule {
    public static PestEspModule INSTANCE;

    /**
     * Off by default. A box that only appears once you are holding the tool that can act on it is
     * tidier, but it also hides the pest while you are still farming - which is exactly when you
     * want to know one is there.
     */
    private final BooleanSetting onlyWithVacuum =
            new BooleanSetting(this, "onlyWithVacuum", "Only with a vacuum in hand", false);

    public PestEspModule() {
        super("pestesp", Category.GARDEN, "Pest ESP",
                "Box the pests in the Garden so you can find them in the crops.",
                0xFF55FF55);
        settings.add(onlyWithVacuum);
        INSTANCE = this;
    }

    /** Whether pests should be boxed this tick. */
    public boolean shouldDraw(Minecraft mc) {
        return !onlyWithVacuum.get() || holdingVacuum(mc);
    }

    /**
     * Whether what is in hand is a pest-catching tool.
     *
     * <p>Matched on the item's name rather than on a list of ids: Hypixel has shipped several of
     * these (the plain Vacuum, the Hyper-Catcher, the InfiniVacuum™ Hooverius, and the lassos for
     * mantids) and a name check keeps working when the next one is added.
     */
    private static boolean holdingVacuum(Minecraft mc) {
        if (mc.player == null) {
            return false;
        }
        String name = mc.player.getMainHandItem().getHoverName().getString().toLowerCase(Locale.ROOT);
        return name.contains("vacuum") || name.contains("hooverius") || name.contains("lasso");
    }
}
