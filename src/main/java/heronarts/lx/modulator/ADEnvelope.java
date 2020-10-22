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

public class ADEnvelope extends LXModulator implements LXNormalizedParameter {

  private final LXParameter startValue;
  private final LXParameter endValue;

  private final LXParameter attack;
  private final LXParameter decay;

  private final LXParameter shape;

  private double normalized = 0;

  private enum Stage {
    ATTACK,
    DECAY
  };

  public final BooleanParameter engage =
    new BooleanParameter("Engage")
    .setMode(BooleanParameter.Mode.MOMENTARY)
    .setDescription("Engages the envelope");

  private Stage stage = Stage.ATTACK;

  public ADEnvelope(String label, LXParameter startValue, LXParameter endValue, LXParameter attack, LXParameter decay, LXParameter shape) {
    super(label);
    this.startValue = startValue;
    this.endValue = endValue;
    this.attack = attack;
    this.decay = decay;
    this.shape = shape;
    addParameter("engage", this.engage);
  }

  public ADEnvelope(String label, double startValue, double endValue, LXParameter attack, LXParameter decay) {
    this(label, new FixedParameter(startValue), new FixedParameter(endValue), attack, decay, new FixedParameter(1));
  }

  public ADEnvelope(String label, double startValue, double endValue, LXParameter attack, LXParameter decay, LXParameter shape) {
    this(label, new FixedParameter(startValue), new FixedParameter(endValue), attack, decay, shape);
  }

  public ADEnvelope(String label, double startValue, LXParameter endValue, LXParameter attack, LXParameter decay) {
    this(label, new FixedParameter(startValue), endValue, attack, decay, new FixedParameter(1));
  }

  public ADEnvelope(String label, double startValue, LXParameter endValue, LXParameter attack, LXParameter decay, LXParameter shape) {
    this(label, new FixedParameter(startValue), endValue, attack, decay, shape);
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
      this.stage = this.engage.isOn() ? Stage.ATTACK : Stage.DECAY;
      start();
    }
  }

  @Override
  public LXNormalizedParameter setNormalized(double value) {
    throw new UnsupportedOperationException("Cannot setNormalized on ADEnvelope");
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
    switch (stage) {
    case ATTACK:
      norm += deltaMs / this.attack.getValue();
      if (norm >= 1) {
        norm = 1;
        this.running.setValue(false);
      }
      break;
    case DECAY:
      norm -= deltaMs / this.decay.getValue();
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
