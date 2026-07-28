package dev.diego.diegoaddons.gui;

import com.render.api.ScreenExtension;
import com.render.api.ScreenExtensionContext;
import com.render.api.ScreenExtensionMode;
import com.render.api.gui.ButtonComponent;
import com.render.api.gui.ContainerComponent;
import com.render.api.gui.GuiGradient;
import com.render.api.gui.ScreenExtensionView;
import com.render.api.gui.layout.GuiAlignment;
import com.render.api.gui.layout.GuiDisplay;
import com.render.api.gui.layout.GuiFlexDirection;
import com.render.api.gui.layout.GuiLength;
import com.render.api.gui.layout.GuiPositionType;
import dev.diego.diegoaddons.module.modules.CustomTitleScreenModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

/**
 * The mod's own main menu, in place of Minecraft's.
 *
 * <p>A <b>replacement</b> extension rather than an additive one: RenderLib stops the host screen
 * drawing and owns the whole surface, which is what lets the row of buttons nobody presses simply
 * not exist rather than be hidden behind something.
 *
 * <p>The background is a gradient whose angle turns, one degree every few frames. A still gradient
 * behind a menu reads as a screenshot of a menu; a slow turn reads as a place. It is a property of
 * the paint rather than an animation of its own, so the motion costs a number per tick and nothing
 * else - and it can be switched off, for people who would rather it held still.
 */
public final class TitleScreenExtension implements ScreenExtension<TitleScreen> {

    @Override
    public Class<TitleScreen> screenClass() {
        return TitleScreen.class;
    }

    @Override
    public boolean matches(TitleScreen screen, ScreenExtensionContext context) {
        CustomTitleScreenModule mod = CustomTitleScreenModule.INSTANCE;
        return mod != null && mod.isEnabled();
    }

    @Override
    public ScreenExtensionMode mode() {
        return ScreenExtensionMode.REPLACEMENT;
    }

    @Override
    public ScreenExtensionView createView(TitleScreen screen, ScreenExtensionContext context) {
        return new View();
    }

    /** The menu itself: the mark and title above, the buttons below, on the moving gradient. */
    private static final class View extends ScreenExtensionView {
        private static final float DESIGN_W = 1920f;
        private static final float DESIGN_H = 1080f;
        private static final float BUTTON_W = 520f;
        private static final float BUTTON_H = 58f;
        private static final float GAP = 12f;

        private ContainerComponent backdrop;
        private float angle;
        private String builtFor = "";

        @Override
        protected void build(ScreenExtensionContext context) {
            root().display(GuiDisplay.BLOCK);
            rebuild();
        }

        @Override
        protected void tick(ScreenExtensionContext context) {
            CustomTitleScreenModule mod = CustomTitleScreenModule.INSTANCE;
            if (mod == null) {
                return;
            }
            if (!signature().equals(builtFor)) {
                rebuild();
                return;
            }
            if (mod.animate() && backdrop != null) {
                // A full turn every couple of minutes: enough that the light moves while you look at
                // it, slow enough that you never catch it moving.
                angle = (angle + 0.35f) % 360f;
                backdrop.gradient(backdrop(angle));
            }
        }

        /** What the menu depends on: the theme, and whether the Hypixel button is wanted. */
        private String signature() {
            CustomTitleScreenModule mod = CustomTitleScreenModule.INSTANCE;
            return Themes.current().name() + "|" + (mod != null && mod.showHypixel())
                    + "|" + (mod != null ? mod.server() : "");
        }

        private void rebuild() {
            builtFor = signature();
            root().clearChildren();
            Theme t = Themes.current();

            backdrop = new ContainerComponent();
            backdrop.position(GuiPositionType.ABSOLUTE).x(0f).y(0f)
                    .size(DESIGN_W, DESIGN_H)
                    .backgroundColor(GuiColors.of(t.surface()))
                    .gradient(backdrop(angle));
            root().add(backdrop);

            ContainerComponent middle = new ContainerComponent();
            middle.position(GuiPositionType.ABSOLUTE).x(0f).y(0f).size(DESIGN_W, DESIGN_H)
                    .display(GuiDisplay.FLEX)
                    .flexDirection(GuiFlexDirection.COLUMN)
                    .alignItems(GuiAlignment.CENTER)
                    .justifyContent(GuiAlignment.CENTER)
                    .rowGap(GuiLength.pixels(GAP))
                    .gap(GAP);

            middle.add(brand(t));
            middle.add(spacer(BUTTON_W, 28f));

            CustomTitleScreenModule mod = CustomTitleScreenModule.INSTANCE;
            Minecraft mc = Minecraft.getInstance();

            middle.add(button("Singleplayer", t.surface(), t.text(),
                    () -> mc.setScreen(new SelectWorldScreen(mc.screen))));
            middle.add(button("Multiplayer", t.surface(), t.text(),
                    () -> mc.setScreen(new JoinMultiplayerScreen(mc.screen))));
            if (mod != null && mod.showHypixel()) {
                middle.add(button("Join Hypixel", t.accent(), t.accentText(),
                        () -> connect(mc, mod.server())));
            }
            middle.add(spacer(BUTTON_W, 10f));
            middle.add(button("DiegoAddons", t.surfaceAlt(), t.text(),
                    () -> new DiegoClickGuiView().open()));
            middle.add(button("Settings", t.surface(), t.text(),
                    () -> mc.setScreen(new OptionsScreen(mc.screen, mc.options, false))));
            middle.add(button("Quit Game", t.surface(), t.textMuted(), mc::stop));

            root().add(middle);
        }

        /** The mark and the name, sitting above the buttons. */
        private ContainerComponent brand(Theme t) {
            ContainerComponent col = new ContainerComponent();
            col.display(GuiDisplay.FLEX).flexDirection(GuiFlexDirection.COLUMN)
                    .alignItems(GuiAlignment.CENTER).rowGap(GuiLength.pixels(10f)).gap(10f);

            ContainerComponent mark = new ContainerComponent();
            mark.size(88f, 88f).cornerRadius(24f)
                    .display(GuiDisplay.FLEX).flexDirection(GuiFlexDirection.ROW)
                    .alignItems(GuiAlignment.CENTER).justifyContent(GuiAlignment.CENTER)
                    .backgroundColor(GuiColors.of(t.accent()))
                    .gradient(new GuiGradient()
                            .startColor(GuiColors.of(t.accent()))
                            .endColor(GuiColors.of(t.accentTo())));
            mark.add(GuiText.label("D", t.accentText(), 44f));
            col.add(mark);
            col.add(GuiText.label("DiegoAddons", t.text(), 34f));
            col.add(GuiText.label("VERSION 2", t.accent(), 12f));
            return col;
        }

        /** One menu button, the width of all the others. */
        private ButtonComponent button(String label, int background, int textColor, Runnable action) {
            Theme t = Themes.current();
            ButtonComponent b = new ButtonComponent();
            b.clearChildren();
            b.onPress(action);
            b.size(BUTTON_W, BUTTON_H).padding(0f).cornerRadius(14f)
                    .display(GuiDisplay.FLEX).flexDirection(GuiFlexDirection.ROW)
                    .alignItems(GuiAlignment.CENTER).justifyContent(GuiAlignment.CENTER)
                    .backgroundColor(GuiColors.of(background))
                    .gradient(flat(background))
                    .borderWidth(1f).borderColor(GuiColors.of(t.border()))
                    .shadow(null).glow(null);
            b.hovered(c -> c.backgroundColor(GuiColors.of(t.elevated())).gradient(flat(t.elevated())));
            b.pressed(c -> c.backgroundColor(GuiColors.of(t.elevated())).gradient(flat(t.elevated())));
            b.add(GuiText.label(label, textColor, 18f));
            return b;
        }

        /** Straight to a server, the way the multiplayer list does it. */
        private static void connect(Minecraft mc, String address) {
            String host = address == null || address.isBlank() ? "mc.hypixel.net" : address.trim();
            ServerData data = new ServerData("Hypixel", host, ServerData.Type.OTHER);
            ConnectScreen.startConnecting(mc.screen, mc, ServerAddress.parseString(host), data,
                    false, null);
        }

        /** The background: the theme's own colours, turned to whatever angle the clock is at. */
        private static GuiGradient backdrop(float angle) {
            Theme t = Themes.current();
            return new GuiGradient()
                    .startColor(GuiColors.of(t.accent()))
                    .endColor(GuiColors.of(t.surface()))
                    .angleDegrees(angle)
                    .linear();
        }

        private static GuiGradient flat(int argb) {
            return new GuiGradient().startColor(GuiColors.of(argb)).endColor(GuiColors.of(argb));
        }

        private static ContainerComponent spacer(float width, float height) {
            ContainerComponent c = new ContainerComponent();
            c.size(width, height);
            return c;
        }
    }
}
