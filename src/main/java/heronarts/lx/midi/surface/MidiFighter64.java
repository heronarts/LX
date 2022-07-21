package heronarts.lx.midi.surface;

import heronarts.lx.LX;
import heronarts.lx.midi.*;

import java.util.Arrays;

public class MidiFighter64 extends LXMidiSurface implements LXMidiSurface.Bidirectional {
  /* Use the Midi Fighter Utility from DJ Tech Tools to apply
   * these recommended settings to your midi controller:
   *   MIDI Channel = 3
   *   MIDI Type = Notes
   *   Corner Button Bank Change: Hold or Disabled
   *
   * Midi Fighter manual (official URL):
   * https://drive.google.com/file/d/0B-QvIds_FsH3WDBNWXUxWTlGVlU/view?resourcekey=0-eGf57BdEMP8GB2TaanYccg
   *
   * When sending notes, these are the pitches that correspond to its button grid:
   *   64 65 66 67  96 97 98 99
   *   60 61 62 63  92 93 94 95
   *   56 57 58 59  88 89 90 91
   *   52 53 54 55  84 85 86 87
   *   48 49 50 51  80 81 82 83
   *   44 45 46 47  76 77 78 79
   *   40 41 42 43  72 73 74 75
   *   36 37 38 39  68 69 70 71
   */

  // We're calling the bottom row 0, so this is the reverse of the diagram above.
  public static final int[] pitchFromXY = {
    36, 37, 38, 39,  68, 69, 70, 71,
    40, 41, 42, 43,  72, 73, 74, 75,
    44, 45, 46, 47,  76, 77, 78, 79,
    48, 49, 50, 51,  80, 81, 82, 83,
    52, 53, 54, 55,  84, 85, 86, 87,
    56, 57, 58, 59,  88, 89, 90, 91,
    60, 61, 62, 63,  92, 93, 94, 95,
    64, 65, 66, 67,  96, 97, 98, 99
  };

  public static final String DEVICE_NAME = "Midi Fighter 64";

  public static final int LED_OFF = 0;
  public static final int LED_GRAY_DIM = 1;
  public static final int LED_GRAY = 2;
  public static final int LED_WHITE = 3;
  public static final int LED_PINK = 4;
  public static final int LED_RED = 5; // And 60
  public static final int LED_RED_HALF = 6;
  public static final int LED_RED_DIM = 7;
  public static final int LED_WARM_WHITE = 8;
  public static final int LED_ORANGE = 9; // And 61
  public static final int LED_ORANGE_HALF = 10;
  public static final int LED_ORANGE_DIM = 11;
  public static final int LED_PALE_YELLOW = 12;
  public static final int LED_YELLOW = 13;
  public static final int LED_YELLOW_HALF = 14;
  public static final int LED_YELLOW_DIM = 15;
  public static final int LED_PALE_GREEN_BLUE = 16;
  public static final int LED_GREEN_HALF = 17; // And 18, 21, 22, 25, 26
  public static final int LED_GREEN = 19; /// And 23, 27
  public static final int LED_PALE_BLUE_GREEN = 20; // And 24
  public static final int LED_AQUA = 28;
  public static final int LED_AQUA_HALF = 29; // And 30
  public static final int LED_AQUA_DIM = 31;
  public static final int LED_BLUE_AQUA = 32;
  public static final int LED_BLUE_AQUA2 = 33; // If you have a better name, send a PR
  public static final int LED_BLUE_AQUA_HALF = 34;
  public static final int LED_BLUE_AQUA_DIM = 35;
  public static final int LED_SKY = 36;
  public static final int LED_AZURE = 37;
  public static final int LED_AZURE_HALF = 38;
  public static final int LED_AZURE_DIM = 39;
  public static final int LED_PERIWINKLE = 40;
  // 41-47 are more shades of blue than I can name
  public static final int LED_LAVENDER = 48;
  public static final int LED_PURPLE = 49;
  public static final int LED_PURPLE_HALF = 50;
  public static final int LED_PURPLE_DIM = 51;
  public static final int LED_PALE_MAGENTA = 52;
  public static final int LED_MAGENTA = 53;
  public static final int LED_MAGENTA_HALF = 54;
  public static final int LED_MAGENTA_DIM = 55;
  public static final int LED_MAGENTA_PINK = 56;
  public static final int LED_HOT_PINK = 57;
  public static final int LED_HOT_PINK_HALF = 58;
  public static final int LED_HOT_PINK_DIM = 59;
  public static final int LED_GOLDENROD = 62;
  public static final int LED_LAWN_GREEN = 63;
  // There are 64 more colors, but they seem like dupes.
  // There might be a small number of slightly unique ones in that range.

  // Holds the colors that will be sent out to the device. This array
  // starts with (0,0), (0,1), ... (7,7) of the LEFT page, and then
  // the same ordering for the RIGHT page.
  private int[] colors;

  // Converts a MIDI note from the MF64 into information about which
  // button was pressed
  public class Mapping {
    // If an unexpected note is received, this is set to false
    // and all other attributes should be ignored.
    public boolean valid;
    // The MF64 has left and right virtual button pages.
    public enum Page {
      LEFT, RIGHT;
    }
    public Page page;
    // 0 is the bottom row, 7 the top.
    public int row;
    // 0 is the left column, 7 the right
    public int col;

    public Mapping(MidiNoteOn note) {
      int pitch = note.getPitch();
      int channel = note.getChannel();
      this.valid = true;
      if (channel == 2) {
        this.page = Page.LEFT;
      } else if (channel == 1) {
        this.page = Page.RIGHT;
      } else {
        LX.warning("Got wild-channel MIDI note " + note);
        this.valid = false;
        return;
      }

      if (pitch >= 36 && pitch <= 67) {
        this.row = (pitch / 4) - 9;
        this.col = pitch % 4;
      } else if (pitch >= 68 && pitch <= 99) {
        this.row = (pitch / 4) - 17;
        this.col = pitch % 4 + 4;
      } else {
        LX.warning("Got wild-pitch MIDI note " + note);
        this.valid = false;
      }
    }
  }

  public void setColors(int[] newColors) {
    if (newColors.length != 128) {
      throw new IllegalArgumentException("newColors is of length " + newColors.length);
    }
    for (int i = 0; i < 128; i++) {
      if (newColors[i] < 0 || newColors[i] > 127) {
        throw new IllegalArgumentException("newColors contained value " + newColors[i]);
      }
    }
    System.arraycopy(newColors, 0, this.colors, 0, 128);
    this.sendColors();
  }

  private void sendColors() {
    int ci = 0;
    for (int channel = 2; channel >= 1; channel--) {
      for (int i = 0; i < 64; i++) {
        this.sendNoteOn(channel, pitchFromXY[i], this.colors[ci++]);
      }
    }
  }

  public MidiFighter64(LX lx, LXMidiInput input, LXMidiOutput output) {
    super(lx, input, output);
    this.colors = new int[8*8*2];
    Arrays.fill(this.colors, 0, 64, LED_WARM_WHITE);
    Arrays.fill(this.colors, 64, 128, LED_RED_DIM);
  }

  @Override
  protected void onEnable(boolean on) {
    this.sendColors();
  }

  @Override
  protected void onReconnect() {
    if (this.enabled.isOn()) {
      initialize(true);
    }
  }

  private void initialize(boolean reconnect) {
    if (reconnect) {
      this.sendColors();
    }
  }

  @Override
  public void noteOnReceived(MidiNoteOn note) {
    Mapping mapping = new Mapping(note);
    String pageStr = mapping.page == Mapping.Page.LEFT ? "left" : "right";
    LX.log("MIDI Fighter page=" + pageStr + " row=" + mapping.row + " col=" + mapping.col);
  }

  @Override
  public void noteOffReceived(MidiNote note) {
  }

  @Override
  public void dispose() {
    super.dispose();
  }
}
