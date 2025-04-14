/**
 * Copyright 2025- Mark C. Slee, Heron Arts LLC
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
import heronarts.lx.LXComponentName;
import heronarts.lx.color.LXColor;
import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.utils.LXUtils;

@LXCategory(LXCategory.CORE)
@LXComponentName("Linear Mask")
public class LinearMaskEffect extends LXEffect {

  public interface PositionFunction {
    public float getPosition(LXPoint p);
  }

  public interface DistanceFunction {
    public float getDistance(float position, float reference);
  }

  public enum Axis {
    X("X-axis", p -> { return p.xn; }),
    Y("Y-axis", p -> { return p.yn; }),
    Z("Z-axis", p -> { return p.zn; });

    public final String label;
    public final PositionFunction position;

    private Axis(String label, PositionFunction position) {
      this.label = label;
      this.position = position;
    }

    @Override
    public String toString() {
      return this.label;
    }
  }

  public enum Mode {
    ABS("Abs", (position, reference) -> { return Math.abs(position - reference); }),
    POS("Pos", (position, reference) -> { return position - reference; }),
    NEG("Neg", (position, reference) -> { return reference - position; });

    public final String label;
    public final DistanceFunction distance;

    private Mode(String label, DistanceFunction distance) {
      this.label = label;
      this.distance = distance;
    }

    @Override
    public String toString() {
      return this.label;
    }
  }

  public enum FadePosition {
    OUTER("Outer"),
    INNER("Inner"),
    MIDDLE("Middle");

    public final String label;

    private FadePosition(String label) {
      this.label = label;
    }

    @Override
    public String toString() {
      return this.label;
    }
  }

  public enum FadeSize {
    ABSOLUTE("Abs"),
    RELATIVE("Rel");

    public final String label;

    private FadeSize(String label) {
      this.label = label;
    }

    @Override
    public String toString() {
      return this.label;
    }
  }

  public final EnumParameter<Axis> axis =
    new EnumParameter<Axis>("Axis", Axis.Y)
    .setDescription("Which axis the mask operates on");

  public final EnumParameter<Mode> mode =
    new EnumParameter<Mode>("Mode", Mode.ABS)
    .setDescription("How the mask is directionally applied");

  public final EnumParameter<FadePosition> fadePosition =
    new EnumParameter<FadePosition>("Fade Position", FadePosition.OUTER)
    .setDescription("Where the fade transition is rendered");

  public final EnumParameter<FadeSize> fadeSize =
    new EnumParameter<FadeSize>("Fade Size", FadeSize.ABSOLUTE)
    .setDescription("Where the fade size is absolute or relative to the mask size");

  public final CompoundParameter offset =
    new CompoundParameter("Offset", 0)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Offset position of the mask");

  public final CompoundParameter size =
    new CompoundParameter("Size", .5)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Size of the mask");

  public final CompoundParameter fade =
    new CompoundParameter("Fade", .25)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Fade size of the mask");

  public final BooleanParameter invert =
    new BooleanParameter("Invert", false)
    .setDescription("Invert the mask");

  public final BooleanParameter cue =
    new BooleanParameter("CUE", false)
    .setMode(BooleanParameter.Mode.MOMENTARY)
    .setDescription("Cue the mask effect");

  public LinearMaskEffect(LX lx) {
    super(lx);
    addParameter("offset", this.offset);
    addParameter("size", this.size);
    addParameter("fade", this.fade);
    addParameter("invert", this.invert);
    addParameter("axis", this.axis);
    addParameter("mode", this.mode);
    addParameter("fadePosition", this.fadePosition);
    addParameter("fadeSize", this.fadeSize);
    addParameter("cue", this.cue);
  }

  @Override
  protected void run(double deltaMs, double enabledAmount) {
    final int effectMask = LXColor.blendMask(enabledAmount);

    final PositionFunction axisFn = this.axis.getEnum().position;
    final DistanceFunction distanceFn = this.mode.getEnum().distance;
    final FadePosition fadeMode = this.fadePosition.getEnum();
    final FadeSize fadeSize = this.fadeSize.getEnum();

    final float offset = this.offset.getValuef();
    final float size = this.size.getValuef();
    final float fade = this.fade.getValuef() * ((fadeSize == FadeSize.RELATIVE) ? size : 1);
    final float falloff = 255 / fade;
    final boolean invert = this.invert.isOn();
    final boolean cue = this.cue.isOn();

    float base = 0;
    switch (fadeMode) {
      case OUTER -> {
        base = 255;
      }
      case INNER -> {
        base = 0;
      }
      case MIDDLE -> {
        base = 128;
      }
    }

    for (LXPoint p : model.points) {
      final float distance = distanceFn.getDistance(axisFn.getPosition(p), offset);
      final int mask = (int) LXUtils.constrainf(base - falloff * (distance - size), 0, 255);
      final int alpha = invert ? mask : (255 - mask);
      colors[p.index] = cue ? LXColor.grayn((255 - alpha) / 255f) :
        LXColor.multiply(colors[p.index], alpha << LXColor.ALPHA_SHIFT, effectMask);
    }

  }

}
