/**
 * Copyright 2024- Justin K. Belcher, Heron Arts LLC
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
 * @author Justin K. Belcher <jkbelcher@gmail.com>
 */

package heronarts.lx.midi;

/**
 * Common interface for messages that are passed through the midi input queue
 */
public interface LXMidiMessage {

  public LXMidiMessage setSource(LXMidiSource source);

  public LXMidiSource getSource();

  public default LXMidiInput getInput() {
    LXMidiSource source = getSource();
    return (source instanceof LXMidiInput) ? (LXMidiInput) source : null;
  }

  public void dispatch(LXMidiListener listener);

}
