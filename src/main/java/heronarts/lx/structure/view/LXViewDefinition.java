/**
 * Copyright 2023- Mark C. Slee, Heron Arts LLC
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

package heronarts.lx.structure.view;

import com.google.gson.JsonObject;

import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import heronarts.lx.model.LXModel;
import heronarts.lx.model.LXView;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.StringParameter;
import heronarts.lx.utils.LXUtils;

public class LXViewDefinition extends LXComponent implements LXComponent.Renamable {

  public final BooleanParameter enabled =
    new BooleanParameter("Enabled", true)
    .setDescription("Whether this view is enabled");

  public final StringParameter selector =
    new StringParameter("View Selector", "")
    .setDescription("Selection string for this view");

  public final EnumParameter<LXView.Normalization> normalization =
    new EnumParameter<LXView.Normalization>("View Normalization", LXView.Normalization.RELATIVE)
    .setDescription("Whether point coordinates are re-normalized relative to the view group, or kept the same as in absolute model");

  public final EnumParameter<LXView.Orientation> orientation =
    new EnumParameter<LXView.Orientation>("View Orientation", LXView.Orientation.GLOBAL)
    .setDescription("Whether view points are oriented in global space or relative to the orientation of their matching view group");

  public final BooleanParameter priority =
    new BooleanParameter("Priority", true)
    .setDescription("Whether this view is enabled on the priority view knob");

  public final BooleanParameter invalidOrientation =
    new BooleanParameter("Invalid Orientation", false)
    .setDescription("Whether the view specifies invalid orientation");

  public final DiscreteParameter numGroups =
    new DiscreteParameter("Num Groups", 0, Integer.MAX_VALUE)
    .setDescription("How many matching groups are in the view");

  public final DiscreteParameter numFixtures =
    new DiscreteParameter("Num Fixtures", 0, Integer.MAX_VALUE)
    .setDescription("How many matching fixtures are in the view");

  public final BooleanParameter cueActive =
    new BooleanParameter("Cue", false)
    .setMode(BooleanParameter.Mode.MOMENTARY)
    .setDescription("Preview the fixtures this view applies to");

  private LXView view = null;

  private int index = 0;

  public LXViewDefinition(LX lx) {
    super(lx, "View");
    setParent(lx.structure.views);
    addParameter("enabled", this.enabled);
    addParameter("selector", this.selector);
    addParameter("normalization", this.normalization);
    addParameter("orientation", this.orientation);
    addParameter("priority", this.priority);
    addParameter("cueActive", this.cueActive);

    this.modulationColor.addListener(this);
  }

  @Override
  public void onParameterChanged(LXParameter p) {
    if (p == this.enabled || p == this.selector || p == this.normalization || p == this.orientation) {
      rebuild();
      if (p == this.enabled) {
        this.lx.structure.views.viewStateChanged(this);
      }
    } else if (p == this.label) {
      this.lx.structure.views.viewRenamed(this);
    } else if (p == this.modulationColor) {
      this.lx.structure.views.viewStateChanged(this);
    } else if (p == this.priority) {
      this.lx.structure.views.viewPriorityChanged(this);
    } else if (p == this.cueActive) {
      if (this.cueActive.isOn()) {
        // Only one at a time! Avoid confusion.
        for (LXViewDefinition view : this.lx.structure.views.views) {
          if (view != this) {
            view.cueActive.setValue(false);
          }
        }
        this.lx.structure.getModel().cueView = this.view;
      } else {
        this.lx.structure.getModel().cueView = null;
      }
    }
  }

  void setIndex(int index) {
    this.index = index;
  }

  public int getIndex() {
    return this.index;
  }

  @Override
  public String getPath() {
    return "view/" + (this.index + 1);
  }

  public LXModel getModelView() {
    final LXView view = getView();
    return (view != null) ? view : this.lx.getModel();
  }

  public LXView getView() {
    return this.view;
  }

  private void disposeView() {
    if (this.view != null) {
      this.view.dispose();
      this.view = null;
    }
  }

  void rebuild() {
    if (this.inLoad) {
      // Avoid spurious rebuilds when loading, which may touch
      // multiple parameters. Set a flag to do *one* rebuild
      // after the full load finishes.
      this.needsRebuild = true;
      return;
    }

    disposeView();
    final String viewSelector = this.selector.getString();
    final LXModel model = this.lx.getModel();

    if (model.size > 0 && this.enabled.isOn() && !LXUtils.isEmpty(viewSelector)) {
      this.view = LXView.create(
        model,
        viewSelector,
        this.normalization.getEnum(),
        this.orientation.getEnum(),
        this
      );
    } else {
      this.invalidOrientation.setValue(false);
      this.numGroups.setValue(0);
      this.numFixtures.setValue(0);
    }
  }

  private boolean inLoad = false;
  private boolean needsRebuild = false;

  @Override
  public void load(LX lx, JsonObject obj) {
    this.inLoad = true;
    super.load(lx, obj);
    this.inLoad = false;
    if (this.needsRebuild) {
      this.needsRebuild = false;
      rebuild();
    }
  }

  @Override
  public void dispose() {
    disposeView();
    this.modulationColor.removeListener(this);
    super.dispose();
  }

}
