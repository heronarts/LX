---
class: heronarts.lx.modulator.MacroKnobs
kind: modulator
sourceRepo: LX
sourcePath: src/main/java/heronarts/lx/modulator/MacroKnobs.java
sourceSha256: 28e8fb70712ee61ce16d4e4be6e04a393f6fa2d260c7dde755adef4140f267a6
classBytesSha256: 8da501511f93162774f5e9d1e9b9d06cf5418e6807de1d165142bd282b2eaad0
classBytesOrigin: ~/.m2/repository/com/heronarts/lx/1.2.1/lx-1.2.1.jar
lxVersion: 1.2.1
generatedAt: 2026-07-17T00:00:00Z
generator: lx-mcp-catalog/2 (claude-sonnet-5)
tags: utility, macro
---

## Summary

MacroKnobs is a bank of eight independently-labeled knobs with no internal computation — its own modulator value is a fixed zero. It exists purely as a mapping/control-surface source: set each knob's value (manually, via MIDI/OSC, or by mapping) and map that value onward to other parameters.

## Parameter interactions

- Each knob is a separate parameter; there is no fan-out or grouping behavior between them — mapping one knob does not affect the others.
- The knob-count display toggle only controls how many of the knobs the UI shows; it does not disable or reset the hidden ones.

## Usage tips

- Use this to give a set of unrelated mapped destinations a single, user-labeled home rather than as a modulator with any autonomous motion.
