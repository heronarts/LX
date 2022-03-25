package heronarts.lx.command;

import heronarts.lx.LX;
import heronarts.lx.snapshot.LXSnapshot;

public class MoveSnapshot extends LXCommand {

        private final ComponentReference<LXSnapshot> snapshot;
        private final int fromIndex;
        private final int toIndex;

        public MoveSnapshot(LXSnapshot snapshot, int toIndex) {
            this.snapshot = new ComponentReference<LXSnapshot>(snapshot);
            this.fromIndex = snapshot.getIndex();
            this.toIndex = toIndex;
        }

        @Override
        public String getDescription() {
            return "Move Snapshot";
        }

        @Override
        public void perform(LX lx) {
            lx.engine.snapshots.moveSnapshot(this.snapshot.get(), this.toIndex);
        }

        @Override
        public void undo(LX lx) {
            lx.engine.snapshots.moveSnapshot(this.snapshot.get(), this.fromIndex);
        }
    }

