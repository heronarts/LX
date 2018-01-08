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

package heronarts.lx.parameter;

/**
 * A virtual parameter is one that wraps or forwards to another real parameter.
 * Typically this is done in situations in which the parameter to forward to
 * varies based on some other contextual action or UI, for instance a virtual
 * knob that maps to whatever pattern is currently active.
 * 
 * This type of parameter is not listenable, since the underlying parameter is
 * dynamic.
 */
public abstract class LXVirtualParameter implements LXParameter {

  /**
   * The parameter to operate on.
   * 
   * @return The underlying real parameter to operate on.
   */
  protected abstract LXParameter getRealParameter();

  public final LXParameter reset() {
    LXParameter p = getRealParameter();
    if (p != null) {
      p.reset();
    }
    return this;
  }

  public final LXParameter setValue(double value) {
    LXParameter p = getRealParameter();
    if (p != null) {
      p.setValue(value);
    }
    return this;
  }

  public double getValue() {
    LXParameter p = getRealParameter();
    if (p != null) {
      return p.getValue();
    }
    return 0;
  }

  public float getValuef() {
    return (float) getValue();
  }

  public String getLabel() {
    LXParameter p = getRealParameter();
    if (p != null) {
      return p.getLabel();
    }
    return null;
  }

}
