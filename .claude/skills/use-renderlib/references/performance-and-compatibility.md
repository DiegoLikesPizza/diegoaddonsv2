# Performance and compatibility

## Cost model

- Retain component trees, scenes, meshes, model data, text layouts, and registrations when their content is durable.
- Mutate state instead of recreating objects each tick or frame.
- Bound remote assets, SVG work, text layout churn, particles, trail history, picking frequency, and effect target sizes.
- Prefer batching-friendly materials and atlases.
- Use virtualized pagination or bounded visible content for very large retained collections.
- Keep animated `ItemModelComponent` populations bounded. Larger boxes use
  native per-identity oversized render targets for true device resolution;
  continuously changing rotation, zoom, or size changes cache identity, and
  player heads may wait on Minecraft's skin cache.
- Preload the shared `VANILLA_ONLY` item runtime when needed, but account for
  its additional built-in texture atlases and baked model set.
- Keep the transient effect graph within RenderLib's documented budgets; simplify or disable nonessential effects after reported allocation fallbacks.

## Renderer compatibility

Query `RenderLibWorld.compatibilityReport()` and capability values. Do not detect Sodium or Iris by importing their implementation classes.

Portable geometry is the baseline. Request framebuffer-dependent effects only when their capability is reported:

- Preserve base content when an optional GUI effect fails.
- Treat world glow as capability-gated; no glow is preferable to an invented halo fallback.
- Fail closed with unknown renderer replacements.
- Keep optional Iris API access outside consumer code unless the mod has an independent reason to integrate with Iris.

## Validation

Compile the consuming Java project after every integration change. Run nearby unit tests for lifecycle, math, or state behavior.

For render-sensitive features, test the combinations the consuming mod claims to support:

1. Vanilla renderer.
2. Sodium.
3. Sodium with Iris and shaders disabled.
4. Sodium with Iris and a shader pack enabled.

Check base geometry, transparent ordering, depth, text and GUI clipping, retained effect fallbacks, state cleanup after closing a screen/world, and diagnostics counters. Do not ship a renderer-specific visual path without a bounded portable or disabled fallback.
