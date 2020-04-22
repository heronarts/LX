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
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonWriter;

import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import heronarts.lx.LXSerializable;
import heronarts.lx.model.LXModel;
import heronarts.lx.model.LXPoint;
import heronarts.lx.output.LXDatagram;
import heronarts.lx.output.LXDatagramOutput;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.StringParameter;

public class LXStructure extends LXComponent {

  private static final String PROJECT_MODEL = "<Embedded in Project>";

  public class Output extends LXDatagramOutput {
    public Output(LX lx) throws SocketException {
      super(lx);
    }

    @Override
    public LXDatagramOutput addDatagram(LXDatagram datagram) {
      throw new UnsupportedOperationException("Adding custom datagrams to LXStructure output is not allowed");
    }

    @Override
    public LXDatagramOutput addDatagrams(LXDatagram[] datagram) {
      throw new UnsupportedOperationException("Adding custom datagrams to LXStructure output is not allowed");
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

  /**
   * Listener interface for the top-level structure
   */
  public interface Listener {
    /**
     * Invoked when a fixture has been added to the structure
     *
     * @param fixture Fixture added
     */
    public void fixtureAdded(LXFixture fixture);

    /**
     * Invoked when a fixture has been removed from the structure
     *
     * @param fixture Fixture removed
     */
    public void fixtureRemoved(LXFixture fixture);

    /**
     * Invoked when a fixture has been moved in the structure's fixture list
     *
     * @param fixture Fixture moved
     * @param index New index of the fixture
     */
    public void fixtureMoved(LXFixture fixture, int index);
  }

  private File modelFile = null;

  public final StringParameter modelName =
    new StringParameter("Model Name", PROJECT_MODEL)
    .setDescription("Displays the name of the loaded model, may be a class or an .lxm file");

  public final BooleanParameter isStatic =
    new BooleanParameter("Static Model", false)
    .setDescription("Whether a static model class is being used");

  public final BooleanParameter syncModelFile =
    new BooleanParameter("Sync Model File", false)
    .setDescription("Keep the project model in sync with the model file. Saving the project automatically writes to the model file.");

  private final List<Listener> listeners = new ArrayList<Listener>();

  private final List<LXFixture> mutableFixtures = new ArrayList<LXFixture>();

  public final List<LXFixture> fixtures = Collections.unmodifiableList(this.mutableFixtures);

  private LXModel model;

  private LXModel staticModel = null;

  // Whether a single immutable model is used, defined at construction time
  private final boolean isImmutable;

  public final Output output;

  public LXStructure(LX lx) {
    this(lx, null);
  }

  public LXStructure(LX lx, LXModel immutable) {
    super(lx);
    addParameter("syncModelFile", this.syncModelFile);
    if (immutable != null) {
      this.isImmutable = true;
      this.staticModel = this.model = immutable.normalizePoints();
      this.isStatic.setValue(true);
    } else {
      this.isImmutable = false;
      this.model = new LXModel();
    }

    Output output = null;
    try {
      output = new Output(lx);
    } catch (SocketException sx) {
      LX.error(sx, "Failed to create datagram socket for structure datagram output, will continue with no network output: " + sx.getMessage());
    }
    this.output = output;
  }

  public File getModelFile() {
    return this.modelFile;
  }

  public LXModel getModel() {
    return this.model;
  }

  public LXStructure addListener(Listener listener) {
    Objects.requireNonNull(listener);
    if (this.listeners.contains(listener)) {
      throw new IllegalStateException("Cannot add duplicate LXStructure.Listener: " + listener);
    }
    this.listeners.add(listener);
    return this;
  }

  public LXStructure removeListener(Listener listener) {
    if (!this.listeners.contains(listener)) {
      throw new IllegalStateException("Cannot remove non-registered LXStructure.Listener: " + listener);
    }
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

  public List<LXFixture> getSelectedFixtures() {
    List<LXFixture> selected = new ArrayList<LXFixture>();
    for (LXFixture fixture : this.fixtures) {
      if (fixture.selected.isOn()) {
        selected.add(fixture);
      }
    }
    return selected;
  }

  public LXStructure removeFixtures(List<LXFixture> fixtures) {
    checkStaticModel(false, "Cannot invoke removeFixtures when static model is in use");
    List<LXFixture> removed = new ArrayList<LXFixture>();
    for (LXFixture fixture : fixtures) {
      if (!this.mutableFixtures.remove(fixture)) {
        throw new IllegalStateException("Cannot remove fixture not present in structure");
      }
      removed.add(fixture);
    }
    _reindexFixtures();
    for (LXFixture fixture : removed) {
      for (Listener l : this.listeners) {
        l.fixtureRemoved(fixture);
      }
      fixture.dispose();
    }
    regenerateModel();
    return this;
  }

  public LXStructure removeSelectedFixtures() {
    checkStaticModel(false, "Cannot invoke removeSelectedFixture when static model is in use");
    List<LXFixture> removed = new ArrayList<LXFixture>();
    for (int i = this.mutableFixtures.size() - 1; i >= 0; --i) {
      LXFixture fixture = this.mutableFixtures.get(i);
      if (fixture.selected.isOn()) {
        this.mutableFixtures.remove(i);
        removed.add(fixture);
      }
    }
    _reindexFixtures();
    for (LXFixture fixture : removed) {
      for (Listener l : this.listeners) {
        l.fixtureRemoved(fixture);
      }
      fixture.dispose();
    }
    regenerateModel();
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

  public LXStructure translateSelectedFixtures(float tx, float ty, float tz) {
    for (LXFixture fixture : this.fixtures) {
      if (fixture.selected.isOn()) {
        fixture.x.incrementValue(tx);
        fixture.y.incrementValue(ty);
        fixture.z.incrementValue(tz);
      }
    }
    return this;
  }

  public LXStructure rotateSelectedFixtures(float theta, float phi) {
    for (LXFixture fixture : this.fixtures) {
      if (fixture.selected.isOn()) {
        fixture.yaw.incrementValue(theta * 180 / Math.PI);
        fixture.pitch.incrementValue(phi * 180 / Math.PI);
      }
    }
    return this;
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

  public LXStructure reset() {
    return reset(false);
  }

  private LXStructure reset(boolean fromSync) {
    this.staticModel = null;
    removeAllFixtures();
    if (!fromSync) {
      this.syncModelFile.setValue(false);
      this.modelFile = null;
      this.modelName.setValue(PROJECT_MODEL);
    }
    this.isStatic.setValue(false);
    return this;
  }

  public LXStructure setDynamicModel() {
    this.staticModel = null;
    this.isStatic.setValue(false);
    regenerateModel();
    return this;
  }

  public LXStructure setStaticModel(LXModel model) {
    // Ensure that all the points in this model are properly indexed and normalized
    // to the top level...
    model.reindexPoints();
    model.normalizePoints();
    this.lx.setModel(this.model = this.staticModel = model);
    this.modelFile = null;
    this.modelName.setValue(model.getClass().getSimpleName() + ".class");
    this.isStatic.setValue(true);
    return this;
  }

  protected void regenerateModel() {
    if (this.isLoading) {
      this.needsRegenerate = true;
      return;
    }
    LXModel[] submodels = new LXModel[this.fixtures.size()];
    int pointIndex = 0;
    int fixtureIndex = 0;
    for (LXFixture fixture : this.fixtures) {
      LXModel fixtureModel = fixture.toModel(pointIndex);
      pointIndex += fixtureModel.size;
      submodels[fixtureIndex++] = fixtureModel;
    }
    this.lx.setModel(this.model = new LXModel(submodels).normalizePoints());
    if (this.modelFile != null) {
      this.modelName.setValue(this.modelFile.getName() + "*");
    }
  }

  protected void fixtureGeometryRegenerated(LXFixture fixture) {
    // No point indices shall have changed, only the fixture's geometry
    for (LXPoint p : fixture.points) {
      this.model.points[p.index].set(p);
    }
    // We need to re-normalize the points in the model, since some have changed
    this.model.update();
    if (this.modelFile != null) {
      this.modelName.setValue(this.modelFile.getName() + "*");
    }
  }

  private boolean isLoading = false;
  private boolean needsRegenerate = false;

  private static final String KEY_FIXTURES = "fixtures";
  private static final String KEY_STATIC_MODEL = "staticModel";
  private static final String KEY_FILE = "file";

  @Override
  public void load(LX lx, JsonObject obj) {
    if (this.isImmutable) {
      return;
    }
    this.isLoading = true;
    reset();
    super.load(lx, obj);

    LXModel staticModel = null;
    File loadModelFile = null;
    if (obj.has(KEY_STATIC_MODEL)) {
      JsonObject modelObj = obj.get(KEY_STATIC_MODEL).getAsJsonObject();
      String className = modelObj.get(LXComponent.KEY_CLASS).getAsString();
      try {
        staticModel = lx.instantiateModel(className);
        staticModel.load(lx, modelObj);
      } catch (LX.InstantiationException x) {
        lx.command.pushError("Could not instantiate model class " + className + ". Check that content files are present?", x);
      }
    }
    loadFixtures(lx, obj);
    if (obj.has(KEY_FILE)) {
      loadModelFile = this.lx.getMediaFile(LX.Media.MODELS, obj.get(KEY_FILE).getAsString());
    }

    this.isLoading = false;
    if (staticModel != null) {
      setStaticModel(staticModel);
      this.needsRegenerate = false;
    } else {
      this.isStatic.setValue(false);
      if ((loadModelFile != null) && this.syncModelFile.isOn()) {
        importModel(loadModelFile, true);
      } else {
        this.modelName.setValue(PROJECT_MODEL);
        if (this.needsRegenerate) {
          this.needsRegenerate = false;
          regenerateModel();
        }
      }
    }
  }

  private void loadFixtures(LX lx, JsonObject obj) {
    if (obj.has(KEY_FIXTURES)) {
      for (JsonElement fixtureElement : obj.getAsJsonArray(KEY_FIXTURES)) {
        JsonObject fixtureObj = fixtureElement.getAsJsonObject();
        try {
          LXFixture fixture = this.lx.instantiateFixture(fixtureObj.get(KEY_CLASS).getAsString());
          fixture.load(lx, fixtureObj);
          addFixture(fixture);
        } catch (LX.InstantiationException x) {
          LX.error(x, "Could not instantiate fixture " + fixtureObj.toString());
        }
      }
    }
  }

  @Override
  public void save(LX lx, JsonObject obj) {
    super.save(lx, obj);
    if (this.isImmutable) {
      return;
    }
    if (this.staticModel != null) {
      obj.add(KEY_STATIC_MODEL, LXSerializable.Utils.toObject(lx, this.staticModel));
    }
    saveFixtures(lx, obj);
    if (this.modelFile != null) {
      obj.addProperty(KEY_FILE, this.lx.getMediaPath(LX.Media.MODELS, this.modelFile));
      if (this.syncModelFile.isOn()) {
        exportModel(this.modelFile);
      }
    }
  }

  private void saveFixtures(LX lx, JsonObject obj) {
    obj.add(KEY_FIXTURES, LXSerializable.Utils.toArray(lx, this.fixtures));
  }

  public LXStructure importModel(File file) {
    return importModel(file, false);
  }

  private LXStructure importModel(File file, boolean fromSync) {
    try (FileReader fr = new FileReader(file)) {
      reset(fromSync);
      loadFixtures(this.lx, new Gson().fromJson(fr, JsonObject.class));
      this.modelFile = file;
      this.modelName.setValue(file.getName());
      this.isStatic.bang();
    } catch (FileNotFoundException e) {
      LX.error("Model file does not exist: " + file);
    } catch (IOException iox) {
      LX.error(iox, "Exception loading model file: " + file);
    }
    return this;
  }

  public LXStructure exportModel(File file) {
    JsonObject obj = new JsonObject();
    this.saveFixtures(this.lx, obj);
    try (JsonWriter writer = new JsonWriter(new FileWriter(file))) {
      writer.setIndent("  ");
      new GsonBuilder().create().toJson(obj, writer);
      this.modelFile = file;
      this.modelName.setValue(file.getName());
      this.isStatic.bang();
      LX.log("Model exported successfully to " + file);
    } catch (IOException iox) {
      LX.error(iox, "Exception writing model file to " + file);
    }
    return this;
  }
}
