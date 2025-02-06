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

    public final DiscreteParameter discreteParameter;

    private Discrete(LXClip clip, DiscreteParameter parameter, double initialNormalized) {
      super(clip, parameter, initialNormalized);
      this.discreteParameter = parameter;
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

  protected ParameterClipLane recordParameterEvent(ParameterClipEvent event) {
    final int insertIndex = cursorInsertIndex(this.clip.cursor);
    final ParameterClipEvent previousEvent = (insertIndex > 0) ? (ParameterClipEvent) this.events.get(insertIndex - 1) : null;
    if (previousEvent == null) {
      // On the first parameter automation, we need to drop a dot with the initial value
      // before this modified value, say a knob was at 0 when recording started, and a first
      // event comes in with value 50 many seconds later, the automation clip should not *only*
      // contain this value of 50, it should have 0 up to the point that the 50 is received and
      // then a jump (e.g. we also don't want a smooth interpolation from 0 to 50)
      recordEvent(new ParameterClipEvent(this, this.parameter, this.initialNormalized));
    } else if ((this instanceof Normalized) && (this.clip.cursor - previousEvent.cursor > SMOOTHING_THRESHOLD_MS)) {
      // For normalized parameters, check if there was a jump in value... for smoothly
      // received knob turns or MIDI that happen close in time we just record the event itself,
      // but if significant time has elapsed, then for the same reason as above, we need to
      // record whatever value the envelope would have held at this point
      double normalized = 0;
      if (insertIndex < this.events.size()) {
        // If there's an event ahead of the previous event, preserve the interpolation between
        // the two
        final ParameterClipEvent nextEvent = (ParameterClipEvent) this.events.get(insertIndex);
        normalized = LXUtils.lerp(
          previousEvent.getNormalized(),
          nextEvent.getNormalized(),
          (this.clip.cursor - previousEvent.cursor) / (nextEvent.cursor - previousEvent.cursor)
        );
      } else {
        normalized = previousEvent.getNormalized();
      }
      recordEvent(new ParameterClipEvent(this, this.parameter, normalized));
    }
    recordEvent(event);
    this.overdubActive = true;
    return this;
  }

  public ParameterClipLane insertEvent(double basis, double normalized) {
    super.insertEvent(
      new ParameterClipEvent(this, this.parameter, normalized)
      .setCursor(basis * this.clip.length.getValue())
    );
    return this;
  }

  private boolean inOverdubPlayback = false;

  public boolean isInOverdubPlayback() {
    return this.inOverdubPlayback;
  }

  @Override
  void postOverdubCursor(double from, double to) {
    if (this.overdubActive) {
      // Okay, here we will have nuked everything original in [from, to) and
      // possibly inserted new events at from. If there are events ahead of us,
      // we need to preserve new overdubbed value until "to" and then jump
      // to whatever *would* have been at "to" prior to the overdub

      int index = cursorPlayIndex(to);
      if (index < this.events.size()) {
        // If there are still pre-overdub events ahead, we need to connect the new overdub recording
        // to the old stuff that existed prior

        // There must be something behind us if overdub was active... extend that value
        if (index > 1) {
          ParameterClipEvent previous = (ParameterClipEvent) this.events.get(index - 1);
          this.mutableEvents.add(index++, new ParameterClipEvent(this, this.parameter, previous.getNormalized()).setCursor(to));
        }

        // Interpolate what the deleted stuff would have looked like
        ParameterClipEvent next = (ParameterClipEvent) this.events.get(index);
        if (next.cursor > to) {
          double normalizedValue = 0;
          if (this.overdubLastOriginalEvent == null) {
            // There was no original stuff before this, just jump to the new value
            normalizedValue = next.getNormalized();
          } else {
            // There was original stuff before this! Figure out what the interpolation would have been
            ParameterClipEvent prior = (ParameterClipEvent) this.overdubLastOriginalEvent;
            normalizedValue = LXUtils.lerp(
              prior.getNormalized(),
              next.getNormalized(),
              (to - prior.cursor) / (next.cursor - prior.cursor)
            );
          }

          // Add the new event at "to"
          this.mutableEvents.add(index, new ParameterClipEvent(this, this.parameter, normalizedValue).setCursor(to));
        }
      }
    } else {
      // No overdubs happening? Play back the automation! Set a flag so we suppress
      // recording changes due to the parameter listeners that will file...
      this.inOverdubPlayback = true;
      advanceCursor(from, to);
      this.inOverdubPlayback = false;
    }
  }

  @Override
  void advanceCursor(double from, double to) {
    int size = this.events.size();
    if (size == 0) {
      return;
    }
    int nextIndex = LXUtils.min(cursorInsertIndex(to), size-1);
    LXClipEvent next = this.events.get(nextIndex);
    LXClipEvent prior = (nextIndex > 0) ? this.events.get(nextIndex - 1) : null;

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

