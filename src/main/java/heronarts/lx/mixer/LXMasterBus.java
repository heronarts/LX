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

package heronarts.lx.mixer;

import heronarts.lx.LX;
import heronarts.lx.clip.LXClip;
import heronarts.lx.clip.LXMasterClip;
import heronarts.lx.parameter.EnumParameter;

/**
 * Represents the master channel. Doesn't do anything special
 * that a normal bus does not.
 */
public class LXMasterBus extends LXBus {

  public enum FaderMode {
    /**
     * The master fader is applied by the mixer, pre-visualization and output.
     * It will be reflected in the UI.
     */
    PRE("Pre"),

    /**
     * The master fader is applied after the visualizer. Its effect won't be
     * seen in the UI, output will need to be scaled accordingly.
     */
    POST("Post");

    private final String label;

    private FaderMode(String label) {
      this.label = label;
    }

    @Override
    public String toString() {
      return this.label;
    }
  }

  public final EnumParameter<FaderMode> faderMode =
    new EnumParameter<FaderMode>("Fader Mode", FaderMode.PRE)
    .setDescription("Whether the master fader is applied pre or post-visualizer");

  public LXMasterBus(LX lx) {
    super(lx, "Master");
    addParameter("faderMode", this.faderMode);
  }

  /**
   * Retrieves the amount of brightness scaling that should be applied to the output.
   * This depends upon the master fader mode, whether its scaling has already been applied
   * or not.
   *
   * @return Brightness scaling to be applied for the output stage
   */
  public double getOutputBrightness() {
    switch (this.faderMode.getEnum()) {
    case POST:
      // The mixer didn't do any master scaling anything, apply it at output
      return fader.getValue();
    default:
    case PRE:
      // The mixer applied scaling pre-output, don't do it again
      return 1.;
    }
  }

  @Override
  public int getIndex() {
    return lx.engine.mixer.channels.size();
  }

  @Override
  public String getPath() {
    return "master";
  }

  @Override
  protected LXClip constructClip(int index) {
    return new LXMasterClip(this.lx, index);
  }
}
