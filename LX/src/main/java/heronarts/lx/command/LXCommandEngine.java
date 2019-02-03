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
 * ##library.name##
 * ##library.sentence##
 * ##library.url##
 *
 * @author      ##author##
 * @modified    ##date##
 * @version     ##library.prettyVersion## (##library.version##)
 */

package heronarts.lx.command;

import java.util.Stack;

import heronarts.lx.LX;
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

  /**
   * Performs a command and pushes it onto the undo stack.
   *
   * @param command Command to perform and push onto the undo stack
   * @return this
   */
  public LXCommandEngine perform(LXCommand command) {
    command.perform(this.lx);

    // If the event it already at the top of the pack, it has been updated
    // and is not re-pushed after it is performed again
    if (this.undoStack.isEmpty() || (this.undoStack.peek() != command)) {
      this.undoStack.push(command);
      this.undoChanged.bang();
    }

    // A new action has occurred, we've branched and redo is done
    this.redoStack.clear();
    this.redoChanged.bang();

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
      } catch (Exception x) {
        System.err.println("Unhandled exception on undo, bad internal state?");
        x.printStackTrace();
        clear();
      }
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
      } catch (Exception x) {
        System.err.println("Unhandled exception on redo, bad internal state?");
        x.printStackTrace();
        clear();
      }
    }
    return this;
  }

}
