---
class: heronarts.lx.modulator.Scaler
kind: modulator
sourceRepo: LX
sourcePath: src/main/java/heronarts/lx/modulator/Scaler.java
sourceSha256: 8014d2e649c2ad253d20a17afc5ce7dd125df53a3c4c626df3ba98ad1745ccf0
classBytesSha256: 563ae393a7b1c71e9173da08306a01ca8db02255f42f727a7382db2a0f764003
classBytesOrigin: ~/.m2/repository/com/heronarts/lx/1.2.1/lx-1.2.1.jar
lxVersion: 1.2.1
generatedAt: 2026-07-17T00:00:00Z
generator: lx-mcp-catalog/2 (claude-sonnet-5)
tags: utility, shaping, gain, smoothing, signal
---

## Summary

Scaler rescales and reshapes an input signal with gain, offset, and a power-curve shaper — a general-purpose waveshaper for conditioning one modulator's output before feeding it to another.
- Input polarity selects unipolar (0..1) or bipolar (-1..1 around 0.5) treatment; gain, offset, and shaping all apply differently depending on this mode.
- Gain polarity picks between two separate gain parameters/multipliers (unipolar vs. bipolar range), independent of input polarity.
- All controls are read continuously, responding live to modulation every frame.

## Parameter interactions

- Negative gain in unipolar mode does not invert by multiplication — it lerps the input toward its complement (1 - input), compressing/folding rather than negating.
- Offset is applied after gain, then clamped into range before shaping runs, so a large gain plus offset can clip the input and flatten the shaper's usable range.
- Shaping applies a power curve: in unipolar mode positive shaping biases toward 0, negative toward 1; in bipolar mode it biases each half independently around the 0.5 center.
- A preview-display toggle is UI-only and has no effect on the computed value.

## Usage tips

- Directly setting its normalized value is unsupported (throws) — drive Scaler only via its input parameter.
- Use as an inline conditioner between two modulators (compress swing, add DC offset, fold a signal) rather than reimplementing gain/offset math in a pattern.
