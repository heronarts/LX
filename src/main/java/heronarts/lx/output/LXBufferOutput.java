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

import heronarts.lx.LX;

public abstract class LXBufferOutput extends LXOutput {

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

  /**
   * Wacky option to have a different byte-order per-pixel, which may be used
   * in rare cases by JSONFixture. This is supported but generally discouraged,
   * as it obviously creates a great opportunity for confusion.
   */
  protected ByteOrder[] byteOrderBuffer = null;

  protected LXBufferOutput(LX lx, int[] indexBuffer) {
    this(lx, indexBuffer, ByteOrder.RGB);
  }

  protected LXBufferOutput(LX lx, int[] indexBuffer, ByteOrder byteOrder) {
    super(lx);
    this.indexBuffer = indexBuffer;
    this.byteOrder = byteOrder;
  }

  /**
   * Updates the values in the index buffer for this output. The indices can change but the size
   * of the output buffer cannot, the indexBuffer must have the same length. It will be copied into the
   * index buffer object held by this output object.
   *
   * @param indexBuffer New index buffer values, must have same length as existing
   * @return this
   */
  public LXBufferOutput updateIndexBuffer(int[] indexBuffer) {
    Objects.requireNonNull(indexBuffer, "May not set null LXBufferOutput.setIndexBuffer()");
    if (indexBuffer.length != this.indexBuffer.length) {
      throw new IllegalArgumentException("May not change length of LXBufferOutput indexBuffer, must make a new Output: " + this.indexBuffer.length + " != " + indexBuffer.length);
    }
    System.arraycopy(indexBuffer, 0, this.indexBuffer, 0, indexBuffer.length);
    return this;
  }

  /**
   * Subclasses should provide a handle to a raw byte buffer
   *
   * @return Raw byte buffer for output data
   */
  protected abstract byte[] getDataBuffer();

  /**
   * Offset into raw byte buffer where color data is written
   *
   * @return Offset into raw byte buffer for color data
   */
  protected abstract int getDataBufferOffset();

  /**
   * Sets the byte ordering of data in this buffer
   *
   * @param byteOrder Byte ordering
   * @return this
   */
  public LXBufferOutput setByteOrder(ByteOrder byteOrder) {
    if (this.byteOrder.getNumBytes() != byteOrder.getNumBytes()) {
      throw new IllegalArgumentException("May not change number of bytes in order");
    }
    this.byteOrder = byteOrder;
    return this;
  }

  /**
   * Sets a dynamic byte ordering on this output, where every index position
   * may have a distinct byte ordering. They must all have the same byte length
   * as the original byte order. Intermixing different byte-lengths is not supported.
   *
   * @param byteOrderBuffer Array of byte orderings
   * @return this
   */
  public LXBufferOutput setByteOrder(ByteOrder[] byteOrderBuffer) {
    if (byteOrderBuffer.length != this.indexBuffer.length) {
      throw new IllegalArgumentException("Invalid byte order buffer length: " + byteOrderBuffer.length + " != " + this.indexBuffer.length);
    }
    for (ByteOrder byteOrder : byteOrderBuffer) {
      if (byteOrder == null) {
        throw new IllegalArgumentException("Dynamic byte order may not contain null entries");
      }
      if (byteOrder.getNumBytes() != this.byteOrder.getNumBytes()) {
        throw new IllegalArgumentException("Dynamic byte order may not have variable size (" + byteOrder + " != " + this.byteOrder + ")");
      }
    }
    this.byteOrderBuffer = byteOrderBuffer;
    return this;
  }

  /**
   * Helper for subclasses to copy a list of points into the data buffer at a
   * specified offset. For many subclasses which wrap RGB buffers, onSend() will
   * be a simple call to this method with the right parameters.
   *
   * @param colors Array of color values
   * @param glut Look-up table of gamma-corrected brightness values
   * @return this
   */
  protected LXBufferOutput updateDataBuffer(int[] colors, byte[] glut) {
    byte[] buffer = getDataBuffer();
    int offset = getDataBufferOffset();

    if (this.byteOrderBuffer != null) {
      // Wacky dynamic byte order mode!
      for (int i = 0; i < this.indexBuffer.length; ++i) {
        int index = this.indexBuffer[i];
        ByteOrder byteOrder = this.byteOrderBuffer[i];
        int[] byteOffset = byteOrder.getByteOffset();
        if (byteOrder.hasWhite()) {
          int color = (index >= 0) ? colors[index] : 0;
          int r = ((color >> 16) & 0xff);
          int g = ((color >> 8) & 0xff);
          int b = (color & 0xff);
          int w = (r < g) ? ((r < b) ? r : b) : ((g < b) ? g : b);
          r -= w;
          g -= w;
          b -= w;
          buffer[offset + byteOffset[0]] = glut[r];
          buffer[offset + byteOffset[1]] = glut[g];
          buffer[offset + byteOffset[2]] = glut[b];
          buffer[offset + byteOffset[3]] = glut[w];
        } else {
          int color = (index >= 0) ? colors[index] : 0;
          buffer[offset + byteOffset[0]] = glut[((color >> 16) & 0xff)]; // R
          buffer[offset + byteOffset[1]] = glut[((color >> 8) & 0xff)]; // G
          buffer[offset + byteOffset[2]] = glut[(color & 0xff)]; // B
        }
        offset += byteOrder.getNumBytes();
      }

    } else {

      int numBytes = this.byteOrder.getNumBytes();
      if (this.byteOrder.hasWhite()) {
        int[] byteOffset = this.byteOrder.getByteOffset();
        for (int index : this.indexBuffer) {
          int color = (index >= 0) ? colors[index] : 0;
          int r = ((color >> 16) & 0xff);
          int g = ((color >> 8) & 0xff);
          int b = (color & 0xff);
          int w = (r < g) ? ((r < b) ? r : b) : ((g < b) ? g : b);
          r -= w;
          g -= w;
          b -= w;
          buffer[offset + byteOffset[0]] = glut[r];
          buffer[offset + byteOffset[1]] = glut[g];
          buffer[offset + byteOffset[2]] = glut[b];
          buffer[offset + byteOffset[3]] = glut[w];
          offset += numBytes;
        }
      } else {
        int[] byteOffset = this.byteOrder.getByteOffset();
        for (int index : indexBuffer) {
          int color = (index >= 0) ? colors[index] : 0;
          buffer[offset + byteOffset[0]] = glut[((color >> 16) & 0xff)]; // R
          buffer[offset + byteOffset[1]] = glut[((color >> 8) & 0xff)]; // G
          buffer[offset + byteOffset[2]] = glut[(color & 0xff)]; // B
          offset += numBytes;
        }
      }
    }
    return this;
  }

}
