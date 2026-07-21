---
class: heronarts.lx.dmx.DmxPattern
kind: pattern
sourceRepo: LX
sourcePath: src/main/java/heronarts/lx/dmx/DmxPattern.java
sourceSha256: 5b67adf3850571b74a62ff443e77c4a46aad42db0075a919a26421c2fba70f2c
classBytesSha256: 77360259245109d3be190e10a629e364bc30a451617a7b54f5442ded03fabd21
classBytesOrigin: ~/.m2/repository/com/heronarts/lx/1.2.1/lx-1.2.1.jar
lxVersion: 1.2.1
generatedAt: 2026-07-17T00:00:00Z
generator: lx-mcp-catalog/2 (claude-sonnet-5)
tags: dmx, utility, color
---

## Summary

Maps live DMX universe data directly to pixel colors, reading 3 consecutive channels per model point starting at a configurable universe/channel offset.

- Advances CONTINUOUSLY across universe boundaries once the current universe's 510 channels are exhausted, spanning as many universes as the model needs.
- Byte order is configurable, letting the same DMX stream be reinterpreted for consoles/fixtures using non-RGB channel ordering.

## Parameter interactions

- Universe and channel together anchor a sequential per-point mapping; changing channel offset slides the mapping within a universe, changing universe jumps it by a full 170-pixel (510-channel) block.
- Byte order reinterprets the same 3 bytes differently without touching position, so switching it on a running show can shift hues without moving any other control.

## Usage tips

- Use when an external DMX console or media server is the primary color source and LX should act as a protocol bridge/visualizer.
- Produces no output of its own — verify the DMX engine is actually receiving data before debugging apparent blank output.
- For installs where only some fixtures are DMX-driven, put this pattern on the relevant channel and blend/mask with other channels for the rest.
