/**
 * Copyright 2022- Mark C. Slee, Heron Arts LLC
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

package heronarts.lx.transform;

import java.util.ArrayList;
import java.util.List;

import heronarts.lx.parameter.LXParameter;

public class LXParameterizedMatrix extends LXMatrix {

  public interface UpdateFunction {
    public void updateMatrix(LXMatrix matrix);
  }

  private class Parameter {
    private final LXParameter parameter;
    private double previousValue = -1;

    private Parameter(LXParameter parameter) {
      this.parameter = parameter;
    }
  }

  private final List<Parameter> parameters = new ArrayList<Parameter>();

  private boolean dirty = true;

  public LXParameterizedMatrix addParameter(LXParameter parameter) {
    this.parameters.add(new Parameter(parameter));
    return this;
  }

  public void update(UpdateFunction update) {
    for (Parameter parameter : this.parameters) {
      double value = parameter.parameter.getValue();
      if (value != parameter.previousValue) {
        this.dirty = true;
        parameter.previousValue = value;
      }
    }
    if (this.dirty) {
      update.updateMatrix(identity());
      this.dirty = false;
    }
  }

}
