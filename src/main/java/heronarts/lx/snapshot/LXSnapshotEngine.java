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
 * @author Justin K. Blecher <jkbelcher@gmail.com>
 */

package heronarts.lx.snapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import heronarts.lx.LXLoopTask;
import heronarts.lx.LXSerializable;
import heronarts.lx.command.LXCommand;
import heronarts.lx.mixer.LXAbstractChannel;
import heronarts.lx.modulator.LinearEnvelope;
import heronarts.lx.osc.LXOscComponent;
import heronarts.lx.osc.OscMessage;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.BoundedParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.parameter.FunctionalParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.snapshot.LXSnapshot.View;
import heronarts.lx.utils.LXUtils;

/**
 * The snapshot engine stores snapshots in time of the state of project settings. This includes
 * mixer settings, the parameter values of the active patterns and effects that are running at
 * the given time.
 */
public class LXSnapshotEngine extends LXComponent implements LXOscComponent, LXLoopTask {

  private static final int NO_SNAPSHOT_INDEX = -1;

  public enum MissingChannelMode {
    IGNORE("Ignore"),
    DISABLE("Disable");

    public final String label;

    private MissingChannelMode(String label) {
      this.label = label;
    }

    @Override
    public String toString() {
      return this.label;
    }
  }

  public enum ChannelMode {
    TOGGLE("Toggle"),
    FADE("Fade");

    public final String label;

    private ChannelMode(String label) {
      this.label = label;
    }

    @Override
    public String toString() {
      return this.label;
    }
  }

  public interface Listener {
    /**
     * A new snapshot has been added to the engine
     * @param engine Snapshot engine
     * @param snapshot Snapshot
     */
    public void snapshotAdded(LXSnapshotEngine engine, LXSnapshot snapshot);

    /**
     * A snapshot has been removed from the engine
     *
     * @param engine Snapshot engine
     * @param snapshot Snapshot that was removed
     */
    public void snapshotRemoved(LXSnapshotEngine engine, LXSnapshot snapshot);

    /**
     * A snapshot's position in the engine has been moved
     *
     * @param engine Snapshot engine
     * @param snapshot Snapshot that has been moved
     */
    public void snapshotMoved(LXSnapshotEngine engine, LXSnapshot snapshot);
  }

  private final List<Listener> listeners = new ArrayList<Listener>();

  private final List<LXSnapshot> mutableSnapshots = new ArrayList<LXSnapshot>();

  /**
   * Public read-only view of all the snapshots.
   */
  public final List<LXSnapshot> snapshots = Collections.unmodifiableList(this.mutableSnapshots);

  public final BooleanParameter recallMixer =
    new BooleanParameter("Mixer", true)
    .setDescription("Whether mixer settings are recalled");

  public final BooleanParameter recallPattern =
    new BooleanParameter("Pattern", true)
    .setDescription("Whether pattern settings are recalled");

  public final BooleanParameter recallEffect =
    new BooleanParameter("Effects", true)
    .setDescription("Whether effect settings are recalled");

  public final BooleanParameter recallModulation =
    new BooleanParameter("Modulation", true)
    .setDescription("Whether global modulation settings are recalled");

  public final EnumParameter<MissingChannelMode> missingChannelMode =
    new EnumParameter<MissingChannelMode>("Missing Channel", MissingChannelMode.IGNORE)
    .setDescription("How to handle channels that are not present in the snapshot");

  public final EnumParameter<ChannelMode> channelMode =
    new EnumParameter<ChannelMode>("Channel Mode", ChannelMode.TOGGLE)
    .setDescription("How to handle turning channels on/off");

  /**
   * Whether auto pattern transition is enabled on this channel
   */
  public final BooleanParameter autoCycleEnabled =
    new BooleanParameter("Auto-Cycle", false)
    .setDescription("When enabled, the engine will automatically cycle through snapshots");

  /**
   * Auto-cycle to a random snapshot, not the next one
   */
  public final EnumParameter<AutoCycleMode> autoCycleMode =
    new EnumParameter<AutoCycleMode>("Auto-Cycle Mode", AutoCycleMode.NEXT)
    .setDescription("Mode of auto cycling");

  /**
   * Time in seconds after which transition thru the pattern set is automatically initiated.
   */
  public final BoundedParameter autoCycleTimeSecs = (BoundedParameter)
    new BoundedParameter("Cycle Time", 60, .1, 60*60*4)
    .setDescription("Sets the number of seconds after which the engine cycles to the next snapshot")
    .setUnits(LXParameter.Units.SECONDS);

  /**
   * Amount of time taken in seconds to transition into a new snapshot view
   */
  public final BoundedParameter transitionTimeSecs = (BoundedParameter)
    new BoundedParameter("Transition Time", 5, .1, 180)
    .setDescription("Sets the duration of interpolated transitions between snapshots")
    .setUnits(LXParameter.Units.SECONDS);

  public final BooleanParameter transitionEnabled =
    new BooleanParameter("Transitions", false)
    .setDescription("When enabled, transitions between snapshots use interpolation");

  public final BooleanParameter triggerSnapshotCycle =
    new BooleanParameter("Trigger Cycle")
    .setMode(BooleanParameter.Mode.MOMENTARY)
    .setDescription("Triggers a snapshot change");

  private LXSnapshot inTransition = null;

  private LinearEnvelope transition = new LinearEnvelope(0, 1, new FunctionalParameter() {
    @Override
    public double getValue() {
      return 1000 * transitionTimeSecs.getValue();
    }
  });

  private double autoCycleProgress = 0;

  public final DiscreteParameter autoCycleCursor =
    new DiscreteParameter("Auto-Cycle", NO_SNAPSHOT_INDEX, NO_SNAPSHOT_INDEX, 0)
    .setDescription("Index for the auto-cycle parameter");

  public enum AutoCycleMode {
    NEXT,
    RANDOM;

    @Override
    public String toString() {
      switch (this) {
      case NEXT:
        return "Next";
      default:
      case RANDOM:
        return "Random";
      }
    }
  };

  public LXSnapshotEngine(LX lx) {
    super(lx, "Snapshots");
    addArray("snapshot", this.snapshots);
    addParameter("recallMixer", this.recallMixer);
    addParameter("recallModulation", this.recallModulation);
    addParameter("recallPattern", this.recallPattern);
    addParameter("recallEffect", this.recallEffect);
    addParameter("channelMode", this.channelMode);
    addParameter("missingChannelMode", this.missingChannelMode);
    addParameter("transitionEnabled", this.transitionEnabled);
    addParameter("transitionTimeSecs", this.transitionTimeSecs);
    addParameter("autoCycleEnabled", this.autoCycleEnabled);
    addParameter("autoCycleMode", this.autoCycleMode);
    addParameter("autoCycleTimeSecs", this.autoCycleTimeSecs);
    addParameter("autoCycleCursor", this.autoCycleCursor);
    addParameter("triggerSnapshotCycle", this.triggerSnapshotCycle);
  }

  @Override
  public void onParameterChanged(LXParameter parameter) {
    super.onParameterChanged(parameter);
    if (parameter == this.autoCycleEnabled) {
      this.autoCycleProgress = 0;
    } else if (parameter == this.triggerSnapshotCycle) {
      if (this.triggerSnapshotCycle.isOn()) {
        this.triggerSnapshotCycle.setValue(false);
        doSnapshotCycle();
      }
    }
  }

  public LXSnapshotEngine addListener(Listener listener) {
    Objects.requireNonNull(listener, "May not add null LXSnapshotEngine.Listener");
    if (this.listeners.contains(listener)) {
      throw new IllegalStateException("Cannot add duplicate LXSnapshotEngine.Listener: " + listener);
    }
    this.listeners.add(listener);
    return this;
  }

  public LXSnapshotEngine removeListener(Listener listener) {
    if (!this.listeners.contains(listener)) {
      throw new IllegalStateException("Cannot remove non-existent LXSnapshotEngine.Listener: " + listener);
    }
    this.listeners.remove(listener);
    return this;
  }

  private void _reindexSnapshots() {
    int i = 0;
    for (LXSnapshot snapshot : this.snapshots) {
      snapshot.setIndex(i++);
    }
  }

  /**
   * Adds a new snapshot that takes the current state of the program.
   *
   * @return New snapshot that holds a view of the current state
   */
  public LXSnapshot addSnapshot() {
    LXSnapshot snapshot = new LXSnapshot(getLX());
    snapshot.initialize();
    snapshot.label.setValue("Snapshot-" + (snapshots.size() + 1));
    addSnapshot(snapshot);
    return snapshot;
  }

  /**
   * Adds a snapshot to the engine. This snapshot is assumed to already exist
   * and have been somehow populated.
   *
   * @param snapshot Snapshot to add
   * @return this
   */
  public LXSnapshotEngine addSnapshot(LXSnapshot snapshot) {
    return addSnapshot(snapshot, -1);
  }

  /**
   * Adds a snapshot to the engine. This snapshot is assumed to already exist
   * and have been somehow populated.

   * @param snapshot Snapshot to add
   * @param index Index to add at
   * @return this
   */
  public LXSnapshotEngine addSnapshot(LXSnapshot snapshot, int index) {
    Objects.requireNonNull(snapshot, "May not LXSnapshotEngine.addSnapshot(null)");
    if (this.snapshots.contains(snapshot)) {
      throw new IllegalStateException("May not add same snapshot instance twice: " + snapshot);
    }
    if (index < 0) {
      this.mutableSnapshots.add(snapshot);
      snapshot.setIndex(this.mutableSnapshots.size() - 1);
    } else {
      this.mutableSnapshots.add(index, snapshot);
      _reindexSnapshots();
    }
    this.autoCycleCursor.setRange(NO_SNAPSHOT_INDEX, this.snapshots.size());
    if (index >= 0 && index <= this.autoCycleCursor.getValuei()) {
      this.autoCycleCursor.increment();
    }
    for (Listener listener : this.listeners) {
      listener.snapshotAdded(this, snapshot);
    }
    return this;
  }

  /**
   * Removes a snapshot from the engine
   *
   * @param snapshot Snapshot to remove
   * @return this
   */
  public LXSnapshotEngine removeSnapshot(LXSnapshot snapshot) {
    if (!this.snapshots.contains(snapshot)) {
      throw new IllegalStateException("Cannot remove snapshot that is not present: " + snapshot);
    }
    int index = this.mutableSnapshots.indexOf(snapshot);
    this.mutableSnapshots.remove(snapshot);
    _reindexSnapshots();
    for (Listener listener : this.listeners) {
      listener.snapshotRemoved(this, snapshot);
    }
    if (index <= this.autoCycleCursor.getValuei()) {
      this.autoCycleCursor.decrement();
    }
    this.autoCycleCursor.setRange(NO_SNAPSHOT_INDEX, this.snapshots.size());
    snapshot.dispose();
    return this;
  }

  /**
   * Moves a snapshot to a new order in the engine snapshot list
   *
   * @param snapshot Snapshot
   * @param index New position to occupy
   * @return this
   */
  public LXSnapshotEngine moveSnapshot(LXSnapshot snapshot, int index) {
    if (!this.snapshots.contains(snapshot)) {
      throw new IllegalArgumentException("Cannot move snapshot not in engine: " + snapshot);
    }
    LXSnapshot autoCycleSnapshot = null;
    int auto = this.autoCycleCursor.getValuei();
    if (auto >= 0 && auto < this.snapshots.size()) {
      autoCycleSnapshot = this.snapshots.get(auto);
    }
    this.mutableSnapshots.remove(snapshot);
    this.mutableSnapshots.add(index, snapshot);
    _reindexSnapshots();
    for (Listener listener : this.listeners) {
      listener.snapshotMoved(this, snapshot);
    }
    if (autoCycleSnapshot != null) {
      this.autoCycleCursor.setValue(this.snapshots.indexOf(autoCycleSnapshot));
    }
    return this;
  }

  /**
   * Recall this snapshot, apply all of its values
   *
   * @param snapshot The snapshot to recall
   */
  public void recall(LXSnapshot snapshot) {
    recall(snapshot, null);
  }

  private final List<LXSnapshot.View> recallViews =
    new ArrayList<LXSnapshot.View>();

  /**
   * Recall this snapshot, and populate an array of commands which
   * would need to be undone by this operation.
   *
   * @param snapshot Snapshot to recall
   * @param commands Array to populate with all the commands processed
   */
  public void recall(LXSnapshot snapshot, List<LXCommand> commands) {
    boolean mixer = this.recallMixer.isOn();
    boolean pattern = this.recallPattern.isOn();
    boolean effect = this.recallEffect.isOn();
    boolean modulation = this.recallModulation.isOn();

    boolean transition = false;
    this.autoCycleProgress = 0;
    if (commands != null) {
      commands.add(new LXCommand.Parameter.SetValue(this.autoCycleCursor, this.autoCycleCursor.getValuei()));
    }
    this.autoCycleCursor.setValue(snapshot.getIndex());
    this.recallViews.clear();
    this.recallViews.addAll(snapshot.views);
    if (this.transitionEnabled.isOn()) {
      transition = true;
      this.inTransition = snapshot;
    }

    // If there are missing channels, add a view to handle them
    if (this.missingChannelMode.getEnum() == MissingChannelMode.DISABLE) {
      for (LXAbstractChannel channel : this.lx.engine.mixer.channels) {
        if (channel.enabled.isOn() && !snapshot.hasChannelFaderView(channel)) {
          this.recallViews.add(snapshot.getMissingChannelView(channel));
        }
      }
    }

    for (View view : this.recallViews) {
      if (view.activeFlag = isValidView(view, mixer, pattern, effect, modulation)) {
        if (transition) {
          view.startTransition();
        } else {
          view.recall();
        }
        if (commands != null) {
          commands.add(view.getCommand());
        }
      }
    }
    if (transition) {
      this.transition.trigger();
    }
  }

  private boolean isValidView(View view, boolean mixer, boolean pattern, boolean effect, boolean modulation) {
    if (!view.enabled.isOn()) {
      return false;
    }
    switch (view.scope) {
    case EFFECTS:
      return effect;
    case MODULATION:
      return modulation;
    case PATTERNS:
      return pattern;
    default:
    case OUTPUT:
    case MIXER:
      return mixer;
    }
  }

  public double getTransitionProgress() {
    return (this.inTransition != null) ? this.transition.getValue() : 0;
  }

  public double getAutoCycleProgress() {
    return this.autoCycleProgress;
  }

  private void doSnapshotCycle() {
    switch (this.autoCycleMode.getEnum()) {
    case NEXT:
      goNextSnapshot();
      break;
    case RANDOM:
      goRandomSnapshot();
      break;
    }
  }

  @Override
  public void loop(double deltaMs) {
    if (this.inTransition != null) {
      this.transition.loop(deltaMs);
      if (this.transition.finished()) {
        for (View view : this.recallViews) {
          if (view.activeFlag) {
            view.finishTransition();
          }
        }
        this.inTransition = null;
      } else {
        for (View view : this.recallViews) {
          if (view.activeFlag) {
            view.interpolate(this.transition.getValue());
          }
        }
      }
      this.autoCycleProgress = 0;
    } else if (this.autoCycleEnabled.isOn()) {
      this.autoCycleProgress += deltaMs / (1000 * this.autoCycleTimeSecs.getValue());
      if (this.autoCycleProgress >= 1) {
        this.autoCycleProgress = 1;
        doSnapshotCycle();
      }
    }
  }

  private void goNextSnapshot() {
    if (this.snapshots.size() <= 1) {
      return;
    }
    int startIndex = this.autoCycleCursor.getValuei();
    int nextIndex = (startIndex + 1) % this.snapshots.size();
    while (nextIndex != startIndex) {
      if (startIndex < 0) {
        startIndex = 0;
      }
      LXSnapshot next = this.snapshots.get(nextIndex);
      if (next.autoCycleEligible.isOn()) {
        recall(next);
        return;
      }
      nextIndex = (nextIndex + 1) % this.snapshots.size();
    }
  }

  private void goRandomSnapshot() {
    if (this.snapshots.size() <= 1) {
      return;
    }
    List<LXSnapshot> eligible = new ArrayList<LXSnapshot>();
    int autoIndex = this.autoCycleCursor.getValuei();
    for (int i = 0; i < this.snapshots.size(); ++i) {
      if (i != autoIndex) {
        LXSnapshot test = this.snapshots.get(i);
        if (test.autoCycleEligible.isOn()) {
          eligible.add(test);
        }
      }
    }
    int numEligible = eligible.size();
    if (numEligible > 0) {
      LXSnapshot random = eligible.get(LXUtils.constrain((int) LXUtils.random(0, numEligible), 0, numEligible - 1));
      recall(random);
    }
  }

  /**
   * Clears all snapshots from the engine. Generally should not be publicly used.
   */
  public void clear() {
    for (int i = this.snapshots.size() - 1; i >= 0; --i) {
      removeSnapshot(this.snapshots.get(i));
    }
  }

  public static final String PATH_SNAPSHOT = "snapshot";

  @Override
  public boolean handleOscMessage(OscMessage message, String[] parts, int index) {
    String path = parts[index];
    for (LXSnapshot snapshot : this.snapshots) {
      if (path.equals(snapshot.getOscPath())) {
        return snapshot.handleOscMessage(message, parts, index+1);
      }
    }
    return super.handleOscMessage(message, parts, index);
  }

  /**
   * Find all snapshot views that involve the selected component. This is typically
   * called before removal of that component to identify now-defunct references.
   *
   * @param component Component
   * @return List of all views that reference this component, or null
   */
  public List<LXSnapshot.View> findSnapshotViews(LXComponent component) {
    List<LXSnapshot.View> views = null;
    for (LXSnapshot snapshot : this.snapshots) {
      for (LXSnapshot.View view : snapshot.views) {
        if (view.isDependentOf(component)) {
          if (views == null) {
            views = new ArrayList<LXSnapshot.View>();
          }
          views.add(view);
        }
      }
    }
    return views;
  }

  /**
   * Remove all snapshot views that reference the given component
   *
   * @param component Component that is referenced
   */
  public void removeSnapshotViews(LXComponent component) {
    List<LXSnapshot.View> removeViews = findSnapshotViews(component);
    if (removeViews != null) {
      for (LXSnapshot.View view : removeViews) {
        view.getSnapshot().removeView(view);
      }
    }
  }

  private static final String KEY_SNAPSHOTS = "snapshots";

  @Override
  public void save(LX lx, JsonObject obj) {
    super.save(lx, obj);
    obj.add(KEY_SNAPSHOTS, LXSerializable.Utils.toArray(lx, this.snapshots));
  }

  @Override
  public void load(LX lx, JsonObject obj) {
    // Clear any current snapshots
    clear();

    super.load(lx, obj);

    if (obj.has(KEY_SNAPSHOTS)) {
      JsonArray snapshotArr = obj.getAsJsonArray(KEY_SNAPSHOTS);
      for (JsonElement snapshotElement : snapshotArr) {
        JsonObject snapshotObj = snapshotElement.getAsJsonObject();
        try {
          LXSnapshot snapshot = new LXSnapshot(this.lx);
          snapshot.load(lx, snapshotObj);
          addSnapshot(snapshot);
        } catch (Exception x) {
          LX.error(x, "Could not load snapshot " + snapshotObj.toString());
        }
      }
    }
  }

}
