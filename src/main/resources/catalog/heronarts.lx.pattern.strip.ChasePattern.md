---
class: heronarts.lx.pattern.strip.ChasePattern
kind: pattern
sourceRepo: LX
sourcePath: src/main/java/heronarts/lx/pattern/strip/ChasePattern.java
sourceSha256: 3ee0f6341098947eab36ab1cf9f82f54419e23b6fe2779816b27d16d7c2015a8
classBytesSha256: 4b5cfb80bd3a519467aa3340927a17426ba7e230a53365b154e0ebe232f216da
classBytesOrigin: ~/.m2/repository/com/heronarts/lx/1.2.1/lx-1.2.1.jar
lxVersion: 1.2.1
generatedAt: 2026-07-17T00:00:00Z
generator: lx-mcp-catalog/2 (claude-sonnet-5)
tags: motion, strip, geometric, generative
---

## Summary

Divides the model's point list into repeating chunks by index order and animates a bright band within each chunk using a selected waveshape.

- Each frame evaluates a waveshape (sine/triangle/ramp-up/ramp-down) of a moving basis to get a position within the chunk, then measures each point's index-distance from that position to set brightness.
- Speed runs CONTINUOUSLY in Hz; with tempo sync on, the motion basis is instead SAMPLED from the tempo's phase each frame.

## Parameter interactions

- Chunk size (interpolated between min/max bounds) sets segment width; shift offsets each successive chunk's motion phase so chunks don't fire in lockstep.
- Skew biases the waveshape's peak toward one end of its cycle; exp then sharpens (positive) or softens (negative) the resulting pulse.
- Wrap mode changes how distance-to-peak is measured: ABS is symmetric, POS/NEG give asymmetric leading/trailing-edge chases, CLIP variants stop at the boundary instead of wrapping.
- The optional swarm attractor (an XY point with its own radius) locally overrides size/fade/brightness near itself, layering a spatial cluster on the base chase.

## Usage tips

- Best suited to strip-topology models where point index order matches physical layout; on 3D models the effect follows point iteration order, which may not match spatial layout.
- Modulate speed or shift from an LFO for layered chases, or enable tempo sync plus a division setting to phase-lock the chase to the beat.
