---
class: heronarts.lx.pattern.texture.NoisePattern
kind: pattern
sourceRepo: LX
sourcePath: src/main/java/heronarts/lx/pattern/texture/NoisePattern.java
sourceSha256: ab0d08bd99b94c16600525dc9f0ab108f505d3ad1e3cd23cf04bf89288bcde95
classBytesSha256: ec9f16d0e40b80d36b258aa248a7da6d0ccb13eab0b3e49efbdaba38ba910044
classBytesOrigin: ~/.m2/repository/com/heronarts/lx/1.2.1/lx-1.2.1.jar
lxVersion: 1.2.1
generatedAt: 2026-07-17T00:00:00Z
generator: lx-mcp-catalog/2 (claude-sonnet-5)
tags: texture, generative, motion, geometric
---

## Summary

Evaluates a 3D noise function at each pixel's transformed coordinate to produce a grayscale brightness field, animated by three independent per-axis sawtooth ramps scrolling an offset through noise space.

- Algorithm choices are Perlin, ridge, fBm, and turbulence (all but Perlin support multiple octaves), or pure per-pixel random static with no spatial coherence.
- Motion speed and per-axis motion rate act CONTINUOUSLY; toggling motion on/off is SAMPLED once and eases in/out over a damped envelope rather than snapping instantly.

## Parameter interactions

- Scale is the dominant spatial control — higher zooms in for fine texture, lower zooms out for large smooth blobs; per-axis scale multiplies it independently, so nonzero Z-motion with zero X/Y-motion flows only along Z.
- Coordinate mode per axis chooses normal (linear sweep), center (mirrored around midpoint), radial (distance from center), or none (fixed) — letting an axis be rotationally symmetric or static independent of the others.
- For fBm/ridge/turbulence, octave count adds higher-frequency detail; lacunarity sets how fast frequency rises per octave, gain sets how much each octave's amplitude decays.
- Rotate plus yaw/pitch/roll reorients the noise frame without affecting motion or offset controls.

## Usage tips

- Pairs well with a downstream ColorizeEffect or GradientMaskEffect to map the grayscale output to palette color.
- Ridge noise produces sharp luminous ridgelines, useful for lightning/vein-like textures; static has no coherent structure and is mainly useful for testing.
- A single Perlin octave with low Z-motion gives slow atmospheric breathing; a fixed seed gives reproducible output across instances.
