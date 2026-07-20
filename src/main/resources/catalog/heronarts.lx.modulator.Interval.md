---
class: heronarts.lx.modulator.Interval
kind: modulator
sourceRepo: LX
sourcePath: src/main/java/heronarts/lx/modulator/Interval.java
sourceSha256: 0e339b702c964a10ae3edd881db207640b8bd8b0bfb55c74e7af746c1c8cd556
classBytesSha256: 3109d0576ad378d5d169d7bb19e19f7e98e97b528fe91ff19eb1ad2936947392
classBytesOrigin: ~/.m2/repository/com/heronarts/lx/1.2.1/lx-1.2.1.jar
lxVersion: 1.2.1
generatedAt: 2026-07-17T00:00:00Z
generator: lx-mcp-catalog/2 (claude-sonnet-5)
tags: trigger, envelope, utility, randomization
---

## Summary

Interval is a randomized periodic trigger: it fires its trigger output on a repeating cycle whose length jitters each round, with a probability gate that can skip a firing entirely.

```
each time the underlying period completes a loop:
  draw a chance roll
  if roll succeeds:
    fire the trigger output, output 1 this frame
    draw a new random fraction for the next period length
  else:
    output 0, no trigger, period still restarts
```

- The effective period is the base period plus a random fraction (drawn fresh only on a successful fire) of the jitter range, so interval length varies between fires.
- The per-cycle probability gate is SAMPLED once per loop completion: the trigger may silently not fire that round if the roll fails, though the modulator still restarts its cycle.
- Inherits looping, tempo-sync, and manual-basis behavior from the shared periodic-modulator base — with tempo sync on, the tempo/division grid drives the period and the base period and jitter range are ignored.

## Parameter interactions

- The trigger source is the trigger output, the correct handle for mapping this modulator as a trigger.
- Interval opts out of being a mapping source; its own value is a transient 0/1 pulse, not a continuous signal — don't wire it as a smooth modulation source.
- The jitter range widens the randomness added atop the base period; large values can occasionally produce very long gaps between fires.

## Usage tips

- Use Interval for irregular, non-mechanical-feeling triggers rather than a strictly periodic trigger.
- Set the per-cycle probability below 100% to occasionally skip a beat; at 100% every completed period fires.
- Enable tempo sync to align cadence to the musical grid instead of a free-running period.
