---
class: heronarts.lx.modulator.Stepper
kind: modulator
sourceRepo: LX
sourcePath: src/main/java/heronarts/lx/modulator/Stepper.java
sourceSha256: f194f7ffd700a8849106c972ff476b3d5c0ac41ec348fe06f57597dc791267a7
classBytesSha256: 7f9dd6ccca64f6cfa1bc0a19bbebccf742eac4c3b9828d5124df3fa9ceebc2d1
classBytesOrigin: ~/.m2/repository/com/heronarts/lx/1.2.1/lx-1.2.1.jar
lxVersion: 1.2.1
generatedAt: 2026-07-17T00:00:00Z
generator: lx-mcp-catalog/2 (claude-sonnet-5)
tags: sequencer, step, trigger, utility
---

## Summary

Stepper is a step sequencer that holds up to 16 fixed value slots and outputs the value of whichever step is currently active, changing only on step advance.
- The output value is SAMPLED at each step advance and held constant until the next advance — it does not interpolate between steps.
- A step-count control limits how many of the 16 slots are actually cycled through.

## Parameter interactions

- An internal trigger mode advances steps on its own timer (tempo-synced or a manual step-time interval); an external trigger mode instead requires sending trigger events in to advance manually — it will not advance on its own in this mode.
- Its step-advance trigger output fires unconditionally on every step advance (unlike the related step-sequencer modulator, which gates firing per-step).

## Usage tips

- Directly setting its normalized value is unsupported (throws) — its value can only change by advancing steps, not by direct assignment.
- Use to author a fixed sequence of discrete values (e.g. a color or brightness sequence) that advances on a clock or external trigger, rather than a continuous LFO.
