---
class: heronarts.lx.modulator.Quantizer
kind: modulator
sourceRepo: LX
sourcePath: src/main/java/heronarts/lx/modulator/Quantizer.java
sourceSha256: 1e3317550199b9ad5128868f560b22e630c3e0993c46d07c564708b667cb83c6
classBytesSha256: 1ce57152dd15556306783c147cd2bc729504eb181049712c63e0a6c687e67e38
classBytesOrigin: ~/.m2/repository/com/heronarts/lx/1.2.1/lx-1.2.1.jar
lxVersion: 1.2.1
generatedAt: 2026-07-17T00:00:00Z
generator: lx-mcp-catalog/2 (claude-sonnet-5)
tags: trigger, tempo, utility
---

## Summary

Quantizer delays an incoming engage trigger until the next boundary of the selected tempo quantization division, then fires its trigger output — a "snap this trigger to the beat grid" utility.

- If the quantization division is set to NONE, the trigger passes through immediately.

## Parameter interactions

- The quantization division is SAMPLED at trigger time but re-evaluated if it changes while a trigger is still pending the beat boundary.
- The trigger source is the trigger output, so this composes as a trigger source for other modulators/mappings; it is not usable as a modulation source — it has no continuous value.

## Usage tips

- Use this to align manually- or externally-fired triggers (MIDI, OSC, another modulator) to musical timing without hand-syncing the upstream source itself.
