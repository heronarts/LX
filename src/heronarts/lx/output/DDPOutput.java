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

package heronarts.lx.output;

import java.net.SocketException;
import java.util.Arrays;
import heronarts.lx.LX;
import heronarts.lx.model.LXFixture;

/**
 * DDPOutput is a helper class that constructs and sends a set of DDPDatagram packets
 * based upon a specified chunk size, which will typically be a function of either
 * the LED layout or UDP network settings.
 *
 * If greater customization over framing and chunking is required, it may be preferable
 * to construct DDPDatagram packets manually.
 */
public class DDPOutput extends LXDatagramOutput {

  public static final int DEFAULT_CHUNK_SIZE = 400;

  public DDPOutput(LX lx, LXFixture fixture) throws SocketException {
    this(lx, LXFixture.Utils.getIndices(fixture));
  }

  public DDPOutput(LX lx, LXFixture fixture, int chunkSize) throws SocketException {
    this(lx, LXFixture.Utils.getIndices(fixture), chunkSize);
  }

  public DDPOutput(LX lx, int[] pointIndices) throws SocketException {
    this(lx, pointIndices, DEFAULT_CHUNK_SIZE);
  }

  /**
   * Constructs a DDPOutput with a given total set of points and a specified chunkSize.
   * By default, the DDP Push flag is only set on the final packet.
   *
   * @param lx LX instance
   * @param pointIndices All of the points to send
   * @param chunkSize Number of points to chunk per packet
   * @throws SocketException if a DatagramSocket coul dnot be created
   */
  public DDPOutput(LX lx, int[] pointIndices, int chunkSize) throws SocketException {
    super(lx);
    int total = pointIndices.length;
    int start = 0;
    while (start < total) {
      int end = Math.min(start + chunkSize, total);
      int[] chunk = Arrays.copyOfRange(pointIndices, start, end);
      DDPDatagram datagram = new DDPDatagram(chunk);
      datagram.setDataOffset(start);
      datagram.setPushFlag(end == total);
      addDatagram(datagram);
      start = end;
    }
  }

  /**
   * Configures whether the push flag is set for all individual DDP datagram packets, or whether
   * it is only set on the final DDP packet. Note that setting the push flag for all packets may
   * result in some visual tearing if chunk size does not fall across wiring boundaries.
   *
   * @param pushAll Whether to set the push flag on all packets
   * @return this
   */
  public DDPOutput setPushAll(boolean pushAll) {
    int i = 0;
    int len = this.datagrams.size();
    for (LXDatagram datagram : this.datagrams) {
      ((DDPDatagram) datagram).setPushFlag(pushAll | (i == len-1));
      ++i;
    }
    return this;
  }

  @Override
  public LXDatagramOutput addDatagram(LXDatagram datagram) {
    if (!(datagram instanceof DDPDatagram)) {
      throw new IllegalArgumentException("May not add non-DDPDatagram to DDPOutput");
    }
    return super.addDatagram(datagram);
  }

}
