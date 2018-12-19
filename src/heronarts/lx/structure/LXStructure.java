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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import heronarts.lx.LXSerializable;
import heronarts.lx.model.LXModel;
import heronarts.lx.model.LXPoint;

public class LXStructure extends LXComponent {

  public interface Listener {
    public void fixtureAdded(LXFixture fixture);
    public void fixtureRemoved(LXFixture fixture);
  }

  private final List<Listener> listeners = new ArrayList<Listener>();

  private final List<LXFixture> mutableFixtures = new ArrayList<LXFixture>();
  public final List<LXFixture> fixtures = Collections.unmodifiableList(this.mutableFixtures);

  private final LX lx;
  private LXModel model = new LXModel();

  public LXStructure(LX lx) {
    super(lx);
    this.lx = lx;
  }

  public LXModel getModel() {
    return this.model;
  }

  public LXStructure addListener(Listener listener) {
    if (this.listeners.contains(listener)) {
      throw new IllegalStateException("Cannot add same LXStructure listener twice: " + listener);
    }
    this.listeners.add(listener);
    return this;
  }

  public LXStructure removeListener(Listener listener) {
    this.listeners.remove(listener);
    return this;
  }

  public LXStructure addFixture(LXFixture fixture) {
    if (this.mutableFixtures.contains(fixture)) {
      throw new IllegalStateException("LXStructure may not contain two copies of same fixture");
    }
    this.mutableFixtures.add(fixture);
    addSubcomponent(fixture);
    fixture.regenerate();
    regenerateModel();
    for (Listener l : this.listeners) {
      l.fixtureAdded(fixture);
    }
    return this;
  }

  public LXStructure setStaticModel(LXModel model) {
    StaticModel staticModel = new StaticModel(this.lx, model);
    this.mutableFixtures.add(staticModel);
    for (Listener l : this.listeners) {
      l.fixtureAdded(staticModel);
    }
    return this;
  }

  public LXStructure setFixtureIndex(LXFixture fixture, int index) {
    if (!this.mutableFixtures.contains(fixture)) {
      throw new IllegalStateException("Cannot set index on fixture not in structure: " + fixture);
    }
    this.mutableFixtures.remove(fixture);
    this.mutableFixtures.add(index, fixture);
    return this;
  }

  public LXStructure removeFixture(LXFixture fixture) {
    if (!this.mutableFixtures.contains(fixture)) {
      throw new IllegalStateException("LXStructure does not contain fixture: " + fixture);
    }
    this.mutableFixtures.remove(fixture);
    for (Listener l : this.listeners) {
      l.fixtureRemoved(fixture);
    }
    fixture.dispose();
    regenerateModel();
    return this;
  }

  protected void regenerateModel() {
    if (this.isLoading) {
      this.needsRegenerate = true;
      return;
    }
    int i = 0;
    List<LXPoint> points = new ArrayList<LXPoint>();
    for (LXFixture fixture : this.fixtures) {
      for (LXPoint p : fixture.points) {
        p.index = i++;
        points.add(new LXPoint(p));
      }
    }
    this.lx.setModel(this.model = new LXModel(points));
  }

  private boolean isLoading = false;
  private boolean needsRegenerate = false;

  private static final String KEY_FIXTURES = "fixtures";

  @Override
  public void load(LX lx, JsonObject obj) {
    this.isLoading = true;
    super.load(lx, obj);
    if (obj.has(KEY_FIXTURES)) {
      JsonArray fixturesArr = obj.getAsJsonArray(KEY_FIXTURES);
      for (JsonElement fixtureElement : fixturesArr) {
        JsonObject fixtureObj = fixtureElement.getAsJsonObject();
        LXFixture fixture = this.lx.instantiateFixture(fixtureObj.get(KEY_CLASS).getAsString());
        if (fixture != null) {
          fixture.load(lx, fixtureObj);
          addFixture(fixture);
        }
      }
    }

    this.isLoading = false;
    if (this.needsRegenerate) {
      this.needsRegenerate = false;
      regenerateModel();
    }
  }

  @Override
  public void save(LX lx, JsonObject obj) {
    super.save(lx, obj);
    obj.add(KEY_FIXTURES, LXSerializable.Utils.toArray(lx, this.fixtures));
  }
}
