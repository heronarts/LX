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

import heronarts.lx.LX;
import heronarts.lx.model.LXModel;

public class FadecandySocket extends OPCSocket {

  public static final int DEFAULT_PORT = 7890;

  private byte firmwareConfig = 0x00;

  private String colorCorrection = null;

  public FadecandySocket(LX lx) {
    this(lx, lx.getModel());
  }

  public FadecandySocket(LX lx, LXModel model) {
    this(lx, model.toIndexBuffer());
  }

  public FadecandySocket(LX lx, int[] indexBuffer) {
    super(lx, indexBuffer);
    setPort(DEFAULT_PORT);
  }

  @Override
  protected void didConnect() {
    sendColorCorrectionPacket();
    sendFirmwareConfigPacket();
  }

  public FadecandySocket setDithering(boolean enabled) {
    if (enabled) {
      this.firmwareConfig &= ~0x01;
    } else {
      this.firmwareConfig |= 0x01;
    }
    sendFirmwareConfigPacket();
    return this;
  }

  public FadecandySocket setInterpolation(boolean enabled) {
    if (enabled) {
      this.firmwareConfig &= ~0x02;
    } else {
      this.firmwareConfig |= 0x02;
    }
    sendFirmwareConfigPacket();
    return this;
  }

  public FadecandySocket setStatusLedAuto() {
    this.firmwareConfig &= 0x0C;
    sendFirmwareConfigPacket();
    return this;
  }

  public FadecandySocket setStatusLed(boolean on) {
    this.firmwareConfig |= 0x04; // Manual LED control
    if (on) {
      this.firmwareConfig |= 0x08;
    } else {
      this.firmwareConfig &= ~0x08;
    }
    sendFirmwareConfigPacket();
    return this;
  }

  private final byte[] firmwarePacket = new byte[9];

  private void sendFirmwareConfigPacket() {
    if (!isConnected()) {
      // We'll do this when we reconnect
      return;
    }

    this.firmwarePacket[0] = 0;          // Channel (reserved)
    this.firmwarePacket[1] = (byte)0xFF; // Command (System Exclusive)
    this.firmwarePacket[2] = 0;          // Length high byte
    this.firmwarePacket[3] = 5;          // Length low byte
    this.firmwarePacket[4] = 0x00;       // System ID high byte
    this.firmwarePacket[5] = 0x01;       // System ID low byte
    this.firmwarePacket[6] = 0x00;       // Command ID high byte
    this.firmwarePacket[7] = 0x02;       // Command ID low byte
    this.firmwarePacket[8] = this.firmwareConfig;

    try {
      this.output.write(this.firmwarePacket);
    } catch (IOException iox) {
      disconnect(iox);
    }
  }

  public FadecandySocket setColorCorrection(float gamma, float red, float green, float blue) {
    this.colorCorrection = "{ \"gamma\": " + gamma + ", \"whitepoint\": [" + red + "," + green + "," + blue + "]}";
    sendColorCorrectionPacket();
    return this;
  }

  public FadecandySocket setColorCorrection(String s) {
    this.colorCorrection = s;
    sendColorCorrectionPacket();
    return this;
  }

  private void sendColorCorrectionPacket() {
    if ((this.colorCorrection == null) || !isConnected()) {
      return;
    }

    byte[] content = this.colorCorrection.getBytes();
    int packetLen = content.length + 4;
    byte[] header = new byte[8];
    header[0] = 0;          // Channel (reserved)
    header[1] = (byte)0xFF; // Command (System Exclusive)
    header[2] = (byte)(packetLen >> 8);
    header[3] = (byte)(packetLen & 0xFF);
    header[4] = 0x00;       // System ID high byte
    header[5] = 0x01;       // System ID low byte
    header[6] = 0x00;       // Command ID high byte
    header[7] = 0x01;       // Command ID low byte

    try {
      this.output.write(header);
      this.output.write(content);
    } catch (IOException iox) {
      disconnect(iox);
    }
  }

}
