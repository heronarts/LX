---
class: heronarts.lx.modulator.NoiseModulator
kind: modulator
sourceRepo: LX
sourcePath: src/main/java/heronarts/lx/modulator/NoiseModulator.java
sourceSha256: 033107289451cae4a31700870acdfac2232359e43f969954203c05fb3a0ba7dd
classBytesSha256: b2b0079889321b3c53ffc788cce191fd003f1263f5686764579f5b1d038c25a8
classBytesOrigin: ~/.m2/repository/com/heronarts/lx/1.2.1/lx-1.2.1.jar
lxVersion: 1.2.1
generatedAt: 2026-07-17T00:00:00Z
generator: lx-mcp-catalog/2 (claude-sonnet-5)
tags: texture, motion, lfo, utility, noise
---

## Summary

NoiseModulator continuously walks a 1D coordinate through a selectable noise field (Perlin, Ridge, FBM, Turbulence, or pure per-frame random "Static") and maps the raw noise sample through the level and contrast controls into a clamped 0-1 range, then rescales into the configured min/max output level bounds.

- The speed control is bipolar (negative walks the coordinate backwards) and is scaled by the speed-range control; both act continuously on the moving coordinate each frame.
- The octave/lacunarity/gain/ridge parameters only affect the RIDGE/FBM/TURBULENCE algorithms — no effect when the algorithm is PERLIN or STATIC.
- STATIC ignores the walking coordinate entirely and returns a fresh independent random value every frame — behaves like white noise, not a smooth-varying field, and is unaffected by speed.
- Directly setting the normalized value is unsupported (throws) — read-only generator, not a settable value.

## Parameter interactions

- The contrast control multiplies the raw noise sample before it's added to the level control and clamped, so high contrast with the level near 0 or 1 clips against the min/max bounds more often (flatter output, less mid-range texture).
- A lookahead computation returns the value after an additional elapsed time without committing state — usable to preview a future sample without advancing the modulator.

## Usage tips

- Prefer PERLIN (with a fixed seed) for smooth, repeatable-looking texture; switch to STATIC only when true per-frame flicker/sparkle noise is wanted, since it ignores speed entirely.
- Use the min/max output level bounds rather than the level control alone to bound the output range when feeding a parameter that shouldn't reach 0 or 1.
