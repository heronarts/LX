---
class: heronarts.lx.modulator.CycleModulator
kind: modulator
sourceRepo: LX
sourcePath: src/main/java/heronarts/lx/modulator/CycleModulator.java
sourceSha256: a0acb1aa743da287a1660c9d3e102c48439a874d1a8b7762ce2f87ec460b67e5
classBytesSha256: aeb6cf19f6e22cc16ae0040853f838eedd1e2a65dadbfc50dfd1ee7735b81a29
classBytesOrigin: ~/.m2/repository/com/heronarts/lx/1.2.1/lx-1.2.1.jar
lxVersion: 1.2.1
generatedAt: 2026-07-17T00:00:00Z
generator: lx-mcp-catalog/2 (claude-sonnet-5)
tags: motion, lfo, phase, utility
---

## Summary

CycleModulator is a free-running phase generator: its output is a normalized basis that advances continuously from 0 to 1 and wraps, driven by a speed control expressed in cycles-per-second rather than a fixed period.

- Basis advances each frame by the elapsed time fraction times the max-rate scale times the speed control, wraps modulo 1, and produces a raw linear sawtooth ramp with no built-in easing or waveshape options.
- The speed-polarity toggle swaps between unipolar (always forward) and bipolar (can reverse) speed; switching converts the current speed value between them, so direction flips live without resetting phase.
- The speed-range control scales the max rate in Hz; both speed controls act CONTINUOUSLY and can be swept live.
- Supports directly setting its normalized value (e.g. mapped control, preset recall), which jumps the basis directly rather than easing toward it.

## Parameter interactions

- Bipolar speed allows negative values to run the phase backward; unipolar speed is clamped non-negative, always forward.
- The reset control snaps basis to 0 immediately rather than letting it wrap out naturally.

## Usage tips

- Prefer this over a periodic LFO when direct Hz-rate control with live sweep, or runtime-switchable forward/reverse motion, is needed.
- Output has no waveshape; pair with downstream shaping (e.g. sine conversion) if smooth easing is required.
- External writes to the normalized value jump phase instantly — expect a visible discontinuity, not a smooth transition.
