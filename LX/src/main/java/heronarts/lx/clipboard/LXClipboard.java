/**
 * Copyright 2013- Mark C. Slee, Heron Arts LLC
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

package heronarts.lx.clipboard;

import java.awt.Toolkit;
import java.awt.datatransfer.Transferable;

public class LXClipboard {

  private LXClipboardItem item;

  public LXClipboard() {}

  public LXClipboard setItem(LXClipboardItem item) {
    this.item = item;
    if (this.item != null) {
      Transferable transferable = item.getSystemClipboardItem();
      if (transferable != null) {
        try {
          Toolkit.getDefaultToolkit().getSystemClipboard().setContents(transferable, null);
        } catch (Exception x) {
          System.err.println("Exception setting system clipboard");
          x.printStackTrace();
        }
      }
    }
    return this;
  }

  public LXClipboardItem getItem() {
    return this.item;
  }

  public LXClipboard clearItem(LXClipboardItem item) {
    if (this.item == item) {
      this.item = null;
    }
    return this;
  }
}
