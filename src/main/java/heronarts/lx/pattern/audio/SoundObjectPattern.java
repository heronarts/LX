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

package heronarts.lx.pattern.audio;

import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.LXComponentName;
import heronarts.lx.audio.SoundObject;
import heronarts.lx.audio.SoundStage;
import heronarts.lx.color.LXColor;
import heronarts.lx.model.LXModel;
import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.pattern.LXPattern;
import heronarts.lx.transform.LXVector;
import heronarts.lx.utils.LXUtils;

@LXCategory(LXCategory.AUDIO)
@LXComponentName("Sound Object")
public class SoundObjectPattern extends LXPattern {

  public interface ShapeFunction {
    public float getDistance(LXPoint p, LXVector so);

    public static ShapeFunction NONE = (p, so) -> { return 0f; };
  }

  public enum ShapeMode {
    ORB("Orb", (p, so) -> { return LXUtils.distf(p.xn, p.yn, p.zn, so.x, so.y, so.z); }),
    BOX("Box", (p, so) -> {
      return LXUtils.maxf(
        Math.abs(p.xn - so.x),
        Math.abs(p.yn - so.y),
        Math.abs(p.zn - so.z)
      );
    }),
    X("X", (p, so) -> { return Math.abs(p.xn - so.x); }),
    Y("Y", (p, so) -> { return Math.abs(p.yn - so.y); }),
    Z("Z", (p, so) -> { return Math.abs(p.zn - so.z); });

    private final String label;
    private final ShapeFunction function;

    private ShapeMode(String label, ShapeFunction function) {
      this.label = label;
      this.function = function;
    }

    @Override
    public String toString() {
      return this.label;
    }
  }

  public static class Engine {

    public final SoundObject.Selector selector = (SoundObject.Selector)
      new SoundObject.Selector("Object")
      .setDescription("Which sound object to render");

    public final EnumParameter<SoundStage.ObjectPositionMode> positionMode =
      new EnumParameter<SoundStage.ObjectPositionMode>("Position Mode", SoundStage.ObjectPositionMode.ABSOLUTE)
      .setDescription("How to calculate the sound object position");

    public final EnumParameter<ShapeMode> shapeMode1 =
      new EnumParameter<ShapeMode>("Shape Mode 1", ShapeMode.ORB)
      .setDescription("How to render the sound object shape");

    public final EnumParameter<ShapeMode> shapeMode2 =
      new EnumParameter<ShapeMode>("Shape Mode 2", ShapeMode.BOX)
      .setDescription("How to render the sound object shape");

    public final CompoundParameter shapeLerp =
      new CompoundParameter("Shaper Lerp")
      .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
      .setDescription("Blending between two object shapes");

    public final CompoundParameter baseSize =
      new CompoundParameter("Size", .1)
      .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
      .setDescription("Base Size of the sound object");

    public final CompoundParameter baseLevel =
      new CompoundParameter("Level", 1)
      .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
      .setDescription("Base brightness level");

    public final CompoundParameter contrast =
      new CompoundParameter("Contrast", .5)
      .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
      .setDescription("Percentage of the total size which fades out");

    public final CompoundParameter modulationInput =
      new CompoundParameter("Mod", 0)
      .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
      .setDescription("Manual modulation input");

    public final CompoundParameter modulationToSize =
      new CompoundParameter("M>Sz", 0, -1, 1)
      .setPolarity(CompoundParameter.Polarity.BIPOLAR)
      .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
      .setDescription("Amount of modulation input applied to size");

    public final CompoundParameter modulationToLevel =
      new CompoundParameter("M>Lev", 0, -1, 1)
      .setPolarity(CompoundParameter.Polarity.BIPOLAR)
      .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
      .setDescription("Amount of modulation input applied to brightness");

    public final CompoundParameter signalToSize =
      new CompoundParameter("Sig>Sz", 0, -1, 1)
      .setPolarity(CompoundParameter.Polarity.BIPOLAR)
      .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
      .setDescription("Amount by which signal level modulates orb size");

    public final CompoundParameter signalToLevel =
      new CompoundParameter("Sig>Lev", 0)
      .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
      .setDescription("Amount by which signal level modulates overall brightness");

    public final CompoundParameter scopeAmount =
      new CompoundParameter("ScpAmt", 0)
      .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
      .setDescription("Amount of audio scope modulation, modulating brightness by input history");

    public final CompoundParameter scopeTimeMs =
      new CompoundParameter("ScpTim", 2000, 100, MAX_SCOPE_MS)
      .setExponent(2)
      .setUnits(CompoundParameter.Units.MILLISECONDS)
      .setDescription("Total amount of time of scope history");

    private final LX lx;

    public Engine(LX lx) {
      this.lx = lx;
    }

    private static final int MAX_SCOPE_MS = 30000;

    private final double[] scope = new double[MAX_SCOPE_MS];
    private int scopeIndex = 0;

    private final static double INV_MAX_DISTANCE = 1 / Math.sqrt(3);

    private final LXVector so = new LXVector();

    private double modulationFactor(double value, LXParameter lerpParam) {
      double lerp = lerpParam.getValue();
      if (lerp > 0) {
        return LXUtils.lerp(1, value, lerp);
      } else {
        return LXUtils.lerp(1, 1-value, -lerp);
      }
    }

    public void run(LXModel model, int[] colors, double deltaMs) {
      final double mod = this.modulationInput.getValue();

      double level = 100 * this.baseLevel.getValue() * modulationFactor(mod, this.modulationToLevel);
      double size = this.baseSize.getValue() * modulationFactor(mod, this.modulationToSize);
      double signal = 0;

      // Get sound object data
      final SoundObject soundObject = this.selector.getObject();
      if (soundObject != null) {
        signal = soundObject.getNormalized();
        this.lx.engine.audio.soundStage.getNormalizedObjectPosition(
          soundObject,
          this.positionMode.getEnum(),
          model,
          this.so
        );
      } else {
        this.so.set(.5f, .5f, .5f);
      }

      // Scale the level and size by signal
      level *= modulationFactor(signal, this.signalToLevel);
      size *= modulationFactor(signal, this.signalToSize);

      // Fill in the scope
      int scopeSteps = (int) deltaMs;
      double currentScope = this.scope[this.scopeIndex];
      double scopeIncrement = (signal - currentScope) / scopeSteps;
      for (int i = 0; i < (int) deltaMs; ++i) {
        this.scopeIndex = (this.scopeIndex + 1) % this.scope.length;
        currentScope += scopeIncrement;
        this.scope[this.scopeIndex] = currentScope;
      }

      final double fadePercent = size * (1-this.contrast.getValue());
      final double fadePercentInv = 1 / fadePercent;
      final double scopeAmount = this.scopeAmount.getValue();
      final double scopeTimeMs = this.scopeTimeMs.getValue();

      final double fadeStart = size - fadePercent;

      final float shapeLerp = this.shapeLerp.getValuef();
      final ShapeFunction distance1 = (shapeLerp < 1) ? this.shapeMode1.getEnum().function : ShapeFunction.NONE;
      final ShapeFunction distance2 = (shapeLerp > 0) ? this.shapeMode2.getEnum().function : ShapeFunction.NONE;

      for (LXPoint p : model.points) {
        final float dist = LXUtils.lerpf(
          distance1.getDistance(p, so),
          distance2.getDistance(p, so),
          shapeLerp
        );
        final int scopeOffset = (int) (dist * INV_MAX_DISTANCE * scopeTimeMs);
        double scopeFactor = 1;
        if (scopeOffset < this.scope.length) {
          final int scopePosition = (this.scopeIndex + this.scope.length - scopeOffset) % this.scope.length;
          scopeFactor = LXUtils.lerp(1, this.scope[scopePosition], scopeAmount);
        }
        final double max = level * scopeFactor;
        final double falloff = max * fadePercentInv;

        colors[p.index] = LXColor.gray(LXUtils.constrain(
          max - falloff * (dist - fadeStart),
          0, max
        ));
      }
    }

  }

  public final Engine engine;

  public SoundObjectPattern(LX lx) {
    super(lx);
    this.engine = new Engine(lx);
    addParameter("baseSize", this.engine.baseSize);
    addParameter("signalToSize", this.engine.signalToSize);
    addParameter("fadePercent", this.engine.contrast);
    addParameter("baseBrightness", this.engine.baseLevel);
    addParameter("modulationInput", this.engine.modulationInput);
    addParameter("modulationToSize", this.engine.modulationToSize);
    addParameter("modulationToBrt", this.engine.modulationToLevel);
    addParameter("signalToBrt", this.engine.signalToLevel);

    addParameter("selector", this.engine.selector);
    addParameter("positionMode", this.engine.positionMode);
    addParameter("shapeMode1", this.engine.shapeMode1);
    addParameter("shapeMode2", this.engine.shapeMode2);
    addParameter("shapeLerp", this.engine.shapeLerp);

    addParameter("scopeAmount", this.engine.scopeAmount);
    addParameter("scopeTimeMs", this.engine.scopeTimeMs);
  }

  @Override
  protected void run(double deltaMs) {
    this.engine.run(this.model, this.colors, deltaMs);
  }
}
