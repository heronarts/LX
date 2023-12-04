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

import java.util.List;

import com.google.gson.JsonObject;

import heronarts.lx.LX;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.parameter.LXNormalizedParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.LXParameter.Polarity;

public class LXCompoundModulation extends LXParameterModulation {

  /**
   * A parameter type that can receive compound modulation. The
   * canonical examples of this are CompoundParameter and
   * CompoundDiscreteParameter.
   */
  public interface Target extends LXNormalizedParameter {
    /**
     * Get the list of modulations applied to this parameter
     *
     * @return List of modulations applied to this parameter
     */
    public List<LXCompoundModulation> getModulations();

    /**
     * Add a modulation to this parameter
     *
     * @param modulation Modulation to add
     * @return The parameter
     */
    public Target addModulation(LXCompoundModulation modulation);

    /**
     * Remove a modulation fromthis parameter
     *
     * @param modulation Modulation to remove
     * @return The parameter
     */
    public Target removeModulation(LXCompoundModulation modulation);


    /**
     * Adds a listener to the modulation target
     *
     * @param listener Listener
     * @return The target
     */
    public Target addModulationListener(Listener listener);

    /**
     * Removes a listener from the modulation target
     *
     * @param listener Listener
     * @return The target
     */
    public Target removeModulationListener(Listener listener);
  }

  /**
   * Listener that is fired when there is a change to the list of modulations being
   * applied to a target parameter.
   */
  public interface Listener {
    /**
     * Fires when a new modulation is added to a parameter
     *
     * @param parameter Target parameter
     * @param modulation Modulation that was added
     */
    public void modulationAdded(Target parameter, LXCompoundModulation modulation);

    /**
     * Fires when a modulation is removed from a target parameter
     *
     * @param parameter Target parameter
     * @param modulation Modulation that was removed
     */
    public void modulationRemoved(Target parameter, LXCompoundModulation modulation);
  }

  /**
   * The source parameter for this compound modulation mapping
   */
  public final LXNormalizedParameter source;

  /**
   * The target parameter that receives the compound modulation
   */
  public final Target target;

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
      (Target) getParameter(lx, scope, obj.getAsJsonObject(KEY_TARGET))
    );
  }

  public LXCompoundModulation(LXModulationEngine scope, LXNormalizedParameter source, Target target) throws ModulationException {
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

  public double getModulationAmount() {
    if (!this.enabled.isOn()) {
      return 0;
    }
    if (this.getPolarity() == Polarity.UNIPOLAR) {
      return this.source.getNormalized() * this.range.getValue();
    } else {
     return 2.*(this.source.getNormalized()-.5) * this.range.getValue();
    }
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
