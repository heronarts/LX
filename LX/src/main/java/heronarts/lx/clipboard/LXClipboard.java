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

import heronarts.lx.LX;

public class LXClipboard {

  private final LX lx;
  private LXClipboardItem item;

  public LXClipboard(LX lx) {
    this.lx = lx;
  }

  public LXClipboard setItem(LXClipboardItem item) {
    this.item = item;
    if (this.item != null) {
      String clipboardString = item.getSystemClipboardString();
      if (clipboardString != null) {
        lx.setSystemClipboardString(clipboardString);
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
