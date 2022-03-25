package heronarts.lx.command;

import com.google.gson.JsonObject;
import heronarts.lx.LX;
import heronarts.lx.LXSerializable;
import heronarts.lx.snapshot.LXSnapshot;

public class RemoveSnapshot extends LXCommand.RemoveComponent{

        private final ComponentReference<LXSnapshot> snapshot;
        private final JsonObject snapshotObj;
        private final int snapshotIndex;

        public RemoveSnapshot(LXSnapshot snapshot) {
            super(snapshot);
            this.snapshot = new ComponentReference<LXSnapshot>(snapshot);
            this.snapshotObj = LXSerializable.Utils.toObject(snapshot);
            this.snapshotIndex = snapshot.getIndex();
        }

        @Override
        public String getDescription() {
            return "Delete Snapshot";
        }

        @Override
        public void perform(LX lx) {
            lx.engine.snapshots.removeSnapshot(this.snapshot.get());
        }

        @Override
        public void undo(LX lx) throws InvalidCommandException {
            LXSnapshot snapshot = new LXSnapshot(lx);
            snapshot.load(lx, this.snapshotObj);
            lx.engine.snapshots.addSnapshot(snapshot, this.snapshotIndex);
            super.undo(lx);
        }
    }


