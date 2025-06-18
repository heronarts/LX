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

package heronarts.lx.structure;

import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.BoundedParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.parameter.LXParameter;

public class LXStructureLabelConfig extends LXParameter.Collection {

  private static final long serialVersionUID = 1485456850356574699L;

  public enum Position {
    POSITION("Base Position"),
    TRANSFORM("Transform Position"),
    MIN("Model Min"),
    CENTER("Model Center"),
    MAX("Model Max");

    private final String label;

    private Position(String label) {
      this.label = label;
    }

    @Override
    public String toString() {
      return this.label;
    }
  }

  public enum HorizontalAlignment {
    LEFT("Left"),
    CENTER("Center"),
    RIGHT("Right");

    private final String label;

    private HorizontalAlignment(String label) {
      this.label = label;
    }

    @Override
    public String toString() {
      return this.label;
    }
  }

  public enum VerticalAlignment {
    TOP("Top"),
    MIDDLE("Middle"),
    BASELINE("Baseline"),
    BOTTOM("Bottom");

    private final String label;

    private VerticalAlignment(String label) {
      this.label = label;
    }

    @Override
    public String toString() {
      return this.label;
    }
  }

  public transient final BooleanParameter showLabels =
    new BooleanParameter("Show Fixture Labels", false)
    .setDescription("Show the fixture labels in the main preview window");

  public transient final EnumParameter<HorizontalAlignment> horizontalAlignment =
    new EnumParameter<>("Label Horizontal Alignment", HorizontalAlignment.CENTER);

  public transient final EnumParameter<VerticalAlignment> verticalAlignment =
    new EnumParameter<>("Label Vertical Alignment", VerticalAlignment.MIDDLE);

  public transient final EnumParameter<Position> positionX =
    new EnumParameter<Position>("Fixture Label Position X", Position.POSITION)
    .setDescription("How to position fixture X coordinate");

  public transient final EnumParameter<Position> positionY =
    new EnumParameter<Position>("Fixture Label Position Y", Position.POSITION)
    .setDescription("How to position fixture Y coordinate");

  public transient final EnumParameter<Position> positionZ =
    new EnumParameter<Position>("Fixture Label Position Z", Position.POSITION)
    .setDescription("How to position fixture Z coordinate");

  public transient final BoundedParameter offsetX =
    LXFixture.newPositionParameter("Fixture Label X-Offset", "X-Offset Fixture Labels");

  public transient final BoundedParameter offsetY =
    LXFixture.newPositionParameter("Fixture Label Y-Offset", "Z-Offset Fixture Labels");

  public transient final BoundedParameter offsetZ =
    LXFixture.newPositionParameter("Fixture Label Z-Offset", "Z-Offset Fixture Labels");

  LXStructureLabelConfig() {
    add("showFixtureLabels", this.showLabels);
    add("fixtureLabelHorizontalAlignment", this.horizontalAlignment);
    add("fixtureLabelVerticalAlignment", this.verticalAlignment);
    add("fixtureLabelPositionX", this.positionX);
    add("fixtureLabelPositionY", this.positionY);
    add("fixtureLabelPositionZ", this.positionZ);
    add("fixtureLabelOffsetX", this.offsetX);
    add("fixtureLabelOffsetY", this.offsetY);
    add("fixtureLabelOffsetZ", this.offsetZ);
  }

}
