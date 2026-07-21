---
class: heronarts.lx.effect.color.TransparifyEffect
kind: effect
sourceRepo: LX
sourcePath: src/main/java/heronarts/lx/effect/color/TransparifyEffect.java
sourceSha256: 862be53c0c7e0b20aa8f3e1e2f2bab5bbaf36e831baec71794be4d6bd3a39c4f
classBytesSha256: 441c9ef757030c245a9b60cc8f8e044dcd58316752f816593fc8709681854d6d
classBytesOrigin: ~/.m2/repository/com/heronarts/lx/1.2.1/lx-1.2.1.jar
lxVersion: 1.2.1
generatedAt: 2026-07-17T00:00:00Z
generator: lx-mcp-catalog/2 (claude-sonnet-5)
tags: masking, color, compositing, utility
---

## Summary

TransparifyEffect drives the alpha channel down for pixels whose sampled scalar value falls at or below a threshold, leaving RGB untouched.
- Pixels above the threshold pass through completely unchanged, including their existing alpha.
- The sampled scalar is selectable per pixel: brightness, luminosity, an individual R/G/B channel, or channel min.
- Parameters are read every frame, so all controls respond continuously to live modulation.

## Parameter interactions

- Threshold sets the ceiling: only pixels with a source value at or below it are candidates for alpha reduction.
- Feather chooses the falloff shape for sub-threshold pixels: feather=0 applies the same uniform maximum reduction to all of them regardless of value; feather=1 grades smoothly from no reduction at the threshold to full reduction at zero.
- Amount scales how far alpha is pushed toward zero, and is itself multiplied by the effect's own enabled/fade amount — fading the effect in or out also scales the transparency strength.
- A combined amount of zero makes the effect a no-op for that frame.

## Usage tips

- Use to knock out a channel's dark background so lower channels in the mixer show through; place near the end of a channel's effect chain, before the composite.
- feather=1 gives a natural graded edge near the threshold; feather=0 gives a hard cutoff with a visible seam.
- Luminosity keys on perceived brightness and tends to key more cleanly on saturated content than brightness (channel max); the R/G/B/min options key on a specific channel instead.
