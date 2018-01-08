/**
 * Copyright 2013- Mark C. Slee, Heron Arts LLC
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

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * An output stage that functions by sending datagram packets.
 */
public class LXDatagramOutput extends LXOutput {

  private final DatagramSocket socket;

  private final List<LXDatagram> datagrams = new ArrayList<LXDatagram>();

  private final SimpleDateFormat date = new SimpleDateFormat("[HH:mm:ss]");

  public LXDatagramOutput(LX lx) throws SocketException {
    this(lx, new DatagramSocket());
  }

  public LXDatagramOutput(LX lx, DatagramSocket socket) {
    super(lx);
    this.socket = socket;
  }

  public LXDatagramOutput addDatagram(LXDatagram datagram) {
    this.datagrams.add(datagram);
    return this;
  }

  public LXDatagramOutput addDatagrams(LXDatagram[] datagrams) {
    for (LXDatagram datagram : datagrams) {
      addDatagram(datagram);
    }
    return this;
  }

  /**
   * Subclasses may override. Invoked before datagrams are sent.
   *
   * @param colors Color values
   */
  protected /* abstract */ void beforeSend(int[] colors) {}

  /**
   * Subclasses may override. Invoked after datagrams are sent.
   *
   * @param colors Color values
   */
  protected /* abstract */ void afterSend(int[] colors) {}

  /**
   * Core method which sends the datagrams.
   */
  @Override
  protected final void onSend(int[] colors) {
    long now = System.currentTimeMillis();
    beforeSend(colors);
    for (LXDatagram datagram : this.datagrams) {
      if (datagram.enabled.isOn() && (now > datagram.sendAfter)) {
        datagram.onSend(colors);
        try {
          this.socket.send(datagram.packet);
          if (datagram.failureCount > 0) {
            System.out.println(this.date.format(now) + " Recovered connectivity to " + datagram.packet.getAddress());
          }
          datagram.error.setValue(false);
          datagram.failureCount = 0;
          datagram.sendAfter = 0;
        } catch (IOException iox) {
          if (datagram.failureCount == 0) {
            System.out.println(this.date.format(now) + " IOException sending to "
                + datagram.packet.getAddress() + " (" + iox.getLocalizedMessage()
                + "), will initiate backoff after 3 consecutive failures");
          }
          ++datagram.failureCount;
          if (datagram.failureCount >= 3) {
            int pow = Math.min(5, datagram.failureCount - 3);
            long waitFor = (long) (50 * Math.pow(2, pow));
            System.out.println(this.date.format(now) + " Retrying " + datagram.packet.getAddress()
                + " in " + waitFor + "ms" + " (" + datagram.failureCount
                + " consecutive failures)");
            datagram.sendAfter = now + waitFor;
            datagram.error.setValue(true);
          }
        }
      }
    }
    afterSend(colors);
  }
}
