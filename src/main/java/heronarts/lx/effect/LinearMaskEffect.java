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
import heronarts.lx.LXComponent;
import heronarts.lx.color.LXColor;
import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.transform.LXMatrix;
import heronarts.lx.transform.LXParameterizedMatrix;
import heronarts.lx.utils.LXUtils;

@LXCategory(LXCategory.CORE)
@LXComponent.Name("Linear Mask")
@LXComponent.Description("Masks content by a brightness gradient with linear falloff")
public class LinearMaskEffect extends LXEffect {

  public interface PositionFunction {
    public float getPosition(LXPoint p, LXMatrix t);
  }

  public interface DistanceFunction {
    public float getDistance(float position, float reference);
  }

  public enum Axis {
    X("X-axis",
      (p, t) -> { return p.xn; },
      (p, t) -> { return t.xn(p); }
    ),

    Y("Y-axis",
      (p, t) -> { return p.yn; },
      (p, t) -> { return t.yn(p); }
    ),

    Z("Z-axis",
      (p, t) -> { return p.zn; },
      (p, t) -> { return t.zn(p); }
    );

    public final String label;
    public final PositionFunction basicPosition;
    public final PositionFunction rotatePosition;

    private Axis(String label, PositionFunction basicPosition, PositionFunction rotatePosition) {
      this.label = label;
      this.basicPosition = basicPosition;
      this.rotatePosition = rotatePosition;
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

  public final BooleanParameter rotate =
    new BooleanParameter("Rotate", false)
    .setDescription("Whether to rotate the geometry");

  public final CompoundParameter yaw =
    new CompoundParameter("Yaw", 0, 360)
    .setWrappable(true)
    .setUnits(CompoundParameter.Units.DEGREES)
    .setDescription("Yaw rotation");

  public final CompoundParameter pitch =
    new CompoundParameter("Pitch", 0, 360)
    .setWrappable(true)
    .setUnits(CompoundParameter.Units.DEGREES)
    .setDescription("Pitch rotation");

  public final CompoundParameter roll =
    new CompoundParameter("Roll", 0, 360)
    .setWrappable(true)
    .setUnits(CompoundParameter.Units.DEGREES)
    .setDescription("Roll rotation");

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
    addTransformParameter("rotate", this.rotate);
    addTransformParameter("yaw", this.yaw);
    addTransformParameter("pitch", this.pitch);
    addTransformParameter("roll", this.roll);
  }

  private void addTransformParameter(String key, LXParameter parameter) {
    addParameter(key, parameter);
    this.transform.addParameter(parameter);
  }

  private final LXParameterizedMatrix transform = new LXParameterizedMatrix();

  @Override
  protected void run(double deltaMs, double enabledAmount) {
    final int effectMask = LXColor.blendMask(enabledAmount);

    final boolean rotate = this.rotate.isOn();
    if (rotate) {
      this.transform.update(matrix -> {
        matrix
          .translate(.5f, .5f, .5f)
          .rotateZ((float) Math.toRadians(-this.roll.getValue()))
          .rotateX((float) Math.toRadians(-this.pitch.getValue()))
          .rotateY((float) Math.toRadians(-this.yaw.getValue()))
          .translate(-.5f, -.5f, -.5f);
      });
    }

    final PositionFunction axisFn = rotate ? this.axis.getEnum().rotatePosition : this.axis.getEnum().basicPosition;
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
      final float distance = distanceFn.getDistance(axisFn.getPosition(p, this.transform), offset);
      final int mask = (int) LXUtils.constrainf(base - falloff * (distance - size), 0, 255);
      final int alpha = invert ? mask : (255 - mask);
      colors[p.index] = cue ? LXColor.grayn((255 - alpha) / 255f) :
        LXColor.multiply(colors[p.index], alpha << LXColor.ALPHA_SHIFT, effectMask);
    }

  }

}
