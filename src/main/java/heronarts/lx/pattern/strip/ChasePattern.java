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

import com.google.gson.JsonObject;

import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.LXSerializable;
import heronarts.lx.color.LXColor;
import heronarts.lx.model.LXPoint;
import heronarts.lx.modulator.LXVariablePeriodModulator.ClockMode;
import heronarts.lx.modulator.LXWaveshape;
import heronarts.lx.modulator.VariableLFO;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.BoundedParameter;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.pattern.LXPattern;
import heronarts.lx.utils.LXUtils;

@LXCategory(LXCategory.STRIP)
public class ChasePattern extends LXPattern {

  public interface DistanceFunction {
    public double compute(double pos, double motion, double chunkSize);
  }

  public enum WrapMode {
    ABS("Abs", (pos, motion, chunkSize) -> {
      return 2 * LXUtils.wrapdist(pos, motion, chunkSize);
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
      return (pos > motion) ? pos - motion : Double.MAX_VALUE;
    }),

    CLIP_NEG("Clip-", (pos, motion, chunkSize) -> {
      return (motion > pos) ? motion - pos: Double.MAX_VALUE;
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

  public final DiscreteParameter minChunk =
    new DiscreteParameter("Min Chunk", 2, 2, 100)
    .setDescription("Minimum swarm chunk size");

  public final DiscreteParameter maxChunk =
    new DiscreteParameter("Max Chunk", 100, 10, 1000)
    .setDescription("Maximum swarm chunk size");

  public final CompoundParameter chunkSize =
    new CompoundParameter("Chunk Size", .5)
    .setDescription("Chunk size within range");

  public final CompoundParameter shift =
    new CompoundParameter("Shift", 0)
    .setDescription("Amount the position of motion is shifted on each chunk");

  public final BoundedParameter shiftRange =
    new BoundedParameter("Shift Range", 1)
    .setDescription("Maximum range of the shift knob");

  public final BooleanParameter alternate =
    new BooleanParameter("Alternate", false)
    .setDescription("Whether to alternate swarm motion every other chunk");

  public final BooleanParameter sync =
    new BooleanParameter("Sync", false)
    .setDescription("Whether to tempo sync the motion");

  public final EnumParameter<WrapMode> wrap =
    new EnumParameter<WrapMode>("Wrap", WrapMode.ABS)
    .setDescription("Whether to wrap distance calculations within chunk");

  public final CompoundParameter size =
    new CompoundParameter("Size", .5)
    .setDescription("Size of core motion");

  public final CompoundParameter fade =
    new CompoundParameter("Fade", .5)
    .setDescription("Fade size of motion");

  public final CompoundParameter invert =
    new CompoundParameter("Invert", 0)
    .setDescription("Invert the levels");

  public final BooleanParameter swarmOn =
    new BooleanParameter("Swarm-On")
    .setDescription("Whether the swarming effect is on");

  public final CompoundParameter swarmX =
    new CompoundParameter("X", 0.5)
    .setDescription("Swarm center X position");

  public final CompoundParameter swarmY =
    new CompoundParameter("Y", 0.5)
    .setDescription("Swarm center Y position");

  public final CompoundParameter swarmSize =
    new CompoundParameter("Size", 0.5)
    .setDescription("Swarm strength");

  public final CompoundParameter swarmFade =
    new CompoundParameter(">Fade", 0.5)
    .setDescription("How much the swarm affects the fade shape");

  public final CompoundParameter swarmBrightness =
    new CompoundParameter(">Brt", 0.5, -1, 1)
    .setDescription("How much the swarm affects the brightness");

  public final CompoundParameter swarmPolarity =
    new CompoundParameter("Polarity", 0)
    .setDescription("Swarm polarity");

  public final VariableLFO motion = new VariableLFO("Motion", WAVESHAPES);

  public ChasePattern(LX lx) {
    super(lx);

    // Motion controls
    addParameter("minChunk", this.minChunk);
    addParameter("maxChunk", this.maxChunk);
    addParameter("chunkSize", this.chunkSize);
    addParameter("shift", this.shift);
    addParameter("shiftRange", this.shiftRange);
    addParameter("alternate", this.alternate);

    addParameter("sync", this.sync);

    // Levels
    addParameter("wrap", this.wrap);
    addParameter("size", this.size);
    addParameter("fade", this.fade);
    addParameter("invert", this.invert);

    // Swarming Effect
    addParameter("swarmOn", this.swarmOn);
    addParameter("swarmX", this.swarmX);
    addParameter("swarmY", this.swarmY);
    addParameter("swarmSize", this.swarmSize);
    addParameter("swarmFade", this.swarmFade);
    addParameter("swarmBrightness", this.swarmBrightness);
    addParameter("swarmPolarity", this.swarmPolarity);

    startModulator(this.motion);

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

  @Override
  public void onParameterChanged(LXParameter p) {
    super.onParameterChanged(p);
    if (this.sync == p) {
      this.motion.clockMode.setValue(this.sync.isOn() ? ClockMode.SYNC : ClockMode.FAST);
    }
  }

  @Override
  protected void run(double deltaMs) {
    double chunkSize = LXUtils.lerp(this.minChunk.getValue(), this.maxChunk.getValue(), this.chunkSize.getValue());
    int chunkSizei = (int) chunkSize;
    double motion = this.motion.getValue() * chunkSize;

    double size = this.size.getValue();
    double fade = this.fade.getValue();
    double invert = this.invert.getValue();

    double sizePixels = chunkSize * size;
    double fadePixels = chunkSize * fade;

    boolean alternate = this.alternate.isOn();
    WrapMode wrap = this.wrap.getEnum();

    boolean swarmOn = this.swarmOn.isOn();
    double swarmSize = .01 + this.swarmSize.getValue();
    double swarmX = this.swarmX.getValue();
    double swarmY = this.swarmY.getValue();
    double swarmFade = this.swarmFade.getValue();
    double swarmFadeAbs = Math.abs(swarmFade);
    double swarmBrightness = this.swarmBrightness.getValue();
    double swarmBrightnessAbs = Math.abs(swarmBrightness);
    double shift = this.shift.getValue() * this.shiftRange.getValue() * chunkSize;

    double minSizePixels = LXUtils.lerp(0, chunkSize + sizePixels, invert);
    double minFadePixels = LXUtils.lerp(0, chunkSize + sizePixels, invert);

    if (swarmFade < 0) {
      minSizePixels = chunkSize - minSizePixels;
      minSizePixels = chunkSize - minFadePixels;
    }

    boolean even = false;

    int i = 0;
    for (LXPoint p : model.points) {
      int chunkIndex = i / chunkSizei;
      double pos = i % chunkSize;
      if (even && alternate) {
        pos = (chunkSize - pos) % chunkSize;
      }

      // Apply shift to motion
      double motion2 = (motion + chunkIndex * shift) % chunkSize;

      // Distance application
      double dist = wrap.distance.compute(pos, motion2, chunkSize);

      double swarmDistance = swarmOn ? LXUtils.dist(swarmX, swarmY, p.xn, p.yn) / swarmSize : 0;

      // Swarm modifies size and falloff
      double swarmFadeLerp = LXUtils.min(1, swarmDistance * swarmFadeAbs);
      double sizePixels2 = LXUtils.lerp(sizePixels, minSizePixels, swarmFadeLerp);
      double fadePixels2 =  LXUtils.lerp(fadePixels, minFadePixels, swarmFadeLerp);

      double falloff = 100 / fadePixels2;
      double btop = LXUtils.constrain((sizePixels2 + fadePixels2 - 1)*100, 0, 100);

      double b = (dist <= sizePixels2) ? btop : LXUtils.max(0, btop - falloff * (dist - sizePixels2));
      b = LXUtils.lerp(b, 100-b, invert);

      // Swarm modifies brightness
      if (swarmBrightness >= 0) {
        double swarmBrightnessLerp = LXUtils.constrain(swarmDistance * swarmBrightnessAbs, 0, 1);
        b = LXUtils.lerp(b, 0, swarmBrightnessLerp);
      } else {
        double swarmBrightnessLerp = LXUtils.constrain((1.414-swarmDistance) * swarmBrightnessAbs, 0, 1);
        b = LXUtils.lerp(b, 0, swarmBrightnessLerp);
      }

      colors[p.index] = LXColor.gray(b);
      ++i;
      even = !even;
    }
  }

  private static final String KEY_MOTION = "motion";

  @Override
  public void save(LX lx, JsonObject obj) {
    super.save(lx, obj);
    obj.add(KEY_MOTION, LXSerializable.Utils.toObject(this.motion));
  }

  @Override
  public void load(LX lx, JsonObject obj) {
    super.load(lx, obj);
    LXSerializable.Utils.loadObject(lx, this.motion, obj, KEY_MOTION);
  }

}
