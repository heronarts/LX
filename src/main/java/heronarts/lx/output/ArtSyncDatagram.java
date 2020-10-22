/**
 * Copyright 2016- Mark C. Slee, Heron Arts LLC
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

package heronarts.lx.output;

import heronarts.lx.LX;

public class ArtSyncDatagram extends LXDatagram {

  private final static int ARTSYNC_HEADER_LENGTH = 14;

  public ArtSyncDatagram(LX lx) {
    super(lx, new int[0], ARTSYNC_HEADER_LENGTH);
    setPort(ArtNetDatagram.ARTNET_PORT);

    this.buffer[0] = 'A';
    this.buffer[1] = 'r';
    this.buffer[2] = 't';
    this.buffer[3] = '-';
    this.buffer[4] = 'N';
    this.buffer[5] = 'e';
    this.buffer[6] = 't';
    this.buffer[7] = 0;
    this.buffer[8] = 0x00; // OpSync low byte
    this.buffer[9] = 0x52; // OpSync hi byte
    this.buffer[10] = 0; // Protocol version
    this.buffer[11] = 14; // Protocol version
    this.buffer[12] = 0; // Aux1
    this.buffer[13] = 0; // Aux2
  }

  @Override
  protected int getDataBufferOffset() {
    return 0;
  }

}
