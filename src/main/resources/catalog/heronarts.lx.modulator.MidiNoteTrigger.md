---
class: heronarts.lx.modulator.MidiNoteTrigger
kind: modulator
sourceRepo: LX
sourcePath: src/main/java/heronarts/lx/modulator/MidiNoteTrigger.java
sourceSha256: ee1dff362492e3b52b9610e3f462b6b0c4296cabcd223ec42090f9dda36fda4c
classBytesSha256: b00b16185a855ac88ed30f436b0bd703149fb5e616d8c0e846e844f1210470ca
classBytesOrigin: ~/.m2/repository/com/heronarts/lx/1.2.1/lx-1.2.1.jar
lxVersion: 1.2.1
generatedAt: 2026-07-17T00:00:00Z
generator: lx-mcp-catalog/2 (claude-sonnet-5)
tags: trigger, midi, audio-reactive, utility
---

## Summary

MidiNoteTrigger converts incoming MIDI note messages (within the note/velocity range configured on its MIDI filter) into a trigger pulse plus normalized pitch and velocity outputs, for driving other parameters from a MIDI keyboard/controller.

- The trigger output is a momentary pulse: without legato mode, it turns on and immediately back off on each note-on. With legato on, it stays on while at least one note is held (internal held-note count) and only clears when the last note releases or a MIDI panic arrives.
- The pitch and velocity outputs are SAMPLED at each note-on (normalized against the filter's configured range at that instant) — they hold their last value between notes, and stop updating if the filter's range collapses to zero width.
- The modulator's own scalar value mirrors the pitch output, so mapping MidiNoteTrigger directly is equivalent to mapping its pitch output.

## Parameter interactions

- Toggling legato mode resets the held-note counter and forces the trigger output off — switching modes while notes are physically held drops the current hold state.
- The trigger source is the trigger output, the correct handle when mapping this modulator as a trigger.
- The show-pitch toggle only controls UI meter visibility; no effect on computed values.

## Usage tips

- Enable legato mode when the destination should stay "held" for chord/sustain playing; leave it off for percussive one-shot triggers.
- Configure the MIDI filter's note range deliberately — a zero-width range silently stops pitch/velocity from updating.
- Because the modulator's own value tracks pitch, map MidiNoteTrigger directly for a destination that should follow played pitch.
