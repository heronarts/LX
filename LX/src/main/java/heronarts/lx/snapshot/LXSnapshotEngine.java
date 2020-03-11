package heronarts.lx.snapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import heronarts.lx.LXSerializable;
import heronarts.lx.osc.OscMessage;

public class LXSnapshotEngine extends LXComponent implements LXSnapshot.Listener {

  private final List<LXSnapshot> mutableSnapshots = new ArrayList<LXSnapshot>();
  public final List<LXSnapshot> snapshots = Collections.unmodifiableList(mutableSnapshots);

  public interface Listener {
    public void snapshotAdded(LXSnapshotEngine controller, LXSnapshot snapshot);
    public void snapshotMoved(LXSnapshotEngine controller, LXSnapshot snapshot);
    public void snapshotRemoved(LXSnapshotEngine controller, LXSnapshot snapshot);
  }

  private final List<Listener> listeners = new ArrayList<Listener>();

  private boolean loadingSnapshot = false;

  public LXSnapshotEngine(LX lx) {
    super(lx, "Snapshots");
    addArray("snapshot", this.snapshots);
  }

  private LXSnapshot _createNewSnapshot() {
    // Find next available snapshot name
    int maxSnapshotId = 0;
    for (LXSnapshot snapshot : this.snapshots) {
      String label = snapshot.label.getString();
      if (label.startsWith("Snapshot")) {
        label = label.replaceFirst("Snapshot", "");
        if (label.matches("\\d+")) {
          int labelId = Integer.parseInt(label);
          maxSnapshotId = Math.max(maxSnapshotId, labelId);
        }
      }
    }
    maxSnapshotId++;
    String snapshotName = "Snapshot" + maxSnapshotId;

    LXSnapshot ss = new LXSnapshot(this.lx, snapshotName);
    ss.capture();

    return ss;
  }

  private LXSnapshot _addSnapshot(LXSnapshot snapshot) {
    if (snapshot == null) {
      throw new IllegalArgumentException("Cannot add null snapshot");
    }
    snapshot.setIndex(this.mutableSnapshots.size());
    this.mutableSnapshots.add(snapshot);
    snapshot.addListener(this);
    for (Listener listener : this.listeners) {
      listener.snapshotAdded(this, snapshot);
    }
    return snapshot;
  }

  public LXSnapshot addSnapshot(LXSnapshot snapshot) {
    return _addSnapshot(snapshot);
  }

  public LXSnapshot addSnapshot() {
    LXSnapshot snapshot = _createNewSnapshot();
    _addSnapshot(snapshot);
    return snapshot;
  }

  public LXSnapshotEngine moveSnapshot(LXSnapshot snapshot, int index) {
    this.mutableSnapshots.remove(snapshot);
    this.mutableSnapshots.add(index, snapshot);
    int i = 0;
    for (LXSnapshot p : this.mutableSnapshots) {
       p.setIndex(i++);
    }
    for (Listener listener : this.listeners) {
      listener.snapshotMoved(this, snapshot);
    }
    return this;
  }

  public LXSnapshotEngine removeSnapshot(LXSnapshot snapshot) {
    int index = this.mutableSnapshots.indexOf(snapshot);
    if (index < 0) {
      return this;
    }
    snapshot.removeListener(this);
    this.mutableSnapshots.remove(snapshot);
    for (int i = index; i < this.mutableSnapshots.size(); ++i) {
      this.mutableSnapshots.get(i).setIndex(i);
    }
    for (Listener listener : this.listeners) {
      listener.snapshotRemoved(this, snapshot);
    }
    snapshot.dispose();

    return this;
  }

  public LXSnapshotEngine addSnapshotListener(Listener listener) {
    this.listeners.add(listener);
    return this;
  }

  public LXSnapshotEngine removeSnapshotListener(Listener listener) {
    this.listeners.remove(listener);
    return this;
  }

  public LXSnapshotEngine removeReferences(LXComponent component) {
    for (LXSnapshot snapshot : this.mutableSnapshots) {
      snapshot.removeLinks(component);
    }
    return this;
  }

  public void onLoadStart(LXSnapshot snapshot) {
    this.loadingSnapshot = true;
  }

  public void onLoadEnd(LXSnapshot snapshot) {
    this.loadingSnapshot = false;
  }

  public static final String PATH_SNAPSHOT = "snapshot";

  @Override
  public boolean handleOscMessage(OscMessage message, String[] parts, int index) {
    String path = parts[index];
    if (path.equals(PATH_SNAPSHOT)) {
      String snapshotId = parts[index+1];
      LXSnapshot snapshot = null;
      if (snapshotId.matches("\\d+")) {
        snapshot = this.snapshots.get(Integer.parseInt(snapshotId) - 1);
      } else {
        for (LXSnapshot s : this.snapshots) {
          if (s.getOscLabel().equals(snapshotId)) {
            snapshot = s;
            break;
          }
        }
      }
      if (snapshot == null) {
        System.err.println("[OSC] SnapshotEngine has no snapshot at path: " + snapshotId);
        return false;
      } else {
        snapshot.load();
        return true;
      }
    }
    return super.handleOscMessage(message, parts, index);
  }

  public boolean isLoadingSnapshot() {
    return this.loadingSnapshot;
  }

  private static final String KEY_SNAPSHOTS = "snapshots";

  @Override
  public void save(LX lx, JsonObject obj) {
    super.save(lx, obj);
    obj.add(KEY_SNAPSHOTS, LXSerializable.Utils.toArray(lx, this.snapshots));
  }

  @Override
  public void load(LX lx, JsonObject obj) {
    // Remove snapshots
    while (this.mutableSnapshots.size() > 0) {
      removeSnapshot(this.mutableSnapshots.get(0));
    }

    super.load(lx, obj);

    // Add snapshots
    if (obj.has(KEY_SNAPSHOTS)) {
      JsonArray snapshotArray = obj.getAsJsonArray(KEY_SNAPSHOTS);
      for (JsonElement snapshotElement : snapshotArray) {
        loadSnapshot(snapshotElement.getAsJsonObject());
      }
    }
  }

  private LXSnapshot loadSnapshot(JsonObject snapshotObj) {
    LXSnapshot snapshot = new LXSnapshot(this.lx);
    snapshot.load(this.lx, snapshotObj);
    addSnapshot(snapshot);
    return snapshot;
  }
}
