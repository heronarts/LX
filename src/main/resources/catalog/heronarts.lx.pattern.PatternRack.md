---
class: heronarts.lx.pattern.PatternRack
kind: pattern
sourceRepo: LX
sourcePath: src/main/java/heronarts/lx/pattern/PatternRack.java
sourceSha256: 7528b63971035d14e97e89242fb82373d085a212f105ae03483b1023eb47d849
classBytesSha256: 03dce456745b0d6fefec29a9b2367d58d9fa739c25f705fe3f1da4b5f693a5e0
classBytesOrigin: ~/.m2/repository/com/heronarts/lx/1.2.1/lx-1.2.1.jar
lxVersion: 1.2.1
generatedAt: 2026-07-17T00:00:00Z
generator: lx-mcp-catalog/2 (claude-sonnet-5)
tags: utility, generative, container
---

## Summary

Embeds a complete pattern engine inside a single pattern slot, giving that slot its own child pattern list, transition engine, and auto-cycle behavior.

- Each frame the rack delegates to its internal engine, which runs (or cross-fades) its active child pattern and writes into the rack's own buffer; the parent channel then treats that buffer as the rack's single pattern output.
- MIDI and OSC messages addressed to the rack are forwarded to the internal engine using the same filtering/addressing a top-level channel uses.

## Parameter interactions

- The rack exposes all pattern engine parameters (transition time, auto-cycle, blend mode) directly on itself, but deliberately excludes them from clip automation and snapshot control so the rack's internal playback state isn't captured or restored by channel-level snapshots.

## Usage tips

- Use when one fixture group needs to auto-cycle independently through its own sub-collection of patterns while the rest of the show does something else.
- Child patterns are addressable via OSC/MIDI at the same relative sub-path the top-level engine uses, so existing controller mappings largely carry over.
- Because rack parameters are snapshot-excluded, don't rely on snapshots to capture or restore which child pattern is currently active inside the rack.
