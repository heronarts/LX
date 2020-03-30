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
 * ##library.name##
 * ##library.sentence##
 * ##library.url##
 *
 * @author      ##author##
 * @modified    ##date##
 * @version     ##library.prettyVersion## (##library.version##)
 */

package heronarts.lx.clip;

import heronarts.lx.LX;
import heronarts.lx.mixer.LXAbstractChannel;

public class LXChannelBusClip extends LXClip {

  public final LXAbstractChannel channel;

  public LXChannelBusClip(LX lx, LXAbstractChannel channel, int index, boolean registerListener) {
    super(lx, channel, index, registerListener);

    this.channel = channel;
    channel.fader.addListener(this.parameterRecorder);
    channel.enabled.addListener(this.parameterRecorder);
  }

  @Override
  public void dispose() {
    this.channel.fader.removeListener(this.parameterRecorder);
    this.channel.enabled.removeListener(this.parameterRecorder);
    super.dispose();
  }

}
