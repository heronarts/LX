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

public class OscString implements OscArgument {

  private String value;
  private int byteLength;

  public OscString(char[] value) {
    this(new String(value));
  }

  public OscString(String value) {
    setValue(value);
  }

  public OscString setValue(String value) {
    this.value = value;
    this.byteLength = value.length() + 1;
    while (this.byteLength % 4 > 0) {
      ++this.byteLength;
    }
    return this;
  }

  public String getValue() {
    return this.value;
  }

  public int getByteLength() {
    return this.byteLength;
  }

  public static OscString parse(byte[] data, int offset, int len) throws OscException {
    for (int i = offset; i < len; ++i) {
      if (data[i] == 0) {
        return new OscString(new String(data, offset, i-offset));
      }
    }
    throw new OscMalformedDataException("OscString has no terminating null character", data, offset, len);
  }

  @Override
  public char getTypeTag() {
    return OscTypeTag.STRING;
  }

  @Override
  public String toString() {
    return this.value;
  }

  public void serialize(ByteBuffer buffer) {
    byte[] bytes = this.value.getBytes();
    buffer.put(bytes);
    for (int i = bytes.length; i < this.byteLength; ++i) {
      buffer.put((byte) 0);
    }
  }

  @Override
  public int toInt() {
    return Integer.parseInt(this.value);
  }

  @Override
  public float toFloat() {
    return Float.parseFloat(this.value);
  }

  @Override
  public double toDouble() {
    return Double.parseDouble(this.value);
  }

  @Override
  public boolean toBoolean() {
    return this.value.equals("true") || this.value.equals("TRUE");
  }
}
