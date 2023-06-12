/**
 * Copyright 2023- Mark C. Slee, Heron Arts LLC
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

import heronarts.lx.LXCategory;
import heronarts.lx.osc.LXOscComponent;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.BoundedParameter;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.parameter.LXNormalizedParameter;
import heronarts.lx.pattern.texture.NoisePattern.Algorithm;
import heronarts.lx.utils.LXUtils;
import heronarts.lx.utils.Noise;

/**
 * Modulator that provides random noise output
 */
@LXModulator.Global("Noise")
@LXModulator.Device("Noise")
@LXCategory(LXCategory.CORE)
public class NoiseModulator extends LXModulator implements LXNormalizedParameter, LXOscComponent {

  public final CompoundParameter speed =
    new CompoundParameter("Speed", .25, -1, 1)
    .setPolarity(CompoundParameter.Polarity.BIPOLAR)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Speed of noise animation");

  public final BoundedParameter speedRange =
    new BoundedParameter("Range", 1, 0, 5)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Range of speed knob");

  public final CompoundParameter level =
    new CompoundParameter("Level", .5)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Midpoint brightness level for the noise generation");

  public final CompoundParameter contrast =
    new CompoundParameter("Contrast", 1, 0, 5)
    .setExponent(2)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Dynamic contrast of noise generation");

  public final CompoundParameter minLevel =
    new CompoundParameter("Min", 0)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Minimum output level");

  public final CompoundParameter maxLevel =
    new CompoundParameter("Max", 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Maximum output level");

  public final EnumParameter<Algorithm> algorithm =
    new EnumParameter<Algorithm>("Algorithm", Algorithm.PERLIN);

  public final DiscreteParameter seed =
    new DiscreteParameter("Seed", 0, 256)
    .setDescription("Seed value supplied to the noise function");

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

  public final BooleanParameter showPreview =
    new BooleanParameter("Preview", false)
    .setDescription("Whether the wave preview is visible in the modulator UI");

  private double basis = 0;

  public NoiseModulator() {
    this("Noise");
  }

  private NoiseModulator(String label) {
    super(label);
    addParameter("speed", this.speed);
    addParameter("speedRange", this.speedRange);
    addParameter("level", this.level);
    addParameter("contrast", this.contrast);
    addParameter("minLevel", this.minLevel);
    addParameter("maxLevel", this.maxLevel);

    addParameter("algorithm", this.algorithm);
    addParameter("seed", this.seed);
    addParameter("octaves", this.octaves);
    addParameter("lacunarity", this.lacunarity);
    addParameter("gain", this.gain);
    addParameter("ridgeOffset", this.ridgeOffset);

    addParameter("showPreview", this.showPreview);

    setDescription("Noise generator that produces normalized output");
  }

  private double getNoise(float basis) {

    final int octaves = this.octaves.getValuei();
    final float lacunarity = this.lacunarity.getValuef();
    final float gain = this.gain.getValuef();
    final float ridgeOffset = this.ridgeOffset.getValuef();

    switch (this.algorithm.getEnum()) {
    case RIDGE:
      return Noise.stb_perlin_ridge_noise3(basis, 0, 0, lacunarity, gain, ridgeOffset, octaves);
    case FBM:
      return Noise.stb_perlin_fbm_noise3(basis, 0, 0, lacunarity, gain, octaves);
    case TURBULENCE:
      return Noise.stb_perlin_turbulence_noise3(basis, 0, 0, lacunarity, gain, octaves);
    case STATIC:
      return -1f + 2*Math.random();
    default:
    case PERLIN:
      return Noise.stb_perlin_noise3_seed(basis, 0, 0, 0, 0, 0, this.seed.getValuei());
    }
  }

  private double _computeValue(float basis) {
    return LXUtils.clamp(
      this.level.getValue() + this.contrast.getValue() * getNoise(basis),
      this.minLevel.getValue(),
      this.maxLevel.getValue()
    );
  }

  @Override
  protected double computeValue(double deltaMs) {
    this.basis = (256 + (this.basis + deltaMs * .001 * this.speedRange.getValue() * this.speed.getValue()) % 256.) % 256.;
    return _computeValue((float) this.basis);
  }

  public double lookahead(double deltaMs) {
    double basis = (256 + (this.basis + deltaMs * .001 * this.speedRange.getValue() * this.speed.getValue()) % 256.) % 256.;
    return _computeValue((float) basis);
  }

  @Override
  public LXNormalizedParameter setNormalized(double value) {
    throw new UnsupportedOperationException("NoiseModulator does not support setNormalized()");
  }

  @Override
  public double getNormalized() {
    return getValue();
  }

}
