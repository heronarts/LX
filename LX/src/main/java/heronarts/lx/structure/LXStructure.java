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

import java.io.File;
import java.net.SocketException;
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
import heronarts.lx.output.LXDatagram;
import heronarts.lx.output.LXDatagramOutput;

public class LXStructure extends LXComponent {

  public class Output extends LXDatagramOutput {
    public Output(LX lx) throws SocketException {
      super(lx);
    }

    @Override
    public LXDatagramOutput addDatagram(LXDatagram datagram) {
      throw new UnsupportedOperationException("No adding custom datagrams to LXStructure output");
    }

    @Override
    public LXDatagramOutput addDatagrams(LXDatagram[] datagram) {
      throw new UnsupportedOperationException("No adding custom datagrams to LXStructure output");
    }

    @Override
    protected void onSend(int[] colors, double brightness) {
      long now = System.currentTimeMillis();
      beforeSend(colors);
      for (LXFixture fixture : fixtures) {
        LXDatagram datagram = fixture.getDatagram();
        if (datagram != null) {
          onSendDatagram(datagram, now, colors, brightness);
        }
      }
      afterSend(colors);
    }
  }

  public interface Listener {
    public void fixtureAdded(LXFixture fixture);
    public void fixtureRemoved(LXFixture fixture);
    public void fixtureMoved(LXFixture fixture, int index);
  }

  String fixturePath = ".";

  private final List<Listener> listeners = new ArrayList<Listener>();

  private final List<LXFixture> mutableFixtures = new ArrayList<LXFixture>();
  public final List<LXFixture> fixtures = Collections.unmodifiableList(this.mutableFixtures);

  private LXModel model = new LXModel();

  private LXModel staticModel = null;

  public final Output output;

  public LXStructure(LX lx) {
    super(lx);
    Output output = null;
    try {
      output = new Output(lx);
    } catch (SocketException sx) {
      System.err.println("Failed to create datagram socket for structure datagram output, will continue with no network output: " + sx.getMessage());
      sx.printStackTrace();
    }
    this.output = output;
  }

  public LXStructure setFixturePath(String fixturePath) {
    this.fixturePath = fixturePath;
    return this;
  }

  public void registerFixtures() {
    File fixtureDir = new File(this.fixturePath + File.separator + "fixtures");
    if (fixtureDir.exists() && fixtureDir.isDirectory()) {
      for (String fixture : fixtureDir.list()) {
        if (fixture.endsWith(".lxf")) {
          this.lx.registerFixture(fixture.substring(0, fixture.length() - ".lxf".length()));
        }
      }
    }
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

  private void checkStaticModel(boolean isStatic, String error) {
    if ((this.staticModel != null) != isStatic) {
      throw new IllegalStateException(error);
    }
  }

  public LXStructure addFixture(LXFixture fixture) {
    return addFixture(fixture, -1);
  }

  public LXStructure addFixture(LXFixture fixture, int index) {
    checkStaticModel(false, "Cannot invoke addFixture when static model is in use");
    if (this.mutableFixtures.contains(fixture)) {
      throw new IllegalStateException("LXStructure may not contain two copies of same fixture");
    }
    if (index > this.fixtures.size()) {
      throw new IllegalArgumentException("Illegal fixture index: " + index);
    }
    if (index < 0) {
      index = this.fixtures.size();
    }
    this.mutableFixtures.add(index, fixture);
    _reindexFixtures();
    fixture.regeneratePoints();
    regenerateModel();
    for (Listener l : this.listeners) {
      l.fixtureAdded(fixture);
    }
    return this;
  }

  private void _reindexFixtures() {
    int i = 0;
    for (LXFixture fixture : this.fixtures) {
      fixture.setIndex(i++);
    }
  }

  public LXStructure moveFixture(LXFixture fixture, int index) {
    checkStaticModel(false, "Cannot invoke setFixtureIndex when static model is in use");
    if (!this.mutableFixtures.contains(fixture)) {
      throw new IllegalStateException("Cannot set index on fixture not in structure: " + fixture);
    }
    this.mutableFixtures.remove(fixture);
    this.mutableFixtures.add(index, fixture);
    _reindexFixtures();
    for (Listener l : this.listeners) {
      l.fixtureMoved(fixture, index);
    }

    // The point ordering is changed - regenerate the model!
    regenerateModel();
    return this;
  }

  public LXStructure selectFixtureRange(LXFixture fixture) {
    int targetIndex = fixture.getIndex();
    int minIndex = targetIndex, maxIndex = targetIndex;
    for (LXFixture f : this.fixtures) {
      int index = f.getIndex();
      if (f.selected.isOn()) {
        if (index < minIndex) {
          minIndex = index;
        }
        if (index > maxIndex) {
          maxIndex = index;
        }
      }
    }
    fixture.selected.setValue(true);
    for (int i = minIndex + 1; i < maxIndex; ++i) {
      this.fixtures.get(i).selected.setValue(true);
    }
    return this;
  }

  public LXStructure selectAllFixtures() {
    for (LXFixture fixture : this.fixtures) {
      fixture.selected.setValue(true);
    }
    return this;
  }

  public LXStructure selectFixture(LXFixture fixture) {
    return selectFixture(fixture, false);
  }

  public LXStructure selectFixture(LXFixture fixture, boolean isMultipleSelection) {
    if (isMultipleSelection) {
      fixture.selected.setValue(true);
    } else {
      for (LXFixture f : this.fixtures) {
        f.selected.setValue(fixture == f);
      }
    }
    return this;
  }

  public LXStructure soloFixture(LXFixture fixture) {
    for (LXFixture f : this.fixtures) {
      f.solo.setValue(f == fixture);
    }
    return this;
  }

  public LXStructure removeSelectedFixtures() {
    for (int i = this.fixtures.size() - 1; i >= 0; --i) {
      LXFixture fixture = this.fixtures.get(i);
      if (fixture.selected.isOn()) {
        removeFixture(fixture);
      }
    }
    return this;
  }

  public LXStructure removeFixture(LXFixture fixture) {
    checkStaticModel(false, "Cannot invoke removeFixture when static model is in use");
    if (!this.mutableFixtures.contains(fixture)) {
      throw new IllegalStateException("LXStructure does not contain fixture: " + fixture);
    }
    this.mutableFixtures.remove(fixture);
    _reindexFixtures();
    for (Listener l : this.listeners) {
      l.fixtureRemoved(fixture);
    }
    fixture.dispose();
    regenerateModel();
    return this;
  }

  private void removeAllFixtures() {
    checkStaticModel(false, "Cannot invoke removeAllFixtures when static model is in use");

    // Do this loop ourselves, rather than calling removeFixture(), so we only regenerate model once...
    for (int i = this.mutableFixtures.size() - 1; i >= 0; --i) {
      LXFixture fixture = this.mutableFixtures.remove(i);
      for (Listener l : this.listeners) {
        l.fixtureRemoved(fixture);
      }
      fixture.dispose();
    }
    regenerateModel();
  }

  public LXStructure adjustSelectedFixtureBrightness(float delta) {
    for (LXFixture fixture : this.fixtures) {
      if (fixture.selected.isOn()) {
        fixture.brightness.setNormalized(fixture.brightness.getNormalized() + delta);
      }
    }
    return this;
  }

  public LXStructure enableSelectedFixtures(boolean enabled) {
    for (LXFixture fixture : this.fixtures) {
      if (fixture.selected.isOn()) {
        fixture.enabled.setValue(enabled);
      }
    }
    return this;
  }

  public LXStructure identifySelectedFixtures(boolean identify) {
    for (LXFixture fixture : this.fixtures) {
      if (fixture.selected.isOn()) {
        fixture.identify.setValue(identify);
      }
    }
    return this;
  }

  public LXStructure setStaticModel(LXModel model) {
    removeAllFixtures();
    this.lx.setModel(this.model = this.staticModel = model);
    return this;
  }

  protected void regenerateModel() {
    if (this.isLoading) {
      this.needsRegenerate = true;
      return;
    }
    int i = 0;
    List<LXPoint> points = new ArrayList<LXPoint>(this.model.size);
    for (LXFixture fixture : this.fixtures) {
      for (LXPoint p : fixture.points) {
        p.index = i++;
        // Note: we make a deep copy here because a change to the number of points in one
        // fixture will alter point indices in all fixtures after it. When we're in multi-threaded
        // mode, that point might have been passed to the UI, which holds a reference to the model.
        // The indices passed to the UI cannot be changed mid-flight, so we make new copies of all
        // points here to stay safe.
        points.add(new LXPoint(p));
      }
      // The fixture's point indices may have changed... we'll need to update its datagram
      fixture.updateDatagram();
    }
    this.lx.setModel(this.model = new LXModel(points));
  }

  protected void fixtureRegenerated(LXFixture fixture) {
    // No indices have changed, only the fixture has been regenerated
    for (LXPoint p : fixture.points) {
      this.model.points[p.index].set(p);
    }
    // We need to re-normalize the points in the model, since some have changed
    this.model.update();
  }

  private boolean isLoading = false;
  private boolean needsRegenerate = false;

  private static final String KEY_FIXTURES = "fixtures";

  @Override
  public void load(LX lx, JsonObject obj) {
    this.isLoading = true;
    super.load(lx, obj);
    if ((this.staticModel == null) && obj.has(KEY_FIXTURES)) {
      removeAllFixtures();
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
