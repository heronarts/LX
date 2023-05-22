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

package heronarts.lx.modulation;

import com.google.gson.JsonObject;

import heronarts.lx.LX;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.parameter.LXNormalizedParameter;
import heronarts.lx.parameter.LXParameter;

public class LXCompoundModulation extends LXParameterModulation {

  public final LXNormalizedParameter source;

  public final CompoundParameter target;

  public final EnumParameter<LXParameter.Polarity> polarity =
    new EnumParameter<LXParameter.Polarity>("Polarity", LXParameter.Polarity.UNIPOLAR)
    .setDescription("Specifies whether this modulation is unipolar (one-directional) or bipolar (bi-directional)");

  public final CompoundParameter range =
    new CompoundParameter("Range", 0, -1, 1)
    .setDescription("Specifies the depth of this modulation, may be positive or negative")
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setPolarity(LXParameter.Polarity.BIPOLAR);

  public LXCompoundModulation(LX lx, LXModulationEngine scope, JsonObject obj) throws ModulationException {
    this(
      scope,
      (LXNormalizedParameter) getParameter(lx, scope, obj.getAsJsonObject(KEY_SOURCE)),
      (CompoundParameter) getParameter(lx, scope, obj.getAsJsonObject(KEY_TARGET))
    );
  }

  public LXCompoundModulation(LXModulationEngine scope, LXNormalizedParameter source, CompoundParameter target) throws ModulationException {
    super(scope, source, target);
    this.source = source;
    this.target = target;
    this.polarity.setValue(source.getPolarity());
    addParameter("polarity", this.polarity);
    addParameter("range", this.range);
    addLegacyParameter("Polarity", this.polarity);
    addLegacyParameter("Range", this.range);

    target.addModulation(this);
    setParent(scope);
  }

  public LXCompoundModulation setPolarity(LXParameter.Polarity polarity) {
    this.polarity.setValue(polarity);
    return this;
  }

  public LXParameter.Polarity getPolarity() {
    return this.polarity.getEnum();
  }

  @Override
  public String getPath() {
    return "modulation/" + (this.index + 1);
  }

  @Override
  public void dispose() {
    this.target.removeModulation(this);
    super.dispose();
  }

}
