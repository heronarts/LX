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

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;

import heronarts.lx.LX;

public abstract class LXSocket extends LXBufferOutput implements LXOutput.InetOutput {

  public static final int DEFAULT_CONNECT_TIMEOUT_MS = 100;

  private InetAddress address = null;
  private int port = NO_PORT;
  private int connectTimeoutMs = DEFAULT_CONNECT_TIMEOUT_MS;


  protected Socket socket;
  protected OutputStream output;

  protected LXSocket(LX lx, int[] indexBuffer) {
    this(lx, indexBuffer, LXBufferOutput.ByteOrder.RGB);
  }

  protected LXSocket(LX lx, int[] indexBuffer, LXBufferOutput.ByteOrder byteOrder) {
    super(lx, indexBuffer, byteOrder);
  }

  public LXSocket setConnectTimeout(int connectTimeoutMs) {
    this.connectTimeoutMs = connectTimeoutMs;
    return this;
  }

  @Override
  public LXSocket setAddress(InetAddress address) {
    if (this.address != address) {
      disconnect(null);
      this.address = address;
    }
    return this;
  }

  @Override
  public InetAddress getAddress() {
    return this.address;
  }

  @Override
  public LXSocket setPort(int port) {
    if (this.port != port) {
      disconnect(null);
      this.port = port;
    }
    return this;
  }

  @Override
  public int getPort() {
    return this.port;
  }

  public boolean isConnected() {
    return (this.socket != null);
  }

  private void connect() {
    if (this.socket == null) {
      if (this.address != null && this.port != NO_PORT) {
        InetSocketAddress inetAddress = new InetSocketAddress(this.address, this.port);
        try {
          this.socket = new Socket();
          this.socket.connect(inetAddress, this.connectTimeoutMs);
          this.socket.setTcpNoDelay(true);
          this.output = this.socket.getOutputStream();
          didConnect();
        } catch (IOException iox) {
          LXOutput.error(getClass().getSimpleName() + " failed connecting to " + inetAddress + ": " + iox.getLocalizedMessage());
          disconnect(iox);
        }
      }
    }
  }

  /**
   * Subclasses may override to take additional actions upon successful connection
   */
  protected void didConnect() {

  }

  protected void disconnect(Exception x) {
    if (this.output != null) {
      try {
        this.output.close();
      } catch (Exception ignored) {
      } finally {
        this.output = null;
      }
    }
    if (this.socket != null) {
      try {
        this.socket.close();
      } catch (Exception ignored) {
      } finally {
        this.socket = null;
      }
    }
    didDisconnect(x);
  }

  /**
   * Subclasses may override to take additional actions upon disconnection
   *
   * @param x Exception that caused the disconnect if there was one
   */
  protected void didDisconnect(Exception x) {

  }

  @Override
  protected void onSend(int[] colors, byte[] glut) {
    connect();
    if (isConnected()) {
      try {
        this.output.write(getPacketData(colors, glut));
      } catch (IOException iox) {
        LXOutput.error(getClass().getSimpleName() + " exception writing to " + this.socket.getInetAddress() + ": " + iox.getLocalizedMessage());
        disconnect(iox);
      }
    }
  }

  protected byte[] getPacketData(int[] colors, byte[] glut) {
    updateDataBuffer(colors, glut);
    return getDataBuffer();
  }

}
