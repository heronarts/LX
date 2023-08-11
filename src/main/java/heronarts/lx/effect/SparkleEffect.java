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

package heronarts.lx.effect;

import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.color.LXColor;
import heronarts.lx.model.LXModel;
import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.pattern.texture.SparklePattern;
import heronarts.lx.utils.LXUtils;

@LXCategory(LXCategory.TEXTURE)
public class SparkleEffect extends LXEffect {

  public final SparklePattern.Engine engine = new SparklePattern.Engine(model);

  public final CompoundParameter amount =
    new CompoundParameter("Amount", 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Amount of sparkle to apply");

  public SparkleEffect(LX lx) {
    super(lx);
    addParameter("amount", this.amount);
    addParameter("density", engine.density);
    addParameter("speed", engine.speed);
    addParameter("variation", engine.variation);
    addParameter("duration", engine.duration);
    addParameter("sharp", engine.sharp);
    addParameter("waveshape", engine.waveshape);
    addParameter("minInterval", engine.minInterval);
    addParameter("maxInterval", engine.maxInterval);
    // addParameter("baseLevel", engine.baseLevel);
    addParameter("minLevel", engine.minLevel);
    addParameter("maxLevel", engine.maxLevel);
  }

  @Override
  protected void onModelChanged(LXModel model) {
    this.engine.setModel(model);
  }

  @Override
  protected void run(double deltaMs, double enabledAmount) {
    double amount = enabledAmount * this.amount.getValue();

    // Even if amount is 0, keep the sparkles advancing, don't want a "freeze-frame" effect
    // when turning the amount off and on
    this.engine.run(deltaMs, model, amount);

    // But no need to multiply if amount is 0
    if (amount > 0) {
      int i = 0;
      for (LXPoint p : model.points) {
        colors[p.index] = LXColor.multiply(colors[p.index], LXColor.gray(LXUtils.clamp(engine.outputLevels[i++], 0, 100)), 0x100);
      }
    }
  }

}
