/**
 * Copyright 2020- Mark C. Slee, Heron Arts LLC
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
import heronarts.lx.model.LXModel;
import heronarts.lx.modulator.LXWaveshape;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.ObjectParameter;
import heronarts.lx.LX;
import heronarts.lx.pattern.LXPattern;
import heronarts.lx.utils.LXUtils;

@LXCategory(LXCategory.TEXTURE)
public class SparklePattern extends LXPattern {

  private final static int MAX_SPARKLES = 1024;
  private final static double MAX_DENSITY = 4;

  private class Sparkle {

    private boolean isOn = false;

    private double basis;

    private double randomVar;
    private double randomLevel;

    private int[] indexBuffer;

    private Sparkle() {
      this.indexBuffer = new int[pixelsPerSparkle];
      this.isOn = false;
      this.basis = Math.random();
      this.randomVar = Math.random();
      this.randomLevel = Math.random();
    }

    private void reindex() {
      for (int i = 0; i < this.indexBuffer.length; ++i) {
        this.indexBuffer[i] = LXUtils.constrain((int) (Math.random() * model.size), 0, model.size - 1);
      }
    }
  }

  public final ObjectParameter<LXWaveshape> waveshape = new ObjectParameter<LXWaveshape>("Wave", new LXWaveshape[] {
    LXWaveshape.TRI,
    LXWaveshape.SIN,
    LXWaveshape.UP,
    LXWaveshape.DOWN,
    LXWaveshape.SQUARE,
  });

  private final Sparkle[] sparkles = new Sparkle[MAX_SPARKLES];

  private double[] sparkleLevels;

  private int numSparkles;
  private int pixelsPerSparkle;

  public final CompoundParameter minInterval = (CompoundParameter)
    new CompoundParameter("Fast", 1, .1, 60)
    .setUnits(CompoundParameter.Units.SECONDS)
    .setDescription("Minimum interval between sparkles");

  public final CompoundParameter maxInterval = (CompoundParameter)
    new CompoundParameter("Slow", 1, .1, 60)
    .setUnits(CompoundParameter.Units.SECONDS)
    .setDescription("Maximum interval between sparkles");

  public final CompoundParameter speed =
    new CompoundParameter("Speed", 0.5)
    .setPolarity(CompoundParameter.Polarity.BIPOLAR)
    .setDescription("Speed of the sparkle effect");

  public final CompoundParameter variation = (CompoundParameter)
    new CompoundParameter("Variation", 25, 0, 100)
    .setUnits(CompoundParameter.Units.PERCENT)
    .setDescription("Variation of sparkle interval");

  public final CompoundParameter duration = (CompoundParameter)
    new CompoundParameter("Duration", 100, 0, 100)
    .setUnits(CompoundParameter.Units.PERCENT)
    .setDescription("Duration of a sparkle as percentage of interval");

  public final CompoundParameter density = (CompoundParameter)
    new CompoundParameter("Density", 50, 0, MAX_DENSITY * 100)
    .setExponent(2)
    .setUnits(CompoundParameter.Units.PERCENT)
    .setDescription("Density of sparkles");

  public final CompoundParameter sharp =
    new CompoundParameter("Sharp", 0, -1, 1)
    .setPolarity(CompoundParameter.Polarity.BIPOLAR)
    .setDescription("Sharpness of sparkle curve");

  public final CompoundParameter baseLevel = (CompoundParameter)
    new CompoundParameter("Base", 0, 0, 100)
    .setUnits(CompoundParameter.Units.PERCENT)
    .setDescription("Base Level");

  public final CompoundParameter minLevel = (CompoundParameter)
    new CompoundParameter("Min", 75, 0, 100)
    .setUnits(CompoundParameter.Units.PERCENT)
    .setDescription("Minimum brightness level, as a percentage of the maximum");

  public final CompoundParameter maxLevel = (CompoundParameter)
    new CompoundParameter("Max", 100, 0, 100)
    .setUnits(CompoundParameter.Units.PERCENT)
    .setDescription("Peak sparkle brightness level");

  public SparklePattern(LX lx) {
    super(lx);
    initModel();
    addParameter("density", this.density);
    addParameter("speed", this.speed);
    addParameter("variation", this.variation);
    addParameter("duration", this.duration);
    addParameter("sharp", this.sharp);
    addParameter("waveshape", this.waveshape);
    addParameter("minInterval", this.minInterval);
    addParameter("maxInterval", this.maxInterval);
    addParameter("baseLevel", this.baseLevel);
    addParameter("minLevel", this.minLevel);
    addParameter("maxLevel", this.maxLevel);
  }

  @Override
  protected void onModelChanged(LXModel model) {
    initModel();
  }

  private void initModel() {
    this.sparkleLevels = new double[model.size];
    this.numSparkles = LXUtils.min(model.size, MAX_SPARKLES);
    this.pixelsPerSparkle = (int) Math.ceil(MAX_DENSITY * model.size / this.numSparkles);

    for (int i = 0; i < this.numSparkles; ++i) {
      if (this.sparkles[i] == null) {
        this.sparkles[i] = new Sparkle();
      } else {
        this.sparkles[i].indexBuffer = new int[this.pixelsPerSparkle];
      }
      this.sparkles[i].reindex();
    }
  }

  @Override
  public void run(double deltaMs) {
    double minInterval = 1000 * this.minInterval.getValue();
    double maxInterval = 1000 * this.maxInterval.getValue();
    double speed = this.speed.getValue();
    double variation = .01 * this.variation.getValue();
    double durationInv = 100 / this.duration.getValue();
    double density = .01 * this.density.getValue();
    double baseLevel = this.baseLevel.getValue();

    LXWaveshape waveshape = this.waveshape.getObject();

    double maxLevel = this.maxLevel.getValue();
    double minLevel = maxLevel * .01 * this.minLevel.getValue();

    double maxDelta = maxLevel - baseLevel;
    double minDelta = minLevel - baseLevel;

    double shape = this.sharp.getValue();
    if (shape >= 0) {
      shape = LXUtils.lerp(1, 3, shape);
    } else {
      shape = 1 / LXUtils.lerp(1, 3, -shape);
    }

    // Initialize all points to base level
    for (int i = 0; i < this.sparkleLevels.length; ++i) {
      this.sparkleLevels[i] = baseLevel;
    }

    // Run all the sparkles
    for (int i = 0; i < this.numSparkles; ++i) {
      Sparkle sparkle = this.sparkles[i];
      double sparkleInterval = LXUtils.lerp(maxInterval, minInterval, LXUtils.constrain(speed + variation * (sparkle.randomVar - .5), 0, 1));
      sparkle.basis += deltaMs / sparkleInterval;
      if (sparkle.basis > 1) {
        sparkle.basis = 0;
        if (sparkle.isOn = (MAX_DENSITY * Math.random() <= density)) {
          sparkle.randomVar = Math.random();
          sparkle.randomLevel = Math.random();
          sparkle.reindex();
        }
      } else if (sparkle.isOn) {
        double sBasis = sparkle.basis * durationInv;
        if (sBasis < 1) {
          double g = waveshape.compute(sBasis);
          if (shape != 1) {
            g = Math.pow(g, shape);
          }

          double maxSparkleDelta = LXUtils.lerp(minDelta, maxDelta, sparkle.randomLevel);
          double sparkleAdd = maxSparkleDelta * g;
          for (int c : sparkle.indexBuffer) {
            this.sparkleLevels[c] += sparkleAdd;
          }
        }
      }
    }

    for (int i = 0; i < colors.length; ++i) {
      colors[i] = LXColor.gray(LXUtils.clamp(this.sparkleLevels[i], 0, 100));
    }
  }
}
