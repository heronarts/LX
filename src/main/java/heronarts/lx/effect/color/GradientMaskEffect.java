/**
 * Copyright 2024- Mark C. Slee, Heron Arts LLC
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
import heronarts.lx.LXComponentName;
import heronarts.lx.ModelBuffer;
import heronarts.lx.blend.LXBlend;
import heronarts.lx.color.LXColor;
import heronarts.lx.effect.LXEffect;
import heronarts.lx.effect.color.ColorMaskEffect.Mode;
import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.pattern.color.GradientPattern;

@LXCategory(LXCategory.COLOR)
@LXComponentName("Gradient Mask")
public class GradientMaskEffect extends LXEffect {

  private final ModelBuffer mask = new ModelBuffer(lx);
  public final GradientPattern.Engine engine;

  public final EnumParameter<Mode> mode =
    new EnumParameter<Mode>("Mode", Mode.MULTIPLY)
    .setDescription("How to apply the color mask");

  public final CompoundParameter depth =
    new CompoundParameter("Depth", 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Amount of masking to apply");

  public final BooleanParameter cueMask =
    new BooleanParameter("CUE", false)
    .setMode(BooleanParameter.Mode.MOMENTARY)
    .setDescription("Directly render the mask");

  public GradientMaskEffect(LX lx) {
    super(lx);
    addParameter("depth", this.depth);
    addParameter("mode", this.mode);
    this.engine = new GradientPattern.Engine(lx);
    addParameters(this.engine.parameters);
    addParameter("cueMask", this.cueMask);
  }

  @Override
  public void onParameterChanged(LXParameter p) {
    this.engine.onParameterChanged(p);
  }

  @Override
  protected void run(double deltaMs, double enabledAmount) {
    enabledAmount *= this.depth.getValue();
    final int alpha = LXColor.blendMask(enabledAmount);
    if (alpha == 0) {
      return;
    }

    final boolean cueMask = this.cueMask.isOn();

    // Run the gradient engine
    final int[] maskColors = this.mask.getArray();
    this.engine.run(deltaMs, this.model, cueMask ? colors : maskColors);

    // Mask input colors by the results
    if (!cueMask) {
      final LXBlend.FunctionalBlend.BlendFunction blend = this.mode.getEnum().function;
      for (LXPoint p : model.points) {
        colors[p.index] = blend.apply(colors[p.index], maskColors[p.index], alpha);
      }
    }
  }

  @Override
  public void dispose() {
    this.mask.dispose();
    super.dispose();
  }

}
