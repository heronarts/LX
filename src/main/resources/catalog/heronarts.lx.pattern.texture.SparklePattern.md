---
class: heronarts.lx.pattern.texture.SparklePattern
kind: pattern
sourceRepo: LX
sourcePath: src/main/java/heronarts/lx/pattern/texture/SparklePattern.java
sourceSha256: bc638a5ec2c448fbf1d9419bbf77048a05a4bae4ebf4d82ff76f570ac64a1122
classBytesSha256: 0619f63042b23de19862c6bac51f722a1d1efa1c69451c103a18995f1f613373
classBytesOrigin: ~/.m2/repository/com/heronarts/lx/1.2.1/lx-1.2.1.jar
lxVersion: 1.2.1
generatedAt: 2026-07-17T00:00:00Z
generator: lx-mcp-catalog/2 (claude-sonnet-5)
tags: texture, generative, strobe
---

## Summary

Runs up to 1024 independent sparkle generators, each periodically SAMPLING a new random set of target pixels and firing one brightness pulse shaped by a waveshape before repeating.

- Density scales how many pixels each generator addresses relative to model size, from a sparse scatter to a near-full blanket where individual sparkles blur together.
- Per-pixel output is the sum of all active sparkles targeting it, added onto a constant base level floor, so the model never goes fully dark between sparkles.

## Parameter interactions

- Speed sets the base sparkle rate between the fast/slow interval bounds; variation randomly perturbs each sparkle's interval so they desynchronize instead of pulsing in unison.
- Duration is the active fraction of each sparkle's cycle — at 100% it's lit the whole interval, at 50% it fires then goes dark before the next cycle.
- Sharp applies a power curve to the waveshape: positive values make peaks narrower/needle-like, negative values softer/dome-like.
- Min/max brightness bound each sparkle's randomly drawn peak level; base level sets the ambient floor beneath all sparkle activity.

## Usage tips

- Best used as a texture overlay on its own channel at reduced level, twinkling over a base color from a SolidPattern or GradientPattern below.
- Output is grayscale/white only — colorize downstream with a ColorizeEffect or palette-driven effect.
- Pixel targeting is fully random per sparkle with no spatial coherence, so the effect works identically on any topology.
