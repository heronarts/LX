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

import java.net.UnknownHostException;
import heronarts.lx.LX;
import heronarts.lx.output.ArtNetDatagram;
import heronarts.lx.output.DDPDatagram;
import heronarts.lx.output.KinetDatagram;
import heronarts.lx.output.LXBufferDatagram;
import heronarts.lx.output.LXDatagram;
import heronarts.lx.output.LXOutput;
import heronarts.lx.output.OPCDatagram;
import heronarts.lx.output.StreamingACNDatagram;
import heronarts.lx.parameter.LXParameter;

/**
 * Class that represents a basic fixture with a fixed number of points,
 * no hierarchy, and that is addressed with a single datagram packet.
 */
public abstract class LXBasicFixture extends LXProtocolFixture {

  private LXBufferDatagram datagram = null;

  protected LXBasicFixture(LX lx, String label) {
    super(lx, label);
    addDatagramParameter("protocol", this.protocol);
    addDatagramParameter("reverse", this.reverse);
    addParameter("host", this.host);
    addParameter("artNetUniverse", this.artNetUniverse);
    addParameter("opcChannel", this.opcChannel);
    addParameter("ddpDataOffset", this.ddpDataOffset);
    addParameter("kinetPort", this.kinetPort);
  }

  /**
   * Accessor for the datagram that corresponds to this fixture
   *
   * @return Datagram that sends data for this fixture
   */
  public LXDatagram getDatagram() {
    return this.datagram;
  }

  @Override
  protected final void buildDatagrams() {
    this.datagram = buildDatagram();
    if (this.datagram != null) {
      addDatagram(this.datagram);
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

  protected LXBufferDatagram buildDatagram() {
    Protocol protocol = this.protocol.getEnum();
    if (protocol == Protocol.NONE) {
      return null;
    }

    LXBufferDatagram datagram;
    switch (protocol) {
    case ARTNET:
      datagram = new ArtNetDatagram(toDynamicIndexBuffer(), this.artNetUniverse.getValuei());
      break;
    case SACN:
      datagram = new StreamingACNDatagram(toDynamicIndexBuffer(), this.artNetUniverse.getValuei());
      break;
    case OPC:
      datagram = new OPCDatagram(toDynamicIndexBuffer(), (byte) this.opcChannel.getValuei());
      break;
    case DDP:
      datagram = new DDPDatagram(toDynamicIndexBuffer(), this.ddpDataOffset.getValuei());
      break;
    case KINET:
      datagram = new KinetDatagram(toDynamicIndexBuffer(), this.kinetPort.getValuei());
      break;
    default:
    case NONE:
      throw new IllegalStateException("Unhandled protocol type: " + protocol);
    }

    try {
      datagram.setAddress(this.host.getString());
    } catch (UnknownHostException uhx) {
      // TODO(mcslee): get an error up to the UI here...
      datagram.enabled.setValue(false);
      LXOutput.error(uhx, "Unknown host for fixture datagram: " + this.host.getString());
    }

    return datagram;
  }

  @Override
  public void onParameterChanged(LXParameter p) {
    super.onParameterChanged(p);
    if (this.datagram != null) {
      if (p == this.host) {
        try {
          this.datagram.setAddress(this.host.getString());
        } catch (UnknownHostException uhx) {
          this.datagram.enabled.setValue(false);
          // TODO(mcslee): get an error to the UI...
          LXOutput.error(uhx, "Unkown host for fixture datagram: " + this.host.getString());
        }
      } else if (p == this.artNetUniverse) {
        if (this.datagram instanceof ArtNetDatagram) {
          ((ArtNetDatagram) this.datagram).setUniverseNumber(this.artNetUniverse.getValuei());
        } else if (this.datagram instanceof StreamingACNDatagram) {
          ((StreamingACNDatagram) this.datagram).setUniverseNumber(this.artNetUniverse.getValuei());
        }
      } else if (p == this.ddpDataOffset) {
        if (this.datagram instanceof DDPDatagram) {
          ((DDPDatagram) this.datagram).setDataOffset(this.ddpDataOffset.getValuei());
        }
      } else if (p == this.opcChannel) {
        if (this.datagram instanceof OPCDatagram) {
          ((OPCDatagram) this.datagram).setChannel((byte) this.opcChannel.getValuei());
        }
      } else if (p == this.kinetPort) {
        if (this.datagram instanceof KinetDatagram) {
          ((KinetDatagram) this.datagram).setKinetPort((byte) this.kinetPort.getValuei());
        }
      }
    }
  }
}
