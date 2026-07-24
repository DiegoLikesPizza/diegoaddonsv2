package dev.diego.diegoaddons.config;

/** One blocked player and why. Plain Gson data object; see the Better Ignore List feature. */
public class BlockedPlayer {
    public String name = "";
    /** Free text, shown when the block is enforced so you remember why you set it. */
    public String reason = "";

    public BlockedPlayer() {
    }

    public BlockedPlayer(String name, String reason) {
        this.name = name;
        this.reason = reason;
    }
}
