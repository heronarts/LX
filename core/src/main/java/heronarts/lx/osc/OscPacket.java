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

package heronarts.lx.osc;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.nio.ByteBuffer;

public abstract class OscPacket {

  public static OscPacket parse(InetAddress source, byte[] data, int offset, int len) throws OscException {
    if (data == null) {
      throw new IllegalArgumentException("OscPacket cannot parse null data array");
    }
    if (len <= 0) {
      throw new OscEmptyPacketException();
    }
    if (data[offset] == '#') {
      return OscBundle.parse(source, data, offset, len);
    } else if (data[offset] == '/') {
      return OscMessage.parse(source,data, offset, len);
    } else {
      throw new OscMalformedDataException("Osc Packet does not start with # or / --- " + new String(data, 0, len < 10 ? len : 10), data, offset, len);
    }
  }

  public static OscPacket parse(DatagramPacket datagram) throws OscException {
    return OscPacket.parse(datagram.getAddress(), datagram.getData(), datagram.getOffset(), datagram.getLength());
  }

  abstract void serialize(ByteBuffer buffer);
}
