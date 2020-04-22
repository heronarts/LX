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

package heronarts.lx;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Generic interface for an object that contains callback methods for
 * the LX engine. These can be used to integrate third-party components
 * or perform custom initializations.
 */
public interface LXPlugin {

  /**
   * An annotation to be applied to an LXPlugin class giving it a user-facing name
   */
  @Documented
  @Target(ElementType.TYPE)
  @Retention(RetentionPolicy.RUNTIME)
  public @interface Name {
    String value();
  }

  /**
   * This method is invoked on the plugin object after LX has been initialized. Note
   * that this happens before any project files have been loaded. The plugin can
   * add components to the LX hierarchy and register listeners if it is going to
   * take actions based upon those.
   *
   * @param lx LX instance
   */
  public void initialize(LX lx);
}
