package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.Category;
import dev.diego.diegoaddons.module.Module;
import dev.diego.diegoaddons.util.PartyCommands;

/**
 * Lets party members run party actions by typing them in party chat - {@code !pt} to be transferred
 * the party, {@code !warp} to warp everyone, and so on.
 *
 * <p>Each action is its own option because they are not equally safe to hand out. Transfer, warp and
 * invite are on by default; <b>kick, promote and disband are off</b>, since those let someone else
 * reshape or end your party. Blocked players are ignored regardless, and one player cannot fire
 * commands faster than every couple of seconds.
 *
 * <p>See {@link PartyCommands} for the parsing and the full trigger list.
 */
public class PartyCommandsModule extends Module {
    public static PartyCommandsModule INSTANCE;

    private final BooleanSetting transfer =
            new BooleanSetting(this, "transfer", "!pt / !transfer", true);
    private final BooleanSetting warp =
            new BooleanSetting(this, "warp", "!warp", true);
    private final BooleanSetting invite =
            new BooleanSetting(this, "invite", "!invite / !allinvite", true);
    private final BooleanSetting mute =
            new BooleanSetting(this, "mute", "!mute / !unmute", false);
    private final BooleanSetting kick =
            new BooleanSetting(this, "kick", "!kick (lets others remove members)", false);
    private final BooleanSetting promote =
            new BooleanSetting(this, "promote", "!promote / !demote", false);
    private final BooleanSetting disband =
            new BooleanSetting(this, "disband", "!disband (ends the party)", false);

    public PartyCommandsModule() {
        super("partycommands", Category.MISC, "Party Commands",
                "Let party members trigger party actions from party chat.");
        settings.add(transfer);
        settings.add(warp);
        settings.add(invite);
        settings.add(mute);
        settings.add(kick);
        settings.add(promote);
        settings.add(disband);
        INSTANCE = this;
    }

    public boolean allowTransfer() {
        return transfer.get();
    }

    public boolean allowWarp() {
        return warp.get();
    }

    public boolean allowInvite() {
        return invite.get();
    }

    public boolean allowMute() {
        return mute.get();
    }

    public boolean allowKick() {
        return kick.get();
    }

    public boolean allowPromote() {
        return promote.get();
    }

    public boolean allowDisband() {
        return disband.get();
    }
}
