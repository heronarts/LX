---
class: heronarts.lx.modulator.MacroSwitches
kind: modulator
sourceRepo: LX
sourcePath: src/main/java/heronarts/lx/modulator/MacroSwitches.java
sourceSha256: 32299eba1ca37f50b80af93142778729cb9b577c6c04afb649be3d629bec6743
classBytesSha256: e15eb97788a1e984b4678d281c2550ef7f2aa651a6513f01747d4035b89da2ff
classBytesOrigin: ~/.m2/repository/com/heronarts/lx/1.2.1/lx-1.2.1.jar
lxVersion: 1.2.1
generatedAt: 2026-07-17T00:00:00Z
generator: lx-mcp-catalog/2 (claude-sonnet-5)
tags: utility, macro, trigger, midi
---

## Summary

MacroSwitches is a bank of eight independently-labeled boolean toggles with no internal computation (its own modulator value is always zero) — a labeled control-surface source to map elsewhere, not an autonomous modulator.

## Parameter interactions

- The exclusive toggle makes the switches radio-button style: turning one on immediately forces all others off, checked on every switch edit — with it on, don't expect more than one switch active at a time.
- MIDI note-on toggles the switch at the note's offset from the filter's minimum note (MIDI filter disabled by default); note-off is ignored.

## Usage tips

- The bank exposes no single trigger source of its own — only the individual switch booleans are usable as mapping/trigger targets; don't try to map the modulator itself as a trigger.
