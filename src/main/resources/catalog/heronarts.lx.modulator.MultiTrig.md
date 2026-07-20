---
class: heronarts.lx.modulator.MultiTrig
kind: modulator
sourceRepo: LX
sourcePath: src/main/java/heronarts/lx/modulator/MultiTrig.java
sourceSha256: 8059eed6af086fa80873a3180e55eceec806bf169274186db80c89e4f2b214f1
classBytesSha256: 3f070660d073f6bae44542e37ae4d8b7a6db64419cceea5edd2fb1562b110d05
classBytesOrigin: ~/.m2/repository/com/heronarts/lx/1.2.1/lx-1.2.1.jar
lxVersion: 1.2.1
generatedAt: 2026-07-17T00:00:00Z
generator: lx-mcp-catalog/2 (claude-sonnet-5)
tags: trigger, utility, routing
---

## Summary

MultiTrig fans one incoming trigger out to up to 5 output triggers, choosing which fire via the fan-out mode: ALL fires every active output, RANDOM fires one at random, CYCLE/REVERSE step an index with wraparound, and FLIP ping-pongs the index between the ends.

- The active-output count (0-5) is read fresh on every incoming trigger — changing it live only affects future fan-outs, not an in-flight one.
- Each per-output chance is rolled independently at fan-out time, so in ALL mode some outputs can silently not fire even though the mode says "all".
- The input-chance gate controls whether the device responds to an incoming trigger at all, rolled once before any per-output chance rolls.

## Parameter interactions

- Has no continuous output value (always 0) and exposes no single trigger source — not itself usable as a trigger/modulation source; only the individual output parameters are.
- The UI labels for the second through fifth chance knobs are all mislabeled identically (a display bug) — identify the correct one by position/index in the device, not by its on-screen label.

## Usage tips

- Use CYCLE or REVERSE for deterministic round-robin fan-out (e.g. sequential activation of 5 zones); use RANDOM or FLIP for less predictable distribution patterns.
- Wire the input-indicator output if a visual/logic confirmation that the input trigger was accepted (independent of downstream chance gating) is needed.
