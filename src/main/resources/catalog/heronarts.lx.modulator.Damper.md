---
class: heronarts.lx.modulator.Damper
kind: modulator
sourceRepo: LX
sourcePath: src/main/java/heronarts/lx/modulator/Damper.java
sourceSha256: c90ee20b9146ea97b883af4f4e526a645d52bb80e93c8bc36a8a786acbd74f6d
classBytesSha256: 6edf94c1931c8dc2dfe955908abdc18dbaec19793406e11159b3208fdfcc094b
classBytesOrigin: ~/.m2/repository/com/heronarts/lx/1.2.1/lx-1.2.1.jar
lxVersion: 1.2.1
generatedAt: 2026-07-17T00:00:00Z
generator: lx-mcp-catalog/2 (claude-sonnet-5)
tags: envelope, smoothing, trigger, utility, midi
---

## Summary

Damper ramps a normalized value between 0 and 1 over a configurable interval whenever its toggle state changes, producing an eased two-state envelope (like a damped fader) rather than an instant switch.

- The toggle state is the source of truth: turning it on ramps toward 1, off ramps toward 0, over the configured ramp period (or a tempo-synced period). Evaluated CONTINUOUSLY, so changing the period mid-ramp changes speed immediately.
- Separate momentary engage/release triggers set the toggle true/false — use these for one-shot pulse control instead of the toggle directly.
- The sine-shaping toggle applies a half-sine ease to the ramp; toggling it live re-warps output continuously without jumping the current position.
- MIDI note-on/off drives the toggle directly (still subject to the ramp), but the MIDI filter is disabled by default.

## Parameter interactions

- The trigger source is the toggle state itself, so consumers react to toggle-state edges, not the ramped value.
- The daily-timing toggle enables a daily-clock feature: configured engage/release times are compared against the system clock at second granularity; a match fires a momentary timer output, which fires the engage/release triggers. Only active while daily timing is on.
- Directly setting the normalized value accounts for sine-shaping, back-solving the linear basis via arcsine so external writes stay consistent with the eased curve.

## Usage tips

- Use Damper instead of a raw boolean mapping when a toggle/trigger needs to arrive smoothly — e.g. easing a mask in/out on a button press.
- For tempo-locked ramps, enable tempo sync; the fixed ramp period is ignored while synced.
- MIDI control requires enabling the MIDI filter, which starts disabled.
