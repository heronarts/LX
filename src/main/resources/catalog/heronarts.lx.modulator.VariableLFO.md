---
class: heronarts.lx.modulator.VariableLFO
kind: modulator
sourceRepo: LX
sourcePath: src/main/java/heronarts/lx/modulator/VariableLFO.java
sourceSha256: b25e73a66e379de4cbac972ae4b21172f3748fd51611854dac287bd29eb8f2a5
classBytesSha256: 1585e8ddbfddd5c6b99caaa83ef1c801270cb897b45dbceabdb3cfecd1521461
classBytesOrigin: ~/.m2/repository/com/heronarts/lx/1.2.1/lx-1.2.1.jar
lxVersion: 1.2.1
generatedAt: 2026-07-17T00:00:00Z
generator: lx-mcp-catalog/2 (claude-sonnet-5)
tags: lfo, motion, waveshaping, envelope, utility
---

## Summary

VariableLFO is the general-purpose periodic oscillator: it advances a 0-1 cycle basis, warps that basis with phase/bias/skew before evaluating a selectable waveshape, then warps the resulting output value with shape/exp.
- The waveshape is selectable from a default set (sine, triangle, square, ramp-up, ramp-down) or a custom list of shapes supplied at construction.
- A clock-mode control picks how the cycle advances, mutually exclusive: fixed fast/slow internal periods, tempo-sync to the LX tempo/division grid, or an input mode driving the cycle from a manual/external basis instead of free-running time.
- All five shaping controls are read continuously every frame — sweeping any live-reshapes the waveform in real time.

```
basis = wrap(cycleBasis + phase, 1)
basis = biasWarp(basis, bias)        # S-curve pull toward/away from cycle center
basis = pow(basis, skewPower(skew))  # skews timing within the cycle
value = waveshape(basis)
value = shapeWarp(value, shape)      # power curve around the output midpoint
value = pow(value, expPower(exp))    # exponential scaling of output amplitude
```

## Parameter interactions

- Bias and skew warp *where in the cycle* the waveshape gets sampled — changing the raw wave's timing/asymmetry (e.g. a sine's rise vs. fall duration) without altering its amplitude curve. Shape and exp instead warp the *output value* after the waveshape runs, reshaping amplitude (e.g. squaring off a sine's peaks) without shifting timing. Combining both types produces an asymmetric-timed wave with a non-default amplitude curve.
- Reverse-mapping a value back to a basis through this LFO (as another modulator may do) only inverts the base waveshape — bias, skew, shape, and exp are not accounted for, so the reverse mapping is inaccurate whenever any of those four are nonzero.

## Usage tips

- Use bias to create swing/shuffle rhythms — it shifts the waveform's peak earlier or later in the cycle without changing its amplitude shape.
- Use shape to push a sine or triangle toward square-like extremes, or squash toward a flatter mid-range, independent of bias/skew timing changes.
- Avoid dependent modulators that reverse-map through this LFO while bias/skew/shape/exp are nonzero — the result will not match the forward-computed curve.
