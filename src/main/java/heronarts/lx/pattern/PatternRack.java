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

import java.util.List;

import com.google.gson.JsonObject;

import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.LXComponent;
import heronarts.lx.midi.LXShortMessage;
import heronarts.lx.midi.MidiPanic;
import heronarts.lx.mixer.LXPatternEngine;
import heronarts.lx.osc.OscMessage;
import heronarts.lx.parameter.LXParameter;

@LXCategory(LXCategory.OTHER)
@LXComponent.Name("Pattern Rack")
public class PatternRack extends LXPattern implements LXPatternEngine.Container, LXPattern.Midi {

  public final LXPatternEngine patternEngine;
  private final LXPatternEngine.Listener delegate = new LXPatternEngine.Listener() {};
  public final List<LXPattern> patterns;

  public PatternRack(LX lx) {
    super(lx);
    this.label.setValue("Rack");
    this.patternEngine = new LXPatternEngine(lx, this);
    addArray("pattern", this.patterns = this.patternEngine.patterns);
    addParameters(this.patternEngine.parameters);
  }

  @Override
  public boolean isClipAutomationControl(LXParameter parameter) {
    return !(this.patternEngine.parameters.containsValue(parameter)) &&
      super.isClipAutomationControl(parameter);
  }

  @Override
  public boolean isSnapshotControl(LXParameter parameter) {
    return !(this.patternEngine.parameters.containsValue(parameter)) &&
      super.isSnapshotControl(parameter);
  }

  @Override
  public boolean isHiddenControl(LXParameter parameter) {
    return
      super.isHiddenControl(parameter) ||
      this.patternEngine.parameters.containsValue(parameter);
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

  @Override
  public void midiDispatch(LXShortMessage message) {
    super.midiDispatch(message);
    if (message instanceof MidiPanic) {
      this.patternEngine.midiDispatch(message);
    } else if (this.midiFilter.filter(message)) {
      this.patternEngine.midiDispatch(message);
    }
  }

  @Override
  public boolean handleOscMessage(OscMessage message, String[] parts, int index) {
    if (this.patternEngine.handleOscMessage(message, parts, index)) {
      return true;
    }
    return super.handleOscMessage(message, parts, index);
  }

  @Override
  public void save(LX lx, JsonObject obj) {
    super.save(lx, obj);
    this.patternEngine.save(lx, obj);
  }

  @Override
  public void load(LX lx, JsonObject obj) {
    this.patternEngine.load(lx, obj);
    super.load(lx, obj);
  }

  @Override
  public void dispose() {
    this.patternEngine.dispose();
    super.dispose();
  }

}
