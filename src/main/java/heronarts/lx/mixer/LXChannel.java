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
import heronarts.lx.LXComponent;
import heronarts.lx.clip.LXChannelClip;
import heronarts.lx.clip.LXClip;
import heronarts.lx.effect.LXEffect;
import heronarts.lx.midi.LXShortMessage;
import heronarts.lx.model.LXModel;
import heronarts.lx.osc.OscMessage;
import heronarts.lx.parameter.MutableParameter;
import heronarts.lx.pattern.LXPattern;
import heronarts.lx.parameter.BooleanParameter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.google.gson.JsonObject;

/**
 * A channel is a single component of the engine that has a set of patterns from
 * which it plays and rotates. It also has a fader to control how this channel
 * is blended with the channels before it.
 */
public class LXChannel extends LXAbstractChannel implements LXPatternEngine.Container {

  /**
   * Listener interface for objects which want to be notified when the internal
   * channel state is modified.
   */
  public interface Listener extends LXAbstractChannel.Listener {
    public default void groupChanged(LXChannel channel, LXGroup group) {}
    public default void patternAdded(LXChannel channel, LXPattern pattern) {}
    public default void patternRemoved(LXChannel channel, LXPattern pattern) {}
    public default void patternMoved(LXChannel channel, LXPattern pattern) {}
    public default void patternWillChange(LXChannel channel, LXPattern pattern, LXPattern nextPattern) {}
    public default void patternDidChange(LXChannel channel, LXPattern pattern) {}
    public default void patternEnabled(LXChannel channel, LXPattern pattern) {}
  }

  private final ArrayList<Listener> listeners = new ArrayList<Listener>();
  private boolean inListener = false;
  private final List<Listener> addListeners = new ArrayList<>();
  private final List<Listener> removeListeners = new ArrayList<>();

  /**
   * Whether the channel control UI is expanded
   */
  public final BooleanParameter controlsExpanded =
    new BooleanParameter("Expanded", true)
    .setDescription("Whether the control elements for the channel device are expanded");

  /**
   * A semaphore used to keep count of how many remote control surfaces may be
   * controlling this channel's patterns. This may be used by UI implementations to indicate
   * to the user that this component is under remote control.
   */
  public final MutableParameter controlSurfaceSemaphore =
    new MutableParameter("Control-Surfaces", 0)
    .setDescription("How many control surfaces are controlling this component");

  public final BooleanParameter viewPatternLabel =
    new BooleanParameter("View Pattern Label", false)
    .setDescription("Whether to show the active pattern as channel label");

  /**
   * Group that this channel belongs to
   */
  private LXGroup group = null;

  public final LXPatternEngine patternEngine;
  public final List<LXPattern> patterns;

  public LXChannel(LX lx, int index, LXPattern[] patterns) {
    super(lx, index, "Channel-" + (index+1));

    this.patternEngine = new LXPatternEngine(lx, this, patterns);

    addArray("pattern", this.patterns = this.patternEngine.patterns);

    addInternalParameter("controlsExpanded", this.controlsExpanded);
    addInternalParameter("viewPatternLabel", this.viewPatternLabel);

    addParameters(this.patternEngine.parameters);
  }

  public LXPatternEngine getPatternEngine() {
    return this.patternEngine;
  }

  public LXPatternEngine.Listener getPatternEngineDelegate() {
    return this.delegate;
  }

  @Override
  public boolean isPlaylist() {
    return this.patternEngine.isPlaylist();
  }

  @Override
  public boolean isComposite() {
    return this.patternEngine.isComposite();
  }

  void updateTransitionBlendOptions() {
    this.patternEngine.updateTransitionBlendOptions();
  }

  @Override
  void updateChannelBlendOptions() {
    super.updateChannelBlendOptions();
    if (this.patternEngine != null) {
      this.patternEngine.updateChannelBlendOptions();
    }
  }

  @Override
  public LXModel getModelView() {
    if ((this.group != null) && this.view.isDefault()) {
      return this.group.getModelView();
    }
    return super.getModelView();
  }

  @Override
  public String getClipLabel() {
    LXPattern pattern = getActivePattern();
    if (pattern != null) {
      return pattern.getLabel();
    }
    return super.getClipLabel();
  }

  public final void addListener(Listener listener) {
    Objects.requireNonNull(listener, "May not add null LXChannel.Listener");
    if (this.listeners.contains(listener)) {
      throw new IllegalStateException("May not add duplicate LXChannel.Listener: " + listener);
    }
    if (this.inListener) {
      this.addListeners.add(listener);
      return;
    }
    super.addListener(listener);
    this.listeners.add(listener);
  }

  public final void removeListener(Listener listener) {
    if (!this.listeners.contains(listener)) {
      throw new IllegalStateException("May not remove non-registered LXChannel.Listener: " + listener);
    }
    if (this.inListener) {
      this.removeListeners.add(listener);
      return;
    }
    super.removeListener(listener);
    this.listeners.remove(listener);
  }

  private void _processReentrantListenerChanges() {
    if (!this.removeListeners.isEmpty()) {
      this.removeListeners.forEach(listener -> removeListener(listener));
      this.removeListeners.clear();
    }
    if (!this.addListeners.isEmpty()) {
      this.addListeners.forEach(listener -> addListener(listener));
      this.addListeners.clear();
    }
  }

  private final LXPatternEngine.Listener delegate = new LXPatternEngine.Listener() {
    public void patternAdded(LXPatternEngine engine, LXPattern pattern) {
      inListener = true;
      listeners.forEach(listener -> listener.patternAdded(LXChannel.this, pattern));
      inListener = false;
      _processReentrantListenerChanges();
    }
    public void patternRemoved(LXPatternEngine engine, LXPattern pattern) {
      inListener = true;
      listeners.forEach(listener -> listener.patternRemoved(LXChannel.this, pattern));
      inListener = false;
      _processReentrantListenerChanges();
    }
    public void patternMoved(LXPatternEngine engine, LXPattern pattern) {
      inListener = true;
      listeners.forEach(listener -> listener.patternMoved(LXChannel.this, pattern));
      inListener = false;
      _processReentrantListenerChanges();
    }
    public void patternWillChange(LXPatternEngine engine, LXPattern pattern, LXPattern nextPattern) {
      inListener = true;
      listeners.forEach(listener -> listener.patternWillChange(LXChannel.this, pattern, nextPattern));
      inListener = false;
      _processReentrantListenerChanges();
    }
    public void patternDidChange(LXPatternEngine engine, LXPattern pattern) {
      inListener = true;
      listeners.forEach(listener -> listener.patternDidChange(LXChannel.this, pattern));
      inListener = false;
      _processReentrantListenerChanges();
    }
    public void patternEnabled(LXPatternEngine engine, LXPattern pattern) {
      inListener = true;
      listeners.forEach(listener -> listener.patternEnabled(LXChannel.this, pattern));
      inListener = false;
      _processReentrantListenerChanges();
    }
  };

  @Override
  public boolean handleOscMessage(OscMessage message, String[] parts, int index) {
    if (this.patternEngine.handleOscMessage(message, parts, index)) {
      return true;
    }
    return super.handleOscMessage(message, parts, index);
  }

  @Override
  public void midiDispatch(LXShortMessage message) {
    super.midiDispatch(message);
    this.patternEngine.midiDispatch(message);
  }

  LXChannel setGroup(LXGroup group) {
    if (this.group != group) {
      this.group = group;
      this.listeners.forEach(listener -> listener.groupChanged(this, group));
    }
    return this;
  }

  @Override
  public LXGroup getGroup() {
    return this.group;
  }

  @Override
  protected LXClip constructClip(int index) {
    return new LXChannelClip(this.lx, this, index);
  }

  public final List<LXPattern> getPatterns() {
    return this.patterns;
  }

  public final LXPattern getPattern(int index) {
    return this.patterns.get(index);
  }

  public final LXPattern getPattern(String label) {
    return this.patternEngine.getPattern(label);
  }

  public final LXPattern getPatternByClassName(String className) {
    return this.patternEngine.getPatternByClassName(className);
  }

  public final LXChannel setPatterns(LXPattern[] patterns) {
    this.patternEngine.setPatterns(patterns);
    return this;
  }

  public final LXChannel addPattern(LXPattern pattern) {
    this.patternEngine.addPattern(pattern);
    return this;
  }

  public final LXChannel addPattern(LXPattern pattern, int index) {
    this.patternEngine.addPattern(pattern, index);
    return this;
  }

  public final LXChannel removePattern(LXPattern pattern) {
    this.patternEngine.removePattern(pattern);
    return this;
  }

  public LXChannel movePattern(LXPattern pattern, int index) {
    this.patternEngine.movePattern(pattern, index);
    return this;
  }

  public final int getFocusedPatternIndex() {
    return this.patternEngine.getFocusedPatternIndex();
  }

  /**
   * Returns the pattern that currently has focus in this channel's
   * pattern list.
   *
   * @return Pattern focused in the list
   */
  public final LXPattern getFocusedPattern() {
    return this.patternEngine.getFocusedPattern();
  }

  /**
   * Returns the index of the currently active pattern, if any
   *
   * @return Index of the currently active pattern
   */
  public final int getActivePatternIndex() {
    return this.patternEngine.getActivePatternIndex();
  }

  public final LXPattern getActivePattern() {
    return this.patternEngine.getActivePattern();
  }

  public final LXPattern getTargetPattern() {
    return this.patternEngine.getTargetPattern();
  }

  public final int getNextPatternIndex() {
    return this.patternEngine.getNextPatternIndex();
  }

  public final LXPattern getNextPattern() {
    return this.patternEngine.getNextPattern();
  }

  public boolean isInTransition() {
    return this.patternEngine.isInTransition();
  }

  /**
   * Activates the previous pattern in this channel's pattern list
   *
   * @return this
   */
  public final LXChannel goPreviousPattern() {
    this.patternEngine.goPreviousPattern();
    return this;
  }

  /**
   * Activates the next pattern in this channel's pattern list
   *
   * @return this
   */
  public final LXChannel goNextPattern() {
    this.patternEngine.goNextPattern();
    return this;
  }

  /**
   * Activates the given pattern, which must belong to this channel.
   *
   * @param pattern Pattern to activate
   * @return this
   */
  public final LXChannel goPattern(LXPattern pattern) {
    this.patternEngine.goPattern(pattern);
    return this;
  }

  /**
   * Activates the given pattern, which must belong to this channel. Transition
   * can be optionally skipped
   *
   * @param pattern Pattern to activate
   * @param skipTransition Skip over a transition
   * @return this
   */
  public final LXChannel goPattern(LXPattern pattern, boolean skipTransition) {
    this.patternEngine.goPattern(pattern, skipTransition);
    return this;
  }

  /**
   * Activates a randomly selected pattern on the channel, from the set of
   * patterns that have auto cycle enabled.
   *
   * @return this
   */
  public final LXChannel goRandomPattern() {
    this.patternEngine.goRandomPattern();
    return this;
  }

  /**
   * Activates the pattern at the given index, if it is within the
   * bounds of this channel's pattern list.
   *
   * @param index Pattern index
   * @return this
   */
  public final LXChannel goPatternIndex(int index) {
    this.patternEngine.goPatternIndex(index);
    return this;
  }

  public LXChannel disableAutoCycle() {
    this.patternEngine.disableAutoCycle();
    return this;
  }

  /**
   * Enable automatic transition from pattern to pattern on this channel
   *
   * @param autoCycleThreshold time in seconds
   * @return this
   */
  public LXBus enableAutoCycle(double autoCycleThreshold) {
    this.patternEngine.enableAutoCycle(autoCycleThreshold);
    return this;
  }

  /**
   * Return progress towards making a cycle
   *
   * @return amount of progress towards the next cycle
   */
  public double getAutoCycleProgress() {
    return this.patternEngine.getAutoCycleProgress();
  }

  /**
   * Return progress through a transition
   *
   * @return amount of progress thru current transition
   */
  public double getTransitionProgress() {
    return this.patternEngine.getTransitionProgress();
  }

  @Override
  public void loop(double deltaMs) {
    long loopStart = System.nanoTime();

    // Delegate to LXAbstractChannel loop method
    super.loop(deltaMs);

    // LXAbstractChannel will have figured out if we need to run everything.
    // If not, then we're done here and skip the rest.
    if (!this.isAnimating) {
      this.profiler.loopNanos = System.nanoTime() - loopStart;
      return;
    }

    // Run the pattern engine
    this.colors = this.blendBuffer.getArray();
    this.patternEngine.loop(this.blendBuffer, getModelView(), deltaMs);
    this.profiler.loopNanos = System.nanoTime() - loopStart;

    // Apply effects
    long effectStart = System.nanoTime();
    if (!this.mutableEffects.isEmpty()) {
      for (LXEffect effect : this.mutableEffects) {
        effect.setBuffer(this.blendBuffer);
        effect.setModel(effect.getModelView());
        effect.loop(deltaMs);
      }
    }
    ((LXBus.Profiler) this.profiler).effectNanos = System.nanoTime() - effectStart;
  }

  @Override
  public void dispose() {
    disposeClips();
    this.patternEngine.dispose();
    if (this.thread.hasStarted) {
      this.thread.interrupt();
    }
    super.dispose();
    this.listeners.forEach(listener -> LX.warning("Stranded LXChannel.Listener: " + listener));
    this.listeners.clear();
  }

  @Override
  public Class<?> getPresetClass() {
    return getClass();
  }

  @Override
  public void postProcessPreset(LX lx, JsonObject obj) {
    super.postProcessPreset(lx, obj);
    obj.remove(KEY_GROUP);
  }

  private static final String KEY_GROUP = "group";
  protected static final String KEY_IS_GROUP = "isGroup";

  @Override
  public void save(LX lx, JsonObject obj) {
    super.save(lx, obj);
    this.patternEngine.save(lx, obj);
    if (this.group != null) {
      obj.addProperty(KEY_GROUP, this.group.getId());
    }
  }

  @Override
  public void load(LX lx, JsonObject obj) {
    this.patternEngine.clearPatterns();

    // Set appropriate group membership
    if (obj.has(KEY_GROUP)) {
      final int groupId = obj.get(KEY_GROUP).getAsInt();
      final LXComponent group = lx.getProjectComponent(groupId);
      if (group instanceof LXGroup) {
        ((LXGroup)group).addChannel(this);
      } else {
        LX.error("Group ID " + groupId + " not found when restoring channel: " + this);
      }
    }

    // Load pattern engine
    this.patternEngine.load(lx, obj);

    // Restore parameter values
    super.load(lx, obj);
  }


}
