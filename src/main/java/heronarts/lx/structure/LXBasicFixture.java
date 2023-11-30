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

/**
 * Class that represents a basic fixture with a fixed number of points,
 * no hierarchy, and that is addressed with a single output definition
 */
public abstract class LXBasicFixture extends LXProtocolFixture {

  protected LXBasicFixture(LX lx, String label) {
    super(lx, label);
    addOutputParameter("protocol", this.protocol);
    addOutputParameter("byteOrder", this.byteOrder);
    addOutputParameter("transport", this.transport);
    addOutputParameter("reverse", this.reverse);
    addOutputParameter("host", this.host);
    addOutputParameter("port", this.port);
    addOutputParameter("dmxChannel", this.dmxChannel);
    addOutputParameter("artNetUniverse", this.artNetUniverse);
    addOutputParameter("artNetSequenceEnabled", this.artNetSequenceEnabled);
    addOutputParameter("sacnPriority", this.sacnPriority);
    addOutputParameter("opcChannel", this.opcChannel);
    addOutputParameter("opcOffset", this.opcOffset);
    addOutputParameter("ddpDataOffset", this.ddpDataOffset);
    addOutputParameter("kinetPort", this.kinetPort);
    addOutputParameter("kinetVersion", this.kinetVersion);
  }

  @Override
  protected void buildOutputs() {
    Protocol protocol = this.protocol.getEnum();
    if (protocol == Protocol.NONE) {
      return;
    }

    InetAddress address = resolveHostAddress();
    if (address == null) {
      return;
    }

    addOutputDefinition(new OutputDefinition(
      protocol,
      getProtocolTransport(),
      address,
      getProtocolPort(),
      getProtocolUniverse(),
      getProtocolChannel(),
      getProtocolPriority(),
      getProtocolSequenceEnabled(),
      getProtocolKinetVersion(),
      OutputDefinition.FPS_UNSPECIFIED,
      buildSegment()
    ));
  }

  protected Segment buildSegment() {
    return new Segment(0, size(), DEFAULT_OUTPUT_STRIDE, DEFAULT_OUTPUT_REPEAT, this.reverse.isOn(), this.byteOrder.getEnum());
  }

}
