# GUI and HUD usage

## Retained screens

Extend `GuiView`, build the tree once in `build()`, keep references to dynamic components, and mutate their state. Managed views use a `1920 x 1080` design space that is scaled and centered; layout sizes are design-space units.

Use the default `GuiUiMode.MODERN` for typed stylesheets, intrinsic sizing, block/inline flow, Flexbox, Grid, positioning, generated content, tables, multicolumn flow, pagination, retained transforms, clips, masks, filters, and view transitions. Select `GuiUiMode.LEGACY` only when the original component-local style and layout behavior is required.

Keep focus, overlays, dialogs, and menus attached to the owning view runtime. Use stable component identities and pseudo states instead of rebuilding subtrees every frame.

## Text and assets

Use `GuiTextStyle`, `GuiRichText`, and `GuiTextEngine.layout(...)` for RenderLib-owned 2D shaping, metrics, hit testing, carets, or selection. Keep `GuiFontPolicy.RESOURCE_ONLY` unless the mod deliberately authorizes bounded system roots or remote hosts.

Use `GuiImage`, `GuiBackgroundLayer`, and retained SVG documents for 2D retained interfaces. Close or release owned resources when their API exposes lifecycle ownership.

## Item, block, and player-head previews

Use `ItemModelComponent` when the result should follow Minecraft's native item
model pipeline. `item(ItemStack)` snapshot-copies the stack and therefore
preserves `ITEM_MODEL`, `CUSTOM_MODEL_DATA`, `PROFILE`, tint, foil/glint, and
other synchronized components. Use `block(Block)` only for inventory models;
world `BlockState` geometry belongs to the world API.

Use `itemModel(Identifier)` and `customModelData(CustomModelData)` after
configuring a base item for server-resource-pack variants. Player-head
overloads accept UUID, player name, `GameProfile`, `ResolvableProfile`, or a
Mojang base64 `textures` property. Do not pass arbitrary image URLs as texture
properties.

Keep the default `ItemModelResourceMode.ACTIVE_PACKS` when local and server
resource packs should apply. Use `VANILLA_ONLY` only for a pristine comparison.
That mode is an isolated asynchronous runtime: optionally call
`RenderLibGui.preloadVanillaItemModels()` and query
`isVanillaItemModelsReady()`. Do not implement a pack switch or active-resource
mutation around it.

Use `displayContext`, deterministic `seed`, model-space `rotation`/per-axis
setters, screen `offset`, and `zoom` for presentation. Count, durability, and
cooldown overlays are not part of the component. The 16-by-16 default is only
intrinsic layout size; larger boxes rasterize through the native oversized
item path at actual device resolution, so high-resolution pack textures keep
their detail.

## HUD selection

Use `ManagedHudLayout` when players should move, scale, hide, or persist
top-level retained elements. Add all top-level `HudLayoutElement` values before
registration. The top-level list freezes at registration while each retained
child tree remains mutable.

Attach developer-defined named sliders and toggles with
`HudLayoutElement.editorSlider(...)` and `editorToggle(...)`. Use the
getter/setter overloads to bind any component or mod property; the editor
resamples the getter while that element is selected. Use the initial-value plus
callback overloads when the mod wants to own application and persistence.
RenderLib does not store custom setting values in its placement JSON.

Use `RenderLibHud.register(...)` for small overlays calculated anew each frame. Draw through the provided canvas/context and do not cache that frame-scoped object.

Use HUD toasts for transient messages. Keep their retained effects bounded and avoid continuously creating identical toast trees.

## Coordinate rule

Do not mix managed-screen design coordinates, managed-HUD placement transforms, and immediate GUI pixels. Convert at the boundary provided by the relevant API; do not apply the managed-screen letterbox transform to HUD effect bounds.
