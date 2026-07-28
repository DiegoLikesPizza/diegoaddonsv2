package dev.diego.diegoaddons.gui;

import com.render.api.HandledScreenContext;
import com.render.api.ScreenExtension;
import com.render.api.ScreenExtensionContext;
import com.render.api.ScreenExtensionMode;
import com.render.api.gui.ButtonComponent;
import com.render.api.gui.ItemModelComponent;
import com.render.api.gui.ScreenExtensionView;
import com.render.api.gui.TooltipComponent;
import com.render.api.gui.layout.GuiAlignment;
import com.render.api.gui.layout.GuiDisplay;
import com.render.api.gui.layout.GuiPositionType;
import dev.diego.diegoaddons.config.InventoryButton;
import dev.diego.diegoaddons.module.modules.InventoryButtonsModule;
import dev.diego.diegoaddons.util.InventoryButtons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import java.util.ArrayList;
import java.util.List;

/**
 * Inventory buttons, drawn by RenderLib as an additive extension of whatever container screen is
 * open.
 *
 * <p>This replaces the previous immediate-mode pass, which drew the buttons from the screen's
 * {@code afterExtract} event and then had to take the click back off the menu through
 * {@code allowMouseClick} so a press was not also read as a slot click. RenderLib owns both ends
 * here: the buttons are real retained components with their own hit testing, so a press lands on the
 * button and never reaches the slot underneath, and the screen's own geometry arrives through
 * {@link HandledScreenContext} instead of an access-widened mixin into {@code leftPos}/{@code topPos}.
 *
 * <p>The buttons are rebuilt only when the configuration or the menu's position changes, so opening
 * a differently-sized container moves them to the right corner without rebuilding every frame.
 */
public final class InventoryButtonsExtension implements ScreenExtension<AbstractContainerScreen<?>> {

    @Override
    @SuppressWarnings("unchecked")
    public Class<AbstractContainerScreen<?>> screenClass() {
        // Every container menu, whatever its concrete screen class.
        return (Class<AbstractContainerScreen<?>>) (Class<?>) AbstractContainerScreen.class;
    }

    @Override
    public boolean matches(AbstractContainerScreen<?> screen, ScreenExtensionContext context) {
        InventoryButtonsModule mod = InventoryButtonsModule.INSTANCE;
        if (mod == null || !mod.isEnabled() || InventoryButtons.all().isEmpty()) {
            return false;
        }
        Minecraft mc = context.client();
        return !(mod.hideInCreative() && mc.player != null && mc.player.isCreative());
    }

    @Override
    public ScreenExtensionMode mode() {
        return ScreenExtensionMode.ADDITIVE;
    }

    @Override
    public ScreenExtensionView createView(AbstractContainerScreen<?> screen, ScreenExtensionContext context) {
        return new View();
    }

    /** The retained tree: one button per configured entry, placed against the menu's corners. */
    private static final class View extends ScreenExtensionView {
        private final List<ButtonComponent> buttons = new ArrayList<>();
        private String builtFor = "";

        @Override
        protected void build(ScreenExtensionContext context) {
            root().display(GuiDisplay.BLOCK);
            rebuild(context);
        }

        @Override
        protected void tick(ScreenExtensionContext context) {
            if (!signature(context).equals(builtFor)) {
                rebuild(context);
            }
        }

        /**
         * What the layout depends on: the buttons themselves and where the menu sits. Rebuilding on a
         * change of either keeps a button anchored to the same corner of a chest of any size.
         */
        private String signature(ScreenExtensionContext context) {
            StringBuilder sb = new StringBuilder();
            HandledScreenContext h = context.handledScreen();
            if (h != null) {
                sb.append(h.x()).append(':').append(h.y()).append(':')
                        .append(h.backgroundWidth()).append(':').append(h.backgroundHeight()).append('|');
            }
            for (InventoryButton b : InventoryButtons.all()) {
                sb.append(b.x).append(',').append(b.y).append(',').append(b.anchorRight)
                        .append(',').append(b.anchorBottom).append(',').append(b.gigantic)
                        .append(',').append(b.icon).append(',').append(b.command).append(';');
            }
            sb.append(Themes.current().name());
            return sb.toString();
        }

        private void rebuild(ScreenExtensionContext context) {
            builtFor = signature(context);
            root().clearChildren();
            buttons.clear();

            HandledScreenContext h = context.handledScreen();
            if (h == null) {
                return;   // not a container screen after all; nothing to anchor to
            }
            InventoryButtonsModule mod = InventoryButtonsModule.INSTANCE;
            boolean tooltips = mod != null && mod.showTooltips();

            for (InventoryButton b : InventoryButtons.all()) {
                root().add(button(b, h, tooltips));
            }
        }

        private ButtonComponent button(InventoryButton b, HandledScreenContext h, boolean tooltips) {
            Theme t = Themes.current();
            float size = InventoryButtons.size(b);
            float x = b.anchorRight ? h.x() + h.backgroundWidth() + b.x : h.x() + b.x;
            float y = b.anchorBottom ? h.y() + h.backgroundHeight() + b.y : h.y() + b.y;

            ButtonComponent button = new ButtonComponent();
            // onPress is declared on the button type; the styling chain below widens to the
            // container type, so it has to be set from the typed reference first.
            button.onPress(() -> run(b));
            button.text("")
                    .size(size, size)
                    .position(GuiPositionType.ABSOLUTE).x(x).y(y)
                    .display(GuiDisplay.FLEX)
                    .alignItems(GuiAlignment.CENTER)
                    .justifyContent(GuiAlignment.CENTER)
                    .cornerRadius(4f)
                    .backgroundColor(GuiColors.of(t.surfaceAlt()))
                    .borderWidth(1f)
                    .borderColor(GuiColors.of(Theme.withAlpha(t.border(), 0.9f)));

            // The icon fills the button but for a 1px rim, so a gigantic button gets a genuinely
            // larger item rather than a stretched 16px sprite.
            ItemModelComponent icon = new ItemModelComponent();
            icon.item(InventoryButtons.icon(b.icon)).size(size - 2f, size - 2f);
            button.add(icon);

            if (tooltips && b.command != null && !b.command.isBlank()) {
                button.tooltip(new TooltipComponent().text("/" + b.command).followMouse(true));
            }
            buttons.add(button);
            return button;
        }

        /**
         * Runs a button's command. The menu is closed first: most of these commands open another
         * menu, and the server will not replace a container the client still thinks is open.
         */
        private void run(InventoryButton b) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || b.command == null || b.command.isBlank()) {
                return;
            }
            String cmd = b.command.startsWith("/") ? b.command.substring(1) : b.command;
            mc.player.closeContainer();
            mc.player.connection.sendCommand(cmd);
        }
    }
}
