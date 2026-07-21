---
class: heronarts.lx.effect.BlurEffect
kind: effect
sourceRepo: LX
sourcePath: src/main/java/heronarts/lx/effect/BlurEffect.java
sourceSha256: 36e0c1c8808c99725aef339cebea0fed7e70028c97f30ec5c6b9d5387a53c3f4
classBytesSha256: c137f8a3a9f1584c0d6fd795768d128d45140a3c9c1d880f8bedd3502069873b
classBytesOrigin: ~/.m2/repository/com/heronarts/lx/1.2.1/lx-1.2.1.jar
lxVersion: 1.2.1
generatedAt: 2026-07-17T00:00:00Z
generator: lx-mcp-catalog/2 (claude-sonnet-5)
tags: motion, texture, utility, smoothing
---

## Summary

BlurEffect maintains a persistent per-pixel blur buffer that accumulates a temporal motion trail across frames.

- Each frame the buffer decays exponentially toward black, then merges with the current frame using a brightest-wins blend, so the trail can never exceed the brightness of the live source.
- The buffer is then composited onto the output via one of five blend modes: Mix (replace), Add (brighten), Screen (soften highlights without blowing out), Multiply (darken), or Lightest (keep brighter of blur vs. live).

## Parameter interactions

- Level sets the blend weight of the blur composite, acting continuously; the buffer keeps accumulating even at Level 0, so raising Level later reveals a trail already built up.
- Decay (seconds) and Factor jointly shape the decay curve: Decay is the time to reach the Factor level, so short Decay with low Factor gives tight snappy trails, while long Decay with high Factor gives a lingering trail. Both act continuously.
- Mode selects the blend function and is read continuously, so switching modes live is safe.

## Usage tips

- Most effective behind fast-moving point content where a persistent trail reads clearly; on static or slow content it mostly softens edges.
- Add mode can blow out to white quickly on bright, full-field content at high Level; Mix, Screen, or Lightest carry less risk of saturation.
- The buffer resets to black whenever the effect is enabled, so toggling it on mid-show produces a brief cold-start ramp before trails rebuild.
