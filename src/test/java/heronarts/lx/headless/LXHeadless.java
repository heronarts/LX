/**
 * Copyright 2017- Mark C. Slee, Heron Arts LLC
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
 */

package heronarts.lx.headless;

import java.io.File;
import java.net.InetAddress;

import heronarts.lx.LX;
import heronarts.lx.model.GridModel;
import heronarts.lx.model.LXModel;
import heronarts.lx.output.ArtNetDatagram;
import heronarts.lx.output.FadecandySocket;
import heronarts.lx.output.OPCSocket;
import heronarts.lx.pattern.LXPattern;

/**
 * Example headless CLI for the LX engine. Just write a bit of scaffolding code
 * to load your model, define your outputs, then we're off to the races.
 */
public class LXHeadless {

  public static LXModel buildModel() {
    // NOTE: you can implement code that loads and builds your model here
    return new GridModel(30, 30);
  }

  public static void addArtNetOutput(LX lx) throws Exception {
    lx.engine.addOutput(
      new ArtNetDatagram(lx, lx.getModel(), 512, 0)
      .setAddress(InetAddress.getByName("localhost"))
    );
  }

  public static void addFadeCandyOutput(LX lx) throws Exception {
    lx.engine.addOutput(new FadecandySocket(lx, lx.getModel()).setAddress(InetAddress.getByName("localhost")).setPort(9090));
  }

  public static void addOPCOutput(LX lx) throws Exception {
    lx.engine.addOutput(new OPCSocket(lx).setAddress(InetAddress.getByName("localhost")).setPort(7890));
  }

  public static void main(String[] args) {
    try {
      LXModel model = buildModel();
      LX lx = new LX(model);

      // NOTE: add your own output code here
      // addArtNetOutput(lx);
      // addFadecandyOutput(lx);
      addOPCOutput(lx);

      // On the CLI you may specify an argument with an .lxp file
      if (args.length > 0) {
        lx.openProject(new File(args[0]));
      } else {
        lx.setPatterns(new LXPattern[] {
          new ExamplePattern(lx)
        });
      }

      lx.engine.start();
    } catch (Exception x) {
      LX.error(x);
    }
  }
}
