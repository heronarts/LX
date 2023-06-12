/**
 * Copyright 2018- Mark C. Slee, Heron Arts LLC
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
 * An annotation to be applied to LXPattern or LXEffect classes describing what category
 * the component belongs to.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface LXCategory {

  public static final String CORE = "Core";
  public static final String FORM = "Form";
  public static final String COLOR = "Color";
  public static final String MIDI = "MIDI";
  public static final String STRIP = "Strip";
  public static final String TEXTURE = "Texture";
  public static final String TRIGGER = "Trigger";
  public static final String TEST = "Test";
  public static final String OTHER = "Other";
  public static final String AUDIO = "Audio";
  public static final String MACRO = "Macro";

  String value();

}
