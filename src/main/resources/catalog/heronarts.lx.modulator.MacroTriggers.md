---
class: heronarts.lx.modulator.MacroTriggers
kind: modulator
sourceRepo: LX
sourcePath: src/main/java/heronarts/lx/modulator/MacroTriggers.java
sourceSha256: 9d8fcb4819e02c2aa124a205cc260145a0e78f982e99cc86bc2f057b2fb92de6
classBytesSha256: 10ae8ea3109db8141948f87e48d53caeea7257e4b66acb86ebd3c98b567861a6
classBytesOrigin: ~/.m2/repository/com/heronarts/lx/1.2.1/lx-1.2.1.jar
lxVersion: 1.2.1
generatedAt: 2026-07-17T00:00:00Z
generator: lx-mcp-catalog/2 (claude-sonnet-5)
tags: utility, macro, trigger, midi
---

## Summary

MacroTriggers is a bank of eight independently-labeled momentary boolean parameters with no internal computation (its own modulator value is always zero) — a labeled control-surface source for one-shot triggers to map elsewhere, not an autonomous modulator.

## Parameter interactions

- MIDI note-on sets the trigger at the note's offset from the filter's minimum note and note-off releases it (held-note state is tracked per index); a MIDI panic forces any currently-held triggers off — so triggers can be sustained by holding a MIDI note rather than pulsing instantly.

## Usage tips

- The bank exposes no single trigger source of its own — only the individual trigger booleans are usable as mapping/trigger targets; don't try to map the modulator itself as a trigger.
