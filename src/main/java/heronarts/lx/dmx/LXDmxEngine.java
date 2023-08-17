/**
 * Copyright 2017- Mark C. Slee, Heron Arts LLC
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

package heronarts.lx.dmx;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;

import com.google.gson.JsonObject;

import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import heronarts.lx.color.LXColor;
import heronarts.lx.output.ArtNetDatagram;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.LXParameter;

public class LXDmxEngine extends LXComponent {

  public final BooleanParameter artNetReceiveActive =
    new BooleanParameter("Art-Net Active", false)
    .setMappable(false)
    .setDescription("Enables or disables Art-Net DMX input");

  private ArtNetReceiver artNetReceiver = null;

  public static final int MAX_CHANNEL = 512;
  public static final int MAX_UNIVERSE = 512;

  private final byte[][] data =
    new byte[MAX_UNIVERSE][ArtNetDatagram.MAX_DATA_LENGTH];

  public LXDmxEngine(LX lx) {
    super(lx);
    addParameter("artNetReceiveActive", this.artNetReceiveActive);
  }

  @Override
  public void onParameterChanged(LXParameter p) {
    if (p == this.artNetReceiveActive) {
      stopReceiver();
      if (this.artNetReceiveActive.isOn()) {
        startReceiver();
      }
    }
  }

  private void stopReceiver() {
    if (this.artNetReceiver != null) {
      this.artNetReceiver.interrupt();
      this.artNetReceiver.socket.close();
      this.artNetReceiver = null;
    }
  }

  private void startReceiver() {
    try {
      final DatagramSocket socket = new DatagramSocket(ArtNetDatagram.ARTNET_PORT);
      this.artNetReceiver = new ArtNetReceiver(socket);
      this.artNetReceiver.start();
    } catch (SocketException sx) {
      error(sx, "Could not create Art-Net listener socket: " + sx.getMessage());
    }
  }

  public int getColor(int universe, int channel) {
    return LXColor.rgba(
      this.data[universe][channel],
      this.data[universe][channel+1],
      this.data[universe][channel+2],
      0xff
    );
  }

  public byte getByte(int universe, int channel) {
    return this.data[universe][channel];
  }

  public double getNormalized(int universe, int channel) {
    return (this.data[universe][channel] & 0xff) / 255.;
  }

  private class ArtNetReceiver extends Thread {

    private final DatagramSocket socket;

    private ArtNetReceiver(DatagramSocket socket) {
      super("Art-Net Receiver Thread");
      this.socket = socket;
    }

    private boolean checkHeader(byte[] dmxData, int offset) {
      for (int i = 0; i < ArtNetDatagram.HEADER.length; ++i) {
        if (dmxData[offset + i] != ArtNetDatagram.HEADER[i]) {
          error("Packet missing valid Art-Net header");
          return false;
        }
      }
      return true;
    }

    @Override
    public void run() {
      log("Starting Art-Net listener on port " + ArtNetDatagram.ARTNET_PORT);
      final byte[] buffer = new byte[ArtNetDatagram.ARTNET_HEADER_LENGTH + ArtNetDatagram.MAX_DATA_LENGTH];
      final DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

      while (!isInterrupted()) {
        try {
          this.socket.receive(packet);
          final byte[] dmxData = packet.getData();
          final int offset = packet.getOffset();
          final int dmxOffset = offset + ArtNetDatagram.ARTNET_HEADER_LENGTH;
          if (!checkHeader(dmxData, offset)) {
            continue;
          }

          final int universe =
            (dmxData[offset + ArtNetDatagram.UNIVERSE_LSB] & 0xff) |
            ((dmxData[offset + ArtNetDatagram.UNIVERSE_MSB] & 0xff) << 8);

          if (universe >= MAX_UNIVERSE) {
            error("Ignoring packet, universe exceeds max: " + universe);
            continue;
          }

          final int dataLength =
            (dmxData[offset + ArtNetDatagram.DATA_LENGTH_LSB] & 0xff) |
            ((dmxData[offset + ArtNetDatagram.DATA_LENGTH_MSB] & 0xff) << 8);

          System.arraycopy(dmxData, dmxOffset, data[universe], 0, dataLength);

        } catch (Throwable x) {
          if (isInterrupted()) {
            break;
          }
          error(x, x.getMessage());
        }
      }

      // Thread is finished, close the socket
      try {
        this.socket.close();
      } catch (Throwable x) {
        LX.error(x, "Error closing Art-Net DatagramSocket: " + x.getMessage());
      }
      log("Art-Net receiver thread finished.");
    }
  }

  @Override
  public void load(LX lx, JsonObject obj) {
    super.load(lx, obj);
    if (obj.has(LXComponent.KEY_RESET)) {
      this.artNetReceiveActive.setValue(false);
    }
  }

  @Override
  public void dispose() {
    stopReceiver();
    super.dispose();
  }

  public static void error(String log) {
    LX.error("[ArtNet] " + log);
  }

  public static void error(Throwable x, String log) {
    LX.error(x, "[ArtNet] " + log);
  }

  public static void log(String log) {
    LX.log("[ArtNet] " + log);
  }
}
