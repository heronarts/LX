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
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.LXNormalizedParameter;
import heronarts.lx.utils.LXUtils;

import java.util.Map;

public abstract class ParameterClipLane extends LXClipLane {

  public static class Boolean extends ParameterClipLane {
    private Boolean(LXClip clip, BooleanParameter parameter, double initialNormalized) {
      super(clip, parameter, initialNormalized);
    }
  }

  public static class Discrete extends ParameterClipLane {
    private Discrete(LXClip clip, DiscreteParameter parameter, double initialNormalized) {
      super(clip, parameter, initialNormalized);
    }
  }

  public static class Normalized extends ParameterClipLane {
    private Normalized(LXClip clip, LXNormalizedParameter parameter, double initialNormalized) {
      super(clip, parameter, initialNormalized);
    }
  }

  static ParameterClipLane create(LXClip clip, LXNormalizedParameter parameter, double initialNormalized) {
    if (parameter instanceof BooleanParameter) {
      return new Boolean(clip, (BooleanParameter) parameter, initialNormalized);
    } else if (parameter instanceof DiscreteParameter) {
      return new Discrete(clip, (DiscreteParameter) parameter, initialNormalized);
    } else {
      return new Normalized(clip, parameter, initialNormalized);
    }
  }

  public final LXNormalizedParameter parameter;
  private double initialNormalized;

  private ParameterClipLane(LXClip clip, LXNormalizedParameter parameter, double initialNormalized) {
    super(clip);
    this.parameter = parameter;
    this.initialNormalized = initialNormalized;
  }

  @Override
  public String getLabel() {
    LXComponent component = this.parameter.getParent();
    if (component != this.clip.bus) {
      return component.getLabel() + " | " + this.parameter.getLabel();
    }
    return this.parameter.getLabel();
  }

  private static final double SMOOTHING_THRESHOLD_MS = 250;

  public ParameterClipLane appendEvent(ParameterClipEvent event) {
    final ParameterClipEvent previousEvent = (ParameterClipEvent) getPreviousEvent();
    if (previousEvent == null) {
      // On the first parameter automation, we need to drop a dot with the initial value
      // before this modified value, say a knob was at 0 when recording started, and a first
      // event comes in with value 50 many seconds later, the automation clip should not *only*
      // contain this value of 50, it should have 0 up to the point that the 50 is received and
      // then a jump (e.g. we also don't want a smooth interpolation from 0 to 50)
      super.insertEvent(new ParameterClipEvent(this, this.parameter, this.initialNormalized));
    } else if (this.clip.cursor - previousEvent.cursor > SMOOTHING_THRESHOLD_MS) {
      // For normalized parameters, check if there was a jump in value... for smoothly
      // received knob turns or MIDI that happen close in time we just record the event itself,
      // but if significant time has elapsed, then for the same reason as above, we need to
      // hold the previous value up to this point and then jump
      super.insertEvent(new ParameterClipEvent(this, this.parameter, previousEvent.getNormalized()));
    }

    // TODO(mcslee): this whole method should be recordEvent due to overdubbing, and the below cannot
    // just assume it's an append...
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
    } else if (this instanceof Normalized) {
      // Interpolate value between the two events surrounding us
      this.parameter.setNormalized(LXUtils.lerp(
        ((ParameterClipEvent) prior).getNormalized(),
        ((ParameterClipEvent) next).getNormalized(),
        (to - prior.cursor) / (next.cursor - prior.cursor)
      ));
    } else {
      // Stick with the prior value until next is actually reached
      this.parameter.setNormalized(((ParameterClipEvent) prior).getNormalized());
    }
  }

  public void setEventsNormalized(Map<ParameterClipEvent, Double> normalized) {
    boolean changed = false;
    for (Map.Entry<ParameterClipEvent, Double> entry : normalized.entrySet()) {
      ParameterClipEvent event = entry.getKey();
      if (this.events.contains(event)) {
        if (event._setNormalized(entry.getValue())) {
          changed = true;
        }
      } else {
        LX.error("ParameterClipLane.setEventsNormalized called with an event not in the events array: " + event);
      }
    }
    if (changed) {
      this.onChange.bang();
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

