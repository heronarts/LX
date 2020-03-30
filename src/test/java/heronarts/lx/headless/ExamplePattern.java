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

package heronarts.lx.headless;

import heronarts.lx.LX;
import heronarts.lx.model.LXPoint;
import heronarts.lx.modulator.LXModulator;
import heronarts.lx.modulator.SawLFO;
import heronarts.lx.modulator.SinLFO;
import heronarts.lx.pattern.LXPattern;

public class ExamplePattern extends LXPattern {

   private final LXModulator hue = startModulator(new SawLFO(0, 360, 9000));
   private final LXModulator brightness = startModulator(new SinLFO(10, 100, 4000));
   private final LXModulator yPos = startModulator(new SinLFO(0, 1, 5000));
   private final LXModulator width = startModulator(new SinLFO(.4, 1, 3000));

   public ExamplePattern(LX lx) {
     super(lx);
   }

   @Override
   public void run(double deltaMs) {
     float hue = this.hue.getValuef();
     float brightness = this.brightness.getValuef();
     float yPos = this.yPos.getValuef();
     float falloff = 100 / (this.width.getValuef());
     for (LXPoint p : model.points) {
       colors[p.index] = LX.hsb(hue, 100, Math.max(0, brightness - falloff * Math.abs(p.yn - yPos)));
     }
   }
 }

