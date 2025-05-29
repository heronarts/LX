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

package heronarts.lx.effect.color;

import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.LXComponent;
import heronarts.lx.color.LXColor;
import heronarts.lx.effect.LXEffect;
import heronarts.lx.effect.color.ColorizeEffect.SourceMode;
import heronarts.lx.effect.color.ColorizeEffect.SourceFunction;
import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.utils.LXUtils;

@LXCategory(LXCategory.COLOR)
@LXComponent.Description("Makes colors transparent")
public class TransparifyEffect extends LXEffect {

  public final EnumParameter<SourceMode> source =
    new EnumParameter<SourceMode>("Source", SourceMode.BRIGHTNESS)
    .setDescription("Determines the source of the color mapping");

  public final CompoundParameter threshold =
    new CompoundParameter("Threshold", 0)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Threshold below which to apply transparency");

  public final CompoundParameter feather =
    new CompoundParameter("Feather", 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Creates a smooth alpha gradient up to the threshold");

  public final CompoundParameter amount =
    new CompoundParameter("Amount", 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Degree of alpha transparency applied");

  public TransparifyEffect(LX lx) {
    super(lx);
    addParameter("threshold", this.threshold);
    addParameter("feather", this.feather);
    addParameter("amount", this.amount);
    addParameter("source", this.source);
  }

  @Override
  protected void run(double deltaMs, double enabledAmount) {
    final float amount = (float) (enabledAmount * this.amount.getValue());
    if (amount > 0) {

      final float threshold = this.threshold.getValuef();
      final float feather = this.feather.getValuef();
      final float invThreshold = 1 / threshold;

      final SourceFunction sourceFunction = this.source.getEnum().lerp;
      for (LXPoint p : model.points) {
        final float lerp = sourceFunction.getLerpFactor(colors[p.index]);
        if (lerp <= threshold) {
          final float lerpAmount = amount * (1f - LXUtils.lerpf(0, lerp * invThreshold, feather));
          final int srcAlpha = (colors[p.index] & LXColor.ALPHA_MASK) >>> LXColor.ALPHA_SHIFT;
          final int dstAlpha = LXUtils.lerpi(srcAlpha, 0, lerpAmount);
          colors[p.index] =
            (dstAlpha << LXColor.ALPHA_SHIFT) |
            (LXColor.RGB_MASK & colors[p.index]);
        }
      }
    }
  }
}
