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

  public final SoundObject.Selector selector = (SoundObject.Selector)
    new SoundObject.Selector("Object")
    .setDescription("Which sound object to render");

  public final EnumParameter<SoundStage.ObjectPositionMode> positionMode =
    new EnumParameter<SoundStage.ObjectPositionMode>("Position Mode", SoundStage.ObjectPositionMode.ABSOLUTE)
    .setDescription("How to calculate the sound object position");

  public final CompoundParameter baseSize =
    new CompoundParameter("Size", .1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Base Size of the sound object");

  public final CompoundParameter baseLevel =
    new CompoundParameter("Level", .5)
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

  private static final int MAX_SCOPE_MS = 30000;

  private final double[] scope = new double[MAX_SCOPE_MS];
  private int scopeIndex = 0;

  public SoundObjectPattern(LX lx) {
    super(lx);
    addParameter("baseSize", this.baseSize);
    addParameter("signalToSize", this.signalToSize);
    addParameter("fadePercent", this.contrast);
    addParameter("baseBrightness", this.baseLevel);
    addParameter("modulationInput", this.modulationInput);
    addParameter("modulationToSize", this.modulationToSize);
    addParameter("modulationToBrt", this.modulationToLevel);
    addParameter("signalToBrt", this.signalToLevel);
    addParameter("scopeAmount", this.scopeAmount);
    addParameter("scopeTimeMs", this.scopeTimeMs);
    addParameter("selector", this.selector);
    addParameter("positionMode", this.positionMode);
  }

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


  @Override
  protected void run(double deltaMs) {
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
        this.model,
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

    for (LXPoint p : model.points) {
      final double dist = LXUtils.distance(p.xn, p.yn, p.zn, so.x, so.y, so.z);
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
