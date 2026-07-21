---
class: heronarts.lx.modulator.Spring
kind: modulator
sourceRepo: LX
sourcePath: src/main/java/heronarts/lx/modulator/Spring.java
sourceSha256: e96d63ff4bdf69388e37807d65263fa7828563f758722a17e79d09b8b30ed92f
classBytesSha256: 3129c2b6d29b47851260d0e862d17dc65428a2c9a1cd03f4ea6fee38f8e8582e
classBytesOrigin: ~/.m2/repository/com/heronarts/lx/1.2.1/lx-1.2.1.jar
lxVersion: 1.2.1
generatedAt: 2026-07-17T00:00:00Z
generator: lx-mcp-catalog/2 (claude-sonnet-5)
tags: motion, physics, envelope, utility, smoothing
---

## Summary

Spring is a physics-simulated spring-damper: it holds a stateful position/velocity pair that is continuously pulled toward a target and bounces elastically off the [0,1] bounds.
- position is the live target the spring is pulled toward — read continuously, so sweeping it moves the target smoothly while the spring's own output value lags and oscillates behind it.
- The modulator's own current value and velocity persist across frames (semi-implicit Euler integration), so it is genuinely stateful, unlike most compute-from-basis modulators.

```
distance = currentValue - target
accel = -(tension*100) * distance - (friction*10) * velocity
currentValue += velocity*dt + 0.5*accel*dt^2
velocity += accel*dt
if currentValue outside [0,1]: clamp to bound; velocity = -velocity * bounce
```

## Parameter interactions

- tension sets stiffness: higher tension pulls toward the target faster and oscillates at a higher frequency.
- friction sets damping: higher friction settles faster with less overshoot; very high friction can overdamp and slow the approach.
- bounce sets the restitution applied only when position hits 0 or 1: bounce=0 kills velocity dead on impact (stops at the boundary), bounce near 1 nearly fully reflects velocity, producing repeated ringing at the bound.
- Changing tension or friction while the spring is mid-motion immediately changes its dynamics for the next frame, since both are read continuously inside the integration step.

## Usage tips

- Directly setting its normalized value is unsupported (throws) — drive Spring only by moving its position (target) parameter, not by writing to the modulator's value.
- Use for organic, physically-motivated transitions between discrete states (e.g. a parameter that should "settle into" a new value with overshoot) rather than a linear ramp.
- Low bounce plus moderate friction gives a clean settle with no overshoot; high bounce plus low friction gives sustained bouncing/ringing at the boundary — pick based on whether ringing is a desired visual effect.
