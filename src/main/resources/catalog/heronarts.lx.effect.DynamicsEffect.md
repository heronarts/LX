---
class: heronarts.lx.effect.DynamicsEffect
kind: effect
sourceRepo: LX
sourcePath: src/main/java/heronarts/lx/effect/DynamicsEffect.java
sourceSha256: b1a1d5bab76897446146a465f3b916da36c6fe73bf0f8ffd919f49537846fe1d
classBytesSha256: 9b9ffeaf5a2140b4a4a31899bcc5d758f33fd9503ce5be6ddb9af932349175a2
classBytesOrigin: ~/.m2/repository/com/heronarts/lx/1.2.1/lx-1.2.1.jar
lxVersion: 1.2.1
generatedAt: 2026-07-17T00:00:00Z
generator: lx-mcp-catalog/2 (claude-sonnet-5)
tags: color, utility, brightness, contrast, gain, envelope
---

## Summary

DynamicsEffect reshapes each color channel's brightness response through a shared 256-entry lookup table — a static per-pixel curve shaper, not a temporal compressor: no attack/release or level-following over time.

- Gate zeroes input below a threshold and rescales the remainder; contrast applies a symmetric S-curve/inverse-S; shape applies a power curve biasing toward highlights or shadows; gain multiplies; floor/ceiling linearly remap the resulting 0-1 curve value into the floor-to-ceiling output range.
- Per-channel red/green/blue amount sliders independently lerp each channel between original and processed value, enabling selective channel dynamics or tinting.

## Parameter interactions

All curve parameters (floor, ceiling, contrast, gain, gate, shape) act CONTINUOUSLY: any change rebuilds the lookup table next frame, so live modulation sweeps the curve in real time.

- Gate applies first, so raising it zeroes dark input before contrast and shape operate on the surviving range.
- High contrast plus high shape compounds toward a near-step response.
- Gain applies after curve shaping, so raising it without lowering ceiling clips output toward maximum.
- Red/green/blue amount changes rebuild only the per-channel tables and also act continuously.

## Usage tips

- Use to punch up contrast on dim patterns or gate out low-level noise; effective on gradients, where shape can turn a soft ramp into a sharp edge or vignette.
- No attack/release exists, so driving gate from an audio modulator yields an instantaneous, unsmoothed noise gate — pair with a separate envelope/smoothing modulator for a softer response.
- Avoid stacking multiple instances with high gain: each applies its LUT to an already-processed buffer, compounding clips most output to full brightness.
