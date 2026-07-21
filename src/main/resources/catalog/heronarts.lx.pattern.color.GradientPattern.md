---
class: heronarts.lx.pattern.color.GradientPattern
kind: pattern
sourceRepo: LX
sourcePath: src/main/java/heronarts/lx/pattern/color/GradientPattern.java
sourceSha256: fa36c9071e5ca752df1e86757c3c478984b5af0782062e8c6659506fd5e4c919
classBytesSha256: ef225bc8e0ed51f529887ac59927e1ac07dd46ca0160d492fb00057eb2dab1e6
classBytesOrigin: ~/.m2/repository/com/heronarts/lx/1.2.1/lx-1.2.1.jar
lxVersion: 1.2.1
generatedAt: 2026-07-17T00:00:00Z
generator: lx-mcp-catalog/2 (claude-sonnet-5)
tags: color, gradient, palette, geometric, generative
---

## Summary

Fills the model with a continuous gradient computed from a weighted sum of per-axis spatial coordinates, looked up against a color ramp.

- Color mode selects the ramp source: Fixed (static color pair), Linked (a palette swatch plus a hue/saturation/brightness delta), or Palette (interpolates across N palette swatches).
- Each axis has an independent amount, offset, and coordinate mode (Normal, Center-folded, or Radial), applied CONTINUOUSLY per frame; amount's sign also selects that axis's inverted coordinate function.
- A compression control normalizes combined per-axis amounts so multiple full-strength axes don't clip the gradient at its endpoints.

## Parameter interactions

- Only a Y-axis amount gives a top-to-bottom gradient; equal X/Y amounts give a diagonal sweep; Radial mode on all axes gives a spherical gradient outward from center.
- A scale control zooms the coordinate range before lookup; with wrap or mirror clamping (instead of hard clamp) this produces repeating bands rather than one sweep.
- A phase offset shifts the lookup position and is the natural animation target — an LFO on phase gives a continuously scrolling wash.
- Center coordinate mode mirrors the gradient around an axis's midpoint.

## Usage tips

- Works well as a static or slow-moving color backdrop beneath texture or motion patterns.
- Combining more than two axes at full amount without compression clips the gradient at its color-stop endpoints, losing the smooth blend in the middle.
- Animate phase for a scrolling wash rather than per-axis amount, which reshapes geometry instead of moving it.
