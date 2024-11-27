package heronarts.lx.midi;

import javax.sound.midi.ShortMessage;
import javax.sound.midi.SysexMessage;

public class LXSysexMessage extends SysexMessage implements LXMidiMessage {

  // Universal SysEx Messages
  // https://midi.org/midi-1-0-universal-system-exclusive-messages
  public static final byte START_SYSEX = (byte) SYSTEM_EXCLUSIVE;
  public static final byte END_SYSEX = (byte) ShortMessage.END_OF_EXCLUSIVE;
  public static final byte REALTIME = 0x7F;
  public static final byte NON_REALTIME = 0x7E;
  public static final byte ANY_DEVICE = 0x7F;
  public static final byte GENERAL_INFORMATION = 0x06;
  public static final byte IDENTITY_REQUEST = 0x01;
  public static final byte IDENTITY_REPLY = 0x02;

  public static LXSysexMessage newIdentityRequest() {
    return newIdentityRequest(ANY_DEVICE);
  }

  public static LXSysexMessage newIdentityRequest(byte deviceId) {
    return new LXSysexMessage(new byte[] {
      START_SYSEX,
      NON_REALTIME,
      deviceId,
      GENERAL_INFORMATION,
      IDENTITY_REQUEST,
      END_SYSEX
    });
  }

  private LXMidiSource source = LXMidiSource.UNKNOWN;

  LXSysexMessage(byte[] data) {
    super(data);
  }

  LXSysexMessage(SysexMessage message) {
    this(message.getMessage());
  }

  public LXSysexMessage setSource(LXMidiSource source) {
    this.source = source;
    return this;
  }

  public LXMidiSource getSource() {
    return this.source;
  }

  public final void dispatch(LXMidiListener listener) {
    listener.sysexReceived(this);
  }

  @Override
  public String toString() {
    return "MidiSysEx:" + bytesToString(getMessage());
  }

  private static String bytesToString(byte[] bytes) {
    StringBuilder sb = new StringBuilder();
    for (byte b : bytes) {
      sb.append(String.format("%02X ", b));
    }
    return sb.toString();
  }
}
