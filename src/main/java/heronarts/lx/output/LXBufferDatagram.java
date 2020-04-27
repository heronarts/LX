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

import java.util.Objects;

public abstract class LXBufferDatagram extends LXDatagram {

  /**
   * Various orderings for RGB buffer data
   */
  public enum ByteOrder {
    RGB(new int[] { 0, 1, 2 }),
    RBG(new int[] { 0, 2, 1 }),
    GRB(new int[] { 1, 0, 2 }),
    GBR(new int[] { 2, 0, 1 }),
    BRG(new int[] { 1, 2, 0 }),
    BGR(new int[] { 2, 1, 0 }),

    RGBW(new int[] { 0, 1, 2, 3 }),
    RBGW(new int[] { 0, 2, 1, 3 }),
    GRBW(new int[] { 1, 0, 2, 3 }),
    GBRW(new int[] { 2, 0, 1, 3 }),
    BRGW(new int[] { 1, 2, 0, 3 }),
    BGRW(new int[] { 2, 1, 0, 3 }),

    WRGB(new int[] { 1, 2, 3, 0 }),
    WRBG(new int[] { 1, 3, 2, 0 }),
    WGRB(new int[] { 2, 1, 3, 0 }),
    WGBR(new int[] { 3, 1, 2, 0 }),
    WBRG(new int[] { 2, 3, 1, 0 }),
    WBGR(new int[] { 3, 2, 1, 0 });

    /**
     * Byte offet is array of integer offsets in order RGBW, indicating
     * at what position the red, green, blue, and optionally white byte
     * go in the payload.
     */
    private final int[] byteOffset;

    ByteOrder(int[] byteOffset) {
      this.byteOffset = byteOffset;
    }

    public boolean hasWhite() {
      return this.byteOffset.length == 4;
    }

    public int getNumBytes() {
      return this.byteOffset.length;
    }

    public int[] getByteOffset() {
      return this.byteOffset;
    }
  };

  protected ByteOrder byteOrder = ByteOrder.RGB;

  protected final int[] indexBuffer;

  protected LXBufferDatagram(int[] indexBuffer, int datagramSize) {
    this(indexBuffer, datagramSize, ByteOrder.RGB);
  }

  protected LXBufferDatagram(int[] indexBuffer, int datagramSize, ByteOrder byteOrder) {
    super(datagramSize);
    this.byteOrder = byteOrder;
    this.indexBuffer = indexBuffer;
  }

  /**
   * Updates the values int he index buffer for this datagram. The indices can change but the size
   * of the datagram cannot, the indexBuffer must have the same length.
   *
   * @param indexBuffer New index buffer, must have same length as existing
   * @return this
   */
  public LXBufferDatagram setIndexBuffer(int[] indexBuffer) {
    Objects.requireNonNull(indexBuffer, "May not set null LXBufferDatagram.setIndexBuffer()");
    if (indexBuffer.length != this.indexBuffer.length) {
      throw new IllegalArgumentException("May not change length of LXBufferDatagram indexBuffer, must make a new Datagram: " + this.indexBuffer.length + " != " + indexBuffer.length);
    }
    System.arraycopy(indexBuffer, 0, this.indexBuffer, 0, indexBuffer.length);
    return this;
  }

  /**
   * Sets the byte ordering of data in this datagram buffer
   *
   * @param byteOrder Byte ordering
   * @return this
   */
  public LXBufferDatagram setByteOrder(ByteOrder byteOrder) {
    if (this.byteOrder.getNumBytes() != byteOrder.getNumBytes()) {
      throw new IllegalArgumentException("May not change number of bytes in order");
    }
    this.byteOrder = byteOrder;
    return this;
  }

  /**
   * Helper for subclasses to copy a list of points into the data buffer at a
   * specified offset. For many subclasses which wrap RGB buffers, onSend() will
   * be a simple call to this method with the right parameters.
   *
   * @param colors Array of color values
   * @param glut Look-up table of gamma-corrected brightness values
   * @param indexBuffer Array of point indices
   * @param offset Offset in buffer to write
   * @return this
   */
  protected LXBufferDatagram copyPoints(int[] colors, byte[] glut, int[] indexBuffer, int offset) {
    int numBytes = this.byteOrder.getNumBytes();
    if (this.byteOrder.hasWhite()) {
      int[] byteOffset = this.byteOrder.getByteOffset();
      for (int index : indexBuffer) {
        int color = (index >= 0) ? colors[index] : 0;
        byte r = glut[((color >> 16) & 0xff)];
        byte g = glut[((color >> 8) & 0xff)];
        byte b = glut[(color & 0xff)];
        byte w = (r < g) ? ((r < b) ? r : b) : ((g < b) ? g : b);
        r -= w;
        g -= w;
        b -= w;
        this.buffer[offset + byteOffset[0]] = r;
        this.buffer[offset + byteOffset[1]] = g;
        this.buffer[offset + byteOffset[2]] = b;
        this.buffer[offset + byteOffset[3]] = w;
        offset += numBytes;
      }
    } else {
      int[] byteOffset = this.byteOrder.getByteOffset();
      for (int index : indexBuffer) {
        int color = (index >= 0) ? colors[index] : 0;
        this.buffer[offset + byteOffset[0]] = glut[((color >> 16) & 0xff)]; // R
        this.buffer[offset + byteOffset[1]] = glut[((color >> 8) & 0xff)]; // G
        this.buffer[offset + byteOffset[2]] = glut[(color & 0xff)]; // B
        offset += numBytes;
      }
    }
    return this;
  }

  /**
   * Subclasses must implement, indicates where the data offset is to write the color
   * data into the buffer
   *
   * @return data offset position
   */
  protected abstract int getDataOffset();

  /**
   * Subclasses may override to update the sequence number, if one is being used.
   */
  protected void updateSequenceNumber() {}

  @Override
  public final void onSend(int[] colors, byte[] glut) {
    copyPoints(colors, glut, this.indexBuffer, getDataOffset());
    updateSequenceNumber();
  }

}
