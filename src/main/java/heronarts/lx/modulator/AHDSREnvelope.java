/**
 * Copyright 2022- Mark C. Slee, Heron Arts LLC
 *
 * This file is part of the LX Studio software library. By using
 * LX, you agree to the terms of the LX Studio Software License
 * and Distribution Agreement, available at: http://lx.studio/license
 *
 * Please note that the LX license is not open-source. The license
 * allows for free, non-commercial use.
 *
 * HERON ARTS MAKES NO WARRANTY, EXPRESS, IMPLIED, STATUTORY, OR
 * OTHERWISE, AND SPECIFICALLY DISCLAIMS ANY WARRANTY OF
 * MERCHANTABILITY, NON-INFRINGEMENT, OR FITNESS FOR A PARTICULAR
 * PURPOSE, WITH RESPECT TO THE SOFTWARE.
 *
 * @author Mark C. Slee <mark@heronarts.com>
 */

package heronarts.lx.modulator;

import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.parameter.FixedParameter;
import heronarts.lx.parameter.LXNormalizedParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.TriggerParameter;
import heronarts.lx.utils.LXUtils;

public class AHDSREnvelope extends LXModulator implements LXNormalizedParameter {

  public static enum Stage {
    DELAY,
    ATTACK,
    HOLD,
    DECAY,
    SUSTAIN,
    RELEASE,
    OFF;
  };

  public static enum StageMode {
    AHDSR(Stage.ATTACK, Stage.HOLD, Stage.DECAY, Stage.SUSTAIN, Stage.RELEASE),
    ADSR(Stage.ATTACK, Stage.DECAY, Stage.SUSTAIN, Stage.RELEASE),
    AHD(Stage.ATTACK, Stage.HOLD, Stage.DECAY),
    AD(Stage.ATTACK, Stage.DECAY),
    DAHDSR(Stage.DELAY, Stage.ATTACK, Stage.HOLD, Stage.DECAY, Stage.SUSTAIN, Stage.RELEASE),
    DADSR(Stage.DELAY, Stage.ATTACK, Stage.DECAY, Stage.SUSTAIN, Stage.RELEASE),
    DAHD(Stage.DELAY, Stage.ATTACK, Stage.HOLD, Stage.DECAY),
    DAD(Stage.DELAY, Stage.ATTACK, Stage.DECAY);

    public final Stage[] stages;

    private StageMode(Stage ... stages) {
      this.stages = stages;
    }

    public boolean has(Stage s) {
      for (Stage stage : this.stages) {
        if (stage == s) {
          return true;
        }
      }
      return false;
    }

    public Stage firstStage() {
      return this.stages[0];
    }

    public Stage endStage() {
     return this.stages[this.stages.length - 1];
    }
  };

  public final BooleanParameter engage =
    new BooleanParameter("Engage")
    .setMode(BooleanParameter.Mode.MOMENTARY)
    .setDescription("Engages the envelope");

  public final TriggerParameter retrig =
    new TriggerParameter("Retrig")
    .setDescription("Retriggers the envelope without resetting it or changing engage status");

  public final BooleanParameter resetMode =
    new BooleanParameter("Reset", false)
    .setDescription("Sets whether the envelope completely resets on each new engagement");

  public final BooleanParameter oneshot =
    new BooleanParameter("Oneshot", false)
    .setDescription("Sets whether the envelope plays out even when disengaged");

  public EnumParameter<StageMode> stageMode =
    new EnumParameter<StageMode>("Stage Mode", StageMode.AHDSR)
    .setDescription("Which stages of the envelope are active");

  public final LXParameter delay, attack, hold, decay, sustain, release, initial, peak;

  private LXParameter shape;

  private double attackFrom = 0;
  private double decayFrom = 1;
  private double releaseFrom = 1;

  private Stage stage = Stage.OFF;

  private double stageBasis = 0;

  public AHDSREnvelope(String label, LXParameter delay, LXParameter attack, LXParameter hold, LXParameter decay, LXParameter sustain, LXParameter release, LXParameter initial, LXParameter peak) {
    super(label);
    addParameter("engage", this.engage);
    addParameter("retrig", this.retrig);
    addParameter("stageMode", this.stageMode);
    addParameter("resetMode", this.resetMode);
    addParameter("oneshot", this.oneshot);
    setDescription("Envelope Value");

    this.delay = delay;
    this.attack = attack;
    this.hold = hold;
    this.decay = decay;
    this.sustain = sustain;
    this.release = release;
    this.initial = initial;
    this.peak = peak;
    this.shape = new FixedParameter(1);;

    this.attackFrom = initial.getValue();
    this.decayFrom = this.releaseFrom = peak.getValue();
  }

  private void setStage(Stage stage) {
    if (this.stage != stage) {
      this.stage = stage;
      onStageChanged(this.stage);
    }
  }

  /**
   * A subclass may override to perform custom logic in this method
   *
   * @param stage Stage that is now active
   */
  protected void onStageChanged(Stage stage) {}

  public AHDSREnvelope setShape(LXParameter shape) {
    this.shape = shape;
    return this;
  }

  @Override
  public void onParameterChanged(LXParameter p) {
    super.onParameterChanged(p);
    if (p == this.stageMode) {
      if (this.stage != Stage.OFF) {
        // The stage mode has changed... but we're mid-envelope! Check
        // that we're not going to get ourselves stuck
        StageMode stageMode = this.stageMode.getEnum();
        if (!stageMode.has(this.stage) || !this.engage.isOn()) {
          this.stageBasis = 0;
          setStage(stageMode.endStage());
          this.decayFrom = this.releaseFrom = getValue();
        }
      }
    } else if (p == this.retrig) {
      if (this.retrig.isOn()) {
        this.stageBasis = 0;
        setStage(this.stageMode.getEnum().firstStage());
        this.attackFrom = getValue();
      }
    } else if (p == this.engage) {
      if (this.engage.isOn()) {
        // The envelope has been engaged, decide whether we're doing a
        // hard re-trigger or not and what stage to start in
        Stage firstStage = this.stageMode.getEnum().firstStage();
        if (this.resetMode.isOn()) {
          // When retrigger is on, we ALWAYS go hard back to the
          // very start of the envelope
          this.stageBasis = 0;
          setStage(firstStage);
          this.attackFrom = this.initial.getValue();
        } else {
          // Re-trigger off? Check if we were in a different stage,
          // only change things if so. Make sure we attack from
          // wherever we were at
          if (this.stage != firstStage) {
            this.stageBasis = 0;
            setStage(firstStage);
            this.attackFrom = getValue();
          }
        }
      } else {
        // The envelope is being disengaged. Set the stage appropriately
        // based upon current state.
        if ((this.stage != Stage.OFF) && !this.oneshot.isOn()) {
          // Only change state if we weren't already in this same stage
          Stage endStage = this.stageMode.getEnum().endStage();
          if (this.stage != endStage) {
            this.stageBasis = 0;
            setStage(endStage);
            this.decayFrom = this.releaseFrom = getValue();
          }
        }
      }
    }
  }

  @Override
  protected double computeValue(double deltaMs) {
    while (deltaMs > 0) {
      deltaMs = processStage(deltaMs);
    }
    return stageValue();
  }

  private double processStage(double deltaMs) {
    double stageMs = stageMs();
    if (stageMs < 0) {
      // Stages with no length (e.g. SUSTAIN or OFF) will consume
      // infinite time, holding forever until something triggers to change
      // the envelope state
      return 0;
    }
    double stageRemainingMs = (1-this.stageBasis) * stageMs;
    if ((stageMs <= 0) || (deltaMs >= stageRemainingMs)) {
      // We are done with this stage, consume appropriate amount of time
      setStage(nextStage());
      this.stageBasis = 0;
      deltaMs -= stageRemainingMs;
      return deltaMs;
    } else {
      // Move forward in this stage
      this.stageBasis += deltaMs / stageMs;
      return 0;
    }
  }

  private double stageMs() {
    switch (this.stage) {
    case DELAY: return this.delay.getValue();
    case ATTACK: return this.attack.getValue();
    case HOLD: return this.hold.getValue();
    case DECAY: return this.decay.getValue();
    case RELEASE: return this.release.getValue();
    case SUSTAIN:
      // End the sustain stage if in one-shot mode and not engaged!
      return (this.oneshot.isOn() && !this.engage.isOn()) ? 0 : -1;
    case OFF:
    default:
      return -1;
    }
  }

  private Stage nextStage() {
    switch (this.stage) {
    case DELAY:
      return Stage.ATTACK;
    case ATTACK:
      this.decayFrom = this.peak.getValue();
      return this.stageMode.getEnum().has(Stage.HOLD) ? Stage.HOLD : Stage.DECAY;
    case HOLD:
      this.decayFrom = this.peak.getValue();
      return Stage.DECAY;
    case DECAY:
      if (this.stageMode.getEnum().has(Stage.SUSTAIN)) {
        return Stage.SUSTAIN;
      }
      return Stage.OFF;
    case SUSTAIN:
      this.releaseFrom = sustainValue();
      return Stage.RELEASE;
    case RELEASE:
    default:
      return Stage.OFF;
    }
  }

  private double sustainValue() {
    return LXUtils.lerp(this.initial.getValue(), this.peak.getValue(), this.sustain.getValue());
  }

  private double stageValue() {
    switch (this.stage) {
    case DELAY:
      return this.attackFrom;
    case ATTACK:
      return LXUtils.lerp(this.attackFrom, this.peak.getValue(), Math.pow(this.stageBasis, this.shape.getValue()));
    case HOLD:
      return this.peak.getValue();
    case DECAY:
      return LXUtils.lerp(this.stageMode.getEnum().has(Stage.SUSTAIN) ? sustainValue() : this.initial.getValue(), this.decayFrom, Math.pow(1 - this.stageBasis, this.shape.getValue()));
    case SUSTAIN:
      return sustainValue();
    case RELEASE:
      return LXUtils.lerp(this.initial.getValue(), this.releaseFrom, Math.pow(1 - this.stageBasis, this.shape.getValue()));
    case OFF:
    default:
      return this.initial.getValue();
    }
  }

  /**
   * Current stage of the envelope.
   */
  public Stage getStage() {
    return this.stage;
  }

  @Override
  public LXNormalizedParameter setNormalized(double value) {
    throw new UnsupportedOperationException("Cannot setNormalized on AHDSREnvelope");
  }


  @Override
  public double getNormalized() {
    return getValue();
  }

}
