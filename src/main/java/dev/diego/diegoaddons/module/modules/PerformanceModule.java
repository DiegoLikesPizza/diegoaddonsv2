package dev.diego.diegoaddons.module.modules;

import dev.diego.diegoaddons.module.BooleanSetting;
import dev.diego.diegoaddons.module.HudModule;
import dev.diego.diegoaddons.util.TpsTracker;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A combined performance / connection readout drawn as one multi-line HUD chip. Each metric - FPS,
 * ping, mod version, server TPS and server IP - has its own toggle in the module's settings, so you
 * pick exactly which rows appear. When no metric is enabled the chip hides itself.
 *
 * <p>Server TPS is estimated by {@link TpsTracker} (fed from server time packets); ping comes from
 * the player list entry; the IP is the address of the current server (or "Singleplayer").
 */
public class PerformanceModule extends HudModule {
    private static final String MOD_VERSION = FabricLoader.getInstance()
            .getModContainer("diegoaddonsv2")
            .map(c -> c.getMetadata().getVersion().getFriendlyString())
            .orElse("1.0.0");

    private final BooleanSetting showFps = new BooleanSetting(this, "fps", "FPS", true);
    private final BooleanSetting showPing = new BooleanSetting(this, "ping", "Ping", true);
    private final BooleanSetting showVersion = new BooleanSetting(this, "version", "Mod version", true);
    private final BooleanSetting showTps = new BooleanSetting(this, "tps", "Server TPS", true);
    private final BooleanSetting showIp = new BooleanSetting(this, "ip", "Server IP", true);

    public PerformanceModule() {
        super("performance", "Performance", "Combined FPS / ping / TPS / version / IP readout.");
        settings.add(showFps);
        settings.add(showPing);
        settings.add(showVersion);
        settings.add(showTps);
        settings.add(showIp);
    }

    @Override
    protected String label() {
        return "Performance";
    }

    @Override
    protected String value(Minecraft mc) {
        return null; // multi-line module; the chip is built in hudLines/editorLines instead
    }

    @Override
    public List<String> hudLines(Minecraft mc) {
        return build(mc, false);
    }

    @Override
    public List<String> editorLines(Minecraft mc) {
        List<String> lines = build(mc, true);
        return lines.isEmpty() ? List.of("Performance (no metrics)") : lines;
    }

    private List<String> build(Minecraft mc, boolean editor) {
        List<String> out = new ArrayList<>();
        if (showFps.get()) {
            out.add(row("FPS", String.valueOf(mc.getFps())));
        }
        if (showPing.get()) {
            out.add(row("Ping", ping(mc, editor)));
        }
        if (showVersion.get()) {
            out.add(row("Mod", MOD_VERSION));
        }
        if (showTps.get()) {
            out.add(row("TPS", tps(editor)));
        }
        if (showIp.get()) {
            out.add(row("IP", ip(mc, editor)));
        }
        return out;
    }

    private String row(String label, String value) {
        return showLabel.get() ? label + ": " + value : value;
    }

    private String ping(Minecraft mc, boolean editor) {
        if (mc.getConnection() != null && mc.player != null) {
            PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
            if (info != null) {
                return info.getLatency() + " ms";
            }
        }
        return editor ? "42 ms" : "—";
    }

    private String tps(boolean editor) {
        if (TpsTracker.hasData()) {
            return String.format(Locale.ROOT, "%.1f", TpsTracker.tps());
        }
        return editor ? "20.0" : "—";
    }

    private String ip(Minecraft mc, boolean editor) {
        if (mc.hasSingleplayerServer()) {
            return "Singleplayer";
        }
        ServerData sd = mc.getCurrentServer();
        if (sd != null && sd.ip != null && !sd.ip.isEmpty()) {
            return sd.ip;
        }
        return editor ? "play.example.net" : "—";
    }
}
