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

package heronarts.lx;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.TriggerParameter;

public class LXEngineUtilities {

  public static final int NUM_CAMERA_POSITIONS = 6;

  final LXParameter.Collection parameters = new LXParameter.Collection();

  public final List<TriggerParameter> recallCameraPosition;

  LXEngineUtilities() {
    final List<TriggerParameter> recallCameraPosition = new ArrayList<>();
    for (int i = 0; i < NUM_CAMERA_POSITIONS; ++i) {
      final TriggerParameter trigger = new TriggerParameter("Recall Camera " + (i+1));
      recallCameraPosition.add(trigger);
      this.parameters.add("camera-" + (i+1), trigger);
    }
    this.recallCameraPosition = Collections.unmodifiableList(recallCameraPosition);
  }

}
