package dev.diego.diegoaddons.gui;

import com.render.api.HandledScreenContext;
import com.render.api.ScreenExtension;
import com.render.api.ScreenExtensionContext;
import com.render.api.ScreenExtensionMode;
import com.render.api.gui.ButtonComponent;
import com.render.api.gui.ContainerComponent;
import com.render.api.gui.ScreenExtensionView;
import com.render.api.gui.layout.GuiAlignment;
import com.render.api.gui.layout.GuiDisplay;
import com.render.api.gui.layout.GuiFlexDirection;
import com.render.api.gui.layout.GuiLength;
import com.render.api.gui.layout.GuiPositionType;
import dev.diego.diegoaddons.module.modules.PartyFinderModule;
import dev.diego.diegoaddons.util.PartyFinder;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/**
 * The class picker beside the party finder, as a RenderLib extension of the menu itself.
 *
 * <p>It was the last thing in the mod drawn by hand into a container screen - a panel painted in the
 * screen's own render pass, with its clicks taken back off the menu afterwards so a press on a
 * toggle was not also read as a slot click. RenderLib owns both ends here: the toggles are real
 * components with their own hit testing, and the menu's geometry arrives through
 * {@link HandledScreenContext} rather than an access-widened mixin.
 */
public final class PartyFinderExtension implements ScreenExtension<AbstractContainerScreen<?>> {
    private static final float PANEL_W = 132f;
    private static final float ROW_H = 26f;
    private static final float GAP = 8f;

    @Override
    @SuppressWarnings("unchecked")
    public Class<AbstractContainerScreen<?>> screenClass() {
        return (Class<AbstractContainerScreen<?>>) (Class<?>) AbstractContainerScreen.class;
    }

    @Override
    public boolean matches(AbstractContainerScreen<?> screen, ScreenExtensionContext context) {
        PartyFinderModule mod = PartyFinderModule.INSTANCE;
        return mod != null && mod.isEnabled() && PartyFinder.isPartyFinder(screen);
    }

    @Override
    public ScreenExtensionMode mode() {
        return ScreenExtensionMode.ADDITIVE;
    }

    @Override
    public ScreenExtensionView createView(AbstractContainerScreen<?> screen,
                                          ScreenExtensionContext context) {
        return new View();
    }

    /** The panel: a row per class, lit when it is one you are looking to play. */
    private static final class View extends ScreenExtensionView {
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

        /** What the panel depends on: where the menu is, and which classes are picked. */
        private String signature(ScreenExtensionContext context) {
            StringBuilder sb = new StringBuilder();
            HandledScreenContext h = context.handledScreen();
            if (h != null) {
                sb.append(h.x()).append(':').append(h.y()).append(':')
                        .append(h.backgroundWidth()).append('|');
            }
            PartyFinderModule mod = PartyFinderModule.INSTANCE;
            for (int i = 0; i < PartyFinder.CLASSES.length; i++) {
                sb.append(mod != null && mod.wants(i) ? '1' : '0');
            }
            sb.append(Themes.current().name());
            return sb.toString();
        }

        private void rebuild(ScreenExtensionContext context) {
            builtFor = signature(context);
            root().clearChildren();

            HandledScreenContext h = context.handledScreen();
            PartyFinderModule mod = PartyFinderModule.INSTANCE;
            if (h == null || mod == null) {
                return;
            }
            Theme t = Themes.current();

            ContainerComponent panel = new ContainerComponent();
            panel.position(GuiPositionType.ABSOLUTE)
                    .x(h.x() + h.backgroundWidth() + GAP)
                    .y(h.y())
                    .width(PANEL_W)
                    .display(GuiDisplay.FLEX)
                    .flexDirection(GuiFlexDirection.COLUMN)
                    .alignItems(GuiAlignment.START)
                    .rowGap(GuiLength.pixels(4f))
                    .gap(4f)
                    .padding(8f)
                    .cornerRadius(8f)
                    .backgroundColor(GuiColors.of(t.surface()))
                    .borderWidth(1f)
                    .borderColor(GuiColors.of(t.border()));

            ContainerComponent head = new ContainerComponent();
            head.width(PANEL_W - 16f).height(18f).display(GuiDisplay.FLEX);
            head.add(GuiText.label("Looking to play", t.textFaint(), 11f));
            panel.add(head);

            for (int i = 0; i < PartyFinder.CLASSES.length; i++) {
                panel.add(toggle(i, mod, t));
            }
            root().add(panel);
        }

        private ButtonComponent toggle(int index, PartyFinderModule mod, Theme t) {
            boolean on = mod.wants(index);
            ButtonComponent b = new ButtonComponent();
            b.clearChildren();
            b.onPress(() -> {
                mod.toggle(index);
                builtFor = "";   // the next tick rebuilds with the new state
            });
            b.autoSize().padding(0f, 10f)
                    .width(PANEL_W - 16f)
                    .height(ROW_H)
                    .cornerRadius(6f)
                    .display(GuiDisplay.FLEX)
                    .flexDirection(GuiFlexDirection.ROW)
                    .alignItems(GuiAlignment.CENTER)
                    .backgroundColor(GuiColors.of(on ? t.accent() : t.surfaceAlt()))
                    .gradient(null)
                    .shadow(null)
                    .glow(null)
                    .borderWidth(1f)
                    .borderColor(GuiColors.of(t.border()));
            b.add(GuiText.label(PartyFinder.CLASS_NAMES[index],
                    on ? t.accentText() : t.textMuted(), 12f));
            return b;
        }
    }
}
