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

import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import heronarts.lx.model.LXModel;
import heronarts.lx.model.LXView;
import heronarts.lx.parameter.BooleanParameter;
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
    .setDescription("Whether view coordinates are noramlized relative to the view, or absolute model");

  private LXView view = null;

  private int index = 0;

  public LXViewDefinition(LX lx) {
    super(lx, "View");
    setParent(lx.structure.views);
    addParameter("enabled", this.enabled);
    addParameter("selector", this.selector);
    addParameter("normalization", this.normalization);
  }

  @Override
  public void onParameterChanged(LXParameter p) {
    if (p == this.enabled || p == this.selector || p == this.normalization) {
      rebuild();
    } else if (p == this.label) {
      this.lx.structure.views.updateSelectors();
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

  void rebuild() {
    if (this.view != null) {
      this.view.dispose();
    }
    final String viewSelector = this.selector.getString();
    if (this.enabled.isOn() && !LXUtils.isEmpty(viewSelector)) {
      this.view = LXView.create(
        this.lx.getModel(),
        viewSelector,
        this.normalization.getEnum()
      );
    }
  }

}
