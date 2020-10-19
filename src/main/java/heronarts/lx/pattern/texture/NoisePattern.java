/**
 * Copyright 2013- Mark C. Slee, Heron Arts LLC
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

package heronarts.lx.pattern.texture;

import heronarts.lx.LXCategory;
import heronarts.lx.color.LXColor;
import heronarts.lx.model.LXPoint;
import heronarts.lx.modulator.LXModulator;
import heronarts.lx.modulator.LinearEnvelope;
import heronarts.lx.modulator.SawLFO;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.BoundedParameter;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.parameter.FunctionalParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.LX;
import heronarts.lx.pattern.LXPattern;
import heronarts.lx.utils.LXUtils;

import static heronarts.lx.utils.Noise.*;
import static heronarts.lx.utils.LXUtils.clamp;

@LXCategory(LXCategory.TEXTURE)
public class NoisePattern extends LXPattern {

  public enum Algorithm {
    PERLIN("Perlin", false),
    RIDGE("Ridge", true),
    FBM("FBM", true),
    TURBULENCE("Turbulence", true),
    STATIC("Static", false);

    private final String name;
    private boolean isPerlinFeedback;

    private Algorithm(String name, boolean isPerlinFeedback) {
      this.name = name;
      this.isPerlinFeedback = isPerlinFeedback;
    }

    public boolean isPerlinFeedback() {
      return this.isPerlinFeedback;
    }

    @Override
    public String toString() {
      return this.name;
    }
  }

  private interface CoordinateFunction {
    float getCoordinate(LXPoint p, float normalized, float offset);
  }

  public static enum CoordinateMode {

    NORMAL("Normal", (p, normalized, offset) ->  {
      return normalized + offset;
    }),

    CENTER("Center", (p, normalized, offset) -> {
      return Math.abs(normalized - .5f * (1 + offset));
    }),

    RADIAL("Radial", (p, normalized, offset) -> {
      return p.rcn + offset * normalized;
    }),

    NONE("None", (p, normalized, offset) -> {
      return .5f + offset;
    });

    public final String name;
    public final CoordinateFunction function;

    private CoordinateMode(String name, CoordinateFunction function) {
      this.name = name;
      this.function = function;
    }

    @Override
    public String toString() {
      return this.name;
    }
  }

  public final EnumParameter<Algorithm> algorithm =
    new EnumParameter<Algorithm>("Algorithm", Algorithm.PERLIN);

  public final DiscreteParameter seed =
    new DiscreteParameter("Seed", 0, 256)
    .setDescription("Seed value supplied to the noise function");

  public final CompoundParameter level =
    new CompoundParameter("Level", 50, 0, 100)
    .setDescription("Midpoint brightness level for the noise generation");

  public final CompoundParameter contrast =
    new CompoundParameter("Contrast", 100, 0, 500)
    .setExponent(2)
    .setDescription("Dynamic contrast of noise generation");

  public final EnumParameter<CoordinateMode> xMode =
    new EnumParameter<CoordinateMode>("X Mode", CoordinateMode.NORMAL)
    .setDescription("Which coorindate mode the X-dimension uses");

  public final EnumParameter<CoordinateMode> yMode =
    new EnumParameter<CoordinateMode>("Y Mode", CoordinateMode.NORMAL)
    .setDescription("Which coorindate mode the Y-dimension uses");

  public final EnumParameter<CoordinateMode> zMode =
    new EnumParameter<CoordinateMode>("Z Mode", CoordinateMode.NORMAL)
    .setDescription("Which coorindate mode the Z-dimension uses");

  public final CompoundParameter xOffset =
    new CompoundParameter("X-Pos", 0, -1, 1)
    .setPolarity(LXParameter.Polarity.BIPOLAR)
    .setDescription("The coordinate offset on the X-axis");

  public final CompoundParameter yOffset =
    new CompoundParameter("Y-Pos", 0, -1, 1)
    .setPolarity(LXParameter.Polarity.BIPOLAR)
    .setDescription("The coordinate offset on the Y-axis");

  public final CompoundParameter zOffset =
    new CompoundParameter("Z-Pos", 0, -1, 1)
    .setPolarity(LXParameter.Polarity.BIPOLAR)
    .setDescription("The coordinate offset on the Z-axis");

  public final CompoundParameter scale =
    new CompoundParameter("Scale", .5f)
    .setDescription("Scale factor for the size of the noise variation");

  public final BoundedParameter minScale =
    new BoundedParameter("Min Scale", 1, 0.1, 100)
    .setExponent(2)
    .setDescription("Minimum scaling value for noise variation");

  public final BoundedParameter maxScale =
    new BoundedParameter("Max Scale", 10, 1, 1000)
    .setExponent(2)
    .setDescription("Maximum scaling value for noise variation");

  public final CompoundParameter xScale =
    new CompoundParameter("X-Scale", 1)
    .setDescription("Amount of scaling applied to the X-axis");

  public final CompoundParameter yScale =
    new CompoundParameter("Y-Scale", 1)
    .setDescription("Amount of scaling applied to the Y-axis");

  public final CompoundParameter zScale =
    new CompoundParameter("Z-Scale", 1)
    .setDescription("Amount of scaling applied to the Z-axis");

  public final BooleanParameter motion =
    new BooleanParameter("Motion", false)
    .setDescription("Whether motion is applied to the noise");

  public final CompoundParameter motionSpeed =
    new CompoundParameter("Speed", 0, -1, 1)
    .setPolarity(LXParameter.Polarity.BIPOLAR)
    .setDescription("Maximum motion speed");

  public final BoundedParameter motionSpeedRange =
    new BoundedParameter("Speed Range", 128, 1, 256)
    .setDescription("Range of the speed control");

  public final CompoundParameter xMotion =
    new CompoundParameter("X-Motion", 0, -1, 1)
    .setPolarity(LXParameter.Polarity.BIPOLAR)
    .setDescription("Rate of motion on the X-axis");

  public final CompoundParameter yMotion =
    new CompoundParameter("Y-Motion", 0, -1, 1)
    .setPolarity(LXParameter.Polarity.BIPOLAR)
    .setDescription("Rate of motion on the Y-axis");

  public final CompoundParameter zMotion =
    new CompoundParameter("Z-Motion", 1, -1, 1)
    .setPolarity(LXParameter.Polarity.BIPOLAR)
    .setDescription("Rate of motion on the Z-axis");

  private final LinearEnvelope motionDamped = addModulator(new LinearEnvelope(motion.isOn() ? 1 : 0, 1, 250));

  private class MotionSpeedParameter extends FunctionalParameter {
    private final LXParameter amount;

    private MotionSpeedParameter(LXParameter amount) {
      this.amount = amount;
    }

    @Override
    public double getValue() {
      double ms = motionSpeed.getValue();
      return 6000000 / (motionSpeedRange.getValue() * ms * Math.abs(ms) * motionDamped.getValue() * this.amount.getValue());
    }
  }

  private final LXModulator xModulation = startModulator(new SawLFO(0, 256, new MotionSpeedParameter(this.xMotion)));
  private final LXModulator yModulation = startModulator(new SawLFO(0, 256, new MotionSpeedParameter(this.yMotion)));
  private final LXModulator zModulation = startModulator(new SawLFO(0, 256, new MotionSpeedParameter(this.zMotion)));

  public final DiscreteParameter octaves =
    new DiscreteParameter("Octaves", 3, 1, 9)
    .setDescription("Number of octaves of perlin noise to sum");

  public final BoundedParameter lacunarity =
    new BoundedParameter("Lacunarity", 2, 0, 4)
    .setDescription("Spacing between successive octaves (use exactly 2.0 for wrapping output)");

  public final BoundedParameter gain =
    new BoundedParameter("Gain", 0.5, 0, 1)
    .setDescription("Relative weighting applied to each successive octave");

  public final BoundedParameter ridgeOffset =
    new BoundedParameter("Ridge", .9, 0, 2)
    .setDescription("Used to invert the feedback ridges");

  public NoisePattern(LX lx) {
    super(lx);

    addParameter("midpoint", this.level);
    addParameter("contrast", this.contrast);
    addParameter("xOffset", this.xOffset);
    addParameter("yOffset", this.yOffset);
    addParameter("zOffset", this.zOffset);
    addParameter("scale", this.scale);
    addParameter("minScale", this.minScale);
    addParameter("maxScale", this.maxScale);

    addParameter("xScale", this.xScale);
    addParameter("yScale", this.yScale);
    addParameter("zScale", this.zScale);

    addParameter("motion", this.motion);
    addParameter("motionSpeed", this.motionSpeed);
    addParameter("motionSpeedRange", this.motionSpeedRange);
    addParameter("xMotion", this.xMotion);
    addParameter("yMotion", this.yMotion);
    addParameter("zMotion", this.zMotion);

    addParameter("algorithm", this.algorithm);
    addParameter("seed", this.seed);
    addParameter("octaves", this.octaves);
    addParameter("lacunarity", this.lacunarity);
    addParameter("gain", this.gain);
    addParameter("ridgeOffset", this.ridgeOffset);

    addParameter("xMode", this.xMode);
    addParameter("yMode", this.yMode);
    addParameter("zMode", this.zMode);

    // Set the order of most useful control parameters
    setRemoteControls(
      this.scale,
      this.level,
      this.contrast,
      this.motionSpeed,
      this.xMotion,
      this.yMotion,
      this.zMotion,
      this.motionSpeedRange
    );
  }

  @Override
  public void onParameterChanged(LXParameter p) {
    super.onParameterChanged(p);
    if (this.motion == p) {
      if (this.motion.isOn()) {
        this.motionDamped.setRangeFromHereTo(1).trigger();
      } else {
        this.motionDamped.setRangeFromHereTo(0).trigger();
      }
    }
  }

  @Override
  public void run(double deltaMs) {
    switch (this.algorithm.getEnum()) {
    case PERLIN:
    case FBM:
    case RIDGE:
    case TURBULENCE:
      runPerlin(deltaMs, this.algorithm.getEnum());
      break;
    case STATIC:
      runStatic(deltaMs);
      break;
    }
  }

  private void runPerlin(double deltaMs, Algorithm algorithm) {
    int seed = this.seed.getValuei();

    float scale = LXUtils.lerpf(this.minScale.getValuef(), this.maxScale.getValuef(), this.scale.getValuef());

    float xa = this.xModulation.getValuef();
    float ya = this.yModulation.getValuef();
    float za = this.zModulation.getValuef();

    float xo = this.xOffset.getValuef();
    float yo = this.yOffset.getValuef();
    float zo = this.zOffset.getValuef();

    float xs = scale * this.xScale.getValuef();
    float ys = scale * this.yScale.getValuef();
    float zs = scale * this.zScale.getValuef();

    float contrast = this.contrast.getValuef();
    float level = this.level.getValuef() - contrast / 4;;

    CoordinateFunction xMode = this.xMode.getEnum().function;
    CoordinateFunction yMode = this.yMode.getEnum().function;
    CoordinateFunction zMode = this.zMode.getEnum().function;

    if (algorithm.equals(Algorithm.PERLIN)) {
      for (LXPoint p : model.points) {
        float xd = xMode.getCoordinate(p, p.xn, xo);
        float yd = yMode.getCoordinate(p, p.yn, yo);
        float zd = zMode.getCoordinate(p, p.zn, zo);

        float b = level + contrast * stb_perlin_noise3_seed(xa + xs * xd, ya + ys * yd, za + zs * zd, 0, 0, 0, seed);
        this.colors[p.index] = LXColor.gray(clamp(b, 0, 100));
      }
    } else {
      int octaves = this.octaves.getValuei();
      float lacunarity = this.lacunarity.getValuef();
      float gain = this.gain.getValuef();

      if (algorithm.equals(Algorithm.RIDGE)) {
        float ridgeOffset = this.ridgeOffset.getValuef();
        for (LXPoint p : model.points) {
          float xd = xMode.getCoordinate(p, p.xn, xo);
          float yd = yMode.getCoordinate(p, p.yn, yo);
          float zd = zMode.getCoordinate(p, p.zn, zo);
          float b = level + contrast * stb_perlin_ridge_noise3(xa + xs * xd, ya + ys * yd, za + zs * zd, lacunarity, gain, ridgeOffset, octaves);
          this.colors[p.index] = LXColor.gray(clamp(b, 0, 100));
        }
      } else if (algorithm.equals(Algorithm.FBM)) {
        for (LXPoint p : model.points) {
          float xd = xMode.getCoordinate(p, p.xn, xo);
          float yd = yMode.getCoordinate(p, p.yn, yo);
          float zd = zMode.getCoordinate(p, p.zn, zo);
          float b = level + contrast * stb_perlin_fbm_noise3(xa + xs * xd, ya + ys * yd, za + zs * zd, lacunarity, gain, octaves);
          this.colors[p.index] = LXColor.gray(clamp(b, 0, 100));
        }
      } else if (algorithm.equals(Algorithm.TURBULENCE)) {
        for (LXPoint p : model.points) {
          float xd = xMode.getCoordinate(p, p.xn, xo);
          float yd = yMode.getCoordinate(p, p.yn, yo);
          float zd = zMode.getCoordinate(p, p.zn, zo);
          float b = level + contrast * stb_perlin_turbulence_noise3(xa + xs * xd, ya + ys * yd, za + zs * zd, lacunarity, gain, octaves);
          this.colors[p.index] = LXColor.gray(clamp(b, 0, 100));
        }
      }
    }
  }

  private void runStatic(double deltaMs) {
    float level = this.level.getValuef();
    float contrast = this.contrast.getValuef();
    for (LXPoint p : model.points) {
      float b = level + contrast * (-1 + 2 * (float) Math.random());
      this.colors[p.index] = LXColor.gray(clamp(b, 0, 100));
    }
  }

}
