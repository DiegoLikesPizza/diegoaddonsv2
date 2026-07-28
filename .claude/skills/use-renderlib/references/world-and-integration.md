# World rendering and screen integration

## Persistent world content

Create one `WorldScene` for each logical owner. Add inert `WorldObject` instances to that scene, mutate them in place, and close either the individual object or the scene when the owner ends.

Use `WorldMaterial.depthMode(...)` for depth behavior. See-through content must be explicit. Use `Text3D.distanceScaling(...)` for distance-aware labels and a stroked `Box3D` for wireframe boxes.

Reuse meshes, models, instances, materials, and effects. Keep model reference handles alive while objects use them, then close them.

## Immediate world content

Use `RenderLibWorld.register(...)` for geometry derived naturally during each extraction. Submit only through the callback's `WorldRenderContext`. Never store the context, access renderer-private state, or draw directly from the extraction callback.

Keep extraction deterministic and bounded. Snapshot mutable game state into the submitted command; RenderLib draws the immutable result later through its selected backend.

## Picking and outlines

Use RenderLib picking against the active scene instead of a parallel geometry representation. Use `EntityOutlineManager` and store its registration when outlines need to be removed. Do not mutate vanilla global outline state directly.

## Existing screens and menus

Use `RenderLibScreen` for additive or replacement content on an existing screen. Scope extensions narrowly and close their registrations when disabled.

Use `RenderLibMenu` for handled-screen virtualization. Dispatch inventory or menu behavior through `MenuAction`, guards, and queues so the server-observed state remains synchronized. Do not call screen-handler internals speculatively or send raw slot actions without the provided safety checks.

