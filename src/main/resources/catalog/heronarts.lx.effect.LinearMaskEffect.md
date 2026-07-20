---
class: heronarts.lx.effect.LinearMaskEffect
kind: effect
sourceRepo: LX
sourcePath: src/main/java/heronarts/lx/effect/LinearMaskEffect.java
sourceSha256: 8edbeb4a88537613058c0b6535c144a8667bce052d87e4d031b4cec5caab3ab5
classBytesSha256: 8c0f08247d552e0a1540c18f89b2b36a02d4934c5b7cf8879e7db21f8af3ca3e
classBytesOrigin: ~/.m2/repository/com/heronarts/lx/1.2.1/lx-1.2.1.jar
lxVersion: 1.2.1
generatedAt: 2026-07-17T00:00:00Z
generator: lx-mcp-catalog/2 (claude-sonnet-5)
tags: masking, geometric, utility, gradient
---

## Summary

Masks the color buffer with a 1D linear-falloff gradient along a chosen spatial axis (X/Y/Z/Radial), fading to black (Fade) or to white (Whiteout).

- Distance is measured from an offset point along the axis; mode selects symmetric falloff (Abs), or one-sided falloff only past (Pos) or before (Neg) the point.
- Size sets the fully-opaque zone; fade sets the transition width, absolute or (in relative mode) proportional to size.
- Offset, size, fade, and rotation all recompute CONTINUOUSLY, so all are safe to drive with live modulation.

## Parameter interactions

- Invert flips which region is fully visible vs. masked, turning a center-reveal into a center-erase.
- Enabling rotation decouples the mask axis from raw model geometry (e.g. rolling a Y-axis mask 45° gives a diagonal band).
- Fade position (Outer/Inner/Middle) sets which end of the gradient starts fully bright, changing whether the mask reads as a vignette or a one-sided wipe.
- A momentary Cue toggle previews the mask shape in fixed greyscale — for aiming the mask, not production output.

## Usage tips

- Y-axis + Neg mode + zero offset makes a bottom-to-top reveal.
- Drive offset or size with a modulator for sweeping wipe/reveal transitions.
- Leave rotation off unless needed off-axis; it costs a per-frame matrix recompute.
- Use Whiteout when compositing needs a white edge instead of a fade to black.
