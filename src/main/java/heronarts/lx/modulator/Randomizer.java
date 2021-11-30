package heronarts.lx.modulator;

import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.LXNormalizedParameter;
import heronarts.lx.parameter.MutableParameter;

/**
 * Modulator that provides randomization within normalized value range.
 */
@LXModulator.Global("Randomizer")
@LXModulator.Device("Randomizer")
public class Randomizer extends LXModulator implements LXNormalizedParameter {

  public final CompoundParameter periodMs = (CompoundParameter)
    new CompoundParameter("Interval", 100, 10, 10000)
    .setExponent(3)
    .setUnits(CompoundParameter.Units.MILLISECONDS)
    .setDescription("Interval for random target value updates");

  public final CompoundParameter speed =
    new CompoundParameter("Speed", 5, .1, 10)
    .setExponent(2)
    .setDescription("Speed of value update");

  public final CompoundParameter damping =
    new CompoundParameter("Damping", 5, 10, 1)
    .setDescription("Damping on value change");

  private final Click click = new Click(this.periodMs);

  private final MutableParameter target = new MutableParameter(0.5);

  private final DampedParameter damper = new DampedParameter(this.target, this.speed, this.damping);

  public Randomizer() {
    this("Random");
    this.click.start();
    this.damper.start();
  }

  protected Randomizer(String label) {
    super(label);
    addParameter("periodMs", this.periodMs);
    addParameter("speed", this.speed);
    addParameter("damping", this.damping);
  }

  @Override
  protected double computeValue(double deltaMs) {
    this.click.loop(deltaMs);
    if (this.click.click()) {
      this.target.setValue(Math.random());
    }
    this.damper.loop(deltaMs);
    return this.damper.getValue();
  }

  @Override
  public LXNormalizedParameter setNormalized(double value) {
    this.target.setValue(value);
    return this;
  }

  @Override
  public double getNormalized() {
    return this.damper.getValue();
  }

}
