---
class: heronarts.lx.effect.FreezeEffect
kind: effect
sourceRepo: LX
sourcePath: src/main/java/heronarts/lx/effect/FreezeEffect.java
sourceSha256: 4d0e04ab74cd59ceebda19d761e6d5fd80ba35b5fce9397f2ded461845be54bc
classBytesSha256: 158dc22db9a8711dd0cd7c06580ec4d5a5c5ad1c1614f838cb4a13e6c3954954
classBytesOrigin: ~/.m2/repository/com/heronarts/lx/1.2.1/lx-1.2.1.jar
lxVersion: 1.2.1
generatedAt: 2026-07-17T00:00:00Z
generator: lx-mcp-catalog/2 (claude-sonnet-5)
tags: utility, motion, strobe, trigger, envelope
---

## Summary

FreezeEffect captures a snapshot of the color buffer and composites it back over the live buffer through a selectable blend mode, with attack/release envelope timing.

- The latching freeze toggle latches: turning it on (with the momentary hold toggle off) captures a frame and engages the freeze until turned off.
- The momentary hold toggle is momentary: freezes only while held, releasing as soon as it's let go.
- The resample control re-captures the current frame without changing engage/release state — refreshes the frozen image mid-hold.
- The internal periodic-capture modulator periodically triggers a capture but does not itself engage the freeze, and it overwrites the buffer unconditionally on every trigger regardless of toggle state — including during the release tail, where the freshly-captured frame is still visible via the decaying release blend even though neither toggle is active.

## Parameter interactions

- The attack time, release time, and blend mode are read continuously every frame, so they respond live to modulation while a freeze is engaging or releasing.
- An attack time of 0 snaps in instantly instead of crossfading; the engine still forces a full release phase even for a zero-attack engage.
- The mix control continuously scales the frozen frame's opacity, combined with the envelope position.
- The blend mode picks the per-pixel blend function: Replace uses a lightest/multiply mask driven by mix, but only while the latching or hold toggle is actively engaged — during the release tail after either toggle turns off, Replace falls back to a plain crossfade instead. Multiply, Add, Subtract, Difference, Spotlight, and Highlight each apply their own direct blend throughout, with no such engaged-vs-releasing difference.

## Usage tips

- Combine the periodic-capture interval with the latching or hold toggle engaged for a periodic sampled-frame look; the interval alone with both off is a no-op.
- Use the resample control while locked/held to swap in a fresh frozen image without retriggering the attack envelope.
- Turning one of the latching/hold toggles on while the other is already on does not re-trigger a capture.
