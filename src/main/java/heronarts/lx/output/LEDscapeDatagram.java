package heronarts.lx.output;

import heronarts.lx.LX;

public class LEDscapeDatagram extends LXDatagram {

  public static final int LEDSCAPE_DEFAULT_PORT = 7890;
  public static final int BYTES_PER_PIXEL = 3;
  public static final int LEDSCAPE_DEFAULT_CHANNELS_PER_UNIVERSE = 170;
  public static final int LEDSCAPE_MAX_UNIVERSES = 32;    // LEDscape naming calls them channels, but they are equivalent to ArtNet universes
  public static final int MAX_CHANNELS_TEMP = LEDSCAPE_DEFAULT_CHANNELS_PER_UNIVERSE * BYTES_PER_PIXEL * LEDSCAPE_MAX_UNIVERSES;
  private final int channelsPerUniverse;                   // LEDscape pixels per channel = modern channels per universe
  private final int dataLength;

  public static final int LEDSCAPE_HEADER_LEN = 4;

  public static final int OFFSET_CHANNEL = 0;
  public static final int OFFSET_COMMAND = 1;
  public static final int OFFSET_DATA_LEN_MSB = 2;
  public static final int OFFSET_DATA_LEN_LSB = 3;
  public static final int OFFSET_DATA = 4;

  public static final byte COMMAND_SET_PIXEL_COLORS = 0;

  static final int OFFSET_R = 0;
  static final int OFFSET_G = 1;
  static final int OFFSET_B = 2;

  public LEDscapeDatagram(LX lx, IndexBuffer indexBuffer) {
    this(lx, indexBuffer, LEDSCAPE_DEFAULT_CHANNELS_PER_UNIVERSE);
  }

  // TODO(JKB): Use parameters to set number of pixels per channel
  public LEDscapeDatagram(LX lx, IndexBuffer indexBuffer, int channelsPerUniverse) {
      super(lx, indexBuffer, LEDSCAPE_HEADER_LEN + (channelsPerUniverse * BYTES_PER_PIXEL * LEDSCAPE_MAX_UNIVERSES));

      this.channelsPerUniverse = channelsPerUniverse;
      this.dataLength = this.channelsPerUniverse * BYTES_PER_PIXEL * LEDSCAPE_MAX_UNIVERSES;
      setPort(LEDSCAPE_DEFAULT_PORT);

      validateBufferSize();

      this.buffer[OFFSET_CHANNEL] = 0;
      this.buffer[OFFSET_COMMAND] = COMMAND_SET_PIXEL_COLORS;
      this.buffer[OFFSET_DATA_LEN_MSB] = (byte) (this.dataLength >>> 8);
      this.buffer[OFFSET_DATA_LEN_LSB] = (byte) (this.dataLength & 0xFF);
  }

  public LEDscapeDatagram setChannel(byte channel) {
      this.buffer[OFFSET_CHANNEL] = channel;
      return this;
  }

  public byte getChannel() {
      return this.buffer[OFFSET_CHANNEL];
  }

  @Override
  protected int getDataBufferOffset() {
    return LEDSCAPE_HEADER_LEN;
  }

  public LEDscapeDatagram setBackgroundColor(int newBackgroundColor) {
      for (int i = 0; i < this.dataLength; i+=3) {
        int dataOffset = OFFSET_DATA + i;
        this.buffer[dataOffset + OFFSET_R] = (byte) (0xFF & (newBackgroundColor >> 16));
        this.buffer[dataOffset + OFFSET_G] = (byte) (0xFF & (newBackgroundColor >> 8));
        this.buffer[dataOffset + OFFSET_B] = (byte) (0xFF & newBackgroundColor);
      }
      return this;
  }
}
