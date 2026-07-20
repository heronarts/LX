---
class: heronarts.lx.effect.color.ColorizeEffect
kind: effect
sourceRepo: LX
sourcePath: src/main/java/heronarts/lx/effect/color/ColorizeEffect.java
sourceSha256: a490feafccd4c48572b0145177dd8f7a9b18a42cdccb9c3b87b29e7f2e6e604c
classBytesSha256: f7573ddbc62cbd4910b16e986864963d95b9aa230d180008d21ed01929b98d57
classBytesOrigin: ~/.m2/repository/com/heronarts/lx/1.2.1/lx-1.2.1.jar
lxVersion: 1.2.1
generatedAt: 2026-07-17T00:00:00Z
generator: lx-mcp-catalog/2 (claude-sonnet-5)
tags: color, masking, palette, utility
---

## Summary

ColorizeEffect recolors every pixel by extracting a scalar "source" value from it and using that value to look up a color along a gradient, blending the result over the original pixel by an amount.

- Source mode picks the scalar: brightness, luminosity, a single R/G/B channel, min(R,G,B), average(R,G,B), or the pixel's alpha.
- Color mode picks the gradient: two fixed colors, a second color derived from the first by HSB offsets, a swatch color plus HSB offsets, or the full active palette gradient.
- Below a configurable low-value threshold, out-of-range pixels can be left alone, forced to black, or cleared to transparent instead of colorized.

## Parameter interactions

- The blend amount scales continuously each frame, so partial colorization is a live crossfade, not a toggle.
- Palette color mode reads the active swatch live, tracking palette changes without reconfiguring the effect.
- A depth control and an invert toggle only apply in Palette color mode: depth compresses how far high values reach into the gradient, invert reverses direction.
- Pixels above the low-value threshold are rescaled so threshold-to-maximum spans the full gradient — filtering stretches the visible gradient rather than only excluding low pixels.
- Relative and Linked color modes recompute gradient colors continuously from their HSB offsets, live rather than sampled once.

## Usage tips

- Put it after a grayscale or brightness-only pattern to impose color onto luminance content.
- Palette color mode makes the gradient follow show-wide palette changes live.
- Clearing out-of-range pixels to transparent with a nonzero threshold keys out dark regions for layering in the mixer.
