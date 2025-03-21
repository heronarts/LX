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
import heronarts.lx.parameter.TriggerParameter;
import heronarts.lx.utils.LXUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public abstract class ParameterClipLane extends LXClipLane<ParameterClipEvent> {

  public static class Trigger extends ParameterClipLane {

    public final TriggerParameter triggerParameter;

    private Trigger(LXClip clip, TriggerParameter parameter) {
      super(clip, parameter, 0);
      this.triggerParameter = parameter;
    }

    @Override
    public boolean shouldRecordParameterChange(LXNormalizedParameter p) {
      // Trigger lanes only record positive trigger events!
      return (p == this.triggerParameter) && this.triggerParameter.isOn();
    }
  }

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
    if (parameter instanceof TriggerParameter) {
      return new Trigger(clip, (TriggerParameter) parameter);
    } else if (parameter instanceof BooleanParameter) {
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

  void updateDefaultValue(double initialNormalized) {
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

  public boolean shouldRecordParameterChange(LXNormalizedParameter p) {
    return true;
  }

  public boolean hasStitching() {
    return !(this instanceof Trigger);
  }

  public boolean hasInterpolation() {
    return (this instanceof Normalized);
  }

  public boolean isStepped() {
    return (this instanceof Boolean) || (this instanceof Discrete);
  }

  @Override
  protected void setEventNormalized(ParameterClipEvent event, double normalized) {
    event.setNormalized(normalized);
  }

  @Override
  protected void reverseEvents(List<ParameterClipEvent> events) {
    Collections.reverse(events);
    if (isStepped()) {
      // If we *reverse* discrete/boolean events, the values are NOT just a simple  mirror image!
      // This is because when a discrete automation event is encountered, the value abruptly changes
      // from prior->current.
      //
      // Say you have: A[0] -> B[1] -> C[2]
      // Timeline will be: [0-1):A, [1-2):B, [2]:C
      //
      // Reverse the points, you get: C[0] -> B[1] -> A[2]
      // Timeline will be [0-1):C, [1-2):B, [2]:A
      //
      // Note the problem - we've got a full time unit of C now and only an instant of A at the end
      // when what we were expecting was an instant of C and a full unit of A. What's happened here
      // is prior->current->next became next->current->prior. We need to actually shift the reversed
      // values over by one so that the prior->current relationship holds properly
      for (int i = 0; i < events.size() - 1; ++i) {
        events.get(i).setNormalized(events.get(i+1).getNormalized());
      }
    }
  }

  protected ParameterClipEvent stitchEvent(ParameterClipEvent prior, ParameterClipEvent next, Cursor cursor) {
    if (!hasStitching()) {
      return null;
    }
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
  protected ParameterClipEvent stitchInner(List<ParameterClipEvent> events, Cursor cursor, int stitchIndex, boolean isMin, boolean force) {
    if (!hasStitching()) {
      return null;
    }
    ParameterClipEvent prior = null;
    ParameterClipEvent next = null;
    if (stitchIndex > 0) {
      prior = events.get(stitchIndex - 1);
      if (!isMin && !force && CursorOp().isEqual(prior.cursor, cursor)) {
        return null;
      }
    }
    if (stitchIndex < events.size()) {
      next = events.get(stitchIndex);
      if (isMin && !force && CursorOp().isEqual(next.cursor, cursor)) {
        return null;
      }
    }
    if ((prior != null) && (next != null)) {
      return stitchEvent(prior, next, cursor);
    } else if (prior != null) {
      return new ParameterClipEvent(this, cursor, prior.getNormalized());
    } else if (next != null) {
      return new ParameterClipEvent(this, cursor, next.getNormalized());
    }
    return null;
  }

  @Override
  protected ParameterClipEvent stitchSelectionMin(List<ParameterClipEvent> originalEvents, List<ParameterClipEvent> modifiedEvents, Cursor selectionMin, int stitchIndex, boolean force) {
    if (!hasStitching()) {
      return null;
    }
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
    if (!hasStitching()) {
      return null;
    }
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

  @Override
  protected ParameterClipEvent stitchOuter(List<ParameterClipEvent> events, Cursor cursor, int rightIndex) {
    if (!hasStitching()) {
      return null;
    }
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
  protected int stitchInsertIfNeeded(List<ParameterClipEvent> events, ParameterClipEvent stitch, boolean after) {
    int index = after ? cursorInsertIndex(events, stitch.cursor) : cursorPlayIndex(events, stitch.cursor);
    if (stitchIsRedundant(events, stitch, index-1, index)) {
      return -1;
    }
    events.add(index, stitch);
    return index;
  }

  @Override
  protected boolean stitchRemoveIfRedundant(List<ParameterClipEvent> events, ParameterClipEvent stitch, int index) {
    if (stitch != null) {
      if (events.get(index) != stitch) {
        throw new IllegalStateException("stitchRemoveIfRedundant index was wrong");
      }
      if (stitchIsRedundant(events, stitch, index-1, index+1)) {
        events.remove(index);
        return true;
      }
    }
    return false;
  }

  public ParameterClipEvent insertEvent(Cursor cursor, double normalized) {
    ParameterClipEvent event = new ParameterClipEvent(this, normalized);
    event.setCursor(cursor);
    super.insertEvent(event);
    return event;
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

  // RECORDING CONTROL

  private static final double SMOOTHING_THRESHOLD_MS = 250;

  protected ParameterClipLane recordParameterEvent(ParameterClipEvent event) {
    if (hasStitching()) {
      final int insertIndex = cursorInsertIndex(this.clip.cursor);
      final ParameterClipEvent previousEvent = (insertIndex > 0) ? (ParameterClipEvent) this.events.get(insertIndex - 1) : null;
      if (previousEvent == null) {
        if (insertIndex < this.events.size()) {
          // There's data ahead of us but we are overdubbing behind it, preserve that properly
          ParameterClipEvent nextEvent = this.events.get(insertIndex);
          recordEvent(new ParameterClipEvent(this, nextEvent.getNormalized()));
        } else {
          // On the first parameter automation, we need to drop a dot with the initial value
          // before this modified value, say a knob was at 0 when recording started, and a first
          // event comes in with value 50 many seconds later, the automation clip should not *only*
          // contain this value of 50, it should have 0 up to the point that the 50 is received and
          // then a jump (e.g. we also don't want a smooth interpolation from 0 to 50)
          recordEvent(new ParameterClipEvent(this, this.initialNormalized));
        }
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
    }

    // Now record the actual event
    recordEvent(event);
    this.overdubActive = true;
    return this;
  }

  private boolean inPlayback = false;

  public boolean isInPlayback() {
    return this.inPlayback;
  }

  @Override
  void loopCursor(Cursor from, Cursor to) {
    if (this.overdubActive && hasStitching()) {
      // Stitch what was before the start of the loop to the value at the end of the loop
      if (!this.events.isEmpty()) {
        ParameterClipEvent stitchLoopStart = stitchOuter(this.mutableEvents, to, cursorPlayIndex(to));
        ParameterClipEvent stitchLoopEnd = stitchOuter(this.mutableEvents, to, cursorPlayIndex(from));
        if (hasInterpolation()) {
          recordEvent(stitchLoopStart);
        }
        recordEvent(stitchLoopEnd);
      }
    }
  }

  @Override
  void playCursor(Cursor from, Cursor to, boolean inclusive) {
    // Set a flag so we these don't trigger recording events
    this.inPlayback = true;

    if (this instanceof Trigger) {

      // Trigger events just fire in a basic way, no interpolated or stepped value stuff
      super.playCursor(from, to, inclusive);

    } else if (!this.events.isEmpty()) {

      // Boolean/Discrete/Normalized events always set value based upon envelope shape
      int nextIndex = LXUtils.min(cursorInsertIndex(to), this.events.size()-1);
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

    this.inPlayback = false;
  }

  @Override
  void overdubCursor(Cursor from, Cursor to, boolean inclusive) {
    final Cursor.Operator CursorOp = CursorOp();

    boolean changed = false;
    this.mutableEvents.begin();

    ParameterClipEvent stitchInner = null, stitchOuter = null;
    int stitchInnerIndex = -1;

    // Clear events in the [from-to] range, respecting inclusivity
    if (this.overdubActive && !this.mutableEvents.isEmpty()) {
      int startIndex = cursorPlayIndex(from);
      int endIndex = inclusive ? cursorInsertIndex(to) : cursorPlayIndex(to);

      if (CursorOp.isBefore(to, this.clip.length.cursor)) {
        stitchOuter = stitchOuter(this.events, to, endIndex);
      }

      if (endIndex > startIndex) {
        if (this.overdubActive) {
          this.mutableEvents.removeRange(startIndex, endIndex);
          changed = true;
        }
      }
    }

    // Insert the new events (presumably they are all at from)
    if (!this.recordQueue.isEmpty()) {
      commitRecordQueue(false);
      changed = true;
    }

    if (this.overdubActive && !this.mutableEvents.isEmpty() && hasStitching()) {
      // Okay, here we will have nuked everything original in [from, to] and
      // possibly inserted new events at from. If there are events ahead of us,
      // we need to preserve new overdubbed value until "to" and then jump
      // to whatever *would* have been at "to" prior to the overdub
      stitchInnerIndex = cursorPlayIndex(to);
      if (stitchInnerIndex > 0) {
        ParameterClipEvent innerEvent = this.events.get(stitchInnerIndex - 1);
        if (CursorOp.isBefore(innerEvent.cursor, to)) {
          stitchInner = new ParameterClipEvent(this, to, innerEvent.getNormalized());
          this.mutableEvents.add(stitchInnerIndex, stitchInner);
          changed = true;
        }
      }
    }

    // Now play back the range with edits applied (not outer stitch)
    playCursor(from, to, inclusive);

    // Add the outer stitch if it's not pointless
    if (stitchOuter != null) {
      stitchInsertIfNeeded(this.mutableEvents, stitchOuter, true);
    }

    // Kill inner stitch if unneeded
    if (stitchInner != null) {
      stitchRemoveIfRedundant(this.mutableEvents, stitchInner, stitchInnerIndex);
    }

    this.mutableEvents.commit();
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

