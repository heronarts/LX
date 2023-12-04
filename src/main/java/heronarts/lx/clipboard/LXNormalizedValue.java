/**
 * Copyright 2019- Mark C. Slee, Heron Arts LLC
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

import heronarts.lx.parameter.LXNormalizedParameter;

public class LXNormalizedValue implements LXClipboardItem {

  public final double value;

  public LXNormalizedValue(LXNormalizedParameter p) {
    this.value = p.getBaseNormalized();
  }

  public LXNormalizedValue(double value) {
    this.value = value;
  }

  public double getValue() {
    return this.value;
  }

  @Override
  public String getSystemClipboardString() {
    return Double.toString(this.value);
  }

  @Override
  public Class<?> getComponentClass() {
    return LXNormalizedValue.class;
  }
}
