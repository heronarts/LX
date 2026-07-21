---
class: heronarts.lx.effect.InvertEffect
kind: effect
sourceRepo: LX
sourcePath: src/main/java/heronarts/lx/effect/InvertEffect.java
sourceSha256: 6510739084044dfccd1c9c03e6243bed9784840ce90e4cef7b2f74cea46f21fd
classBytesSha256: 2295b7320f3f2268ef6c7c21b06f55423c9b84658d51527c16f1c64b1a269c16
classBytesOrigin: ~/.m2/repository/com/heronarts/lx/1.2.1/lx-1.2.1.jar
lxVersion: 1.2.1
generatedAt: 2026-07-17T00:00:00Z
generator: lx-mcp-catalog/2 (claude-sonnet-5)
tags: color, utility, invert
---

## Summary

Produces a per-channel negative of the incoming color by interpolating each RGB channel toward 255-minus-itself via lookup table.

- Master amount and each of the three per-channel amounts multiply together, so red/green/blue can be inverted independently or in any combination.
- Alpha is untouched; only RGB is remapped.
- Effective amounts are CONTINUOUS, but the lookup table for a channel only rebuilds when that channel's effective amount actually changes, and the effect is a true no-op (zero cost) when all three effective amounts are zero.

## Parameter interactions

- Master amount scales all three channel amounts together; a channel amount of zero leaves that channel un-inverted no matter how high master is set.
- Inverting only one channel (e.g. red) shifts the palette toward that channel's complement (cyan) while the other two channels pass through unchanged — a lightweight split-tone / color-grading tool.
- All amounts at zero disables rendering entirely for this effect, so it is safe to leave enabled at zero amount.

## Usage tips

- Animate master amount with an LFO for a smooth negative-to-positive cycling look.
- Use per-channel amounts instead of master amount for color grading (e.g., invert red only) rather than a full photographic negative.
