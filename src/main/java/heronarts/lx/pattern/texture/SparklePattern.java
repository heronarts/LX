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
import heronarts.lx.model.LXPoint;
import heronarts.lx.modulator.LXWaveshape;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.ObjectParameter;
import heronarts.lx.LX;
import heronarts.lx.pattern.LXPattern;
import heronarts.lx.utils.LXUtils;

@LXCategory(LXCategory.TEXTURE)
public class SparklePattern extends LXPattern {

  public static class Engine {

    private final static int MAX_SPARKLES = 1024;
    private final static double MAX_DENSITY = 4;

    private class Sparkle {

      private boolean isOn = false;

      // Moves through 0-1 for sparkle lifecycle
      private double basis;

      // Random 0-1 constant used to control how much time-scale variation is applied
      private double randomVar;

      // Random 0-1 constant used to control brightness of sparkle
      private double randomLevel;

      // How many pixels to output to
      private int activePixels;

      // Each individual sparkle maintains a list of output indices that it is applied to
      private final int[] indexBuffer;

      private Sparkle(LXModel model) {
        this.isOn = false;
        this.basis = Math.random();
        this.randomVar = Math.random();
        this.randomLevel = Math.random();
        this.indexBuffer = new int[maxPixelsPerSparkle];
        reindex(model);
      }

      private void reindex(LXModel model) {
        // Choose a set of LED indices at random for this sparkle to point to
        for (int i = 0; i < this.indexBuffer.length; ++i) {
          this.indexBuffer[i] = LXUtils.randomi(model.size-1);
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

    /**
     * Array of raw value output levels, matching the size of the model
     */
    public double[] outputLevels;

    private int numSparkles;
    private int maxPixelsPerSparkle;

    public final CompoundParameter minInterval =
      new CompoundParameter("Fast", 1, .1, 60)
      .setExponent(2)
      .setUnits(CompoundParameter.Units.SECONDS)
      .setDescription("Minimum interval between sparkles");

    public final CompoundParameter maxInterval =
      new CompoundParameter("Slow", 1, .1, 60)
      .setExponent(2)
      .setUnits(CompoundParameter.Units.SECONDS)
      .setDescription("Maximum interval between sparkles");

    public final CompoundParameter speed =
      new CompoundParameter("Speed", 0.5)
      .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
      .setPolarity(CompoundParameter.Polarity.BIPOLAR)
      .setDescription("Speed of the sparkle effect");

    public final CompoundParameter variation =
      new CompoundParameter("Variation", 25, 0, 100)
      .setUnits(CompoundParameter.Units.PERCENT)
      .setDescription("Variation of sparkle interval");

    public final CompoundParameter duration =
      new CompoundParameter("Duration", 100, 0, 100)
      .setUnits(CompoundParameter.Units.PERCENT)
      .setDescription("Duration of a sparkle as percentage of interval");

    public final CompoundParameter density =
      new CompoundParameter("Density", 50, 0, MAX_DENSITY * 100)
      .setExponent(2)
      .setUnits(CompoundParameter.Units.PERCENT)
      .setDescription("Density of sparkles");

    public final CompoundParameter sharp =
      new CompoundParameter("Sharp", 0, -1, 1)
      .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
      .setPolarity(CompoundParameter.Polarity.BIPOLAR)
      .setDescription("Sharpness of sparkle curve");

    public final CompoundParameter baseLevel =
      new CompoundParameter("Base", 0, 0, 100)
      .setUnits(CompoundParameter.Units.PERCENT)
      .setDescription("Base Level");

    public final CompoundParameter minLevel =
      new CompoundParameter("Min", 75, 0, 100)
      .setUnits(CompoundParameter.Units.PERCENT)
      .setDescription("Minimum brightness level, as a percentage of the maximum");

    public final CompoundParameter maxLevel =
      new CompoundParameter("Max", 100, 0, 100)
      .setUnits(CompoundParameter.Units.PERCENT)
      .setDescription("Peak sparkle brightness level");

    private int currentSize = -1;

    public Engine(LXModel model) {
      setModel(model);
    }

    public void setModel(LXModel model) {
      if (model.size == this.currentSize) {
        return;
      }
      this.currentSize = model.size;

      // An output level for every pixel in the model
      this.outputLevels = new double[model.size];

      // Set a cap on the maximum number of sparkle generators
      this.numSparkles = LXUtils.min(model.size, MAX_SPARKLES);

      // There can be up to MAX_DENSITY times the size of the model sparkle destinations,
      // so each generator will address up to that many pixels
      this.maxPixelsPerSparkle = (int) Math.ceil(MAX_DENSITY * model.size / this.numSparkles);

      // Reallocate and reindex sparkles against this model
      for (int i = 0; i < this.numSparkles; ++i) {
        this.sparkles[i] = new Sparkle(model);
      }
    }

    public void run(double deltaMs, LXModel model, double amount) {
      final double minIntervalMs = 1000 * this.minInterval.getValue();
      final double maxIntervalMs = 1000 * this.maxInterval.getValue();
      final double speed = this.speed.getValue();
      final double variation = .01 * this.variation.getValue();
      final double durationInv = 100 / this.duration.getValue();
      final double density = .01 * this.density.getValue();
      final double baseLevel = LXUtils.lerp(100, this.baseLevel.getValue(), amount);

      LXWaveshape waveshape = this.waveshape.getObject();

      double maxLevel = this.maxLevel.getValue();
      double minLevel = maxLevel * .01 * this.minLevel.getValue();

      // Amount is used when in effect mode, if amount is cranked down to 0, then
      // the max and min levels with both lerp back to 100 resulting in a full-white
      // output that doesn't mask anything
      maxLevel = LXUtils.lerp(100, maxLevel, amount);
      minLevel = LXUtils.lerp(100, minLevel, amount);

      // Compute how much brightness sparkles can add to reach top level
      final double maxDelta = maxLevel - baseLevel;
      final double minDelta = minLevel - baseLevel;

      double shape = this.sharp.getValue();
      if (shape >= 0) {
        shape = LXUtils.lerp(1, 3, shape);
      } else {
        shape = 1 / LXUtils.lerp(1, 3, -shape);
      }

      // Initialize all output levels to base level
      for (int i = 0; i < this.outputLevels.length; ++i) {
        this.outputLevels[i] = baseLevel;
      }

      // Run all the sparkles
      for (int i = 0; i < this.numSparkles; ++i) {
        final Sparkle sparkle = this.sparkles[i];
        double sparkleInterval = LXUtils.lerp(maxIntervalMs, minIntervalMs, LXUtils.constrain(speed + variation * (sparkle.randomVar - .5), 0, 1));
        sparkle.basis += deltaMs / sparkleInterval;

        // Check if the sparkle has looped
        if (sparkle.basis > 1) {
          sparkle.basis = sparkle.basis % 1.;

          int desiredPixels = (int) (model.size * density);
          float desiredPixelsPerSparkle = desiredPixels / (float) this.numSparkles;

          if (desiredPixels < this.numSparkles) {
            sparkle.activePixels = 1;
            sparkle.isOn = Math.random() < desiredPixelsPerSparkle;
          } else {
            sparkle.isOn = true;
            sparkle.activePixels = Math.round(desiredPixelsPerSparkle);
          }

          // Re-randomize this sparkle
          if (sparkle.isOn) {
            sparkle.randomVar = Math.random();
            sparkle.randomLevel = Math.random();
            sparkle.reindex(model);
          }
        }

        // Process active sparkles
        if (sparkle.isOn && (amount > 0)) {
          // The duration is a percentage 0-100% of the total period time for which the
          // sparkle is active. Here we scale the sparkle's raw 0-1 basis onto this portion
          // of duration, and only process the sparkle if it's still in the 0-1 range, e.g.
          // if duration is 50% then durationInv = 2 and we'll be done after 0-0.5
          double sBasis = sparkle.basis * durationInv;
          if (sBasis < 1) {
            // Compute and scale the sparkle's waveshape
            double g = waveshape.compute(sBasis);
            if (shape != 1) {
              g = Math.pow(g, shape);
            }

            // Determine how much brightness to add for this sparkle
            double maxSparkleDelta = LXUtils.lerp(minDelta, maxDelta, sparkle.randomLevel);
            double sparkleAdd = maxSparkleDelta * g;

            // Add the sparkle's brightness level to all output pixels
            for (int c = 0; c < sparkle.activePixels; ++c) {
              this.outputLevels[sparkle.indexBuffer[c]] += sparkleAdd;
            }
          }
        }
      }
    }
  }

  public final Engine engine = new Engine(model);

  public SparklePattern(LX lx) {
    super(lx);
    addParameter("density", engine.density);
    addParameter("speed", engine.speed);
    addParameter("variation", engine.variation);
    addParameter("duration", engine.duration);
    addParameter("sharp", engine.sharp);
    addParameter("waveshape", engine.waveshape);
    addParameter("minInterval", engine.minInterval);
    addParameter("maxInterval", engine.maxInterval);
    addParameter("baseLevel", engine.baseLevel);
    addParameter("minLevel", engine.minLevel);
    addParameter("maxLevel", engine.maxLevel);
  }

  @Override
  protected void onModelChanged(LXModel model) {
    engine.setModel(model);
  }

  @Override
  public void run(double deltaMs) {
    engine.run(deltaMs, model, 1.);
    int i = 0;
    for (LXPoint p : model.points) {
      colors[p.index] = LXColor.gray(LXUtils.clamp(engine.outputLevels[i++], 0, 100));
    }
  }
}
