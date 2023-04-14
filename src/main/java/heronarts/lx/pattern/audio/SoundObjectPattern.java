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
    new CompoundParameter("Base Size", .1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED);

  public final CompoundParameter meterResponse =
    new CompoundParameter("Meter Response", .5)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED);

  public final CompoundParameter fadePercent =
    new CompoundParameter("Fade Size", .5)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED);

  public SoundObjectPattern(LX lx) {
    super(lx);
    addParameter("baseSize", this.baseSize);
    addParameter("meterResponse", this.meterResponse);
    addParameter("fadePercent", this.fadePercent);
    addParameter("selector", this.selector);
  }

  @Override
  protected void run(double deltaMs) {
    double sx = .5, sy = .5, sz = .5;
    double size = 0;

    final SoundObject soundObject = this.selector.getObject();
    if (soundObject != null) {
      size = this.baseSize.getValue() + this.meterResponse.getValue() * soundObject.getValue();
      sx = soundObject.getX();
      sy = soundObject.getY();
      sz = soundObject.getZ();
    }

    final double fadePercent = size * this.fadePercent.getValue();
    double fadeStart = size - fadePercent;
    double falloff = 100 / fadePercent;

    for (LXPoint p : model.points) {
      double dist = LXUtils.distance(p.xn, p.yn, p.zn, sx, sy, sz);
      colors[p.index] = LXColor.gray(LXUtils.constrain(
        100 - falloff * (dist - fadeStart),
        0, 100
      ));
    }

  }

}
