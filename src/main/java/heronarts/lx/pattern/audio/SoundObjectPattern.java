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
import heronarts.lx.pattern.LXPattern;
import heronarts.lx.utils.LXUtils;

@LXCategory(LXCategory.AUDIO)
@LXComponentName("Sound Object")
public class SoundObjectPattern extends LXPattern {

  public final SoundObject.Selector selector = (SoundObject.Selector)
    new SoundObject.Selector("Object")
    .setDescription("Which sound object to render");

  public final CompoundParameter baseSize =
    new CompoundParameter("Base", .1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED);

  public final CompoundParameter fadePercent =
    new CompoundParameter("Fade", .5)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED);

  public final CompoundParameter levelToSize =
    new CompoundParameter("Lev>Sz", .5)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED);

  public final CompoundParameter levelToBrt =
    new CompoundParameter("Lev>Brt", 0)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED);

  public final CompoundParameter scopeAmount =
    new CompoundParameter("ScpAmt", 0)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Amount of audio scope modulation");

  public final CompoundParameter scopeTimeMs =
    new CompoundParameter("ScpTim", 2000, 100, MAX_SCOPE_MS)
    .setUnits(CompoundParameter.Units.MILLISECONDS);

  private static final int MAX_SCOPE_MS = 10000;

  private final double[] scope = new double[MAX_SCOPE_MS];
  private int scopeIndex = 0;

  public SoundObjectPattern(LX lx) {
    super(lx);
    addParameter("baseSize", this.baseSize);
    addParameter("fadePercent", this.fadePercent);
    addParameter("levelToSize", this.levelToSize);
    addParameter("levelToBrt", this.levelToBrt);
    addParameter("scopeAmount", this.scopeAmount);
    addParameter("scopeTimeMs", this.scopeTimeMs);
    addParameter("selector", this.selector);
  }

  private final static double INV_MAX_DISTANCE = 1 / Math.sqrt(3);

  @Override
  protected void run(double deltaMs) {
    double sx = .5, sy = .5, sz = .5;
    double size = 0;
    double level = 0;

    // Get sound object data
    final SoundObject soundObject = this.selector.getObject();
    if (soundObject != null) {
      level = soundObject.getValue();
      size = this.baseSize.getValue() + this.levelToSize.getValue() * level;
      sx = soundObject.getX();
      sy = soundObject.getY();
      sz = soundObject.getZ();
    }

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
