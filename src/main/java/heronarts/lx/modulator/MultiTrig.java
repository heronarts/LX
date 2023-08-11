package heronarts.lx.modulator;

import heronarts.lx.LXCategory;
import heronarts.lx.osc.LXOscComponent;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.parameter.TriggerParameter;
import heronarts.lx.utils.LXUtils;

@LXModulator.Global("Multi Trig")
@LXModulator.Device("Multi Trig")
@LXCategory(LXCategory.TRIGGER)
public class MultiTrig extends LXModulator implements LXOscComponent, LXTriggerSource {

  public enum Mode {
    ALL("All"),
    RANDOM("Random"),
    CYCLE("Cycle"),
    REVERSE("Reverse"),
    FLIP("Flip");

    public final String label;

    private Mode(String label) {
      this.label = label;
    }

    @Override
    public String toString() {
      return this.label;
    }
  }

  public static final int MAX_OUTPUTS = 5;

  public final TriggerParameter triggerIn =
    new TriggerParameter("Trig", this::onTrig)
    .setDescription("Trigger input");

  public final CompoundParameter inputChance =
    new CompoundParameter("Chance", 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Chance that device responds to input");

  public final TriggerParameter inputIndicator =
    new TriggerParameter("Input")
    .setDescription("Indicates when the input has fired");

  public final DiscreteParameter numOutputs =
    new DiscreteParameter("Num Outs", MAX_OUTPUTS, 0, MAX_OUTPUTS + 1)
    .setDescription("Number of active outputs");

  public final EnumParameter<Mode> mode =
    new EnumParameter<Mode>("Mode", Mode.ALL)
    .setDescription("Operation mode");

  public final TriggerParameter out1 =
    new TriggerParameter("Out 1")
    .setDescription("Trigger Output 1");

  public final TriggerParameter out2 =
    new TriggerParameter("Out 2")
    .setDescription("Trigger Output 2");

  public final TriggerParameter out3 =
    new TriggerParameter("Out 3")
    .setDescription("Trigger Output 3");

  public final TriggerParameter out4 =
    new TriggerParameter("Out 4")
    .setDescription("Trigger Output 4");

  public final TriggerParameter out5 =
    new TriggerParameter("Out 5")
    .setDescription("Trigger Output 5");

  public final CompoundParameter chance1 =
    new CompoundParameter("Chance 1", 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Chance of output on output 1");

  public final CompoundParameter chance2 =
    new CompoundParameter("Chance 1", 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Chance of output on output 2");

  public final CompoundParameter chance3 =
    new CompoundParameter("Chance 1", 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Chance of output on output 3");

  public final CompoundParameter chance4 =
    new CompoundParameter("Chance 1", 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Chance of output on output 4");

  public final CompoundParameter chance5 =
    new CompoundParameter("Chance 1", 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Chance of output on output 5");

  private final TriggerParameter[] out = {
    out1, out2, out3, out4, out5
  };

  private final CompoundParameter[] chance = {
    chance1, chance2, chance3, chance4, chance5,
  };

  public MultiTrig() {
    this("Multi Trig");
    addParameter("triggerIn", this.triggerIn);
    addParameter("inputChance", this.inputChance);
    addParameter("numOutputs", this.numOutputs);
    addParameter("mode", this.mode);
    addParameter("out1", this.out1);
    addParameter("out2", this.out2);
    addParameter("out3", this.out3);
    addParameter("out4", this.out4);
    addParameter("out5", this.out5);
    addParameter("chance1", this.chance1);
    addParameter("chance2", this.chance2);
    addParameter("chance3", this.chance3);
    addParameter("chance4", this.chance4);
    addParameter("chance5", this.chance5);
    setMappingSource(false);
  }

  public MultiTrig(String label) {
    super(label);
  }

  private int index = 0;
  private int increment = 1;

  private void onTrig() {
    if (!this.running.isOn() || (this.inputChance.getValue() <= Math.random())) {
      return;
    }
    this.inputIndicator.trigger();

    final int numOutputs = this.numOutputs.getValuei();
    if (numOutputs == 0) {
      return;
    }

    switch (this.mode.getEnum()) {
    case ALL:
      for (int i = 0; i < numOutputs; ++i) {
        triggerOut(i);
      }
      break;
    case RANDOM:
      triggerOut(LXUtils.randomi(0, numOutputs-1));
      break;
    case CYCLE:
      this.index = (this.index + 1) % numOutputs;
      triggerOut(this.index);
      break;
    case REVERSE:
      this.index = (this.index + numOutputs - 1) % numOutputs;
      triggerOut(this.index);
      break;
    case FLIP:
      if (this.index <= 0) {
        this.increment = 1;
      } else if (this.index >= numOutputs - 1) {
        this.increment = -1;
      }
      this.index += this.increment;
      triggerOut(this.index);
      break;
    }
  }

  private void triggerOut(int index) {
    if (this.chance[index].getValue() > Math.random()) {
      this.out[index].trigger();
    }
  }

  @Override
  protected double computeValue(double deltaMs) {
    return 0;
  }

  @Override
  public BooleanParameter getTriggerSource() {
    return null;
  }

}
