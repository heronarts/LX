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
import heronarts.lx.LXUtils;
import heronarts.lx.color.LXColor;
import heronarts.lx.modulator.TriangleLFO;

public class BouncingPattern extends LXPattern {

  private final int BOUNCER_WIDTH = 2;
  private final int NUM_BOUNCERS;

  private final double[] mags;
  private final double[] speeds;
  private final double[] accum;

  private final TriangleLFO[] magLFO;

  public BouncingPattern(LX lx) {
    super(lx);
    this.NUM_BOUNCERS = lx.width / 2;
    this.mags = new double[this.NUM_BOUNCERS];
    this.speeds = new double[this.NUM_BOUNCERS];
    this.accum = new double[this.NUM_BOUNCERS];
    this.magLFO = new TriangleLFO[this.NUM_BOUNCERS];

    for (int i = 0; i < this.NUM_BOUNCERS; ++i) {
      this.mags[i] = LXUtils.random(2, 7);
      this.speeds[i] = LXUtils.random(300, 400);
      this.accum[i] = LXUtils.random(0, 5000);
      this.magLFO[i] = new TriangleLFO(2, 8, 8000 + i * 1000);
      addModulator(this.magLFO[i].setValue(this.mags[i])).trigger();
    }
  }

  @Override
  protected void run(double deltaMs) {
    for (int i = 0; i < this.NUM_BOUNCERS; ++i) {
      this.mags[i] = this.magLFO[i].getValue();
      this.accum[i] += deltaMs / this.speeds[i];
      double v = this.mags[i] * Math.abs(Math.sin(this.accum[i]));
      double h = ((int) (this.accum[i] * 20)) % 360;
      for (int j = 0; j < lx.height; ++j) {
        for (int x = 0; x < this.BOUNCER_WIDTH; ++x) {
          setColor(
              this.BOUNCER_WIDTH * i + x,
              lx.height - 1 - j,
              LXColor.hsb(h, 100., Math.max(0, 100 - 40 * (Math.abs(v - j)))));
        }
      }
    }
  }

}
