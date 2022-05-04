package heronarts.lx.modulator;

import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.FunctionalParameter;
import heronarts.lx.parameter.LXNormalizedParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.MutableParameter;
import heronarts.lx.utils.LXUtils;

/**
 * Modulator that provides randomization within normalized value range.
 */
@LXModulator.Global("Randomizer")
@LXModulator.Device("Randomizer")
public class Randomizer extends LXModulator implements LXNormalizedParameter, LXTriggerSource {

  public final CompoundParameter periodMs = (CompoundParameter)
    new CompoundParameter("Interval", 100, 10, 1000*60)
    .setExponent(3)
    .setUnits(CompoundParameter.Units.MILLISECONDS)
    .setDescription("Base interval for random target value updates");

  public final CompoundParameter randomMs = (CompoundParameter)
    new CompoundParameter("Random", 100, 0, 1000*60)
    .setExponent(3)
    .setUnits(CompoundParameter.Units.MILLISECONDS)
    .setDescription("Range of random time added to each interval");

  private double randomInterval = 0;

  public final BooleanParameter damping =
    new BooleanParameter("Damping", true)
    .setDescription("Apply damping to the movement of the random value");

  private final FunctionalParameter totalMs = new FunctionalParameter() {
    @Override
    public double getValue() {
      return periodMs.getValue() + randomInterval * randomMs.getValue();
    }
  };

  private final Click click = new Click(this.totalMs);

  public final CompoundParameter speed =
    new CompoundParameter("Speed", 5, .1, 10)
    .setExponent(2)
    .setDescription("Speed of value update");

  public final CompoundParameter accel =
    new CompoundParameter("Acceleration", 5, 1, 10)
    .setDescription("Acceleration on value change");

  public final CompoundParameter min =
    new CompoundParameter("Minimum", 0)
    .setDescription("Minimum output value");

  public final CompoundParameter max =
    new CompoundParameter("Maximum", 1)
    .setDescription("Maximum output value");

  public final BooleanParameter triggerOut =
    new BooleanParameter("Trigger Out")
    .setDescription("Engages when the randomizer triggers")
    .setMode(BooleanParameter.Mode.MOMENTARY);

  private final MutableParameter target = new MutableParameter(0.5);

  private final DampedParameter damper = new DampedParameter(this.target, this.speed, this.accel);

  public Randomizer() {
    this("Random");
  }

  private Randomizer(String label) {
    super(label);
    addParameter("periodMs", this.periodMs);
    addParameter("randomMs", this.randomMs);

    addParameter("damping", this.damping);
    addParameter("speed", this.speed);
    addParameter("accel", this.accel);

    addParameter("min", this.min);
    addParameter("max", this.max);

    addParameter("triggerOut", this.triggerOut);

    this.click.start();
    this.damper.start();

    setDescription("Random value updated with specified interval and range");
  }

  @Override
  public void onParameterChanged(LXParameter p) {
    super.onParameterChanged(p);
    if (p == this.triggerOut) {
      this.triggerOut.setValue(false);
    }
  }

  @Override
  protected double computeValue(double deltaMs) {
    this.click.loop(deltaMs);
    if (this.click.click()) {
      this.randomInterval = Math.random();
      this.target.setValue(LXUtils.lerp(this.min.getValue(), this.max.getValue(), Math.random()));
      this.triggerOut.setValue(true);
    }
    this.damper.loop(deltaMs);
    if (this.damping.isOn()) {
      return LXUtils.constrain(this.damper.getValue(), 0, 1);
    } else {
      return this.target.getValue();
    }
  }

  @Override
  public LXNormalizedParameter setNormalized(double value) {
    this.target.setValue(value);
    return this;
  }

  @Override
  public double getNormalized() {
    return getValue();
  }

  @Override
  public BooleanParameter getTriggerSource() {
    return this.triggerOut;
  }

}
