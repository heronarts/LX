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
import heronarts.lx.color.LXColor;
import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.pattern.LXPattern;
import heronarts.lx.utils.LXUtils;

@LXCategory(LXCategory.AUDIO)
@LXComponentName("Sound Object")
public class SoundObjectPattern extends LXPattern {

  public enum CoordMode {
    XYZ("XYZ"),
    AE1("AE1");

    public final String label;

    private CoordMode(String label) {
      this.label = label;
    }

    @Override
    public String toString() {
      return this.label;
    }
  }

  public final SoundObject.Selector selector = (SoundObject.Selector)
    new SoundObject.Selector("Object")
    .setDescription("Which sound object to render");

  public final EnumParameter<CoordMode> coordMode =
    new EnumParameter<CoordMode>("Mode", CoordMode.XYZ)
    .setDescription("How to compute the source position");

  public final CompoundParameter baseSize =
    new CompoundParameter("Base", .1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Base Size of the sound object");

  public final CompoundParameter fadePercent =
    new CompoundParameter("Fade", .5)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Percentage of the size which fades out");

  public final CompoundParameter modulationInput =
    new CompoundParameter("Mod", 0)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Manual modulation input");

  public final CompoundParameter modulationAmount =
    new CompoundParameter("M>Lev", 0, -1, 1)
    .setPolarity(CompoundParameter.Polarity.BIPOLAR)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Amount of modulation input applied to level");

  public final CompoundParameter levelToSize =
    new CompoundParameter("Lev>Sz", .5)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Amount by which signal level modulates orb size");

  public final CompoundParameter levelToBrt =
    new CompoundParameter("Lev>Brt", 0)
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
    addParameter("fadePercent", this.fadePercent);
    addParameter("modulationInput", this.modulationInput);
    addParameter("modulationAmount", this.modulationAmount);
    addParameter("levelToSize", this.levelToSize);
    addParameter("levelToBrt", this.levelToBrt);
    addParameter("scopeAmount", this.scopeAmount);
    addParameter("scopeTimeMs", this.scopeTimeMs);
    addParameter("selector", this.selector);
    addParameter("coordMode", this.coordMode);
  }

  private final static double INV_MAX_DISTANCE = 1 / Math.sqrt(3);

  @Override
  protected void run(double deltaMs) {
    double sx = .5, sy = .5, sz = .5;
    double size = this.baseSize.getValue();
    double level = this.modulationInput.getValue() * this.modulationAmount.getValue();

    // Get sound object data
    final SoundObject soundObject = this.selector.getObject();
    if (soundObject != null) {
      level += soundObject.getValue();
      size += this.levelToSize.getValue() * level;
      switch (this.coordMode.getEnum()) {
      case AE1:
        double azim = Math.toRadians(soundObject.azimuth.getValue());
        double elev = Math.toRadians(soundObject.elevation.getValue());
        double sinAzim = Math.sin(azim);
        double cosAzim = Math.cos(azim);
        double sinElev = Math.sin(elev);
        double cosElev = Math.cos(elev);
        sx = .5 * (1 + cosElev * sinAzim);
        sz = .5 * (1 + cosElev * cosAzim);
        sy = .5 * (1 + sinElev);
        break;
      default:
      case XYZ:
        sx = soundObject.getX();
        sy = soundObject.getY();
        sz = soundObject.getZ();
        break;

      }
    }

    level = LXUtils.constrain(level, 0, 1);

    // Fill in the scope
    int scopeSteps = (int) deltaMs;
    double currentScope = this.scope[this.scopeIndex];
    double scopeIncrement = (level - currentScope) / scopeSteps;
    for (int i = 0; i < (int) deltaMs; ++i) {
      this.scopeIndex = (this.scopeIndex + 1) % this.scope.length;
      currentScope += scopeIncrement;
      this.scope[this.scopeIndex] = currentScope;
    }

    final double fadePercent = size * this.fadePercent.getValue();
    final double fadePercentInv = 1 / fadePercent;
    // final double falloff = 100  / fadePercent;
    final double scopeAmount = this.scopeAmount.getValue();
    final double scopeTimeMs = this.scopeTimeMs.getValue();
    final double levelToBrt = 100 * LXUtils.lerp(1, level, this.levelToBrt.getValue());

    final double fadeStart = size - fadePercent;

    for (LXPoint p : model.points) {
      final double dist = LXUtils.distance(p.xn, p.yn, p.zn, sx, sy, sz);
      final int scopeOffset = (int) (dist * INV_MAX_DISTANCE * scopeTimeMs);
      final int scopePosition = (this.scopeIndex + this.scope.length - scopeOffset) % this.scope.length;
      final double max =
        levelToBrt *
        LXUtils.lerp(1, this.scope[scopePosition], scopeAmount);

      final double falloff = max * fadePercentInv;

      colors[p.index] = LXColor.gray(LXUtils.constrain(
        max - falloff * (dist - fadeStart),
        0, max
      ));
    }
  }

}
