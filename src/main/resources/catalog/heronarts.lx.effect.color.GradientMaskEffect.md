---
class: heronarts.lx.effect.color.GradientMaskEffect
kind: effect
sourceRepo: LX
sourcePath: src/main/java/heronarts/lx/effect/color/GradientMaskEffect.java
sourceSha256: c514642397b4b3962c9ccd919c675916f8463564c216486dbe849f4ad1f010db
classBytesSha256: 1c1e740ede053d42bae5e7f4e8beac543416c0c20ba144121a92fc9e0ac7e8a0
classBytesOrigin: ~/.m2/repository/com/heronarts/lx/1.2.1/lx-1.2.1.jar
lxVersion: 1.2.1
generatedAt: 2026-07-17T00:00:00Z
generator: lx-mcp-catalog/2 (claude-sonnet-5)
tags: color, masking, spatial, geometric, palette, blending
---

## Summary

GradientMaskEffect renders the same gradient-generation engine used by the gradient pattern into an internal buffer each frame, then composites it onto the incoming colors with a selectable blend mode. Use it to apply a spatially-varying (not uniform) color influence — e.g. warm-to-cool across an axis — on top of an existing pattern's content, without replacing that content's motion.

## Parameter interactions

- All gradient-generation parameters (axis, color stops, spread, animation) are exposed directly and behave as in the standalone pattern; the gradient is recomputed continuously each frame, not sampled once.
- Mode picks the blend operator: Multiply darkens/tints (white is a no-op; darker gradient colors add a cast); Add brightens toward the gradient color and can clip to white; Subtract removes the gradient color's channel values; Difference gives an absolute-difference inversion; Lerp crossfades directly toward the gradient color; Spotlight and Highlight are multiply+add composites that brighten while preserving more of the underlying content than plain Add.
- Depth scales the effective blend alpha continuously (via the effect's enabled amount), letting the gradient's influence be dialed between none and full strength live.
- CUE, while held, bypasses compositing entirely and writes the raw gradient straight to output — no blending, no Depth scaling — for previewing gradient placement.

## Usage tips

- Choose this over a plain gradient pattern when the goal is to color-shape an existing animated pattern rather than replace it — chain it after that pattern in the same channel.
- Multiply with a hue gradient is the simplest way to give different spatial regions of a model distinct colors while preserving underlying brightness/motion.
- Hold CUE first to verify axis and color-stop placement before picking a blend Mode, since blended results can obscure gradient geometry.
