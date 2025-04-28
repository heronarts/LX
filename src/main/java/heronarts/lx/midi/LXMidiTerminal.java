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
 * Base level abstraction for a Midi object, which can be a source or destination either
 * of the real hardware variety (subclasses of LXMidiDevice) or of a virtual variety,
 * such as those in the LXMidiSource and LXMidiDestination interfaces
 */
public interface LXMidiTerminal {

  /**
   * Retrieves the midi device associated with the terminal, if there is one.
   * May be null if this is a virtual terminal, like LXMidiSource.UNKNOWN
   *
   * @return Midi device, if available
   */
  public default LXMidiDevice getMidiDevice() {
    return (this instanceof LXMidiDevice) ? (LXMidiDevice) this : null;
  }

  /**
   * Returns the midi terminal name
   *
   * @return Name
   */
  public default String getName() {
    return toString();
  }
}
