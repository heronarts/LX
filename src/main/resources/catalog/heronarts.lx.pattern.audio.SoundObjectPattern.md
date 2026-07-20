---
class: heronarts.lx.pattern.audio.SoundObjectPattern
kind: pattern
sourceRepo: LX
sourcePath: src/main/java/heronarts/lx/pattern/audio/SoundObjectPattern.java
sourceSha256: a3bebd665bf96c43ad46be8b04bdef852267205e2627ecc6d5c25b58ed3b0292
classBytesSha256: 4ea4e4589a86a9fdaf1a0bc041bf8a0a7a851ebb7424805c09ec5c2a1222b1c6
classBytesOrigin: ~/.m2/repository/com/heronarts/lx/1.2.1/lx-1.2.1.jar
lxVersion: 1.2.1
generatedAt: 2026-07-17T00:00:00Z
generator: lx-mcp-catalog/2 (claude-sonnet-5)
tags: audio-reactive, geometric, motion, generative
---

## Summary

Renders a greyscale orb centered on a tracked sound object's 3D position, sized and brightened by that object's live signal level.

- Distance from each point to the object comes from a selectable shape function (sphere/box/single-axis), continuously blended between two shape choices.
- Falloff is linear from full brightness at the core to zero at an edge set by size and contrast — low contrast gives a soft diffuse orb, high contrast a sharp ring.
- Base size and brightness are each scaled CONTINUOUSLY by a manual modulation input and by the live audio signal.

## Parameter interactions

- Signal/modulation-to-size or -brightness controls can be positive (scale the value up as the input rises) or negative (invert the relationship).
- An optional scope mode adds a spatial echo: distance maps to elapsed time in a rolling signal-history buffer, so points farther out reflect older audio. Scope time sets how far back that history reaches; scope amount sets how strongly it dimples edge brightness — zero disables the echo.
- Position mode determines how the tracked position resolves into model space before the above is applied.

## Usage tips

- Best suited to multi-object or spatial audio setups where objects move through 3D space; for a mono source, drive size/brightness from the manual modulation input instead.
- Very short scope times cause edge flicker; very long values create a faint trailing halo outlasting the transient.
- Shape blend lets one instance morph continuously between two distance functions instead of needing two patterns.
