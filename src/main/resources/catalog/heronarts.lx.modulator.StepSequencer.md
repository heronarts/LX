---
class: heronarts.lx.modulator.StepSequencer
kind: modulator
sourceRepo: LX
sourcePath: src/main/java/heronarts/lx/modulator/StepSequencer.java
sourceSha256: 384d44095ee147c2fd5eca08b6233658984f582ececcf0bcc45faa1ef208fdd3
classBytesSha256: 1e1abb2118172847abfff3939d3ee668cbd01c9201c806ba540de655e067c723
classBytesOrigin: ~/.m2/repository/com/heronarts/lx/1.2.1/lx-1.2.1.jar
lxVersion: 1.2.1
generatedAt: 2026-07-17T00:00:00Z
generator: lx-mcp-catalog/2 (claude-sonnet-5)
tags: trigger, sequencer, rhythm, utility
---

## Summary

StepSequencer is a boolean 16-step gate sequencer: it fires its trigger output only on steps flagged active, letting an operator author an on/off rhythmic pattern.
- Unlike the related step-value modulator, it does not compute a per-step numeric value, so its own scalar value stays at 0 — it is a pure trigger source, not a value source.
- It disables itself as a mapping source (not selectable for parameter modulation), signaling that only its trigger output is meaningful.

## Parameter interactions

- Each step's boolean flag is checked at the moment that step becomes active; a step with its flag off is silently skipped (no trigger), while flag-on steps fire the trigger output.
- Step advance behaves as in the shared step-advance base: an internal trigger mode advances on its own timer, an external trigger mode needs incoming trigger events to advance, and a step-count control bounds how many of the 16 steps cycle.

## Usage tips

- Read this modulator via its trigger output rather than its scalar value, since the value is not meaningful.
- Use to build a repeating rhythmic gate pattern (e.g. drive a strobe or flash pattern) across up to 16 steps.
