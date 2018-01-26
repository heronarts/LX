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
import heronarts.lx.LX;
import heronarts.lx.LXPattern;
import heronarts.lx.model.GridModel;
import heronarts.lx.model.LXModel;
import heronarts.lx.model.StripModel;
import heronarts.lx.output.*;
import heronarts.lx.pattern.*;

import static java.lang.Thread.sleep;

/**
 * Example headless CLI for the LX engine. Just write a bit of scaffolding code
 * to load your model, define your outputs, then we're off to the races.
 */
public class LXHeadless {

  public static LXModel buildModel() {
    // TODO: implement code that loads and builds your model here
    return new GridModel(30, 30);
  }

  public static void addArtNetOutput(LX lx) throws Exception {
    lx.engine.addOutput(
      new LXDatagramOutput(lx).addDatagram(
        new ArtNetDatagram(lx.model, 512, 0)
        .setAddress("localhost")
      )
    );
  }

//  public static void addOPCOutput(LX lx) throws Exception {
//    lx.engine.addOutput(
//            new OPCOutput(lx, "localhost", 7890).addDatagram(
//                    new OPCDatagram(lx.model)
//                            .setAddress("localhost")
//            )
//    );
//  }

  public static void addFadeCandyOutput(LX lx) throws Exception {
    lx.engine.addOutput(new FadecandyOutput(lx, "localhost", 9090, lx.model));
  }


//  private static void sleep(int millis) {
//    try {
//      Thread.sleep(millis);
//    }
//    catch (InterruptedException e) { /* pass */ };
//  }

  public static void main(String[] args) {
    try {
      LXModel model = buildModel();
      LX lx = new LX(model);

//      LX lx = new LX(new StripModel(600));
      String host = "localhost";

      int port = 7890;
      // TODO: add your own output code here
//       addArtNetOutput(lx);
//       addFadeCandyOutput(lx);
      lx.engine.addOutput(new OPCOutput(lx, "localhost", 7890));
//      lx.engine.addOutput(new UdpOPC(lx, host, port));
//
      // On the CLI you specify an argument with an .lxp file
      if (args.length > 0) {
        lx.openProject(new File(args[0]));
      }

      LXPattern[] patterns = new LXPattern[] {
              new SimpleDemoPattern(lx)
//              new BouncingPattern(lx)
      };

      lx.setPatterns(patterns);


//      lx.registerPattern(new LXPattern[]{
//               new BouncingPattern(lx)
//      });
//      System.out.println("starting threaded..");
//      lx.engine.setThreaded(true);

      lx.engine.start();
      lx.engine.onDraw();


    } catch (Exception x) {
      System.err.println(x.getLocalizedMessage());
    }
  }
}
