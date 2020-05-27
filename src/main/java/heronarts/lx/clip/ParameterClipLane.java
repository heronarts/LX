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
 * @author Mark C. Slee <mark@heronarts.com>
 */

package heronarts.lx.clip;

import com.google.gson.JsonObject;

import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import heronarts.lx.parameter.LXNormalizedParameter;
import heronarts.lx.utils.LXUtils;

public class ParameterClipLane extends LXClipLane {

  public final LXNormalizedParameter parameter;

  ParameterClipLane(LXClip clip, LXNormalizedParameter parameter) {
    super(clip);
    this.parameter = parameter;
  }

  @Override
  public String getLabel() {
    LXComponent component = this.parameter.getParent();
    if (component != this.clip.bus) {
      return component.getLabel() + " | " + this.parameter.getLabel();
    }
    return this.parameter.getLabel();
  }

  public ParameterClipLane appendEvent(ParameterClipEvent event) {
    super.appendEvent(event);
    return this;
  }

  public ParameterClipLane insertEvent(double basis, double normalized) {
    super.insertEvent(
      new ParameterClipEvent(this, this.parameter, normalized)
      .setCursor(basis * this.clip.length.getValue())
    );
    return this;
  }

  @Override
  void advanceCursor(double from, double to) {
    if (this.events.size() == 0) {
      return;
    }
    LXClipEvent prior = null;
    LXClipEvent next = null;
    for (LXClipEvent event : this.events) {
      prior = next;
      next = event;
      if (to < next.cursor) {
        break;
      }
    }
    if (from > next.cursor) {
      // Do nothing, we've already passed it all
    } else if (prior == null) {
      // Nothing before us, set the first value
      this.parameter.setNormalized(((ParameterClipEvent) next).getNormalized());
    } else if (to > next.cursor) {
      // We're past the last event, just set its value
      this.parameter.setNormalized(((ParameterClipEvent) next).getNormalized());
    } else {
      // Interpolate value between the two events surrounding usgs
      this.parameter.setNormalized(LXUtils.lerp(
        ((ParameterClipEvent) prior).getNormalized(),
        ((ParameterClipEvent) next).getNormalized(),
        (to - prior.cursor) / (next.cursor - prior.cursor)
      ));
    }
  }

  @Override
  public void save(LX lx, JsonObject obj) {
    super.save(lx, obj);
    obj.addProperty(LXComponent.KEY_PATH, this.parameter.getCanonicalPath(this.clip.bus));
    obj.addProperty(LXComponent.KEY_COMPONENT_ID, this.parameter.getParent().getId());
    obj.addProperty(LXComponent.KEY_PARAMETER_PATH, this.parameter.getPath());
  }

  @Override
  protected LXClipEvent loadEvent(LX lx, JsonObject eventObj) {
    double normalized = eventObj.get(ParameterClipEvent.KEY_NORMALIZED).getAsDouble();
    return new ParameterClipEvent(this, this.parameter, normalized);
  }
}

