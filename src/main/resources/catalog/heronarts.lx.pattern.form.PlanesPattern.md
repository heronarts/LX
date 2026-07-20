---
class: heronarts.lx.pattern.form.PlanesPattern
kind: pattern
sourceRepo: LX
sourcePath: src/main/java/heronarts/lx/pattern/form/PlanesPattern.java
sourceSha256: db135379cbdfd619838bcabf992f01b7ed73e7017bac3d4c8eaf99e554ba6079
classBytesSha256: 6ae2ae9e2b4c3096f2d3ba660eca9d328db177589587c38ba28b6942c71daec2
classBytesOrigin: ~/.m2/repository/com/heronarts/lx/1.2.1/lx-1.2.1.jar
lxVersion: 1.2.1
generatedAt: 2026-07-17T00:00:00Z
generator: lx-mcp-catalog/2 (claude-sonnet-5)
tags: geometric, masking, motion, generative
---

## Summary

Renders up to 8 independently configured luminous planes (slabs) through 3D space, additive-composited onto black.

- Each plane's shape is set by axis mode (X/Y/Z/Free/R-center/R-origin), position, half-width, and edge contrast; pixels inside the slab get brightness falling off to zero at the contrast-controlled edge.
- All active planes share one global yaw/pitch/roll rotation of the coordinate space; each plane also carries its own tilt and spin rotation around its own pivot.
- Free and radial (R-center, R-origin) axis modes bypass tilt/spin and specify the plane directly via coefficients or a radius.

## Parameter interactions

- Position, width, and contrast act CONTINUOUSLY and are natural LFO targets: position sweeps the slab, width sets thickness, contrast controls edge hardness.
- Tilt/tiltPosition and spin/spinPosition rotate the slab around two secondary in-plane axes (angled slices, not axis-aligned cuts); only meaningful for X/Y/Z axis modes.
- Position/width min/max range parameters bound where an LFO-driven sweep can reach, confining a full-range modulator to a useful region.
- R-center produces an expanding/contracting sphere shell around the model center; R-origin a shell around the model's origin — neither is a flat plane.

## Usage tips

- A single Y-axis plane swept slowly via an LFO on position makes a clean horizontal reveal/wash; several planes at staggered positions/speeds produce a comb effect.
- Keep per-plane level low when stacking many planes — brightness is additive and easily saturates.
- Only plane 1's controls are exposed as remote controls by default; the other 7 require the full parameter tree.
