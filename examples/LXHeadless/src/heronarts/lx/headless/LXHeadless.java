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
import heronarts.lx.model.LXFixture;
import heronarts.lx.model.LXModel;
import heronarts.lx.output.*;

/**
 * Example headless CLI for the LX engine. Just write a bit of scaffolding code
 * to load your model, define your outputs, then we're off to the races.
 */
public class LXHeadless {

  public static LXModel buildModel() {
    // TODO: implement code that loads and builds your model here
    return new GridModel(5, 30);
  }

  public static void addArtNetOutput(LX lx) throws Exception {
    lx.engine.addOutput(
      new LXDatagramOutput(lx).addDatagram(
        new ArtNetDatagram(lx.model, 512, 0)
        .setAddress("localhost")
          )
  );
  }

  public static void addFadeCandyOutput(LX lx) throws Exception {
    lx.engine.addOutput(new FadecandyOutput(lx, "localhost", 9090, lx.model));
  }

  public static void addOPCOutput(LX lx) throws Exception {
    lx.engine.addOutput(new OPCOutput(lx, "localhost", 7890));
  }

  public static void addTenereOutput(LX lx, String ip) throws Exception {
    lx.engine.addOutput(
            new LXDatagramOutput(lx).addDatagram(
                    new TenereDatagram(lx, LXFixture.Utils.getIndices(lx.model.fixtures.get(0)), (byte) 0x00).setAddress(ip).setPort(1337))
                    .addDatagram(
                            new TenereDatagram(lx, LXFixture.Utils.getIndices(lx.model.fixtures.get(0)), (byte) 0x04).setAddress(ip).setPort(1337))
    );
  }


  public static void addDatagramOPCOutput(LX lx, String ip_addr) throws Exception {
    lx.engine.addOutput(
            new LXDatagramOutput(lx).addDatagram(
                    new OPCDatagram(lx.model)
                            .setAddress(ip_addr)
                            .setPort(7890)

            )
    );
  }

  public static void main(String[] args) {
    try {
      LXModel model = buildModel();
      LX lx = new LX(model);

      String[] shlomo_controller_ips = { "10.200.1.98" };
      for (String shlomo_controller_ip : shlomo_controller_ips){
        addTenereOutput(lx, shlomo_controller_ip);
      }
//       target some OPC servers
      String[] controller_ips = {
//              "10.200.1.102",
//              "10.200.1.142",
//              "10.200.1.141",
//              "10.200.1.98"
      };

      for (String controller_ipaddr : controller_ips){
        addDatagramOPCOutput(lx, controller_ipaddr);
      }

      // TODO: add your own output code here
//       addArtNetOutput(lx);
//       addFadecandyOutput(lx);
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
      System.err.println(x.getLocalizedMessage());
    }
  }
}
