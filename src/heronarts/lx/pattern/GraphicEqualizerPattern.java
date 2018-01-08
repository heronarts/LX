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
import heronarts.lx.audio.GraphicMeter;

public class GraphicEqualizerPattern extends LXPattern {

  private final GraphicMeter eq;

  public GraphicEqualizerPattern(LX lx) {
    super(lx);
    addModulator(this.eq = new GraphicMeter(lx.engine.audio.getInput())).start();
  }

  @Override
  public void run(double deltaMs) {
    for (int i = 0; i < this.lx.width; ++i) {
      int avgIndex = (int) (i / (double) this.lx.width * (eq.numBands - 1));
      double value = eq.getBand(avgIndex);
      for (int j = 0; j < this.lx.height; ++j) {
        double jscaled = (this.lx.height - 1 - j)
            / (double) (this.lx.height - 1);
        double b = LXUtils.constrain(400. * (value - jscaled), 0, 100);
        this.setColor(i, j, palette.getColor(b));
      }
    }
  }

}