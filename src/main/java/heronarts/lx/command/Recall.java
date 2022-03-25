package heronarts.lx.command;

import heronarts.lx.LX;
import heronarts.lx.snapshot.LXSnapshot;

import java.util.ArrayList;
import java.util.List;

public class Recall extends LXCommand{

        private final ComponentReference<LXSnapshot> snapshot;
        private final List<LXCommand> commands = new ArrayList<LXCommand>();
        private boolean recalled = false;

        public Recall(LXSnapshot snapshot) {
            this.snapshot = new ComponentReference<LXSnapshot>(snapshot);
        }

        @Override
        public void perform(LX lx) {
            this.commands.clear();
            this.recalled = lx.engine.snapshots.recall(this.snapshot.get(), this.commands);
        }

        @Override
        public void undo(LX lx) throws InvalidCommandException {
            for (LXCommand command : this.commands) {
                command.undo(lx);
            }
        }

        @Override
        public boolean isIgnored() {
            return !this.recalled;
        }

        @Override
        public String getDescription() {
            return "Recall Snapshot";
        }
    }

