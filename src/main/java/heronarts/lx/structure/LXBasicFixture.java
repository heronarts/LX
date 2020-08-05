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
import heronarts.lx.LX;
import heronarts.lx.output.ArtNetDatagram;
import heronarts.lx.output.DDPDatagram;
import heronarts.lx.output.KinetDatagram;
import heronarts.lx.output.LXOutput;
import heronarts.lx.output.OPCDatagram;
import heronarts.lx.output.OPCOutput;
import heronarts.lx.output.OPCSocket;
import heronarts.lx.output.StreamingACNDatagram;
import heronarts.lx.parameter.LXParameter;

/**
 * Class that represents a basic fixture with a fixed number of points,
 * no hierarchy, and that is addressed with a single datagram packet.
 */
public abstract class LXBasicFixture extends LXProtocolFixture {

  private LXOutput output = null;

  protected LXBasicFixture(LX lx, String label) {
    super(lx, label);
    addOutputParameter("protocol", this.protocol);
    addOutputParameter("transport", this.transport);
    addOutputParameter("reverse", this.reverse);
    addParameter("host", this.host);
    addParameter("port", this.port);
    addParameter("artNetUniverse", this.artNetUniverse);
    addParameter("opcChannel", this.opcChannel);
    addParameter("ddpDataOffset", this.ddpDataOffset);
    addParameter("kinetPort", this.kinetPort);
  }

  /**
   * Accessor for the output that corresponds to this fixture
   *
   * @return Output that sends data for this fixture
   */
  public LXOutput getOutput() {
    return this.output;
  }

  @Override
  protected void buildOutputs() {
    this.output = buildOutput();
    if (this.output != null) {
      addOutput(this.output);
    }
  }

  @Override
  protected int[] toDynamicIndexBuffer() {
    if (this.reverse.isOn()) {
      return super.toDynamicIndexBuffer(size() - 1, size(), -1);
    } else {
      return super.toDynamicIndexBuffer();
    }
  }

  protected LXOutput buildOutput() {
    Protocol protocol = this.protocol.getEnum();
    if (protocol == Protocol.NONE) {
      return null;
    }

    InetAddress address = resolveHostAddress();
    if (address == null) {
      return null;
    }

    LXOutput output;
    switch (protocol) {
    case ARTNET:
      output = new ArtNetDatagram(this.lx, toDynamicIndexBuffer(), this.artNetUniverse.getValuei());
      break;
    case SACN:
      output = new StreamingACNDatagram(this.lx, toDynamicIndexBuffer(), this.artNetUniverse.getValuei());
      break;
    case OPC:
      switch (this.transport.getEnum()) {
      case TCP:
        output = new OPCSocket(this.lx, toDynamicIndexBuffer(), (byte) this.opcChannel.getValuei());
        break;
      default:
      case UDP:
        output = new OPCDatagram(this.lx, toDynamicIndexBuffer(), (byte) this.opcChannel.getValuei());
        break;
      }
      ((OPCOutput) output).setPort(this.port.getValuei());
      break;
    case DDP:
      output = new DDPDatagram(this.lx, toDynamicIndexBuffer(), this.ddpDataOffset.getValuei());
      break;
    case KINET:
      output = new KinetDatagram(this.lx, toDynamicIndexBuffer(), this.kinetPort.getValuei());
      break;
    default:
    case NONE:
      throw new IllegalStateException("Unhandled LXBasicFixture protocol type: " + protocol);
    }

    if (output instanceof LXOutput.InetOutput) {
      ((LXOutput.InetOutput) output).setAddress(address);
    }

    return output;
  }

  @Override
  public void onParameterChanged(LXParameter p) {
    super.onParameterChanged(p);
    if (this.output != null) {
      if (p == this.host) {
        InetAddress address = resolveHostAddress();
        if (this.output instanceof LXOutput.InetOutput) {
          this.output.enabled.setValue(address != null);
          if (address != null) {
            ((LXOutput.InetOutput) this.output).setAddress(address);
          }
        }
      } else if (p == this.port) {
        if (this.output instanceof OPCOutput) {
          ((OPCOutput) this.output).setPort(this.port.getValuei());
        }
      } else if (p == this.artNetUniverse) {
        if (this.output instanceof ArtNetDatagram) {
          ((ArtNetDatagram) this.output).setUniverseNumber(this.artNetUniverse.getValuei());
        } else if (this.output instanceof StreamingACNDatagram) {
          ((StreamingACNDatagram) this.output).setUniverseNumber(this.artNetUniverse.getValuei());
        }
      } else if (p == this.ddpDataOffset) {
        if (this.output instanceof DDPDatagram) {
          ((DDPDatagram) this.output).setDataOffset(this.ddpDataOffset.getValuei());
        }
      } else if (p == this.opcChannel) {
        if (this.output instanceof OPCOutput) {
          ((OPCOutput) this.output).setChannel((byte) this.opcChannel.getValuei());
        }
      } else if (p == this.kinetPort) {
        if (this.output instanceof KinetDatagram) {
          ((KinetDatagram) this.output).setKinetPort((byte) this.kinetPort.getValuei());
        }
      }
    }
  }
}
