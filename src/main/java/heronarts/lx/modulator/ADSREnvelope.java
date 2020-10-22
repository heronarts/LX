/**
 * Copyright 2017- Mark C. Slee, Heron Arts LLC
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
import heronarts.lx.parameter.FixedParameter;
import heronarts.lx.parameter.LXNormalizedParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.utils.LXUtils;

public class ADSREnvelope extends LXModulator implements LXNormalizedParameter {

  private final LXParameter startValue;
  private final LXParameter endValue;

  private final LXParameter attack;
  private final LXParameter decay;
  private final LXParameter sustain;
  private final LXParameter release;

  private final LXParameter shape;

  private double normalized = 0;

  private enum Stage {
    ATTACK,
    DECAY,
    SUSTAIN,
    RELEASE
  };

  public final BooleanParameter engage =
    new BooleanParameter("Engage")
    .setMode(BooleanParameter.Mode.MOMENTARY)
    .setDescription("Engages the envelope");

  private Stage stage = Stage.ATTACK;

  public ADSREnvelope(String label, LXParameter startValue, LXParameter endValue, LXParameter attack, LXParameter decay, LXParameter sustain, LXParameter release, LXParameter shape) {
    super(label);
    this.startValue = startValue;
    this.endValue = endValue;
    this.attack = attack;
    this.decay = decay;
    this.sustain = sustain;
    this.release = release;
    this.shape = shape;
    addParameter("engage", this.engage);
  }

  public ADSREnvelope(String label, double startValue, double endValue, LXParameter attack, LXParameter decay, LXParameter sustain, LXParameter release) {
    this(label, new FixedParameter(startValue), new FixedParameter(endValue), attack, decay, sustain, release, new FixedParameter(1));
  }

  public ADSREnvelope(String label, double startValue, double endValue, LXParameter attack, LXParameter decay, LXParameter sustain, LXParameter release, LXParameter shape) {
    this(label, new FixedParameter(startValue), new FixedParameter(endValue), attack, decay, sustain, release, shape);
  }

  public ADSREnvelope(String label, double startValue, LXParameter endValue, LXParameter attack, LXParameter decay, LXParameter sustain, LXParameter release) {
    this(label, new FixedParameter(startValue), endValue, attack, decay, sustain, release, new FixedParameter(1));
  }

  public ADSREnvelope(String label, double startValue, LXParameter endValue, LXParameter attack, LXParameter decay, LXParameter sustain, LXParameter release, LXParameter shape) {
    this(label, new FixedParameter(startValue), endValue, attack, decay, sustain, release, shape);
  }

  public void attack() {
    this.stage = Stage.ATTACK;
    this.engage.setValue(true);
    start();
  }

  public void release() {
    this.engage.setValue(false);
  }

  @Override
  public void onReset() {
    this.normalized = 0;
    this.stage = Stage.ATTACK;
  }

  @Override
  public void onParameterChanged(LXParameter p) {
    super.onParameterChanged(p);
    if (p == this.engage) {
      this.stage = this.engage.isOn() ? Stage.ATTACK : Stage.RELEASE;
      start();
    }
  }

  @Override
  public LXNormalizedParameter setNormalized(double value) {
    throw new UnsupportedOperationException("Cannot setNormalized on ADSREnvelope");
  }

  @Override
  public double getNormalized() {
    return this.normalized;
  }

  @Override
  public float getNormalizedf() {
    return (float) getNormalized();
  }

  @Override
  public double getExponent() {
    return 1;
  }

  @Override
  protected double computeValue(double deltaMs) {
    double norm = this.normalized;
    switch (this.stage) {
    case ATTACK:
      norm += deltaMs / this.attack.getValue();
      if (norm >= 1) {
        norm = 1;
        this.stage = Stage.DECAY;
      }
      break;
    case DECAY:
      double sustain = this.sustain.getValue();
      norm -= deltaMs * (1 - sustain) / this.decay.getValue();
      if (norm <= sustain) {
        norm = sustain;
        this.stage = Stage.SUSTAIN;
        this.running.setValue(false);
      }
      break;
    case SUSTAIN:
      norm = this.sustain.getValue();
      break;
    case RELEASE:
      norm -= deltaMs * this.sustain.getValue() / this.release.getValue();
      if (norm <= 0) {
        norm = 0;
        this.running.setValue(false);
      }
      break;
    }
    this.normalized = norm;
    return LXUtils.lerp(this.startValue.getValue(), this.endValue.getValue(), Math.pow(this.normalized, this.shape.getValue()));
  }

}
