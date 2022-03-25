package heronarts.lx.command;

import com.google.gson.JsonObject;
import heronarts.lx.LX;
import heronarts.lx.LXSerializable;
import heronarts.lx.snapshot.LXSnapshot;

public class Update extends LXCommand {

        private final ComponentReference<LXSnapshot> snapshot;
        private JsonObject previousState;

        public Update(LXSnapshot snapshot) {
            this.snapshot = new ComponentReference<LXSnapshot>(snapshot);
        }

        @Override
        public String getDescription() {
            return "Update Snapshot";
        }

        @Override
        public void perform(LX lx) throws InvalidCommandException {
            this.previousState = LXSerializable.Utils.toObject(lx, this.snapshot.get());
            this.snapshot.get().update();
        }

        @Override
        public void undo(LX lx) throws InvalidCommandException {
            this.snapshot.get().load(lx, this.previousState);
        }

    }

