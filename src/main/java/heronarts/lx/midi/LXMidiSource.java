/**
 * Copyright 2024- Mark C. Slee, Heron Arts LLC
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

package heronarts.lx.midi;

/**
 * Interface that provides an abstract notion of a MIDI source. This would most
 * typically be a LXMidiInput from a device, but it can also be something virtual
 * like the computer keyboard, OSC input, or the abstract notion of "any MIDI inputs"
 */
public interface LXMidiSource extends LXMidiTerminal {

  public default boolean matches(LXMidiSource that) {
    return this == that;
  }


  public final static LXMidiSource UNKNOWN = new LXMidiSource() {
    @Override
    public String toString() {
      return "Unknown";
    }
  };

  public final static LXMidiSource NONE = new LXMidiSource() {
    @Override
    public String toString() {
      return "None";
    }

    @Override
    public boolean matches(LXMidiSource that) {
      return false;
    }
  };

  public final static LXMidiSource ALL_INS = new LXMidiSource() {
    @Override
    public String toString() {
      return "All Ins";
    }

    @Override
    public boolean matches(LXMidiSource that) {
      return true;
    }
  };

  public final static LXMidiSource COMPUTER_KEYBOARD = new LXMidiSource() {
    @Override
    public String toString() {
      return "Computer Keyboard";
    }
  };

  public final static LXMidiSource OSC = new LXMidiSource() {
    @Override
    public String toString() {
      return "OSC In";
    }
  };
}
