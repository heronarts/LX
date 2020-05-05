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

import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.BoundedParameter;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

public abstract class LXDatagram {

  protected static class ErrorState {
    // Destination address
    final String destination;

    // Number of failures sending to this datagram address
    int failureCount = 0;

    // Timestamp to re-try sending to this address again after
    long sendAfter = 0;

    private ErrorState(String destination) {
      this.destination = destination;
    }
  }

  private static final Map<String, ErrorState> _datagramErrorState =
    new HashMap<String, ErrorState>();

  private static ErrorState getDatagramErrorState(LXDatagram datagram) {
    String destination = datagram.getAddress() + ":" + datagram.getPort();
    ErrorState datagramErrorState = _datagramErrorState.get(destination);
    if (datagramErrorState == null) {
      _datagramErrorState.put(destination, datagramErrorState = new ErrorState(destination));
    }
    return datagramErrorState;
  }

  protected final byte[] buffer;

  ErrorState errorState = null;

  final DatagramPacket packet;

  /**
   * Whether this datagram is active
   */
  public final BooleanParameter enabled =
    new BooleanParameter("Enabled", true)
    .setDescription("Whether this datagram is active");

  /**
   * Whether this datagram is in an error state
   */
  public final BooleanParameter error =
    new BooleanParameter("Error", false)
    .setDescription("Whether there have been errors sending to this datagram address");

  /**
   * Brightness of the datagram
   */
  public final BoundedParameter brightness =
    new BoundedParameter("Brightness", 1)
    .setDescription("Level of the output");

  protected LXDatagram(int datagramSize) {
    this.buffer = new byte[datagramSize];
    for (int i = 0; i < datagramSize; ++i) {
      this.buffer[i] = 0;
    }
    this.packet = new DatagramPacket(this.buffer, datagramSize);
  }

  protected ErrorState getErrorState() {
    if (this.errorState != null) {
      return this.errorState;
    }
    return this.errorState = getDatagramErrorState(this);
  }

  /**
   * Sets the destination address of this datagram
   *
   * @param address Destination address
   * @return this
   */
  public LXDatagram setAddress(InetAddress address) {
    this.errorState = null;
    this.packet.setAddress(address);
    return this;
  }

  /**
   * Gets the address this datagram sends to
   *
   * @return Destination address
   */
  public InetAddress getAddress() {
    return this.packet.getAddress();
  }

  /**
   * Sets the destination port number to send this datagram to
   *
   * @param port Port number
   * @return this
   */
  public LXDatagram setPort(int port) {
    this.errorState = null;
    this.packet.setPort(port);
    return this;
  }

  /**
   * Gets the destination port number this datagram is sent to
   *
   * @return Destination port number
   */
  public int getPort() {
    return this.packet.getPort();
  }

  /**
   * Invoked by engine to send this packet when new color data is available. The
   * LXDatagram should update the packet object accordingly to contain the
   * appropriate buffer.
   *
   * @param colors Color buffer
   * @param glut Look-up table with gamma-adjusted brightness values
   */
  public abstract void onSend(int[] colors, byte[] glut);

  /**
   * Invoked when the datagram is no longer needed. Typically a no-op, but subclasses
   * may override if cleanup work is necessary.
   */
  public void dispose() {}
}
