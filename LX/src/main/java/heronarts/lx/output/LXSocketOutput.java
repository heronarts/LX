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
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;

import heronarts.lx.LX;

public abstract class LXSocketOutput extends LXOutput {

  public final String host;
  public final int port;

  protected Socket socket;
  protected OutputStream output;

  protected LXSocketOutput(LX lx, String host, int port) {
    super(lx);
    this.host = host;
    this.port = port;
  }

  public boolean isConnected() {
    return (this.socket != null);
  }

  private void connect() {
    if (this.socket == null) {
      try {
        this.socket = new Socket();
        this.socket.connect(new InetSocketAddress(this.host, this.port), 100);
        this.socket.setTcpNoDelay(true);
        this.output = this.socket.getOutputStream();
        didConnect();
      } catch (ConnectException cx) {
        dispose(cx);
      } catch (IOException iox) {
        dispose(iox);
      }
    }
  }

  protected void didConnect() {

  }

  protected void dispose(Exception x) {
    this.socket = null;
    this.output = null;
    didDispose(x);
  }

  protected void didDispose(Exception x) {
  }

  @Override
  protected void onSend(int[] colors, byte[] glut) {
    connect();
    if (isConnected()) {
      try {
        this.output.write(getPacketData(colors, glut));
      } catch (IOException iox) {
        dispose(iox);
      }
    }
  }

  protected abstract byte[] getPacketData(int[] colors, byte[] glut);

}
