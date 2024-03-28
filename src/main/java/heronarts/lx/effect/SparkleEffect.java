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
import heronarts.lx.blend.LXBlend;
import heronarts.lx.color.LXColor;
import heronarts.lx.model.LXModel;
import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.pattern.texture.SparklePattern;
import heronarts.lx.utils.LXUtils;

@LXCategory(LXCategory.TEXTURE)
public class SparkleEffect extends LXEffect {

  public enum MaskMode {
    MULTIPLY("Mask", LXColor::multiply),
    ADD("Add", LXColor::add),
    SPOTLIGHT("Spotlight", LXColor::spotlight),
    HIGHLIGHT("Highlight", LXColor::highlight),
    SUBTRACT("Subtract", LXColor::subtract),
    DIFFERENCE("Difference", LXColor::difference),
    LERP("Lerp", LXColor::lerp);

    public final String label;
    public final LXBlend.FunctionalBlend.BlendFunction function;

    private MaskMode(String label, LXBlend.FunctionalBlend.BlendFunction function) {
      this.label = label;
      this.function = function;
    }

    @Override
    public String toString() {
      return this.label;
    }
  }

  public final SparklePattern.Engine engine = new SparklePattern.Engine(model);

  public final CompoundParameter amount =
    new CompoundParameter("Amount", 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Amount of sparkle to apply");

  public final EnumParameter<MaskMode> maskMode =
    new EnumParameter<MaskMode>("Mode", MaskMode.MULTIPLY)
    .setDescription("How to apply the sparkle mask");

  public SparkleEffect(LX lx) {
    super(lx);
    addParameter("amount", this.amount);
    addParameters(engine.parameters);
    addParameter("maskMode", this.maskMode);
  }

  @Override
  protected void onModelChanged(LXModel model) {
    this.engine.setModel(model);
  }

  @Override
  protected void run(double deltaMs, double enabledAmount) {
    enabledAmount *= this.amount.getValue();

    // Even if amount is 0, keep the sparkles advancing, don't want a "freeze-frame" effect
    // when turning the amount off and on
    this.engine.run(deltaMs, model, 0, enabledAmount > 0);

    // Only apply masking if amount is over 0
    if (enabledAmount > 0) {
      final int blendMask = LXColor.blendMask(enabledAmount);
      final LXBlend.FunctionalBlend.BlendFunction mask = this.maskMode.getEnum().function;
      int i = 0;
      for (LXPoint p : model.points) {
        colors[p.index] = mask.apply(colors[p.index], LXColor.gray(LXUtils.clamp(engine.outputLevels[i++], 0, 100)), blendMask);
      }
    }
  }

}
