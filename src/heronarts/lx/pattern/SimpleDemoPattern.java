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

import heronarts.lx.LX;
import heronarts.lx.LXPattern;
import heronarts.lx.color.ColorParameter;
import heronarts.lx.color.LXColor;
import heronarts.lx.model.LXPoint;
import heronarts.lx.modulator.SinLFO;
import heronarts.lx.modulator.TriangleLFO;
import java.lang.*;

public class SimpleDemoPattern extends LXPattern {

  public final ColorParameter color = new ColorParameter("Color");
  private final SinLFO sweepLFO = new SinLFO(model.xMin,  model.xMax, 4000);
  private final SinLFO magLFO = new SinLFO(10, 100, 4000);


  public SimpleDemoPattern(LX lx) {
//    this(lx, LXColor.RED);
    this(lx, LXColor.GREEN);
  }

  public SimpleDemoPattern(LX lx, int color) {
    super(lx);
    this.color.setColor(color);
    addParameter("color", this.color);
    setColors(this.color.getColor());

    addModulator(this.magLFO).trigger();
  }


  double aggregateTime = 0;
  @Override
  public void run(double deltaMs) {
    aggregateTime += deltaMs;

//    float centerx = MathUtils.map(Noise.noise(centerParam, 100.0f), 0.0f, 1.0f, -0.1f, 1.1f);
    setColors(LXColor.hsb(
            (this.color.hue.getValue() + (aggregateTime/10))%360,
            this.color.saturation.getValue(),
            magLFO.getValue()
//      this.color.brightness.getValue()
    ));

//    for (LXPoint p : model.points){
//      inc++;
//      colors[p.index] |=
//      ((inc << inc));
//      colors[p.index] |=
//              (( (inc << (int)(bitmod*20) >> 5)) << 8);
//      colors[p.index] |=
//      ( (inc << (int)(bitmod*1.3) + 7)) << 16;
//    }
  }
}
