/**
 * Copyright 2022- Mark C. Slee, Heron Arts LLC
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

package heronarts.lx.pattern.strip;

import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.Tempo;
import heronarts.lx.color.LXColor;
import heronarts.lx.model.LXPoint;
import heronarts.lx.modulator.LXWaveshape;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.BoundedParameter;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.ObjectParameter;
import heronarts.lx.pattern.LXPattern;
import heronarts.lx.utils.LXUtils;

@LXCategory(LXCategory.STRIP)
public class ChasePattern extends LXPattern {

  public interface DistanceFunction {
    public float compute(float pos, float motion, float chunkSize);
  }

  public enum WrapMode {
    ABS("Abs", (pos, motion, chunkSize) -> {
      return 2 * LXUtils.wrapdistf(pos, motion, chunkSize);
    }),

    POS("Pos", (pos, motion, chunkSize) -> {
      return (pos > motion) ? pos-motion : pos + chunkSize - motion;
    }),

    NEG("Neg", (pos, motion, chunkSize) -> {
      return (motion > pos) ? motion - pos : motion + chunkSize - pos;
    }),

    CLIP("Clip", (pos, motion, chunkSize) -> {
      return 2 * Math.abs(pos - motion);
    }),

    CLIP_POS("Clip+", (pos, motion, chunkSize) -> {
      return (pos > motion) ? pos - motion : Float.MAX_VALUE;
    }),

    CLIP_NEG("Clip-", (pos, motion, chunkSize) -> {
      return (motion > pos) ? motion - pos: Float.MAX_VALUE;
    });

    private final String label;
    public final DistanceFunction distance;

    private WrapMode(String label, DistanceFunction distance) {
      this.label = label;
      this.distance = distance;
    }

    @Override
    public String toString() {
      return this.label;
    }
  }

  private static final LXWaveshape[] WAVESHAPES = {
    LXWaveshape.SIN,
    LXWaveshape.TRI,
    LXWaveshape.UP,
    LXWaveshape.DOWN
  };

  public final ObjectParameter<LXWaveshape> waveshape =
    new ObjectParameter<LXWaveshape>("Waveshape", WAVESHAPES)
    .setDescription("What waveshape to use");

  public final CompoundParameter skew = new CompoundParameter("Skew", 0, -1, 1)
    .setDescription("Sets a skew coefficient for the waveshape")
    .setPolarity(LXParameter.Polarity.BIPOLAR);

  public final CompoundParameter exp = new CompoundParameter("Exp", 0, -1, 1)
    .setDescription("Applies exponential scaling to the waveshape")
    .setPolarity(LXParameter.Polarity.BIPOLAR);

  public final DiscreteParameter minChunk =
    new DiscreteParameter("Min Chunk", 2, 2, 100)
    .setDescription("Minimum swarm chunk size");

  public final DiscreteParameter maxChunk =
    new DiscreteParameter("Max Chunk", 100, 10, 1000)
    .setDescription("Maximum swarm chunk size");

  public final CompoundParameter chunkSize =
    new CompoundParameter("Chunk Size", 50, 0, 100)
    .setUnits(CompoundParameter.Units.PERCENT)
    .setDescription("Chunk size within range");

  public final CompoundParameter shift =
    new CompoundParameter("Shift", 0, 0, 100)
    .setUnits(CompoundParameter.Units.PERCENT)
    .setDescription("Amount the position of motion is shifted on each chunk");

  public final BoundedParameter shiftRange =
    new BoundedParameter("Shift Range", 100, 0, 100)
    .setUnits(BoundedParameter.Units.PERCENT)
    .setDescription("Maximum range of the shift knob");

  public final BooleanParameter interlace =
    new BooleanParameter("Interlace", false)
    .setDescription("Whether to interlace opposite chase direction every other pixel");

  public final BooleanParameter alternateChunk =
    new BooleanParameter("Alternate", false)
    .setDescription("Whether to alternate chase direction every other chunk");

  public final EnumParameter<WrapMode> wrap =
    new EnumParameter<WrapMode>("Wrap", WrapMode.ABS)
    .setDescription("Whether to wrap distance calculations within chunk");

  public final CompoundParameter speed =
    new CompoundParameter("Speed", 50, -100, 100)
    .setPolarity(CompoundParameter.Polarity.BIPOLAR)
    .setUnits(CompoundParameter.Units.PERCENT)
    .setDescription("Speed of chase motion");

  public final BoundedParameter speedRange = new BoundedParameter("Range", 1, 0, 10)
    .setUnits(BoundedParameter.Units.HERTZ)
    .setDescription("Maximum range of the speed control in Hz");

  public final BooleanParameter tempoSync =
    new BooleanParameter("Sync", false)
    .setDescription("Whether this modulator syncs to a tempo");

  public final EnumParameter<Tempo.Division> tempoDivision =
    new EnumParameter<Tempo.Division>("Division", Tempo.Division.QUARTER)
    .setDescription("Tempo division when in sync mode");

  public final BooleanParameter reverse =
    new BooleanParameter("Reverse", false)
    .setDescription("Whether to reverse the direction of motion");

  public final CompoundParameter size =
    new CompoundParameter("Size", 50, 0, 100)
    .setUnits(CompoundParameter.Units.PERCENT)
    .setDescription("Size of core motion");

  public final CompoundParameter fade =
    new CompoundParameter("Fade", 50, 0, 100)
    .setUnits(CompoundParameter.Units.PERCENT)
    .setDescription("Fade size of motion");

  public final CompoundParameter level =
    new CompoundParameter("Level", 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Output level");

  public final CompoundParameter invert =
    new CompoundParameter("Invert", 0, 0, 100)
    .setUnits(CompoundParameter.Units.PERCENT)
    .setDescription("Invert the levels");

  public final BooleanParameter swarmOn =
    new BooleanParameter("Swarm-On")
    .setDescription("Whether the swarming effect is on");

  public final CompoundParameter swarmX =
    new CompoundParameter("X", 0.5)
    .setPolarity(CompoundParameter.Polarity.BIPOLAR)
    .setDescription("Swarm center X position");

  public final CompoundParameter swarmY =
    new CompoundParameter("Y", 0.5)
    .setPolarity(CompoundParameter.Polarity.BIPOLAR)
    .setDescription("Swarm center Y position");

  public final CompoundParameter swarmSize =
    new CompoundParameter("Size", 0.5)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Swarm strength");

  public final CompoundParameter swarmFade =
    new CompoundParameter(">Fade", 50, 0, 100)
    .setUnits(CompoundParameter.Units.PERCENT)
    .setDescription("How much the swarm affects the fade shape");

  public final CompoundParameter swarmBrightness =
    new CompoundParameter(">Brt", 50, -100, 100)
    .setUnits(CompoundParameter.Units.PERCENT)
    .setDescription("How much the swarm affects the brightness");

  public final CompoundParameter swarmPolarity =
    new CompoundParameter("Polarity", 0)
    .setDescription("Swarm polarity");

  public ChasePattern(LX lx) {
    super(lx);

    // Motion controls
    addParameter("minChunk", this.minChunk);
    addParameter("maxChunk", this.maxChunk);
    addParameter("chunkSize", this.chunkSize);
    addParameter("shift", this.shift);
    addParameter("shiftRange", this.shiftRange);
    addLegacyParameter("alternate", this.interlace);
    addParameter("interlace", this.interlace);
    addParameter("alternateChunk", this.alternateChunk);
    addParameter("waveshape", this.waveshape);
    addParameter("skew", this.skew);
    addParameter("exp", this.exp);

    // Speed controls
    addParameter("speed", this.speed);
    addParameter("speedRange", this.speedRange);
    addParameter("tempoSync", this.tempoSync);
    addParameter("tempoDivision", this.tempoDivision);
    addParameter("reverse", this.reverse);

    // Levels
    addParameter("wrap", this.wrap);
    addParameter("size", this.size);
    addParameter("fade", this.fade);
    addParameter("level", this.level);
    addParameter("invert", this.invert);

    // Swarming Effect
    addParameter("swarmOn", this.swarmOn);
    addParameter("swarmX", this.swarmX);
    addParameter("swarmY", this.swarmY);
    addParameter("swarmSize", this.swarmSize);
    addParameter("swarmFade", this.swarmFade);
    addParameter("swarmBrightness", this.swarmBrightness);
    addParameter("swarmPolarity", this.swarmPolarity);

    setRemoteControls(
      this.chunkSize,
      this.shift,
      this.size,
      this.fade,
      this.invert,
      this.swarmOn,
      this.swarmX,
      this.swarmY,
      this.swarmSize,
      this.swarmFade,
      this.swarmBrightness
    );
  }

  private double basis = 0;

  @Override
  protected void run(double deltaMs) {
    if (this.tempoSync.isOn()) {
      this.basis = lx.engine.tempo.getBasis(this.tempoDivision.getEnum());
      if (this.reverse.isOn()) {
        this.basis = 1f - basis;
      }
    } else {
      double increment = deltaMs * .001 * this.speed.getValue() * this.speedRange.getValue() * .01;
      this.basis += increment * (this.reverse.isOn() ? -1 : 1);
    }
    double basis = (this.basis - Math.floor(this.basis));

    // Skew the thing
    final double skew = this.skew.getValue();
    double skewPower = (skew >= 0) ? (1 + 3*skew) : (1 / (1-3*skew));
    if (skewPower != 1) {
      basis = Math.pow(basis, skewPower);
    }

    double wave = this.waveshape.getObject().compute(basis);

    // Apply scaling
    final double exp = this.exp.getValue();
    double expPower = (exp >= 0) ? (1 + 3*exp) : (1 / (1 - 3*exp));
    if (expPower != 1) {
      wave = Math.pow(wave, expPower);
    }

    // Now work on the chase chunks
    final float chunkSize = LXUtils.lerpf(this.minChunk.getValuef(), this.maxChunk.getValuef(), this.chunkSize.getValuef() * .01f);
    final int chunkSizei = (int) chunkSize;
    final float motion = (float) wave * chunkSize;

    final float size = this.size.getValuef() * .01f;
    final float fade = this.fade.getValuef() * .01f;
    final float invert = this.invert.getValuef() * .01f;

    final float sizePixels = chunkSize * size;
    final float fadePixels = chunkSize * fade;

    final boolean interlace = this.interlace.isOn();
    final boolean alternateChunk = this.alternateChunk.isOn();
    final WrapMode wrap = this.wrap.getEnum();

    final boolean swarmOn = this.swarmOn.isOn();
    final float swarmSize = .01f + this.swarmSize.getValuef();
    final float swarmX = this.swarmX.getValuef();
    final float swarmY = this.swarmY.getValuef();
    final float swarmFade = this.swarmFade.getValuef() * .01f;
    final float swarmFadeAbs = Math.abs(swarmFade);
    final float swarmBrightness = this.swarmBrightness.getValuef() * .01f;
    final float swarmBrightnessAbs = Math.abs(swarmBrightness);
    final float shift = this.shift.getValuef() * .01f * this.shiftRange.getValuef() * .01f * chunkSize;

    final float level = this.level.getValuef();

    float minSizePixels = LXUtils.lerpf(0, chunkSize + sizePixels, invert);
    float minFadePixels = LXUtils.lerpf(0, chunkSize + sizePixels, invert);

    if (swarmFade < 0) {
      minSizePixels = chunkSize - minSizePixels;
      minSizePixels = chunkSize - minFadePixels;
    }

    boolean even = false;

    int i = 0;
    for (LXPoint p : model.points) {
      int chunkIndex = i / chunkSizei;
      float pos = i % chunkSize;
      boolean evenChunk = (chunkIndex % 2) == 0;
      if ((even && interlace) ^ (evenChunk & alternateChunk)) {
        pos = (chunkSize - pos) % chunkSize;
      }

      // Apply shift to motion
      float motion2 = (motion + chunkIndex * shift) % chunkSize;

      // Distance application
      float dist = wrap.distance.compute(pos, motion2, chunkSize);

      float swarmDistance = swarmOn ? LXUtils.distf(swarmX, swarmY, p.xn, p.yn) / swarmSize : 0;

      // Swarm modifies size and falloff
      float swarmFadeLerp = LXUtils.minf(1, swarmDistance * swarmFadeAbs);
      float sizePixels2 = LXUtils.lerpf(sizePixels, minSizePixels, swarmFadeLerp);
      float fadePixels2 =  LXUtils.lerpf(fadePixels, minFadePixels, swarmFadeLerp);

      float falloff = 100 / fadePixels2;
      float btop = LXUtils.constrainf((sizePixels2 + fadePixels2 - 1)*100, 0, 100);

      float b = (dist <= sizePixels2) ? btop : LXUtils.maxf(0, btop - falloff * (dist - sizePixels2));
      b = LXUtils.lerpf(b, 100-b, invert);

      // Swarm modifies brightness
      if (swarmBrightness >= 0) {
        float swarmBrightnessLerp = LXUtils.constrainf(swarmDistance * swarmBrightnessAbs, 0, 1);
        b = LXUtils.lerpf(b, 0, swarmBrightnessLerp);
      } else {
        float swarmBrightnessLerp = LXUtils.constrainf((1.414f-swarmDistance) * swarmBrightnessAbs, 0, 1);
        b = LXUtils.lerpf(b, 0, swarmBrightnessLerp);
      }

      colors[p.index] = LXColor.gray(level * b);
      ++i;
      even = !even;
    }
  }

}
