---
class: heronarts.lx.audio.BandFilter
kind: modulator
sourceRepo: LX
sourcePath: src/main/java/heronarts/lx/audio/BandFilter.java
sourceSha256: 8f35815ad0d448367d956a2356a90e28f7ff9d61d41c2ae41813c4834f3a944b
classBytesSha256: 57818cd801d46bd3d2a58b6a32337d774709b61b069c2a03d97e7115493910c4
classBytesOrigin: ~/.m2/repository/com/heronarts/lx/1.2.1/lx-1.2.1.jar
lxVersion: 1.2.1
generatedAt: 2026-07-17T00:00:00Z
generator: lx-mcp-catalog/2 (claude-sonnet-5)
tags: audio-reactive, envelope, smoothing, utility
---

## Summary

BandFilter registers as a processor on the global audio spectrum meter and continuously outputs the smoothed average level (normalized 0-1) of a configurable frequency band, applying its own gain/range/attack/release/slope on top of the meter's FFT rather than reading a fixed band index.

- Value only updates while the modulator is running and the meter delivers audio frames; it freezes (does not reset) if stopped mid-signal, and resets to 0 when the meter reports audio has stopped.
- Directly setting the normalized value is unsupported (throws) — this is a read-only computed output, never settable.

## Parameter interactions

- The min/max frequency bounds act CONTINUOUSLY and are mutually clamped — moving one past the other snaps them to match rather than inverting the range.
- The slope (dB/octave) is applied relative to the band's average octave (midpoint of the min/max frequency bounds), so widening the range shifts the slope's effect at a fixed value.
- The attack/release times act CONTINUOUSLY on both the internal per-bin meter smoothing and the band's own average smoothing.

## Usage tips

- Use this (or BandGate) instead of hand-rolling FFT band math in a pattern — it is the standard per-band audio metering primitive.
- Prefer BandGate when a trigger/beat-detect boolean is needed; BandFilter exposes only a continuous level, no trigger output.
