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

import com.google.gson.JsonObject;

import heronarts.lx.LX;
import heronarts.lx.output.ArtNetDatagram;
import heronarts.lx.output.DDPDatagram;
import heronarts.lx.output.KinetDatagram;
import heronarts.lx.output.LXBufferOutput;
import heronarts.lx.output.LXOutput;
import heronarts.lx.output.OPCOutput;
import heronarts.lx.output.StreamingACNDatagram;
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

  public final EnumParameter<Protocol> protocol =
    new EnumParameter<Protocol>("Protocol", Protocol.NONE)
    .setDescription("Which lighting data protocol this fixture uses");

  public final EnumParameter<Transport> transport =
    new EnumParameter<Transport>("Transport", Transport.UDP)
    .setDescription("Which transport the protocol should use");

  public final EnumParameter<LXBufferOutput.ByteOrder> byteOrder =
    new EnumParameter<LXBufferOutput.ByteOrder>("Byte Order", LXBufferOutput.ByteOrder.RGB)
    .setDescription("Which byte ordering the output uses");

  public final StringParameter host =
    new StringParameter("Host", "127.0.0.1")
    .setDescription("Host/IP this fixture transmits to");

  public final BooleanParameter unknownHost =
    new BooleanParameter("Unknown Host", false);

  public final DiscreteParameter port =
    new DiscreteParameter("Port", OPCOutput.DEFAULT_PORT, 0, 65536)
    .setDescription("Port number this fixture transmits to");

  public final DiscreteParameter dmxChannel =
    new DiscreteParameter("DMX Channel", 0, 512)
    .setUnits(LXParameter.Units.INTEGER)
    .setDescription("Starting DMX data channel offset for ArtNet/SACN/Kinet");

  public final DiscreteParameter artNetUniverse =
    new DiscreteParameter("ArtNet Universe", 0, 0, ArtNetDatagram.MAX_UNIVERSE)
    .setUnits(LXParameter.Units.INTEGER)
    .setDescription("Which ArtNet universe is used");

  public final BooleanParameter artNetSequenceEnabled =
    new BooleanParameter("ArtNet Sequence", false)
    .setDescription("Whether ArtNet sequence numbers are used");

  public final DiscreteParameter sacnPriority =
    new DiscreteParameter("sACN Priority", StreamingACNDatagram.DEFAULT_PRIORITY, 0, StreamingACNDatagram.MAX_PRIORITY+1)
    .setDescription("sACN Priority Value (0-" + StreamingACNDatagram.MAX_PRIORITY + ")");

  public final DiscreteParameter opcChannel =
    new DiscreteParameter("OPC Channel", 0, 0, 256)
    .setUnits(LXParameter.Units.INTEGER)
    .setDescription("Which OPC channel is used");

  public final DiscreteParameter opcOffset =
    new DiscreteParameter("OPC Offset", 0, 0, OPCOutput.MAX_DATA_LENGTH)
    .setUnits(LXParameter.Units.INTEGER)
    .setDescription("The OPC data offset for this fixture");

  public final DiscreteParameter ddpDataOffset =
    new DiscreteParameter("DDP Offset", 0, 0, DDPDatagram.MAX_DATA_LENGTH)
    .setUnits(LXParameter.Units.INTEGER)
    .setDescription("The DDP data offset for this fixture packet");

  public final DiscreteParameter kinetPort =
    new DiscreteParameter("KiNET Port", 1, 0, 256)
    .setUnits(LXParameter.Units.INTEGER)
    .setDescription("Which KiNET physical output port is used");

  public final EnumParameter<KinetDatagram.Version> kinetVersion =
    new EnumParameter<KinetDatagram.Version>("KiNET Version", KinetDatagram.Version.PORTOUT)
    .setDescription("Which KiNET version is used");

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

  protected Transport getProtocolTransport() {
    switch (this.protocol.getEnum()) {
    case OPC:
      return this.transport.getEnum();
    default:
      return Transport.UDP;
    }
  }

  protected int getProtocolPort() {
    Protocol protocol = this.protocol.getEnum();
    switch (protocol) {
    case OPC:
      return this.port.getValuei();
    case ARTNET:
    case SACN:
    case DDP:
    case KINET:
    case NONE:
    default:
      return protocol.defaultPort;
    }
  }

  protected int getProtocolUniverse() {
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

  protected int getProtocolChannel() {
    switch (this.protocol.getEnum()) {
    case ARTNET:
    case SACN:
    case KINET:
      return this.dmxChannel.getValuei();
    case OPC:
      return this.opcOffset.getValuei();
    case DDP:
      // DDP packets are always sent individually with data offset
      return 0;
    case NONE:
    default:
      return 0;
    }
  }

  protected boolean getProtocolSequenceEnabled() {
    switch (this.protocol.getEnum()) {
    case ARTNET:
      return this.artNetSequenceEnabled.isOn();
    default:
      return false;
    }
  }

  protected KinetDatagram.Version getProtocolKinetVersion() {
    switch (this.protocol.getEnum()) {
    case KINET:
      return this.kinetVersion.getEnum();
    default:
      return KinetDatagram.Version.PORTOUT;
    }
  }

  protected int getProtocolPriority() {
    switch (this.protocol.getEnum()) {
    case SACN:
      return this.sacnPriority.getValuei();
    default:
      return 0;
    }
  }

  @Override
  protected void addLXFOutputs(JsonObject obj) {
    JsonObject output = new JsonObject();
    output.addProperty(JsonFixture.KEY_ENABLED, this.enabled.isOn());
    output.addProperty(JsonFixture.KEY_HOST, this.host.getString());
    output.addProperty(JsonFixture.KEY_BYTE_ORDER, this.byteOrder.getEnum().name().toLowerCase());

    switch (this.protocol.getEnum()) {
    case ARTNET -> {
      output.addProperty(JsonFixture.KEY_PROTOCOL, "artnet");
      output.addProperty(JsonFixture.KEY_UNIVERSE, this.artNetUniverse.getValuei());
      output.addProperty(JsonFixture.KEY_CHANNEL, this.dmxChannel.getValuei());
      output.addProperty(JsonFixture.KEY_SEQUENCE_ENABLED, this.artNetSequenceEnabled.isOn());
    }
    case DDP -> {
      output.addProperty(JsonFixture.KEY_PROTOCOL, "ddp");
      output.addProperty(JsonFixture.KEY_OFFSET, this.ddpDataOffset.getValuei());

    }
    case KINET -> {
      output.addProperty(JsonFixture.KEY_PROTOCOL, "kinet");
      output.addProperty(JsonFixture.KEY_KINET_PORT, this.kinetPort.getValuei());
      output.addProperty(JsonFixture.KEY_CHANNEL, this.dmxChannel.getValuei());
      output.addProperty(JsonFixture.KEY_KINET_VERSION, this.kinetVersion.getEnum().name());
    }
    case OPC -> {
      output.addProperty(JsonFixture.KEY_PROTOCOL, "opc");
      output.addProperty(JsonFixture.KEY_CHANNEL, this.opcChannel.getValuei());
      output.addProperty(JsonFixture.KEY_OFFSET, this.opcOffset.getValuei());
      output.addProperty(JsonFixture.KEY_TRANSPORT, this.transport.getEnum().name().toLowerCase());
      output.addProperty(JsonFixture.KEY_PORT, this.port.getValuei());
    }
    case SACN -> {
      output.addProperty(JsonFixture.KEY_PROTOCOL, "sacn");
      output.addProperty(JsonFixture.KEY_UNIVERSE, this.artNetUniverse.getValuei());
      output.addProperty(JsonFixture.KEY_CHANNEL, this.dmxChannel.getValuei());
    }
    default -> output = null;
    case NONE -> output = null;
    }
    if (output != null) {
      output.addProperty(JsonFixture.KEY_REVERSE, this.reverse.isOn());
      obj.add(JsonFixture.KEY_OUTPUT, output);
    }
  }
}
