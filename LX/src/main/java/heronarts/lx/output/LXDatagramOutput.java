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
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * An output stage that functions by sending datagram packets.
 */
public class LXDatagramOutput extends LXOutput {

  private static DatagramSocket defaultSocket = null;

  private static DatagramSocket getDefaultSocket() throws SocketException {
    if (defaultSocket == null) {
      defaultSocket = new DatagramSocket();
    }
    return defaultSocket;
  }

  private final DatagramSocket socket;

  protected final List<LXDatagram> datagrams = new ArrayList<LXDatagram>();

  private final SimpleDateFormat date = new SimpleDateFormat("[HH:mm:ss]");

  public LXDatagramOutput(LX lx) throws SocketException {
    this(lx, getDefaultSocket());
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

  public LXDatagramOutput addDatagrams(List<LXDatagram> datagrams) {
    for (LXDatagram datagram : datagrams) {
      addDatagram(datagram);
    }
    return this;
  }

  /**
   * Sets the destination address of all datagrams on this output
   *
   * @param ipAddress IP address or hostname as string
   * @return this
   * @throws UnknownHostException Bad address
   */
  public LXDatagramOutput setAddress(String ipAddress) throws UnknownHostException {
    setAddress(InetAddress.getByName(ipAddress));
    return this;
  }

  /**
   * Sets the destination address of all datagrams on this output
   *
   * @param address Destination address
   * @return this
   */
  public LXDatagramOutput setAddress(InetAddress address) {
    for (LXDatagram datagram : this.datagrams) {
      datagram.setAddress(address);
    }
    return this;
  }

  /**
   * Sets the port number for all datagrams on this output
   *
   * @param port UDP port number
   * @return this
   */
  public LXDatagramOutput setPort(int port) {
    for (LXDatagram datagram : this.datagrams) {
      datagram.setPort(port);
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
  protected void onSend(int[] colors, double brightness) {
    long now = System.currentTimeMillis();
    beforeSend(colors);
    for (LXDatagram datagram : this.datagrams) {
      onSendDatagram(datagram, now, colors, brightness);
    }
    afterSend(colors);
  }

  protected void onSendDatagram(LXDatagram datagram, long nowMillis, int[] colors, double brightness) {
    if (!datagram.enabled.isOn()) {
      return;
    }

    LXDatagram.ErrorState datagramErrorState = datagram.getErrorState();
    if (datagramErrorState.sendAfter >= nowMillis) {
      // This datagram can't be sent now... mark its error state
      datagram.error.setValue(true);
      return;
    }

    byte[] glut = this.gammaLut[(int) Math.round(brightness * datagram.brightness.getValue() * 255.f)];
    datagram.onSend(colors, glut);
    try {
      this.socket.send(datagram.packet);
      if (datagramErrorState.failureCount > 0) {
        System.out.println(this.date.format(nowMillis) + " Recovered connectivity to " + datagramErrorState.destination);
      }
      // Sent fine! All good here...
      datagramErrorState.failureCount = 0;
      datagramErrorState.sendAfter = 0;
      datagram.error.setValue(false);
    } catch (IOException iox) {
      if (datagramErrorState.failureCount == 0) {
        System.err.println(this.date.format(nowMillis) + " IOException sending to "
            + datagramErrorState.destination + " (" + iox.getLocalizedMessage()
            + "), will initiate backoff after 3 consecutive failures");
      }
      ++datagramErrorState.failureCount;
      if (datagramErrorState.failureCount >= 3) {
        int pow = Math.min(5, datagramErrorState.failureCount - 3);
        long waitFor = (long) (50 * Math.pow(2, pow));
        System.err.println(this.date.format(nowMillis) + " Retrying " + datagramErrorState.destination
            + " in " + waitFor + "ms" + " (" + datagramErrorState.failureCount
            + " consecutive failures)");
        datagramErrorState.sendAfter = nowMillis + waitFor;
        datagram.error.setValue(true);
      }
    }
  }

  @Override
  protected void onSend(int[] colors, byte[] glut) {
    throw new UnsupportedOperationException("LXDatagramOutput does not implement onSend by glut");
  }
}
