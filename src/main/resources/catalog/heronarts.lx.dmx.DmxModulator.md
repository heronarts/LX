---
class: heronarts.lx.dmx.DmxModulator
kind: modulator
sourceRepo: LX
sourcePath: src/main/java/heronarts/lx/dmx/DmxModulator.java
sourceSha256: e1334ce3300a8f7f0ea2e522e250289d1b450bc7f34934130f9c6bda5b07c147
classBytesSha256: e6c7a048829e96355f0b590547930df4c35503b3e718f4635d7921816d966dcf
classBytesOrigin: ~/.m2/repository/com/heronarts/lx/1.2.1/lx-1.2.1.jar
lxVersion: 1.2.1
generatedAt: 2026-07-17T00:00:00Z
generator: lx-mcp-catalog/2 (claude-sonnet-5)
tags: dmx, trigger, utility, envelope
---

## Summary

DmxModulator reads live DMX input and outputs a normalized value in one of three mutually exclusive modes: 8-bit (one channel scaled to 0-1), 16-bit (two consecutive channels combined for higher resolution), or Range (a sub-range of one channel's value, output as both a normalized fraction and a boolean "in range" flag).

- Switching mode reconfigures how many DMX bytes are consumed and resets the in-range flag to false, clearing prior Range-mode trigger state.
- The trigger source is the in-range flag, so this only functions as a usable trigger source in Range mode — in 8-bit/16-bit mode the in-range flag never changes.
- Directly setting the normalized value is unsupported (throws) — output tracks live DMX input only.

## Parameter interactions

- The min/max bounds (Range mode) are mutually clamped CONTINUOUSLY — pushing one past the other drags it to match, so the range can collapse but never invert.
- In Range mode, when DMX falls outside the min/max bounds the modulator's value forces to 0 regardless of where the raw signal actually sits outside that range.

## Usage tips

- Use Range mode specifically when a boolean DMX trigger is needed (e.g. a console button mapped to a channel value window); 8-bit/16-bit modes give a continuous fader-style value with no trigger semantics.
- 16-bit mode reads the configured channel and the next channel as a big-endian pair — reserve two consecutive DMX channels for it, not one.
