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

import heronarts.lx.parameter.BooleanParameter;

/**
 * Interface for any component that can own modulators + mappings
 */
public interface LXModulationContainer {
  /**
   * Get the modulation engine implementation for this component
   *
   * @return Modulation engine
   */
  public LXModulationEngine getModulationEngine();

  /**
   * Return the parameter used to toggle whether the modulation engine
   * is expanded in the UI.
   *
   * @return Parameter that toggles modulation visibility, or null
   */
  public default BooleanParameter getModulationExpanded() { return null; }
}
