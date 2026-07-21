---
class: heronarts.lx.modulator.Smoother
kind: modulator
sourceRepo: LX
sourcePath: src/main/java/heronarts/lx/modulator/Smoother.java
sourceSha256: 474ebfbf68836549849df4d59ee9976ab052ae1cc4961f965b95ed6160095efe
classBytesSha256: d0705c7fc339b9d43db5c557016d5fe24ce6d91d9eac3220f6005d54e4893a24
classBytesOrigin: ~/.m2/repository/com/heronarts/lx/1.2.1/lx-1.2.1.jar
lxVersion: 1.2.1
generatedAt: 2026-07-17T00:00:00Z
generator: lx-mcp-catalog/2 (claude-sonnet-5)
tags: utility, smoothing, filter, envelope
---

## Summary

Smoother low-pass filters a live input by lerping its own value toward the input each frame — an ease-out damper, not a fixed-time exponential filter.
- A window control (scaled by a separate range setting) sets the smoothing time window; input is tracked continuously every frame.
- The per-frame lerp amount is the elapsed frame time divided by the effective window, clamped to 1, so a small window or a long frame gap causes the output to snap directly to the input in a single frame instead of smoothing.

## Parameter interactions

- The window range control rescales what "1.0" on the main window control means in milliseconds, so the two must be read together to know the effective smoothing time.
- Very short windows relative to actual frame time produce no visible smoothing at all, since the lerp clamp lets the output fully catch up each frame.

## Usage tips

- Directly setting its normalized value is unsupported (throws) — drive Smoother only through its input parameter, not by writing to the modulator's value directly.
- Use to damp a jittery or steppy signal (e.g. raw audio level, a snapping parameter) into a continuous curve rather than building a custom lerp in a pattern.
