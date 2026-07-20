---
class: heronarts.lx.audio.SoundObject
kind: modulator
sourceRepo: LX
sourcePath: src/main/java/heronarts/lx/audio/SoundObject.java
sourceSha256: 1e127e2233c243c3d8755b1470108833d1ada31967668ebeb0f26a3c8be3b87b
classBytesSha256: 81ce03ff00ce808b1bf4eaf885148874b22e78d37b88f863c6ab489ebe234c8f
classBytesOrigin: ~/.m2/repository/com/heronarts/lx/1.2.1/lx-1.2.1.jar
lxVersion: 1.2.1
generatedAt: 2026-07-17T00:00:00Z
generator: lx-mcp-catalog/2 (claude-sonnet-5)
tags: audio-reactive, spatial, geometric, envelope, smoothing, utility
---

## Summary

SoundObject represents one positioned, metered point in 3D space (azimuth/elevation/distance plus derived cartesian position and normalized-position vectors) whose meter level is pulled from one of several selectable sources (audio mix/L/R, Envelop channel, or Reaper channel), or driven externally by ADM-OSC. Every instance self-registers into a global static registry, pickable elsewhere via a selector reference instead of a direct reference.

- Only one meter source is sampled per frame; switching sources changes which input feeds the meter level entirely, it does not blend.
- The ADM-OSC sync toggle (CONTINUOUS while on) overrides azimuth/elevation/distance from ADM-OSC every frame, so manual edits are overwritten while sync is enabled.

## Parameter interactions

- The meter floor/ceiling controls act CONTINUOUSLY as an inverse-lerp window mapping the raw meter level to the normalized output; mutually clamped so floor cannot exceed ceiling.
- The attack/release times smooth the transition toward the windowed target CONTINUOUSLY — 0 disables smoothing in that direction (instant jump).
- Distance beyond 100% pushes the cartesian position outside the [0,1] cube, while the normalized-position vector always stays on the unit sphere — the two vectors diverge past full scale.
- Directly setting the normalized value is unsupported (throws) — output is computed, not directly settable.
- Renaming a SoundObject rebuilds every live selector's option list.

## Usage tips

- Look up an existing instance by reference (e.g. via the object's static registry lookup or a selector) rather than constructing ad hoc — construction has global side effects (registry insertion, selector rebuild, engine's object count bump).
- Set the meter source to NONE deliberately when only spatial position is needed, to avoid coupling to whichever audio input happens to be live.
