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

import java.util.List;
import java.util.Map;

public abstract class ParameterClipLane extends LXClipLane<ParameterClipEvent> {

  public static class Boolean extends ParameterClipLane {

    public final BooleanParameter booleanParameter;

    private Boolean(LXClip clip, BooleanParameter parameter, double initialNormalized) {
      super(clip, parameter, initialNormalized);
      this.booleanParameter = parameter;
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

  public boolean hasInterpolation() {
    return (this instanceof Normalized);
  }

  protected ParameterClipEvent stitchEvent(ParameterClipEvent prior, ParameterClipEvent next, Cursor cursor) {
    if (prior == null || next == null) {
      return null;
    }
    if (hasInterpolation()) {
      return new ParameterClipEvent(this, cursor, LXUtils.lerp(
        prior.getNormalized(),
        next.getNormalized(),
        CursorOp().getLerpFactor(cursor, prior.cursor, next.cursor)
      ));
    }
    return new ParameterClipEvent(this, cursor, CursorOp().isAfterOrEqual(cursor, next.cursor) ? next.getNormalized() : prior.getNormalized());
  }

  @Override
  protected ParameterClipEvent stitchSelectionMin(List<ParameterClipEvent> originalEvents, List<ParameterClipEvent> modifiedEvents, Cursor selectionMin, int stitchIndex, boolean force) {
    ParameterClipEvent prior = null;
    ParameterClipEvent next = null;
    if (stitchIndex > 0) {
      prior = originalEvents.get(stitchIndex - 1);
    }
    if (!modifiedEvents.isEmpty()) {
      next = modifiedEvents.get(0);
      if (!force && CursorOp().isEqual(next.cursor, selectionMin)) {
        return null;
      }
    } else if (stitchIndex < originalEvents.size()) {
      next = originalEvents.get(stitchIndex);
    }
    if (prior == null && (next != null)) {
      // There was nothing before the selection, stitch to the next event
      return new ParameterClipEvent(this, selectionMin, next.getNormalized());
    }
    return stitchEvent(prior, next, selectionMin);
  }

  @Override
  protected ParameterClipEvent stitchSelectionMax(List<ParameterClipEvent> originalEvents, List<ParameterClipEvent> modifiedEvents, Cursor selectionMax, int stitchIndex, boolean force) {
    ParameterClipEvent prior = null;
    ParameterClipEvent next = null;
    if (stitchIndex < originalEvents.size()) {
      next = originalEvents.get(stitchIndex);
    }
    if (!modifiedEvents.isEmpty()) {
      prior = modifiedEvents.get(modifiedEvents.size() - 1);
      if (!force && CursorOp().isEqual(prior.cursor, selectionMax)) {
        return null;
      }
    } else if (stitchIndex > 0) {
      prior = originalEvents.get(stitchIndex - 1);
    }
    if (next == null && (prior != null)) {
      // There was nothing after the selection, stitch to the prior event
      return new ParameterClipEvent(this, selectionMax, prior.getNormalized());
    }
    return stitchEvent(prior, next, selectionMax);
  }

  private ParameterClipEvent stitchOuter(List<ParameterClipEvent> events, Cursor cursor, int rightIndex) {
    if (events.isEmpty()) {
      return null;
    }
    if (rightIndex > 0 && rightIndex < events.size()) {
      return stitchEvent(
        events.get(rightIndex - 1),
        events.get(rightIndex),
        cursor
      );
    } else if (rightIndex == 0) {
      return new ParameterClipEvent(this, cursor, events.get(rightIndex).getNormalized());
    } else if (rightIndex == events.size()) {
      return new ParameterClipEvent(this, cursor, events.get(rightIndex - 1).getNormalized());
    }
    return null;
  }

  @Override
  protected ParameterClipEvent stitchOuterMin(List<ParameterClipEvent> events, Cursor selectionMin) {
    return stitchOuter(events, selectionMin, cursorPlayIndex(events, selectionMin));
  }

  @Override
  protected ParameterClipEvent stitchOuterMax(List<ParameterClipEvent> events, Cursor selectionMax) {
    return stitchOuter(events, selectionMax, cursorInsertIndex(events, selectionMax));
  }

  private boolean stitchIsRedundant(List<ParameterClipEvent> events, ParameterClipEvent stitch, int priorIndex, int nextIndex) {
    if (events.isEmpty()) {
      return false;
    }
    ParameterClipEvent prior = null;
    ParameterClipEvent next = null;
    boolean equalsPrior = true;
    boolean equalsNext = true;
    double stitchNormalized = stitch.getNormalized();

    if (priorIndex >= 0) {
      prior = events.get(priorIndex);
      equalsPrior = (stitchNormalized == prior.getNormalized());
      if (equalsPrior && (!hasInterpolation() || CursorOp().isEqual(stitch.cursor, prior.cursor))) {
        // Redundant point that matches the prior
        return true;
      }
    }
    if (nextIndex < events.size()) {
      next = events.get(nextIndex);
      equalsNext = (stitchNormalized == next.getNormalized());
      if (equalsNext && CursorOp().isEqual(stitch.cursor, next.cursor)) {
        // Redundant point that matches the next
        return true;
      }
    }
    if (equalsPrior && equalsNext) {
      // Useless point between two others of equal value
      return true;
    }
    return false;
  }

  @Override
  protected boolean stitchInsertIfNeeded(List<ParameterClipEvent> events, ParameterClipEvent stitch, int index) {
    if (stitchIsRedundant(events, stitch, index-1, index)) {
      return false;
    }
    events.add(index, stitch);
    return true;
  }

  @Override
  protected boolean stitchRemoveIfRedundant(List<ParameterClipEvent> events, ParameterClipEvent stitch, int index) {
    if (stitch != null) {
      if (stitchIsRedundant(events, stitch, index-1, index+1)) {
        events.remove(index);
        return true;
      }
    }
    return false;
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
      recordEvent(new ParameterClipEvent(this, this.initialNormalized));
    } else if (hasInterpolation() && (this.clip.cursor.getDeltaMillis(previousEvent.cursor) > SMOOTHING_THRESHOLD_MS)) {
      // For normalized parameters, check if there was a jump in value... for smoothly
      // received knob turns or MIDI that happen close in time we just record the event itself,
      // but if significant time has elapsed, then for the same reason as above, we need to
      // record whatever value the envelope would have held at this point
      double normalized = 0;
      if (insertIndex < this.events.size()) {
        // If there's an event ahead of the previous event, preserve the interpolation between
        // the two
        final ParameterClipEvent nextEvent = this.events.get(insertIndex);
        normalized = LXUtils.lerp(
          previousEvent.getNormalized(),
          nextEvent.getNormalized(),
          CursorOp().getLerpFactor(this.clip.cursor, previousEvent.cursor, nextEvent.cursor)
        );
      } else {
        normalized = previousEvent.getNormalized();
      }
      recordEvent(new ParameterClipEvent(this, normalized));
    }
    recordEvent(event);
    this.overdubActive = true;
    return this;
  }

  public ParameterClipEvent insertEvent(Cursor cursor, double normalized) {
    ParameterClipEvent event = new ParameterClipEvent(this, normalized);
    event.setCursor(cursor);
    super.insertEvent(event);
    return event;
  }

  private boolean inOverdubPlayback = false;

  public boolean isInOverdubPlayback() {
    return this.inOverdubPlayback;
  }

  @Override
  void postOverdubCursor(Cursor from, Cursor to) {
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
          ParameterClipEvent previous = this.events.get(index - 1);
          ParameterClipEvent event = new ParameterClipEvent(this, previous.getNormalized());
          event.setCursor(to);
          this.mutableEvents.add(index++, event);
        }

        // Interpolate what the deleted stuff would have looked like
        ParameterClipEvent next = this.events.get(index);
        if (CursorOp().isAfter(next.cursor, to)) {
          double normalizedValue = 0;
          if (this.overdubLastOriginalEvent == null) {
            // There was no original stuff before this, just jump to the new value
            normalizedValue = next.getNormalized();
          } else {
            // There was original stuff before this! Figure out what the interpolation would have been
            ParameterClipEvent prior = this.overdubLastOriginalEvent;
            normalizedValue = LXUtils.lerp(
              prior.getNormalized(),
              next.getNormalized(),
              CursorOp().getLerpFactor(to, prior.cursor, next.cursor)
            );
          }

          // Add the new event at "to"
          ParameterClipEvent event = new ParameterClipEvent(this, normalizedValue);
          event.setCursor(to);
          this.mutableEvents.add(index, event);
        }
      }
    } else {
      // No overdubs happening? Play back the automation! Set a flag so we suppress
      // recording changes due to the parameter listeners that will file...
      this.inOverdubPlayback = true;
      advanceCursor(from, to, false);
      this.inOverdubPlayback = false;
    }
  }

  @Override
  void advanceCursor(Cursor from, Cursor to, boolean inclusive) {
    int size = this.events.size();
    if (size == 0) {
      return;
    }
    int nextIndex = LXUtils.min(cursorInsertIndex(to), size-1);
    ParameterClipEvent next = this.events.get(nextIndex);
    ParameterClipEvent prior = (nextIndex > 0) ? this.events.get(nextIndex - 1) : null;

    if (CursorOp().isAfter(from, next.cursor)) {
      // Do nothing, we've already passed it all
    } else if (prior == null) {
      // Nothing before us, set the first value
      this.parameter.setNormalized(next.getNormalized());
    } else if (CursorOp().isAfter(to, next.cursor)) {
      // We're past the last event, just set its value
      this.parameter.setNormalized(next.getNormalized());
    } else if (hasInterpolation()) {
      // Interpolate value between the two events surrounding us
      this.parameter.setNormalized(LXUtils.lerp(
        prior.getNormalized(),
        next.getNormalized(),
        CursorOp().getLerpFactor(to, prior.cursor, next.cursor)
      ));
    } else {
      // Stick with the prior value until next is actually reached
      this.parameter.setNormalized(prior.getNormalized());
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
  protected ParameterClipEvent loadEvent(LX lx, JsonObject eventObj) {
    double normalized = eventObj.get(ParameterClipEvent.KEY_NORMALIZED).getAsDouble();
    return new ParameterClipEvent(this, normalized);
  }
}

