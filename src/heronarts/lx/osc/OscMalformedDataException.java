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

public class OscMalformedDataException extends OscException {

  private static final long serialVersionUID = 1L;

  private final byte[] data;
  private final int offset;
  private final int len;

  OscMalformedDataException(String message, byte[] data, int offset, int len) {
    super(message);
    this.data = data;
    this.offset = offset;
    this.len = len;
  }

  public byte[] getData() {
    return this.data;
  }

  public int getOffset() {
    return this.offset;
  }

  public int getLength() {
    return this.len;
  }

}