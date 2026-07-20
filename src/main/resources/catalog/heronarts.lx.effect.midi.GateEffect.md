---
class: heronarts.lx.effect.midi.GateEffect
kind: effect
sourceRepo: LX
sourcePath: src/main/java/heronarts/lx/effect/midi/GateEffect.java
sourceSha256: 51ea6827da04963fc24c2ecb7586a340e3b740c667d3963f2010d86b56a74c7d
classBytesSha256: b70b04cb77ee6abea9eef008a69a123cb3546cd4d5f815ad41d6cd43843315ee
classBytesOrigin: ~/.m2/repository/com/heronarts/lx/1.2.1/lx-1.2.1.jar
lxVersion: 1.2.1
generatedAt: 2026-07-17T00:00:00Z
generator: lx-mcp-catalog/2 (claude-sonnet-5)
tags: midi, audio-reactive, envelope, trigger, masking, utility
---

## Summary

Multiplies the whole color buffer by an AHDSR envelope value, gating brightness on MIDI note on/off, a manual toggle, or an external trigger input.

- Gate mode holds sustain between note-on and note-off; one-shot (Trigger) mode always plays attack/hold/decay to completion, ignoring note-off, sustain, and release.
- Velocity and note-pitch response are SAMPLED once at each note-on to scale that note's peak level; changing them live only affects the next note.
- Shape bends the attack/decay curves (positive = slow start, fast finish; negative = the inverse) CONTINUOUSLY on the running envelope.

## Parameter interactions

- Retrigger mode governs overlapping notes: Retrig restarts from the current level, Legato ignores new notes until all keys release, Reset snaps to zero before re-engaging.
- Positive velocity/note response scales peak up with harder or higher notes; negative response inverts this so louder or higher notes get dimmer.
- Initial and peak set the envelope's floor and ceiling; peak is only fully reached at maximum velocity/note-scaled amount.
- A manual trigger toggle and a separate trigger-input parameter drive the same engage state as MIDI note-on/off, so any source can gate the effect without MIDI.

## Usage tips

- For percussive flash-hits: near-zero attack, short hold, moderate decay, zero sustain and release.
- For sustained instrument-following: sustain near maximum with comfortable attack/release.
- Set velocity response positive so quiet notes flash more subtly instead of always hitting full brightness.
- Place at the end of an effect chain so the fully-shaped output is what gets gated.
