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

package heronarts.lx.effect.audio;

import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.LXComponentName;
import heronarts.lx.ModelBuffer;
import heronarts.lx.blend.LXBlend;
import heronarts.lx.color.LXColor;
import heronarts.lx.effect.LXEffect;
import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.pattern.audio.SoundObjectPattern;

@LXCategory(LXCategory.AUDIO)
@LXComponentName("Sound Object")
public class SoundObjectEffect extends LXEffect {

  public enum MaskMode {
    MULTIPLY("Mask", LXColor::multiply),
    SPOTLIGHT("Spotlight", LXColor::spotlight),
    HIGHLIGHT("Highlight", LXColor::highlight),
    ADD("Add", LXColor::add),
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

  public final SoundObjectPattern.Engine engine;

  private final ModelBuffer blendBuffer;

  public final EnumParameter<MaskMode> maskMode =
    new EnumParameter<MaskMode>("Mode", MaskMode.MULTIPLY)
    .setDescription("How to apply the sound object mask");

  public final CompoundParameter maskDepth =
    new CompoundParameter("Depth", 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Depth of masking effect");

  public final BooleanParameter cueMask =
    new BooleanParameter("CUE", false)
    .setMode(BooleanParameter.Mode.MOMENTARY)
    .setDescription("Directly render the mask");

  public SoundObjectEffect(LX lx) {
    super(lx);
    this.engine = new SoundObjectPattern.Engine(lx);
    this.blendBuffer = new ModelBuffer(lx);

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

    addParameter("maskDepth", this.maskDepth);
    addParameter("maskMode", this.maskMode);
    addParameter("cueMask", this.cueMask);
  }

  @Override
  protected void run(double deltaMs, double enabledAmount) {
    final boolean cueMask = this.cueMask.isOn();
    enabledAmount *= this.maskDepth.getValue();
    if (cueMask || (enabledAmount > 0)) {
      final int[] blend = this.blendBuffer.getArray();
      this.engine.run(this.model, blend, deltaMs);
      if (cueMask) {
        for (LXPoint p : model.points) {
          colors[p.index] = blend[p.index];
        }
      } else {
        final int alpha = LXColor.blendMask(enabledAmount);
        final LXBlend.FunctionalBlend.BlendFunction mask = this.maskMode.getEnum().function;
        for (LXPoint p : model.points) {
          colors[p.index] = mask.apply(colors[p.index], blend[p.index], alpha);
        }
      }
    }
  }

  @Override
  public void dispose() {
    this.blendBuffer.dispose();
    super.dispose();
  }
}
