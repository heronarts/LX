---
class: heronarts.lx.modulator.Timer
kind: modulator
sourceRepo: LX
sourcePath: src/main/java/heronarts/lx/modulator/Timer.java
sourceSha256: 9778ff3551aa7cec8b91ebfead325c958b55f02ce9f9976387ab57c6803a39e8
classBytesSha256: 71ebb4c149a05b3af576a21264542742a4bc6bf9eb5e470f67b4b192cfcf31d2
classBytesOrigin: ~/.m2/repository/com/heronarts/lx/1.2.1/lx-1.2.1.jar
lxVersion: 1.2.1
generatedAt: 2026-07-17T00:00:00Z
generator: lx-mcp-catalog/2 (claude-sonnet-5)
tags: trigger, schedule, time, utility
---

## Summary

Timer fires a momentary trigger once at a specified time-of-day, on selected days of the week, by polling the system clock every frame.
- It compares the current wall-clock second-of-day to the configured time for exact equality, so it fires for only the single frame where they match.
- A per-weekday on/off toggle gates which days it is armed on; the current day is re-evaluated continuously against the system calendar.

## Parameter interactions

- Because the match is exact-second equality rather than a range, any interruption that skips over the matching second (system sleep/wake, clock adjustment, or the engine running below ~1fps) will cause it to miss firing that day.
- It disables itself as a mapping source, signaling it should be consumed via its trigger output rather than as a general modulation value.

## Usage tips

- Read via its trigger output rather than the modulator's scalar value, which is 1 only on the firing frame and 0 otherwise.
- Directly setting its normalized value is unsupported (throws). Use for schedule-driven show cues (e.g. "fire at 9:00pm on weekdays") rather than as a periodic modulator.
