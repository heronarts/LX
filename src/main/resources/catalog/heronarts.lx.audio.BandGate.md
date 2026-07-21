---
class: heronarts.lx.audio.BandGate
kind: modulator
sourceRepo: LX
sourcePath: src/main/java/heronarts/lx/audio/BandGate.java
sourceSha256: e9b90548a1569a5e536cda7b58926d3689e789df32c0912fe5836ea1d415faff
classBytesSha256: 21d95f04b6ad26a0cfba6a81982888a016d78736513aba08eaff2a413cfe68e5
classBytesOrigin: ~/.m2/repository/com/heronarts/lx/1.2.1/lx-1.2.1.jar
lxVersion: 1.2.1
generatedAt: 2026-07-17T00:00:00Z
generator: lx-mcp-catalog/2 (claude-sonnet-5)
tags: audio-reactive, trigger, envelope, beat-detect, tempo
---

## Summary

BandGate extends BandFilter into a beat/hit detector: it reuses the parent's band-average computation but adds a threshold/floor gate that fires a one-frame trigger and drives a decaying envelope. The inherited attack/release still shape trigger timing, since they smooth the averaged level the gate thresholds against; the gate's own decay time only controls how fast the output envelope falls after a trigger, and never affects when triggers fire.

- Edge-driven: it fires once when the level crosses the threshold, then latches off until the level falls below the re-arm floor (a fraction of the threshold).
- The trigger source is the gate boolean, wireable directly into trigger-consuming modulators/mappings.

## Parameter interactions

- The floor is a fraction of the threshold, not an absolute level — raising the threshold also raises the absolute re-arm floor.
- The decay time shapes only the envelope output (linear falloff 1 to 0 over the decay time after a trigger); it does not gate when the next trigger can occur — that's purely the floor.
- The tempo-teach (tap) control, when enabled, taps the global tempo on every trigger and auto-disables after 4 taps — a one-shot sync action, not a persistent mode.
- Directly setting the normalized value is unsupported (throws), matching the parent.

## Usage tips

- Prefer BandGate over BandFilter whenever a boolean/trigger beat signal is needed (kick/snare/hat detection).
- The average-level output exposes the underlying continuous band level alongside the trigger, from one modulator.
