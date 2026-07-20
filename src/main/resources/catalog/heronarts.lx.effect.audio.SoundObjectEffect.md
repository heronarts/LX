---
class: heronarts.lx.effect.audio.SoundObjectEffect
kind: effect
sourceRepo: LX
sourcePath: src/main/java/heronarts/lx/effect/audio/SoundObjectEffect.java
sourceSha256: c20b3fd26909eb9e302719a94bbab19ddcb2c07e1562bab15d3508f9e2ea28c4
classBytesSha256: 907d7c214a9e5d43973ce895bc674bd78a55db7d2af7ccf4aac90c0e72bb74f1
classBytesOrigin: ~/.m2/repository/com/heronarts/lx/1.2.1/lx-1.2.1.jar
lxVersion: 1.2.1
generatedAt: 2026-07-17T00:00:00Z
generator: lx-mcp-catalog/2 (claude-sonnet-5)
tags: audio-reactive, masking, spatial, utility
---

## Summary

SoundObjectEffect reuses the sound-object rendering engine shared with the matching pattern to build a per-point brightness field around a tracked sound object, then composites that field onto the existing buffer rather than generating its own colors.

- Size and brightness are driven continuously by a selected audio signal plus an optional modulation input, and the result is composited onto the buffer using one of several blend behaviors (see Parameter interactions).
- A momentary cue control bypasses the blend and writes the raw mask straight to the buffer, for tuning placement without affecting live output.

## Parameter interactions

- A base size and brightness floor the object when the driving signal is quiet; signal-to-size and signal-to-brightness amounts scale how far the audio pushes those up, continuously. The modulation input drives size/brightness independently of the audio signal, for LFO/envelope layering on top of the audio response.
- The object's outline can be rendered as a spherical falloff, a box-shaped (Chebyshev-distance) falloff, or a falloff along a single axis; two independent shape choices can be continuously cross-blended, and a separate control picks how the object's spatial position is computed within the model.
- A contrast-style control sets how much of the object's size fades gradually to zero versus staying solid core.
- Scope controls sample signal history at a delay proportional to each point's distance from the object, trading a flat brightness field for radiating temporal echoes outward from the object.
- The blend depth scales overall mask opacity; the blend mode picks whether the mask darkens existing content (multiplicative), only brightens it (screen-like), boosts only where the mask and existing content already overlap (spotlight-style), or replaces/adds onto it outright.

## Usage tips

- Requires existing rendered content beneath it — otherwise only the cue view or the additive/replace blend modes show anything.
- The multiplicative blend mode is most dramatic, darkening everything outside the object; the screen/spotlight-style modes preserve more of the background.
- Use the cue control to preview/position the mask before raising blend depth for live blending.
- The object selector picks which detected sound object drives the effect when multiple exist.
