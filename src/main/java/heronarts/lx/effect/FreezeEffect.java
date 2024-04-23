/**
 * Copyright 2024- Mark C. Slee, Heron Arts LLC
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

package heronarts.lx.effect;

import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.ModelBuffer;
import heronarts.lx.blend.LXBlend;
import heronarts.lx.color.LXColor;
import heronarts.lx.model.LXPoint;
import heronarts.lx.modulator.Interval;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.TriggerParameter;
import heronarts.lx.utils.LXUtils;

@LXCategory(LXCategory.CORE)
public class FreezeEffect extends LXEffect {

  public enum Mode {
    REPLACE("Replace", LXColor::lerp),
    MULTIPLY("Multiply", LXColor::multiply),
    ADD("Add", LXColor::add),
    SPOTLIGHT("Spotlight", LXColor::spotlight),
    HIGHLIGHT("Highlight", LXColor::highlight),
    SUBTRACT("Subtract", LXColor::subtract),
    DIFFERENCE("Difference", LXColor::difference);

    public final String label;
    public final LXBlend.FunctionalBlend.BlendFunction function;

    private Mode(String label, LXBlend.FunctionalBlend.BlendFunction function) {
      this.label = label;
      this.function = function;
    }

    @Override
    public String toString() {
      return this.label;
    }
  }

  private final ModelBuffer buffer = new ModelBuffer(lx);

  public final Interval interval = new Interval();

  public final BooleanParameter lock =
    new BooleanParameter("Lock", false)
    .setDescription("Locks the freeze effect active");

  public final BooleanParameter hold =
    new BooleanParameter("Hold", false)
    .setMode(BooleanParameter.Mode.MOMENTARY)
    .setDescription("Freezes the frame only while held");

  public final TriggerParameter resample =
    new TriggerParameter("Resample", this::resample)
    .setDescription("Samples a new underlying frame");

  public final CompoundParameter mix =
    new CompoundParameter("Mix", 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Level of the frozen frame");

  public final CompoundParameter attackMs =
    new CompoundParameter("Attack", 0, 0, 1000)
    .setUnits(CompoundParameter.Units.MILLISECONDS_RAW)
    .setDescription("Time to blend into the frozen frame");

  public final CompoundParameter releaseMs =
    new CompoundParameter("Release", 50, 0, 10000)
    .setExponent(2)
    .setUnits(CompoundParameter.Units.MILLISECONDS_RAW)
    .setDescription("Time to blend out from the frozen frame");

  public final EnumParameter<Mode> mode =
    new EnumParameter<Mode>("Mode", Mode.REPLACE)
    .setDescription("How to blend the frozen frame");

  public FreezeEffect(LX lx) {
    super(lx);
    addParameter("lock", this.lock);
    addParameter("hold", this.hold);
    addParameter("resample", this.resample);
    addParameter("mix", this.mix);
    addParameter("attackMs", this.attackMs);
    addParameter("releaseMs", this.releaseMs);
    addParameter("mode", this.mode);
    addModulator("interval", this.interval);

    this.interval.triggerOut.addListener(this);
  }

  private boolean capture = false;
  private boolean engage = false;
  private double basis = 0;

  @Override
  public void onParameterChanged(LXParameter p) {
    super.onParameterChanged(p);
    if (p == this.lock) {
      if (!this.hold.isOn() && this.lock.isOn()) {
        this.capture = true;
        this.engage = true;
      }
    } else if (p == this.hold) {
      if (!this.lock.isOn() && this.hold.isOn()) {
        this.capture = true;
        this.engage = true;
      }
    } else if (p == this.interval.triggerOut) {
      if (this.interval.triggerOut.isOn()) {
        this.capture = true;
      }
    }
  }

  private void resample() {
    this.capture = true;
  }

  private int replaceInMask = LXColor.BLACK;

  @Override
  protected void run(double deltaMs, double enabledAmount) {
    final int[] array = this.buffer.getArray();
    if (this.capture) {
      for (LXPoint p : model.points) {
        array[p.index] = colors[p.index];
      }
      this.capture = false;

      // Ensure release phase occurs for insta-triggers
      if (this.engage && (this.attackMs.getValue() == 0)) {
        this.basis = 1;
      }
      this.engage = false;
    }

    final Mode mode = this.mode.getEnum();
    LXBlend.FunctionalBlend.BlendFunction blend = mode.function;

    if (this.lock.isOn() || this.hold.isOn()) {
      this.basis = LXUtils.min(1, this.basis + deltaMs / this.attackMs.getValue());
      if (mode == Mode.REPLACE) {
        blend = this::replaceIn;
        replaceInMask = LXColor.grayn(this.mix.getValue());
      }
    } else {
      this.basis = LXUtils.max(0, this.basis - deltaMs / this.releaseMs.getValue());
    }

    if (this.basis > 0) {
      final double mix = this.basis * this.mix.getValue() * enabledAmount;
      final int blendMask = LXColor.blendMask(mix);
      if (blendMask > 0) {
        for (LXPoint p : model.points) {
          colors[p.index] = blend.apply(colors[p.index], array[p.index], blendMask);
        }
      }
    }
  }

  private int replaceIn(int dst, int src, int alpha) {
    return LXColor.lightest(LXColor.multiply(src, this.replaceInMask, LXColor.BLEND_ALPHA_FULL), dst, LXColor.BLEND_ALPHA_FULL - alpha);
  }

  @Override
  public void dispose() {
    this.interval.triggerOut.removeListener(this);
    super.dispose();
  }

}
