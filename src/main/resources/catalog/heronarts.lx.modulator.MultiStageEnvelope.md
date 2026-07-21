---
class: heronarts.lx.modulator.MultiStageEnvelope
kind: modulator
sourceRepo: LX
sourcePath: src/main/java/heronarts/lx/modulator/MultiStageEnvelope.java
sourceSha256: 2370e8509805aa60e5b68664a84c5229df5b2e7722ddc0fb947f35b69ddf8390
classBytesSha256: 8521fb5f13b7e7b2a4ba5150a34815151d091120a5f07127a320a8de6c0e3525
classBytesOrigin: ~/.m2/repository/com/heronarts/lx/1.2.1/lx-1.2.1.jar
lxVersion: 1.2.1
generatedAt: 2026-07-17T00:00:00Z
generator: lx-mcp-catalog/2 (claude-sonnet-5)
tags: envelope, motion, trigger, utility, editable-curve
---

## Summary

MultiStageEnvelope holds an editable, arbitrary-length list of stages (each a basis/value/shape point) and interpolates between whichever pair brackets the current basis, exponentiating the segment's relative basis by that segment's own shape value.

```
find the two stages bracketing basis
relativeBasis = (basis - prevStage.basis) / (stage.basis - prevStage.basis)
value = lerp(prevStage.value, stage.value, relativeBasis ^ stage.shape)
```

- First/last stages are fixed at basis 0/1 and cannot be removed; interior stages can be added/removed, with basis clamped between their neighbors when repositioned.
- Per-segment shape != 1 skews that segment's ease (concave vs. convex) independently of other segments, unlike a single global curve exponent.
- Not invertible: computing basis from a target value throws, so this cannot be driven backwards.

## Parameter interactions

- Inherits the shared variable-period clock mode: fast/slow select a fixed period, sync ties to tempo, input drives the basis directly and continuously from an external input — useful for scrubbing the curve from an audio envelope follower.
- The looping toggle defaults off, so by default this behaves as a one-shot envelope that finishes at basis 1 rather than repeating.
- Stage edits (basis/value/shape) apply immediately on the next compute — CONTINUOUS, not sampled at trigger time.

## Usage tips

- Prefer this over MultiModeEnvelope/AHDSR when the desired curve isn't expressible as attack/hold/decay/sustain/release — e.g. multi-hump or asymmetric multi-segment shapes.
- Not invertible — don't wire it as a target for value-to-basis feedback; only forward (basis-to-value) use is supported.
