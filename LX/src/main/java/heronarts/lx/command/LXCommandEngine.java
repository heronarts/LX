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

/**
 * The LX command engine keeps track of high-level UI commands that have been
 * performed. A stack is maintained which enables Undo operations to take place.
 */
public class LXCommandEngine {

  private final LX lx;

  public LXCommandEngine(LX lx) {
    this.lx = lx;
  }

  private final Stack<LXCommand> undoStack = new Stack<LXCommand>();

  /**
   * Pushes a command onto the undo stack. This method does *not* perform
   * the operation. This is useful for situations in which the command
   * action has already taken place, or happens via a different mechanism,
   * and only undo behavior is desired.
   *
   * @param command Command to push onto the undo stack
   * @return this
   */
  public LXCommandEngine push(LXCommand command) {
    this.undoStack.push(command);
    return this;
  }

  /**
   * Peforms a command and pushes it onto the undo stack.
   *
   * @param command Command to perform and push onto the undo stack
   * @return this
   */
  public LXCommandEngine perform(LXCommand command) {
    command.perform(this.lx);
    push(command);
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
      if (command != null) {
        try {
          command.undo(this.lx);
        } catch (Exception x) {
          System.err.println("Unhandled exception on undo, bad internal state?");
          x.printStackTrace();
        }
      }
    }
    return this;
  }

}
