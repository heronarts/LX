/**
 * Copyright 2013- Mark C. Slee, Heron Arts LLC
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

package heronarts.lx.model;

import java.util.ArrayList;
import java.util.List;

import heronarts.lx.transform.LXVector;

/**
 * Simple model of a strip of points in one axis.
 */
public class StripModel extends LXModel {

  public static class Metrics {

    public final int length;

    private final LXVector origin = new LXVector(0, 0, 0);
    private final LXVector spacing = new LXVector(1, 0, 0);

    public Metrics(int length) {
      this.length = length;
    }

    public Metrics setOrigin(float x, float y, float z) {
      this.origin.set(x, y, z);
      return this;
    }

    public Metrics setOrigin(LXVector v) {
      this.origin.set(v);
      return this;
    }

    public Metrics setSpacing(float x, float y, float z) {
      this.spacing.set(x, y, z);
      return this;
    }

    public Metrics setSpacing(LXVector v) {
      this.spacing.set(v);
      return this;
    }
  }

  public final Metrics metrics;

  public final int length;

  public StripModel(Metrics metrics) {
    super(makePoints(metrics), LXModel.Key.STRIP);
    this.metrics = metrics;
    this.length = metrics.length;
  }

  public StripModel(int length) {
    this(new Metrics(length));
  }

  private static List<LXPoint> makePoints(Metrics metrics) {
    List<LXPoint> points = new ArrayList<LXPoint>(metrics.length);
    for (int i = 0; i < metrics.length; ++i) {
      points.add(new LXPoint(
        metrics.origin.x + i * metrics.spacing.x,
        metrics.origin.y + i * metrics.spacing.y,
        metrics.origin.z + i * metrics.spacing.z
      ));
    }
    return points;
  }
}
