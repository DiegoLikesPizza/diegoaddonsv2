package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.gui.ReplaceWordsScreen;
import dev.diego.diegoaddons.module.ActionSetting;
import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.Module;
import net.minecraft.client.Minecraft;

/**
 * Rewrites text you read: shorten "Aspect of the Void" to "AOTV", rename a player to whatever you
 * actually call them. Purely local - nothing you send is changed, so nobody else sees the rename.
 */
public class ReplaceWordsModule extends Module {
    public static ReplaceWordsModule INSTANCE;

    private final BooleanSetting inChat =
            new BooleanSetting(this, "chat", "Replace in chat", true);
    private final BooleanSetting inItems =
            new BooleanSetting(this, "items", "Replace in item names", true);
    /** In the friend list, show the real IGN after the replacement so accounts stay identifiable. */
    private final BooleanSetting ignInFriendList =
            new BooleanSetting(this, "ignInFriends", "Keep IGN in friend list", true);
    private final ActionSetting editor =
            new ActionSetting(this, "editor", "Word list", "Open", ReplaceWordsModule::open);

    public ReplaceWordsModule() {
        super("replacewords", Category.MISC, "Replace Words",
                "Rewrite words in chat and item names.");
        settings.add(inChat);
        settings.add(inItems);
        settings.add(ignInFriendList);
        settings.add(editor);
        INSTANCE = this;
    }

    private static void open() {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new ReplaceWordsScreen(mc.screen));
    }

    public boolean inChat() {
        return inChat.get();
    }

    public boolean inItems() {
        return inItems.get();
    }

    public boolean ignInFriendList() {
        return ignInFriendList.get();
    }
}
