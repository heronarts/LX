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

public class OscChar implements OscArgument {

  private char value = 0;

  public OscChar() {}

  public OscChar(char value) {
    this.value = value;
  }

  public OscChar setValue(char value) {
    this.value = value;
    return this;
  }

  public char getValue() {
    return this.value;
  }

  @Override
  public int getByteLength() {
    return 4;
  }

  @Override
  public char getTypeTag() {
    return OscTypeTag.CHAR;
  }

  @Override
  public String toString() {
    return Character.toString(this.value);
  }

  @Override
  public void serialize(ByteBuffer buffer) {
    buffer.putChar(this.value);
    buffer.putChar(this.value);
    buffer.putChar(this.value);
    buffer.putChar(this.value);
  }

  @Override
  public int toInt() {
    return this.value;
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
