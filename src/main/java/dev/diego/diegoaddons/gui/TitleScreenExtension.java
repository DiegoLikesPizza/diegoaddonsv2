package dev.diego.diegoaddons.gui;

import com.render.api.ScreenExtension;
import com.render.api.ScreenBounds;
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
 * <p>The buttons keep Minecraft's own geometry - 200 by 20, four apart, starting a quarter of the
 * way down, with the last two sharing a row. The menu you know in different colours is still a menu
 * you can use without looking; a stack of great rounded slabs is a different program wearing the
 * same name.
 *
 * <p>The background is a gradient whose angle turns, a third of a degree a tick. A still gradient
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

    /** The menu itself: vanilla's layout, drawn in this mod's colours. */
    private static final class View extends ScreenExtensionView {
        /** Minecraft's own menu-button metrics. */
        private static final float BUTTON_W = 200f;
        private static final float BUTTON_H = 20f;
        private static final float ROW = 24f;
        private static final float HALF_W = 98f;

        private ContainerComponent backdrop;
        private float angle;
        private String builtFor = "";

        @Override
        protected void build(ScreenExtensionContext context) {
            root().display(GuiDisplay.BLOCK);
            rebuild(context);
        }

        @Override
        protected void tick(ScreenExtensionContext context) {
            CustomTitleScreenModule mod = CustomTitleScreenModule.INSTANCE;
            if (mod == null) {
                return;
            }
            if (!signature(context).equals(builtFor)) {
                rebuild(context);
                return;
            }
            if (mod.animate() && backdrop != null) {
                // A full turn every couple of minutes: enough that the light moves while you look at
                // it, slow enough that you never catch it moving.
                angle = (angle + 0.35f) % 360f;
                backdrop.gradient(backdrop(angle));
            }
        }

        /** What the menu depends on: the screen size, the theme, and which buttons are wanted. */
        private String signature(ScreenExtensionContext context) {
            CustomTitleScreenModule mod = CustomTitleScreenModule.INSTANCE;
            ScreenBounds b = context.hostBounds();
            return b.width() + "x" + b.height() + "|" + Themes.current().name()
                    + "|" + (mod != null && mod.showHypixel());
        }

        private void rebuild(ScreenExtensionContext context) {
            builtFor = signature(context);
            root().clearChildren();
            Theme t = Themes.current();
            ScreenBounds bounds = context.hostBounds();
            float width = bounds.width();
            float height = bounds.height();

            backdrop = new ContainerComponent();
            backdrop.position(GuiPositionType.ABSOLUTE).x(0f).y(0f)
                    .size(width, height)
                    .backgroundColor(GuiColors.of(t.surface()))
                    .gradient(backdrop(angle));
            root().add(backdrop);

            CustomTitleScreenModule mod = CustomTitleScreenModule.INSTANCE;
            Minecraft mc = Minecraft.getInstance();
            float left = width / 2f - BUTTON_W / 2f;
            // Vanilla starts its buttons a quarter of the way down plus 48, and so does this.
            float y = height / 4f + 48f;

            root().add(brand(t, width, height / 4f - 34f));

            root().add(button("Singleplayer", left, y, BUTTON_W, t.surface(), t.text(),
                    () -> mc.setScreen(new SelectWorldScreen(mc.screen))));
            y += ROW;
            root().add(button("Multiplayer", left, y, BUTTON_W, t.surface(), t.text(),
                    () -> mc.setScreen(new JoinMultiplayerScreen(mc.screen))));
            if (mod != null && mod.showHypixel()) {
                y += ROW;
                root().add(button("Join Hypixel", left, y, BUTTON_W, t.accent(), t.accentText(),
                        () -> connect(mc, mod.server())));
            }
            y += ROW;
            root().add(button("DiegoAddons", left, y, BUTTON_W, t.surfaceAlt(), t.text(),
                    () -> new DiegoClickGuiView().open()));

            // Options and Quit share the last row, the way they do in vanilla.
            y += ROW;
            root().add(button("Settings", left, y, HALF_W, t.surface(), t.text(),
                    () -> mc.setScreen(new OptionsScreen(mc.screen, mc.options, false))));
            root().add(button("Quit Game", left + BUTTON_W - HALF_W, y, HALF_W,
                    t.surface(), t.textMuted(), mc::stop));
        }

        /** The mark and the name, where vanilla puts its logo. */
        private ContainerComponent brand(Theme t, float width, float top) {
            ContainerComponent col = new ContainerComponent();
            col.position(GuiPositionType.ABSOLUTE).x(0f).y(top).width(width)
                    .display(GuiDisplay.FLEX).flexDirection(GuiFlexDirection.COLUMN)
                    .alignItems(GuiAlignment.CENTER).rowGap(GuiLength.pixels(4f)).gap(4f);

            ContainerComponent mark = new ContainerComponent();
            mark.size(34f, 34f).cornerRadius(9f)
                    .display(GuiDisplay.FLEX).flexDirection(GuiFlexDirection.ROW)
                    .alignItems(GuiAlignment.CENTER).justifyContent(GuiAlignment.CENTER)
                    .backgroundColor(GuiColors.of(t.accent()))
                    .gradient(new GuiGradient()
                            .startColor(GuiColors.of(t.accent()))
                            .endColor(GuiColors.of(t.accentTo())));
            mark.add(GuiText.label("D", t.accentText(), 18f));
            col.add(mark);
            col.add(GuiText.label("DiegoAddons", t.text(), 16f));
            col.add(GuiText.label("VERSION 2", t.accent(), 7f));
            return col;
        }

        /** One menu button, placed where vanilla would place it. */
        private ButtonComponent button(String label, float x, float y, float w,
                                       int background, int textColor, Runnable action) {
            Theme t = Themes.current();
            ButtonComponent b = new ButtonComponent();
            b.clearChildren();
            b.onPress(action);
            b.position(GuiPositionType.ABSOLUTE).x(x).y(y)
                    .size(w, BUTTON_H).padding(0f).cornerRadius(4f)
                    .display(GuiDisplay.FLEX).flexDirection(GuiFlexDirection.ROW)
                    .alignItems(GuiAlignment.CENTER).justifyContent(GuiAlignment.CENTER)
                    .backgroundColor(GuiColors.of(background))
                    .gradient(flat(background))
                    .borderWidth(1f).borderColor(GuiColors.of(t.border()))
                    .shadow(null).glow(null);
            b.hovered(c -> c.backgroundColor(GuiColors.of(t.elevated())).gradient(flat(t.elevated())));
            b.pressed(c -> c.backgroundColor(GuiColors.of(t.elevated())).gradient(flat(t.elevated())));
            b.add(GuiText.label(label, textColor, 10f));
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
    }
}
