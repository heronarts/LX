---
class: heronarts.lx.effect.SparkleEffect
kind: effect
sourceRepo: LX
sourcePath: src/main/java/heronarts/lx/effect/SparkleEffect.java
sourceSha256: f55a5652b85172950b2d767e28ddc0084a4129be78c5832ded126f1f086282ad
classBytesSha256: 261300a94172f29489ce522bde1dc86129636291eb9d255f7449ede182fbabf4
classBytesOrigin: ~/.m2/repository/com/heronarts/lx/1.2.1/lx-1.2.1.jar
lxVersion: 1.2.1
generatedAt: 2026-07-17T00:00:00Z
generator: lx-mcp-catalog/2 (claude-sonnet-5)
tags: texture, sparkle, motion, masking, utility
---

## Summary

Composites a shared sparkle-texture engine (the same one behind SparklePattern) over the input buffer using a selectable blend mode.

- The engine advances CONTINUOUSLY every frame regardless of the amount parameter — sparkles never freeze, so toggling amount off and back on shows no pop or freeze-frame.
- Masking is only applied to the output when the effective amount is above zero; below that the buffer passes through unmodified.
- Blend mode changes what sparkles do to content: Multiply/Mask darkens everything but relatively brightens sparkle peaks; Add/Spotlight brighten sparkle locations; Subtract/Difference darken or invert at sparkle peaks.

## Parameter interactions

- Engine density, speed, and the sparkle interval range jointly control how many pixels sparkle at once and how fast each cycles.
- Variation randomizes per-sparkle timing so dense configurations read as organic rather than synchronized.
- Min/max level set the brightness floor/ceiling of individual sparkles independent of blend mode.

## Usage tips

- Multiply (Mask) mode on a uniformly lit pattern gives a classic starfield shimmer.
- Add mode over a dark pattern reads as twinkling points of light.
- Low density with Highlight blend and max level near full gives a subtle glitter finish that only brightens, never darkens, the base pattern.
