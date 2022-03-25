package heronarts.lx.command;

import com.google.gson.JsonObject;
import heronarts.lx.LX;
import heronarts.lx.LXSerializable;
import heronarts.lx.snapshot.LXSnapshot;

public class AddSnapshot extends LXCommand {

        private ComponentReference<LXSnapshot> snapshot;
        private JsonObject initialObj = null;
        private JsonObject snapshotObj = null;
        private final int index;

        public AddSnapshot() {
            this.index = -1;
        }

        public AddSnapshot(JsonObject snapshotObj, int index) {
            this.initialObj = snapshotObj;
            this.index = index;
        }

        @Override
        public String getDescription() {
            return "Add Snapshot";
        }

        @Override
        public void perform(LX lx) {
            if (this.snapshotObj == null) {
                LXSnapshot instance;
                if (this.initialObj != null) {
                    instance = new LXSnapshot(lx);
                    instance.load(lx, this.initialObj);
                    lx.engine.snapshots.addSnapshot(instance, this.index);
                } else {
                    instance = lx.engine.snapshots.addSnapshot();
                }
                this.snapshot = new ComponentReference<LXSnapshot>(instance);
                this.snapshotObj = LXSerializable.Utils.toObject(lx, instance);
            } else {
                LXSnapshot instance = new LXSnapshot(lx);
                instance.load(lx, this.snapshotObj);
                this.snapshot = new ComponentReference<LXSnapshot>(instance);
                lx.engine.snapshots.addSnapshot(instance);
            }
        }

        @Override
        public void undo(LX lx) {
            lx.engine.snapshots.removeSnapshot(this.snapshot.get());
        }
    }


