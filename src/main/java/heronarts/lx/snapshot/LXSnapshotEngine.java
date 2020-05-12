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
import heronarts.lx.LXSerializable;
import heronarts.lx.osc.LXOscComponent;
import heronarts.lx.osc.OscMessage;

/**
 * The snapshot engine stores snapshots in time of the state of project settings. This includes
 * mixer settings, the parameter values of the active patterns and effects that are running at
 * the given time.
 */
public class LXSnapshotEngine extends LXComponent implements LXOscComponent {

  private final List<LXSnapshot> mutableSnapshots = new ArrayList<LXSnapshot>();

  /**
   * Public read-only view of all the snapshots.
   */
  public final List<LXSnapshot> snapshots = Collections.unmodifiableList(this.mutableSnapshots);

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

  public LXSnapshotEngine(LX lx) {
    super(lx, "Snapshots");
    addArray("snapshot", this.snapshots);
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
   * @return
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
    this.mutableSnapshots.remove(snapshot);
    _reindexSnapshots();
    for (Listener listener : this.listeners) {
      listener.snapshotRemoved(this, snapshot);
    }
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
    this.mutableSnapshots.remove(snapshot);
    this.mutableSnapshots.add(index, snapshot);
    _reindexSnapshots();
    for (Listener listener : this.listeners) {
      listener.snapshotMoved(this, snapshot);
    }
    return this;
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
