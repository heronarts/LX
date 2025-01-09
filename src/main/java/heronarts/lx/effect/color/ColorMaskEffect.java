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

package heronarts.lx.effect.color;

import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.LXComponent;
import heronarts.lx.LXComponentName;
import heronarts.lx.blend.LXBlend;
import heronarts.lx.color.LXColor;
import heronarts.lx.color.LinkedColorParameter;
import heronarts.lx.effect.LXEffect;
import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.EnumParameter;

@LXCategory(LXCategory.COLOR)
@LXComponentName("Color Mask")
@LXComponent.Description("Masks existing content against a distinct color")
public class ColorMaskEffect extends LXEffect {

  public static enum Mode {
    MULTIPLY("Multiply", LXColor::multiply),
    ADD("Add", LXColor::add),
    SUBTRACT("Subtract", LXColor::subtract),
    DIFFERENCE("Difference", LXColor::difference),
    SPOTLIGHT("Spotlight", LXColor::spotlight),
    HIGHLIGHT("Highlight", LXColor::highlight),
    LERP("Lerp", LXColor::lerp);

    public final String label;
    public final LXBlend.FunctionalBlend.BlendFunction function;

    private Mode(String label, LXBlend.FunctionalBlend.BlendFunction function) {
      this.label = label;
      this.function = function;
    }

    @Override
    public String toString() {
      return this.label;
    }
  }

  public final EnumParameter<Mode> mode =
    new EnumParameter<Mode>("Mode", Mode.MULTIPLY)
    .setDescription("How to apply the color mask");

  public final LinkedColorParameter color =
    new LinkedColorParameter("Color", LXColor.WHITE)
    .setDescription("Masking color");

  public final CompoundParameter depth =
    new CompoundParameter("Depth", 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Amount of masking to apply");

  public ColorMaskEffect(LX lx) {
    super(lx);
    addParameter("depth", this.depth);
    addParameter("mode", this.mode);
    addParameter("color", this.color);
  }

  @Override
  protected void run(double deltaMs, double enabledAmount) {
    enabledAmount *= this.depth.getValue();
    if (enabledAmount == 0) {
      return;
    }
    final int color = this.color.calcColor();
    final int alpha = LXColor.blendMask(enabledAmount);
    final LXBlend.FunctionalBlend.BlendFunction blend = this.mode.getEnum().function;
    for (LXPoint p : model.points) {
      colors[p.index] = blend.apply(colors[p.index], color, alpha);
    }
  }

}
