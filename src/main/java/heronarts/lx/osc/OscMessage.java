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

import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class OscMessage extends OscPacket implements Iterable<OscArgument> {
  private OscString addressPattern;

  private OscString typeTag;
  private boolean typeTagDirty = true;

  private InetAddress source;

  private final List<OscArgument> arguments = new ArrayList<OscArgument>();

  private int cursor = 0;

  public final long nanoTime = System.nanoTime();

  public OscMessage() {}

  public OscMessage(String addressPattern) {
    setAddressPattern(addressPattern);
  }

  public OscMessage clearArguments() {
    this.arguments.clear();
    this.typeTagDirty = true;
    return this;
  }

  public OscMessage add(OscArgument argument) {
    this.arguments.add(argument);
    this.typeTagDirty = true;
    return this;
  }

  public OscMessage add(int argument) {
    return add(new OscInt(argument));
  }

  public OscMessage add(String argument) {
    return add(new OscString(argument));
  }

  public OscMessage add(double argument) {
    return add(new OscDouble(argument));
  }

  public OscMessage add(float argument) {
    return add(new OscFloat(argument));
  }

  public int size() {
    return this.arguments.size();
  }

  public OscArgument get() {
    return this.arguments.get(this.cursor++);
  }

  public OscArgument get(int index) {
    return this.arguments.get(index);
  }

  public int getInt() {
    return get().toInt();
  }

  public int getInt(int index) {
    return get(index).toInt();
  }

  public boolean getBoolean() {
    return get().toBoolean();
  }

  public boolean getBoolean(int index) {
    return get(index).toBoolean();
  }

  public float getFloat() {
    return get().toFloat();
  }

  public float getFloat(int index) {
    return get(index).toFloat();
  }

  public double getDouble() {
    return get().toDouble();
  }

  public double getDouble(int index) {
    return get(index).toDouble();
  }

  public String getString() {
    return get().toString();
  }

  public String getString(int index) {
    return get(index).toString();
  }

  public OscBlob getBlob() {
    return (OscBlob) get();
  }

  public OscBlob getBlob(int index) {
    return (OscBlob) get(index);
  }

  public OscMessage resetCursor() {
    this.cursor = 0;
    return this;
  }

  public OscMessage setAddressPattern(String addressPattern) {
    this.addressPattern = new OscString(addressPattern);
    return this;
  }

  public OscMessage setAddressPattern(OscString addressPattern) {
    this.addressPattern = addressPattern;
    return this;
  }

  OscMessage setSource(InetAddress address) {
    this.source = address;
    return this;
  }

  public InetAddress getSource() {
    return this.source;
  }

  public OscString getAddressPattern() {
    return this.addressPattern;
  }

  private void rebuildTypeTag() {
    char[] typeTag = new char[this.arguments.size() + 1];
    int i = 0;
    typeTag[i++] = ',';
    for (OscArgument argument : this.arguments) {
      typeTag[i++] = argument.getTypeTag();
    }
    this.typeTag = new OscString(typeTag);
    this.typeTagDirty = false;
  }

  public OscString getTypeTag() {
    if (this.typeTagDirty) {
      rebuildTypeTag();
    }
    return this.typeTag;
  }

  public boolean matches(String pattern) {
    // TODO(mcslee): add wildcard matching?
    return this.addressPattern.getValue().equals(pattern);
  }

  public boolean hasPrefix(String pattern) {
    String address = this.addressPattern.getValue();
    return
      address.startsWith(pattern) && (
        (address.length() == pattern.length()) ||
        (address.charAt(pattern.length()) == '/')
      );
  }

  public static OscMessage parse(InetAddress source, byte[] data, int offset, int len) throws OscException {
    OscMessage message = new OscMessage();
    message.setSource(source);

    // Read address pattern
    OscString addressPattern = OscString.parse(data, offset, len);
    offset += addressPattern.getByteLength();
    message.setAddressPattern(addressPattern);

    // Is there a typetag?
    if (offset < len) {
      OscString typeTag = OscString.parse(data, offset, len);
      offset += typeTag.getByteLength();

      // TODO(mcslee): check for buffer overruns
      ByteBuffer buffer = ByteBuffer.wrap(data);
      String typeTagValue = typeTag.getValue();
      for (int i = 1; i < typeTagValue.length(); ++i) {
        char tag = typeTagValue.charAt(i);
        OscArgument argument = null;
        switch (tag) {
          case OscTypeTag.INT:
            argument = new OscInt(buffer.getInt(offset));
            break;
          case OscTypeTag.FLOAT:
            argument = new OscFloat(buffer.getFloat(offset));
            break;
          case OscTypeTag.STRING:
            argument = OscString.parse(data, offset, len);
            break;
          case OscTypeTag.BLOB:
            int blobLength = buffer.getInt(offset);
            byte[] blobData = new byte[blobLength];
            System.arraycopy(buffer, offset, blobData, 0, blobLength);
            argument = new OscBlob(blobData);
            break;
          case OscTypeTag.LONG:
            argument = new OscLong(buffer.getLong(offset));
            break;
          case OscTypeTag.TIMETAG:
            argument = new OscTimeTag(buffer.getLong(offset));
            break;
          case OscTypeTag.DOUBLE:
            argument = new OscDouble(buffer.getDouble(offset));
            break;
          case OscTypeTag.SYMBOL:
            argument = OscSymbol.parse(data, offset, len);
            break;
          case OscTypeTag.CHAR:
            argument = new OscChar((char) buffer.getInt(offset));
            break;
          case OscTypeTag.RGBA:
            argument = new OscRgba(buffer.getInt(offset));
            break;
          case OscTypeTag.MIDI:
            argument = new OscMidi(buffer.getInt(offset));
            break;
          case OscTypeTag.TRUE:
            argument = new OscTrue();
            break;
          case OscTypeTag.FALSE:
            argument = new OscFalse();
            break;
          case OscTypeTag.NIL:
            argument = new OscNil();
            break;
          case OscTypeTag.INFINITUM:
            argument = new OscInfinitum();
            break;
          default:
            throw new OscMalformedDataException("Unrecognized type tag: " + tag, data, offset, len);
        }
        offset += argument.getByteLength();
        message.add(argument);
      }
    }
    return message;
  }

  @Override
  public Iterator<OscArgument> iterator() {
    return this.arguments.iterator();
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(this.addressPattern.toString());
    if (this.typeTag != null) {
      sb.append(this.typeTag.toString());
    }
    boolean first = true;
    for (OscArgument argument : this) {
      if (first) {
        sb.append(' ');
        first = false;
      } else {
        sb.append(',');
      }
      sb.append(argument.toString());
    }
    return sb.toString();
  }

  @Override
  void serialize(ByteBuffer buffer) {
    this.addressPattern.serialize(buffer);
    getTypeTag().serialize(buffer);
    for (OscArgument argument : this.arguments) {
      argument.serialize(buffer);
    }
  }
}
