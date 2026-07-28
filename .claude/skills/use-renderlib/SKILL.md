---
name: use-renderlib
description: Integrate and use the public RenderLib Fabric library in consuming Minecraft mods. Use for Gradle dependency setup, retained GUI screens, managed or immediate HUDs, item/block/player-head model previews, high-fidelity 2D text, world-space scenes and primitives, screen extensions, virtual menus, lifecycle cleanup, renderer compatibility, performance choices, and RenderLib usage best practices. Do not use for maintaining or releasing the RenderLib repository itself.
---

# Use RenderLib

Build consuming mods through RenderLib's public `com.render.api`, `com.render.api.gui`, and `com.render.api.world` packages. Prefer the highest-level retained surface that owns the feature's lifecycle.

## Establish compatibility

1. Read the consuming mod's Minecraft, Java, Fabric Loader, mappings, and RenderLib versions.
2. Keep RenderLib on its matching Minecraft line; do not assume compatibility with older Minecraft releases.
3. Add the public Maven repository and an exact RenderLib version. Read [setup.md](references/setup.md) for the current coordinates and dependency declarations.
4. Compile the consuming Java project after changes.

## Route the feature

- Read [gui-and-hud.md](references/gui-and-hud.md) for retained screens, styling, item/block/head previews, text, managed HUD placement, toasts, and immediate HUD callbacks.
- Read [world-and-integration.md](references/world-and-integration.md) for world scenes, immediate extraction commands, outlines, existing-screen extensions, and virtual menus.
- Read [performance-and-compatibility.md](references/performance-and-compatibility.md) for lifecycle ownership, cost control, renderer capabilities, fallbacks, and validation.
- Use the hosted [documentation](https://renderlib.ifallious.com/) and [Javadoc](https://renderlib.ifallious.com/javadoc/) when an exact overload or newly released type is needed.

## Choose the owning surface

- Use `GuiView` and retained components for full application-like screens.
- Use `ManagedHudLayout` for movable, scalable, persisted retained HUD elements,
  including selected-element developer sliders and toggles.
- Use `ItemModelComponent` for native item stacks, block inventory models,
  resource-pack custom items, and player heads on managed GUI or HUD surfaces.
- Use `RenderLibHud.register(...)` for lightweight frame-derived HUD drawing.
- Use an owned `WorldScene` for persistent world objects.
- Use `RenderLibWorld.register(...)` for ephemeral world commands produced during extraction.
- Use `RenderLibScreen` to add to or replace arbitrary existing screens.
- Use `RenderLibMenu` for handled-screen virtualization and packet-safe queued actions.
- Use `RenderColor` for new color APIs; treat raw ARGB overloads as migration-only.

## Preserve lifecycle

1. Create retained objects once and mutate their state.
2. Keep every returned registration or scene handle that may need cleanup.
3. Close registrations, scenes, world objects, and model references when their owner ends.
4. Keep Minecraft client rendering code client-only.
5. Never retain `WorldRenderContext` or issue drawing calls outside its extraction callback.
6. Do not call RenderLib bootstrap methods from a consumer; its Fabric entrypoint installs them.

## Verify the consumer

Compile the consuming project and run its relevant tests. Exercise affected screens or render paths in a client with the renderers the mod supports. Treat missing base geometry, broken depth, renderer state leakage, mixin conflicts, and unbounded per-frame allocation as blockers.
