---
class: heronarts.lx.modulator.Randomizer
kind: modulator
sourceRepo: LX
sourcePath: src/main/java/heronarts/lx/modulator/Randomizer.java
sourceSha256: 6a85e404b2e5a4372c9661343ff4c6fe238193ab3d95d5feee438555188aa21d
classBytesSha256: 4d6d5164be3b97b88d471c157522fcb0105039442f86625c09939b98689dc558
classBytesOrigin: ~/.m2/repository/com/heronarts/lx/1.2.1/lx-1.2.1.jar
lxVersion: 1.2.1
generatedAt: 2026-07-17T00:00:00Z
generator: lx-mcp-catalog/2 (claude-sonnet-5)
tags: motion, trigger, midi, utility, smoothing, lfo
---

## Summary

Randomizer is a two-stage design: a timing stage decides WHEN to pick a new random target value, and an independent interpolation stage decides HOW the output chases that target.

```
every period (internal) or on external trigger/MIDI note-on:
  if a chance roll succeeds: pick new target in the min/max bounds; fire the trigger output
then each frame, move current value toward target per the interpolation mode
```

- The trigger-mode INTERNAL setting auto-fires on a periodic cycle with random jitter; EXTERNAL disables the internal clock entirely and firing only happens via trigger input or MIDI note-on.
- The per-fire chance is rolled fresh on every fire attempt — even a valid trigger may not update the target if the roll fails.
- The interpolation mode DIRECT snaps to each target with no smoothing; DAMPING chases it with a physically-modeled accel/decel profile; SMOOTHING exponentially eases over a time window. All three are CONTINUOUS — changing their rate parameters mid-chase reshapes motion already in flight.

## Parameter interactions

- The min/max bounds only where new targets are picked from; DAMPING's output is separately clamped to 0-1, so overshoot near a bound differs subtly from SMOOTHING's monotonic approach.
- An external trigger fire resets the internal timing basis to 0, even though the internal clock isn't driving that fire.
- MIDI note-on fires the same chance-gated path as the external trigger input, but without resetting basis — and it does so in both internal and external trigger modes, since note-on handling has no trigger-mode check.

## Usage tips

- Use DAMPING for organic eased motion; SMOOTHING for a simpler exponential ease; DIRECT for hard stepped random jumps (sample-and-hold).
- Set the trigger mode to EXTERNAL to drive re-targeting entirely from an external trigger/MIDI source instead of a free-running timer.
