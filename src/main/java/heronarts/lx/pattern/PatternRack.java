/**
 * Copyright 2025- Mark C. Slee, Heron Arts LLC
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

package heronarts.lx.pattern;

import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.LXComponent;
import heronarts.lx.mixer.LXPatternEngine;

@LXCategory(LXCategory.OTHER)
@LXComponent.Name("Pattern Rack")
public class PatternRack extends LXPattern implements LXPatternEngine.Container {

  public final LXPatternEngine patternEngine;
  private final LXPatternEngine.Listener delegate = new LXPatternEngine.Listener() {};

  public PatternRack(LX lx) {
    super(lx);
    this.patternEngine = new LXPatternEngine(lx, this);
  }

  @Override
  protected void run(double deltaMs) {
    // All handled by the pattern engine!
    this.patternEngine.loop(getBuffer(), getModelView(), deltaMs);
  }

  @Override
  public LXPatternEngine getPatternEngine() {
    return this.patternEngine;
  }

  @Override
  public LXPatternEngine.Listener getPatternEngineDelegate() {
    return this.delegate;
  }

}
