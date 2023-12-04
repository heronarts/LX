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

package heronarts.lx.pattern;

import heronarts.lx.LXCategory;
import heronarts.lx.LX;
import heronarts.lx.audio.GraphicMeter;
import heronarts.lx.color.LXColor;
import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.EnumParameter;

@LXCategory(LXCategory.FORM)
public class GraphicEqualizerPattern extends LXPattern {

  public enum Plane {
    XY,
    ZY,
    XZ,
    YZ,
    YX,
    ZX
  }

  public final EnumParameter<Plane> plane =
    new EnumParameter<Plane>("Plane", Plane.XY)
    .setDescription("Which plane the equalizer renders on");

  public final CompoundParameter gain =
    new CompoundParameter("Gain", 1, 0, 3)
    .setDescription("Amount of gain-scaling");

  public final CompoundParameter center =
    new CompoundParameter("Center", 0)
    .setDescription("Center point on the axis");

  public final CompoundParameter fade =
    new CompoundParameter("Fade", 0.1)
    .setExponent(2)
    .setDescription("Amount of fade");

  public final CompoundParameter sharp =
    new CompoundParameter("Sharp", 0.9)
    .setExponent(2)
    .setDescription("Amount of sharpness");

  public GraphicEqualizerPattern(LX lx) {
    super(lx);
    addParameter("gain", this.gain);
    addParameter("center", this.center);
    addParameter("fade", this.fade);
    addParameter("sharp", this.sharp);
    addParameter("plane", this.plane);
  }

  @Override
  public void run(double deltaMs) {
    GraphicMeter eq = lx.engine.audio.meter;
    float center = this.center.getValuef();
    float fade = 100 / this.fade.getValuef();
    float sharp = 100 / (1 - this.sharp.getValuef());
    float gain = this.gain.getValuef();
    Plane plane = this.plane.getEnum();

    float v1 = 0, v2 = 0;
    for (LXPoint p : model.points) {
      switch (plane) {
      case XY: v1 = p.xn; v2 = p.yn; break;
      case ZY: v1 = p.zn; v2 = p.yn; break;
      case XZ: v1 = p.xn; v2 = p.zn; break;
      case YZ: v1 = p.yn; v2 = p.zn; break;
      case YX: v1 = p.yn; v2 = p.xn; break;
      case ZX: v1 = p.zn; v2 = p.xn; break;
      }
      float level = gain * eq.getBandf((int) (v1 * (eq.numBands - 1)));
      float value = Math.abs(v2 - center);
      if (value > level) {
        colors[p.index] = LXColor.BLACK;
      } else {
        colors[p.index] = LXColor.gray(Math.min(100, Math.min((level-value) * sharp, value * fade)));
      }
    }
  }

}