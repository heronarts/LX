/**
 * Copyright 2017- Mark C. Slee, Heron Arts LLC
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

package heronarts.lx.command;

import java.util.Stack;

import heronarts.lx.LX;
import heronarts.lx.command.LXCommand.InvalidCommandException;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.MutableParameter;

/**
 * The LX command engine keeps track of high-level UI commands that have been
 * performed. A stack is maintained which enables Undo operations to take place.
 */
public class LXCommandEngine {

  private final LX lx;

  public LXCommandEngine(LX lx) {
    this.lx = lx;
  }

  public final MutableParameter undoChanged = new MutableParameter("Undo");
  public final MutableParameter redoChanged = new MutableParameter("Redo");

  private final Stack<LXCommand> undoStack = new Stack<LXCommand>();
  private final Stack<LXCommand> redoStack = new Stack<LXCommand>();

  private long dirtyTimeMs = -1;

  public final BooleanParameter dirty =
    new BooleanParameter("Dirty", false)
    .setDescription("Whether the project has unsaved changes");

  /**
   * Performs a command and pushes it onto the undo stack.
   *
   * @param command Command to perform and push onto the undo stack
   * @return this
   */
  public LXCommandEngine perform(LXCommand command) {
    try {

      // Perform the command
      command.perform(this.lx);

      if (!command.isIgnored()) {
        // If the event it already at the top of the pack, it has been updated
        // and is not re-pushed after it is performed again
        if (this.undoStack.isEmpty() || (this.undoStack.peek() != command)) {
          this.undoStack.push(command);
          this.undoChanged.bang();
        }

        // A new action has occurred, we've branched and redo is done
        this.redoStack.clear();
        this.redoChanged.bang();
      }

    } catch (InvalidCommandException icx) {
      this.lx.pushError(icx, "Unexpected error performing action " + command.getName() + "\n" + getErrorMessage(icx));
      LX.error(icx, "Unexpected error performing action " + command + " - bad internal state?");
      clear();
    } catch (Exception x) {
      this.lx.pushError(x, "Unexpected error performing action " + command.getName() + "\n" + getErrorMessage(x));
      LX.error(x, "Unexpected error performing action " + command + " - bad internal state?");
      clear();
    }

    setDirty(true);
    return this;
  }

  private String getErrorMessage(Exception x) {
    String msg = x.getLocalizedMessage();
    if (msg != null) {
      return msg;
    }
    return x.getClass().getSimpleName();
  }

  public boolean isDirty() {
    return this.dirty.isOn();
  }

  public boolean isDirty(long sinceMs) {
    return (this.dirtyTimeMs > sinceMs) && isDirty();
  }

  public LXCommandEngine setDirty(boolean dirty) {
    this.dirty.setValue(dirty);
    this.dirtyTimeMs = this.lx.engine.nowMillis;
    return this;
  }

  public LXCommand getUndoCommand() {
    return this.undoStack.empty() ? null : this.undoStack.peek();
  }

  public LXCommand getRedoCommand() {
    return this.redoStack.empty() ? null : this.redoStack.peek();
  }

  public LXCommandEngine clear() {
    this.undoStack.clear();
    this.redoStack.clear();
    this.undoChanged.bang();
    this.redoChanged.bang();
    return this;
  }

  /**
   * Undoes the last command on the undo stack, if there is any. If the
   * undo stack is empty then this is a no-op.
   *
   * @return this
   */
  public LXCommandEngine undo() {
    if (!this.undoStack.empty()) {
      LXCommand command = this.undoStack.pop();
      try {
        command.undo(this.lx);
        this.redoStack.push(command);
        this.undoChanged.bang();
        this.redoChanged.bang();
      } catch (InvalidCommandException icx) {
        this.lx.pushError(icx, "Unexpected error on undo of " + command.getName() + "\n" + icx.getMessage());
        LX.error(icx, "Unhandled exception on undo " + command + " - bad internal state?");
        clear();
      } catch (Exception x) {
        this.lx.pushError(x, "Unexpected error on undo of " + command.getName() + "\n" + x.getLocalizedMessage());
        LX.error(x, "Unhandled exception on undo " + command + " - bad internal state?");
        clear();
      }
      setDirty(true);
    }
    return this;
  }

  /**
   * When possible, re-does an operation that has been undone.
   *
   * @return this
   */
  public LXCommandEngine redo() {
    if (!this.redoStack.empty()) {
      LXCommand command = this.redoStack.pop();
      try {
        command.perform(this.lx);
        this.undoStack.push(command);
        this.undoChanged.bang();
        this.redoChanged.bang();
      } catch (InvalidCommandException icx) {
        this.lx.pushError(icx, "Unexpected error on redo of " + command.getName() + "\n" + icx.getMessage());
        LX.error(icx, "Unhandled exception on redo " + command + " - bad internal state?");
        clear();
      } catch (Exception x) {
        this.lx.pushError(x, "Unexpected error on redo of " + command.getName() + "\n" + x.getLocalizedMessage());
        LX.error(x, "Unhandled exception on redo " + command + " - bad internal state?");
        clear();
      }
      setDirty(true);
    }
    return this;
  }

}
