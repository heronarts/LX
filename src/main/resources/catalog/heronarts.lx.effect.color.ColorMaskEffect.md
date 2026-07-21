---
class: heronarts.lx.effect.color.ColorMaskEffect
kind: effect
sourceRepo: LX
sourcePath: src/main/java/heronarts/lx/effect/color/ColorMaskEffect.java
sourceSha256: 16120f086cf57e45c8d6bff8a24210b73d5d06c9d75caee4bf0ec867b4cd7e5d
classBytesSha256: 57716e5b836c1639df708659852ab7bb8f01a11d4ee3bb9d997c4aadff348430
classBytesOrigin: ~/.m2/repository/com/heronarts/lx/1.2.1/lx-1.2.1.jar
lxVersion: 1.2.1
generatedAt: 2026-07-17T00:00:00Z
generator: lx-mcp-catalog/2 (claude-sonnet-5)
tags: color, masking, utility, blend
---

## Summary

ColorMaskEffect blends a single flat color against every pixel using a per-pixel blend function, applying a uniform tint or wash across the channel in one pass.

- The mask color is a linked color parameter: a static authored value, or linked to track the active palette swatch.
- Blend strength is depth multiplied by the effect's enabled amount; either at zero makes the effect a no-op for that frame.
- Depth and color are sampled fresh each frame, so live modulation of either sweeps the mask continuously.

## Parameter interactions

- Mode picks the blend operator: Multiply darkens/tints (white is a no-op; darker colors add a cast); Add brightens toward the mask color and can clip to white; Subtract removes the mask color's channel values; Difference gives an absolute-difference inversion; Lerp crossfades directly toward the mask color.
- Highlight and Spotlight are multiply+add composites that brighten while preserving more destination shape than plain Add.
- Depth scales blend alpha, so partial values weaken the selected mode rather than switching to a different blend.

## Usage tips

- Use Multiply with a saturated color to tint without overriding luminance — near-black stays unaffected while bright areas pick up the cast.
- Use Lerp with depth below full for a gradual fade-to-color; at depth 1 it fully replaces content with the mask color.
- Link color to the palette swatch for show-wide recoloring without touching individual patterns.
