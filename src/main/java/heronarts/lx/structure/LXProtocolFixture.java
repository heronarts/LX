/**
 * Copyright 2018- Mark C. Slee, Heron Arts LLC
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

package heronarts.lx.structure;

import java.net.InetAddress;
import java.net.UnknownHostException;

import heronarts.lx.LX;
import heronarts.lx.output.LXOutput;
import heronarts.lx.output.OPCOutput;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.StringParameter;

/**
 * Utility base class that adds a bunch of parameters to a fixture
 * object for selection of the most common protocols. Subclasses
 * are responsible for actually adding the parameters they care
 * about and registering them in the proper fashion.
 */
public abstract class LXProtocolFixture extends LXFixture {

  public enum Transport {
    UDP,
    TCP;
  }

  public final EnumParameter<Protocol> protocol =
    new EnumParameter<Protocol>("Protocol", Protocol.NONE)
    .setDescription("Which lighting data protocol this fixture uses");

  public final EnumParameter<Transport> transport =
    new EnumParameter<Transport>("Transport", Transport.UDP)
    .setDescription("Which transport the protocol should use");

  public final StringParameter host =
    new StringParameter("Host", "127.0.0.1")
    .setDescription("Host/IP this fixture transmits to");

  public final DiscreteParameter port =
    new DiscreteParameter("Port", OPCOutput.DEFAULT_PORT, 0, 65536)
    .setDescription("Port number this fixture transmits to");

  public final BooleanParameter unknownHost =
    new BooleanParameter("Unknown Host", false);

  public final DiscreteParameter artNetUniverse = (DiscreteParameter)
    new DiscreteParameter("ArtNet Universe", 0, 0, 32768)
    .setUnits(LXParameter.Units.INTEGER)
    .setDescription("Which ArtNet universe is used");

  public final DiscreteParameter opcChannel = (DiscreteParameter)
    new DiscreteParameter("OPC Channel", 0, 0, 256)
    .setUnits(LXParameter.Units.INTEGER)
    .setDescription("Which OPC channel is used");

  public final DiscreteParameter ddpDataOffset = (DiscreteParameter)
    new DiscreteParameter("DDP Offset", 0, 0, 65536)
    .setUnits(LXParameter.Units.INTEGER)
    .setDescription("The DDP data offset for this packet");

  public final DiscreteParameter kinetPort = (DiscreteParameter)
    new DiscreteParameter("KiNET Port", 1, 0, 256)
    .setUnits(LXParameter.Units.INTEGER)
    .setDescription("Which KiNET physical output port is used");

  public final BooleanParameter reverse =
    new BooleanParameter("Reverse", false)
    .setDescription("Whether the output wiring of this fixture is reversed");

  protected LXProtocolFixture(LX lx, String label) {
    super(lx, label);
  }

  protected InetAddress resolveHostAddress() {
    try {
      InetAddress address = InetAddress.getByName(this.host.getString());
      this.unknownHost.setValue(false);
      return address;
    } catch (UnknownHostException uhx) {
      LXOutput.error("Unknown host for fixture datagram: " + uhx.getLocalizedMessage());
      this.unknownHost.setValue(true);
    }
    return null;
  }

  protected int getProtocolChannel() {
    switch (this.protocol.getEnum()) {
    case ARTNET:
    case SACN:
      return this.artNetUniverse.getValuei();
    case DDP:
      return this.ddpDataOffset.getValuei();
    case KINET:
      return this.kinetPort.getValuei();
    case OPC:
      return this.opcChannel.getValuei();
    case NONE:
    default:
      return 0;
    }
  }
}
