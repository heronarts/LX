---
class: heronarts.lx.modulator.MultiModeEnvelope
kind: modulator
sourceRepo: LX
sourcePath: src/main/java/heronarts/lx/modulator/MultiModeEnvelope.java
sourceSha256: 481db845aa1c3abc86d21dddbfb3f1075c3567ee72ac992aa015eaa01672b4d7
classBytesSha256: 0c5ecbfce63c57f903cd5e17a6a54413828a4411d6c03d3eb72feccfce4bb188
classBytesOrigin: ~/.m2/repository/com/heronarts/lx/1.2.1/lx-1.2.1.jar
lxVersion: 1.2.1
generatedAt: 2026-07-17T00:00:00Z
generator: lx-mcp-catalog/2 (claude-sonnet-5)
tags: envelope, trigger, midi, adsr, motion, utility
---

## Summary

MultiModeEnvelope is a full DAHDSR envelope built on a shared stage-machine base, with a stage-mode selector choosing which stages (delay/attack/hold/decay/sustain/release) are active and a bipolar shape control continuously reshaping the attack/decay/release curves.

- The momentary engage trigger starts the envelope from its first active stage; a separate retrigger control restarts from the first stage too, but without touching engage or jumping the current value — it re-attacks from wherever the envelope currently sits.
- The reset-mode toggle off (default) makes re-engaging while already in the first stage a no-op (soft re-arm from the current level); on, it always hard-resets to the initial level.
- The one-shot toggle on lets the envelope play through decay/sustain/release to completion even after engage goes false (sustain is skipped rather than held); off, it jumps straight to the stage mode's last active stage the instant engage goes false — release for modes that include sustain/release, but decay for attack-decay-only stage modes that have no sustain/release stage.

## Parameter interactions

- The shape control maps continuously to an exponent applied to the attack/decay/release curves, and re-applies live rather than only at trigger time — but it warps attack in the opposite direction from decay/release: a positive shape value makes attack rise slowly at first and accelerate near the end (ease-in), while the same positive value makes decay/release fall quickly at first and taper into a long, slow tail (ease-out). Negative shape values reverse each of those.
- MIDI note-on scales the peak level per-note from velocity/note-response settings (sampled at note-on); legato mode suppresses retrigger for overlapping notes, sustaining the current stage instead.
- The manual-trigger and target-trigger inputs both force the peak level to 1 before engaging — external trigger sources always get full-strength envelopes, not velocity-scaled ones.
- The trigger source is the engage control, so this can be wired as a trigger source for other modulators.

## Usage tips

- Use the retrigger control for rapid re-triggering without a value jump; use engage with reset-mode on for a hard-reset percussive re-strike.
- Turn on one-shot mode for percussive MIDI hits so the full decay/release plays out regardless of note duration.
