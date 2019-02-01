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

import heronarts.lx.midi.MidiAftertouch;
import heronarts.lx.midi.MidiControlChange;

import com.google.gson.JsonObject;

import heronarts.lx.midi.LXMidiListener;
import heronarts.lx.midi.MidiNote;
import heronarts.lx.midi.MidiNoteOn;
import heronarts.lx.midi.MidiPitchBend;
import heronarts.lx.midi.MidiProgramChange;
import heronarts.lx.osc.LXOscComponent;
import heronarts.lx.parameter.BooleanParameter;

/**
 * A pattern is the core object that the animation engine uses to generate
 * colors for all the points.
 */
public abstract class LXPattern extends LXDeviceComponent implements LXComponent.Renamable, LXLayeredComponent.Buffered, LXMidiListener, LXOscComponent {

  private int index = -1;

  private int intervalBegin = -1;

  private int intervalEnd = -1;

  public final BooleanParameter autoCycleEligible = new BooleanParameter("Cycle", true);

  protected double runMs = 0;

  public final Timer timer = new Timer();

  public class Timer {
    public long runNanos = 0;
  }

  protected LXPattern(LX lx) {
    super(lx);
    this.label.setDescription("The name of this pattern");
  }

  @Override
  public String getPath() {
    return LXChannel.PATH_PATTERN + "/" + (this.index + 1);
  }

  void setIndex(int index) {
    this.index = index;
  }

  public int getIndex() {
    return this.index;
  }

  /**
   * Gets the channel that this pattern is loaded in. May be null if the pattern is
   * not yet loaded onto any channel.
   *
   * @return Channel pattern is loaded onto
   */
  public final LXChannel getChannel() {
    return (LXChannel) getParent();
  }

  /**
   * Called by the engine when pattern is loaded onto a channel. This may only be
   * called once, by the engine. Do not call directly.
   *
   * @param channel Channel pattern is loaded onto
   * @return this
   */
  final LXPattern setChannel(LXChannel channel) {
    setParent(channel);
    return this;
  }

  /**
   * Set an interval during which this pattern is allowed to run. Begin and end
   * times are specified in minutes of the daytime. So midnight corresponds to
   * the value of 0, 360 would be 6:00am, 1080 would be 18:00 (or 6:00pm)
   *
   * @param begin Interval start time
   * @param end Interval end time
   * @return this
   */
  public LXPattern setInterval(int begin, int end) {
    this.intervalBegin = begin;
    this.intervalEnd = end;
    return this;
  }

  /**
   * Clears a timer interval set to this pattern.
   *
   * @return this
   */
  public LXPattern clearInterval() {
    this.intervalBegin = this.intervalEnd = -1;
    return this;
  }

  /**
   * Tests whether there is an interval for this pattern.
   *
   * @return true if there is an interval
   */
  public final boolean hasInterval() {
    return (this.intervalBegin >= 0) && (this.intervalEnd >= 0);
  }

  /**
   * Tests whether this pattern is in an eligible interval.
   *
   * @return true if the pattern has an interval, and is currently in it.
   */
  public final boolean isInInterval() {
    if (!this.hasInterval()) {
      return false;
    }
    int now = LXTime.hour() * 60 + LXTime.minute();
    if (this.intervalBegin < this.intervalEnd) {
      // Normal daytime interval
      return (now >= this.intervalBegin) && (now < this.intervalEnd);
    } else {
      // Wrapping around midnight
      return (now >= this.intervalBegin) || (now < this.intervalEnd);
    }
  }

  /**
   * Sets whether this pattern is eligible for automatic selection.
   *
   * @param eligible Whether eligible for auto-rotation
   * @return this
   */
  public final LXPattern setAutoCycleEligible(boolean eligible) {
    this.autoCycleEligible.setValue(eligible);
    return this;
  }

  /**
   * Toggles the eligibility state of this pattern.
   *
   * @return this
   */
  public final LXPattern toggleAutoCycleEligible() {
    this.autoCycleEligible.toggle();
    return this;
  }

  /**
   * Determines whether this pattern is eligible to be run at the moment. A
   * pattern is eligible if its eligibility flag has not been set to false, and
   * if it either has no interval, or is currently in its interval.
   *
   * @return True if pattern is eligible to run now
   */
  public final boolean isAutoCycleEligible() {
    return this.autoCycleEligible.isOn() && (!this.hasInterval() || this.isInInterval());
  }

  @Override
  protected final void onLoop(double deltaMs) {
    long runStart = System.nanoTime();
    this.runMs += deltaMs;
    this.run(deltaMs);
    this.timer.runNanos = System.nanoTime() - runStart;
  }

  /**
   * Main pattern loop function. Invoked in a render loop. Subclasses must
   * implement this function.
   *
   * @param deltaMs Number of milliseconds elapsed since last invocation
   */
  protected abstract void run(double deltaMs);

  /**
   * Subclasses may override this method. It will be invoked when the pattern is
   * about to become active. Patterns may take care of any initialization needed
   * or reset parameters if desired.
   */
  public/* abstract */void onActive() {
  }

  /**
   * Subclasses may override this method. It will be invoked when the pattern is
   * no longer active. Resources may be freed if desired.
   */
  public/* abstract */void onInactive() {
  }

  /**
   * Subclasses may override this method. It will be invoked if a transition
   * into this pattern is taking place. This will be called after onActive. This
   * is not invoked on an already-running pattern. It is only called on the new
   * pattern.
   */
  public/* abstract */void onTransitionStart() {
  }

  /**
   * Subclasses may override this method. It will be invoked when the transition
   * into this pattern is complete.
   */
  public/* abstract */void onTransitionEnd() {
  }

  @Override
  public void noteOnReceived(MidiNoteOn note) {

  }

  @Override
  public void noteOffReceived(MidiNote note) {

  }

  @Override
  public void controlChangeReceived(MidiControlChange cc) {

  }

  @Override
  public void programChangeReceived(MidiProgramChange cc) {

  }

  @Override
  public void pitchBendReceived(MidiPitchBend pitchBend) {

  }

  @Override
  public void aftertouchReceived(MidiAftertouch aftertouch) {

  }

  private static final String KEY_AUTO_CYCLE = "autoCycleEnabled";

  @Override
  public void save(LX lx, JsonObject obj) {
    super.save(lx, obj);
    obj.addProperty(KEY_AUTO_CYCLE, this.autoCycleEligible.isOn());
  }

  @Override
  public void load(LX lx, JsonObject obj) {
    super.load(lx, obj);
    LXSerializable.Utils.loadBoolean(this.autoCycleEligible, obj, KEY_AUTO_CYCLE);
  }
}
