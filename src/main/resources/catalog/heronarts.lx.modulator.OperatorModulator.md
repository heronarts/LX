---
class: heronarts.lx.modulator.OperatorModulator
kind: modulator
sourceRepo: LX
sourcePath: src/main/java/heronarts/lx/modulator/OperatorModulator.java
sourceSha256: 50e2bcc598d764d0905e44382c4f06f207f032d4f7521137e1576e75a75333b4
classBytesSha256: 462f727c4e041c43d110019d034b911594f914642659cd38af13b097babd8ac4
classBytesOrigin: ~/.m2/repository/com/heronarts/lx/1.2.1/lx-1.2.1.jar
lxVersion: 1.2.1
generatedAt: 2026-07-17T00:00:00Z
generator: lx-mcp-catalog/2 (claude-sonnet-5)
tags: utility, math, modulation-combiner
---

## Summary

OperatorModulator continuously combines two input values with a selectable binary operation, blended in by the amount control from a pass-through of the first input at 0 up to the full operation result at 1. All inputs are read fresh every frame — fully CONTINUOUS, no internal state or triggering.

- LERP: linear interpolation from the first input to the second input.
- ADD/SUBTRACT: the first input plus/minus the second input scaled by the amount control, clamped to 0-1.
- DIFFERENCE: blends from the first input toward the absolute difference of the two inputs.
- MULTIPLY: the first input scaled by a factor that lerps from 1 to the second input as the amount control rises.
- DIVIDE: blends toward the first input divided by the second, clamped to at most 1 — a zero second input produces a divide-by-zero result before clamping.
- RATIO: blends toward the smaller/larger ratio of the two inputs, constrained to 0-1.
- MIN/MAX: blends toward whichever input is smaller/larger.
- INVERT: blends toward the complement of the first input (1 minus it); the second input is unused in this mode.

## Parameter interactions

- Directly setting the normalized value is unsupported (throws) — this is a read-only derived value, not a settable one; drive its output by changing the two inputs or the amount control upstream.

## Usage tips

- Avoid DIVIDE when the second input can reach 0 (e.g. another modulator's raw output before rescaling) — use RATIO instead if a bounded, divide-safe comparison of two values is needed.
- INVERT with the second input left at its default is a convenient way to continuously crossfade a value against its own complement via the amount control.
