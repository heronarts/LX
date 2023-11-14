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

import com.google.gson.JsonObject;

import heronarts.lx.LX;
import heronarts.lx.LXSerializable;
import heronarts.lx.clip.LXClip;
import heronarts.lx.clip.LXMasterClip;
import heronarts.lx.parameter.EnumParameter;

/**
 * Represents the master channel. Doesn't do anything special
 * that a normal bus does not.
 */
public class LXMasterBus extends LXBus {

  public enum PreviewMode {
    /**
     * The preview is generated before the master fader is applied by the mixer,
     * so the UI always shows a full brightness render, even if the fader is being
     * used to dim the whole animation.
     */
    PRE("Pre"),

    /**
     * The preview is generated after the master fader, so the UI may be dim or
     * completely off if the master fader is reducing the animation level.
     */
    POST("Post");

    private final String label;

    private PreviewMode(String label) {
      this.label = label;
    }

    @Override
    public String toString() {
      return this.label;
    }
  }

  public final EnumParameter<PreviewMode> previewMode =
    new EnumParameter<PreviewMode>("Preview Mode", PreviewMode.PRE)
    .setDescription("Whether the preview is generated PRE or POST the master fader");

  public LXMasterBus(LX lx) {
    super(lx, "Master");
    addParameter("previewMode", this.previewMode);
  }

  /**
   * Retrieves the amount of brightness scaling that should be applied to the output.
   * This depends upon the master fader mode, whether its scaling has already been applied
   * or not.
   *
   * @return Brightness scaling to be applied for the output stage
   */
  public double getOutputBrightness() {
    switch (this.previewMode.getEnum()) {
    case PRE:
      // Mixer scaling not yet applied, chain it to output level
      return fader.getValue();
    default:
    case POST:
      // Mixer scaling is already applied, don't repeat it
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

  @Override
  public void postProcessPreset(LX lx, JsonObject obj) {
    super.postProcessPreset(lx, obj);
    LXSerializable.Utils.stripParameter(obj, this.previewMode);
  }

}
