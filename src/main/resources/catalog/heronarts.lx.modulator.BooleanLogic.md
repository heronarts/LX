---
class: heronarts.lx.modulator.BooleanLogic
kind: modulator
sourceRepo: LX
sourcePath: src/main/java/heronarts/lx/modulator/BooleanLogic.java
sourceSha256: bd9b8ecaa8e1b3645587601fd66e2849ab7235ea5b177a5a6c955e7fa37f5b81
classBytesSha256: edfa062fe78cf512d20822e4a6a43682ff3f52b54b4cc90c13e47710bf56f62f
classBytesOrigin: ~/.m2/repository/com/heronarts/lx/1.2.1/lx-1.2.1.jar
lxVersion: 1.2.1
generatedAt: 2026-07-17T00:00:00Z
generator: lx-mcp-catalog/2 (claude-sonnet-5)
tags: trigger, utility, logic
---

## Summary

BooleanLogic combines up to 4 boolean inputs through 3 chained binary operators (AND/OR/XOR) evaluated strictly left to right — each operator combines the running result with the next input in sequence — with per-input NOT inversion, and exposes the result on its output as both its computed value and its trigger source.

```
result = first input (optionally inverted)
result = first operator(result, second input optionally inverted)
result = second operator(result, third input optionally inverted)
result = third operator(result, fourth input optionally inverted)
```

## Parameter interactions

- Strictly left-to-right evaluation means no operator precedence — a chain like "input 1 OR input 2 AND input 3" evaluates as "(input 1 OR input 2) AND input 3", not with AND binding tighter.
- To use fewer than 4 inputs, set the corresponding operator to a no-op for the default (false) value (e.g. OR) rather than assuming unused slots are ignored — they always participate.
- No latching; output re-evaluates every frame from current input states.

## Usage tips

- Use this to combine several trigger/gate sources (e.g. two BandGate outputs) into one derived trigger without writing a custom modulator.
- Not a modulator for combining more than 4 boolean sources or expressing precedence-sensitive expressions — chain multiple BooleanLogic instances if that's needed.
