package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.KeybindSetting;
import dev.diego.diegoaddons.module.Module;

/**
 * Lets you lock player-inventory slots so their item can't be moved, swapped or dropped by accident.
 * Point at a slot in any container screen and press the bound key to toggle its lock. Logic lives in
 * {@link dev.diego.diegoaddons.util.SlotLocks}.
 *
 * <p>MVP scope: locking blocks clicks, hotbar-swap keys and the drop key on the slot. The drag-to-bind
 * shift-click swap from the plan is a planned follow-up.
 */
public class SlotLockModule extends Module {
    public static SlotLockModule INSTANCE;

    private final KeybindSetting lockKey =
            new KeybindSetting(this, "lockKey", "Lock/unlock hovered slot");
    private final BooleanSetting overlay =
            new BooleanSetting(this, "overlay", "Show lock overlay", true);

    public SlotLockModule() {
        super("slotlock", Category.MISC, "Slot Lock",
                "Lock inventory slots against moving, swapping or dropping.");
        settings.add(lockKey);
        settings.add(overlay);
        INSTANCE = this;
    }

    public KeybindSetting lockKey() {
        return lockKey;
    }

    public boolean overlay() {
        return overlay.get();
    }
}
