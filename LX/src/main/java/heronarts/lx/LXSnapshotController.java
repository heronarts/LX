package heronarts.lx;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import heronarts.lx.osc.OscMessage;

public class LXSnapshotController extends LXComponent {

  private Integer nextShapshotID = 1;

  private final List<LXSnapshot> snapshots = new ArrayList<LXSnapshot>();
  private final TreeMap<Integer, LXSnapshot> snapshotsDict = new TreeMap<Integer, LXSnapshot>();

  public interface SnapshotListener {
    public void snapshotAdded(LXSnapshotController controller, LXSnapshot snapshot);
    public void snapshotRemoved(LXSnapshotController controller, LXSnapshot snapshot);
  }

  private final List<SnapshotListener> snapshotListeners = new ArrayList<SnapshotListener>();

  public LXSnapshotController(LX lx) {
    super(lx);
    this.label.setDescription("Snapshots");
  }

  private LXSnapshot _createNewSnapshot() {
    LXSnapshot ss = new LXSnapshot(this.lx, this.nextShapshotID);
    ss.initialize();
    this.nextShapshotID++;

    return ss;
  }

  private LXSnapshot _addSnapshot(LXSnapshot snapshot) {
    if (snapshot == null) {
      throw new IllegalArgumentException("Cannot add null snapshot");
    }
    this.snapshots.add(snapshot);
    this.snapshotsDict.put(snapshot.snapshotID, snapshot);
    for (SnapshotListener snapshotListener : this.snapshotListeners) {
      snapshotListener.snapshotAdded(this, snapshot);
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

  public void runSnapshot(int snapshotID) {
    if (snapshotsDict.containsKey(snapshotID)) {
      snapshotsDict.get(snapshotID).run();
    } else {
      System.err.println("Unknown snapshotID, cannot run snapshot.");
    }
  }

  public LXSnapshotController removeSnapshot(LXSnapshot snapshot) {
    this.snapshotsDict.remove(snapshot.snapshotID);
    this.snapshots.remove(snapshot);
    for (SnapshotListener snapshotListener : this.snapshotListeners) {
      snapshotListener.snapshotRemoved(this, snapshot);
    }
    snapshot.dispose();

    if (this.snapshots.size()==0) {
      this.nextShapshotID= 1;
    }
    return this;
  }

  public LXSnapshotController addSnapshotListener(SnapshotListener listener) {
    this.snapshotListeners.add(listener);
    return this;
  }

  public LXSnapshotController removeSnapshotListener(SnapshotListener listener) {
    this.snapshotListeners.remove(listener);
    return this;
  }

  public LXSnapshotController removeLinks(LXComponent component) {
    for (LXSnapshot snapshot : this.snapshots) {
      snapshot.removeLinks(component);
    }
    return this;
  }

  @Override
  public boolean handleOscMessage(OscMessage message, String[] parts, int index) {
    String path = parts[index];
    try {
      int snapshotID = Integer.parseInt(path);
      if (this.snapshotsDict.containsKey(snapshotID)) {
        this.snapshotsDict.get(snapshotID).run();
      } else {
        System.out.println("Invalid snapshot ID received over OSC: " + snapshotID);
      }
    } catch (NumberFormatException exception) {
      System.err.println("Invalid snapshot ID received over OSC: " + path);
    }
    return true;
  }

  private static final String KEY_NEXTSNAPSHOTID = "nextSnapshotID";
  private static final String KEY_SNAPSHOTS = "snapshots";

  @Override
  public void save(LX lx, JsonObject obj) {
    super.save(lx, obj);
    obj.addProperty(KEY_NEXTSNAPSHOTID, this.nextShapshotID);
    obj.add(KEY_SNAPSHOTS, LXSerializable.Utils.toArray(lx, this.snapshots));
  }

  @Override
  public void load(LX lx, JsonObject obj) {
    // Remove snapshots
    while (this.snapshots.size() > 0) {
      removeSnapshot(this.snapshots.get(0));
    }

    super.load(lx, obj);

    // Add snapshots
    if (obj.has(KEY_SNAPSHOTS)) {
      JsonArray snapshotArray = obj.getAsJsonArray(KEY_SNAPSHOTS);
      for (JsonElement snapshotElement : snapshotArray) {
        loadSnapshot(snapshotElement.getAsJsonObject());
      }
    }

    // Next snapshotID
    if (obj.has(KEY_NEXTSNAPSHOTID)) {
      this.nextShapshotID = obj.get(KEY_NEXTSNAPSHOTID).getAsInt();
    } else {
      this.nextShapshotID=1;
      for (Integer snapshotID : this.snapshotsDict.keySet()) {
        this.nextShapshotID = Math.max(this.nextShapshotID, snapshotID+1);
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
