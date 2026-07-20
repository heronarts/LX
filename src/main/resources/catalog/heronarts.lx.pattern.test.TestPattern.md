---
class: heronarts.lx.pattern.test.TestPattern
kind: pattern
sourceRepo: LX
sourcePath: src/main/java/heronarts/lx/pattern/test/TestPattern.java
sourceSha256: 334de968a8182c42b479c0422d3b8d3d0e8db460587c5efe256542fe8c2a7c00
classBytesSha256: 23c1895aeaca5b89ffe34719f279ba471b4a6e55eaf03a072ecfc74e012b340b
classBytesOrigin: ~/.m2/repository/com/heronarts/lx/1.2.1/lx-1.2.1.jar
lxVersion: 1.2.1
generatedAt: 2026-07-17T00:00:00Z
generator: lx-mcp-catalog/2 (claude-sonnet-5)
tags: utility, test
---

## Summary

Lights exactly one region white at a time against black, to identify which physical LED corresponds to which model point or tag.

- Iterate mode SAMPLES the next point index at a configurable interval (in milliseconds) and cycles through all model points in index order, wrapping back to the start.
- Fixed mode pins a single point by index; tag mode either lights every point under a named model tag simultaneously, or steps through them one at a time by index.

## Parameter interactions

- Mode selects which of rate, fixedIndex, tag, tagAll, and tagIndex are relevant; the others are inert.
- CPU test is unrelated to the visual output — it burns a configurable number of extra multiplications per frame purely to load-test the render pipeline.
- The pattern is marked not eligible for auto-cycle, so it never gets automatically swapped out once selected.

## Usage tips

- Use at commissioning time to verify fixture-to-index mapping and confirm model topology matches the physical install; tag mode confirms fixtures were grouped correctly.
- Remove or swap out before a live show — auto-cycle exclusion means it stays loaded indefinitely if left active.
- Leave CPU test at zero outside of render-performance benchmarking; it has no visual effect.
