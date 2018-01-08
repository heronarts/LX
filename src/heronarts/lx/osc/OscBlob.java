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

import java.nio.ByteBuffer;

public class OscBlob implements OscArgument {
  private int byteLength;
  private byte[] data;

  public OscBlob(byte[] data) {
    if (data == null) {
      throw new IllegalArgumentException("Cannot pass null array to OscBlob constructor");
    }
    setData(data);
  }

  public byte[] getData() {
    return this.data;
  }

  public OscBlob setData(byte[] data) {
    this.data = data;
    this.byteLength = 4 + data.length;
    while (this.byteLength % 4 > 0) {
      ++this.byteLength;
    }
    return this;
  }

  public int getByteLength() {
    return this.byteLength;
  }

  @Override
  public char getTypeTag() {
    return OscTypeTag.BLOB;
  }

  @Override
  public String toString() {
    return "<" + data.length + "-byte blob>";
  }

  @Override
  public void serialize(ByteBuffer buffer) {
    buffer.putInt(this.byteLength);
    buffer.put(this.data);
    for (int i = this.data.length; i < this.byteLength; ++i) {
      buffer.put((byte) 0);
    }
  }

  @Override
  public int toInt() {
    return 0;
  }

  @Override
  public float toFloat() {
    return 0;
  }

  @Override
  public double toDouble() {
    return 0;
  }

  @Override
  public boolean toBoolean() {
    return false;
  }
}
