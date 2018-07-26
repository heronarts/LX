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

package heronarts.lx.parameter;

import com.google.gson.JsonObject;

import heronarts.lx.LX;

public class LXCompoundModulation extends LXParameterModulation {

  public final LXNormalizedParameter source;

  public final CompoundParameter target;

  public final EnumParameter<LXParameter.Polarity> polarity =
    new EnumParameter<LXParameter.Polarity>("Polarity", LXParameter.Polarity.UNIPOLAR)
    .setDescription("Species whether this modulation is unipolar (one-directional) or bipolar (bi-directional)");

  public final BoundedParameter range = (BoundedParameter)
    new BoundedParameter("Range", 0, -1, 1)
    .setDescription("Species the depth of this modulation, may be positive or negative")
    .setPolarity(LXParameter.Polarity.BIPOLAR);

  public LXCompoundModulation(LX lx, JsonObject obj) {
    this(
      (LXNormalizedParameter) getParameter(lx, obj.getAsJsonObject(KEY_SOURCE)),
      (CompoundParameter) getParameter(lx, obj.getAsJsonObject(KEY_TARGET))
    );
  }

  public LXCompoundModulation(LXNormalizedParameter source, CompoundParameter target) {
    super(source, target);
    this.source = source;
    this.target = target;
    this.polarity.setValue(source.getPolarity());
    addParameter(this.polarity);
    addParameter(this.range);
    target.addModulation(this);
  }

  public LXCompoundModulation setPolarity(LXParameter.Polarity polarity) {
    this.polarity.setValue(polarity);
    return this;
  }

  public LXParameter.Polarity getPolarity() {
    return this.polarity.getEnum();
  }

  @Override
  public void dispose() {
    this.target.removeModulation(this);
    super.dispose();
  }

}
