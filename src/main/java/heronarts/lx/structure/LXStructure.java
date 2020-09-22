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
import heronarts.lx.command.LXCommand;
import heronarts.lx.model.LXModel;
import heronarts.lx.output.LXOutput;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.StringParameter;

public class LXStructure extends LXComponent implements LXFixtureContainer {

  private static final String PROJECT_MODEL = "<Embedded in Project>";

  public class Output extends LXOutput {

    public Output(LX lx) throws SocketException {
      super(lx);
      this.gammaMode.setValue(GammaMode.DIRECT);
    }

    @Override
    protected void onSend(int[] colors, double brightness) {
      for (LXFixture fixture : fixtures) {
        onSendFixture(fixture, colors, brightness);
      }
    }

    @Override
    protected void onSend(int[] colors, byte[] glut) {
      throw new UnsupportedOperationException("LXStructure.Output does not use onSend(int[] colors, byte[] glut)");
    }

    private void onSendFixture(LXFixture fixture, int[] colors, double brightness) {
      // Check enabled state of fixture
      if (fixture.enabled.isOn()) {
        // Adjust by fixture brightness
        brightness *= fixture.brightness.getValue();

        // Recursively send all the fixture's children
        for (LXFixture child : fixture.children) {
          onSendFixture(child, colors, brightness);
        }

        // Then send the fixture's own direct packets
        for (LXOutput output : fixture.outputs) {
          output.setGammaDelegate(this);
          output.send(colors, brightness);
        }
      }
    }

  }

  /**
   * Implementation-only interface to relay model changes back to the core LX instance. This
   * is not a user-facing API.
   */
  public interface ModelListener {
    public void structureChanged(LXModel model);
    public void structureGenerationChanged(LXModel model);
  }

  // Internal implementation only
  private ModelListener modelListener;

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
      lx.pushError(sx, "Serious network error, could not create output socket. Program will continue with no network output.\n" + sx.getLocalizedMessage());
      LX.error(sx, "Failed to create datagram socket for structure datagram output, will continue with no network output: " + sx.getLocalizedMessage());
    }
    this.output = output;
  }

  /**
   * Internal implementation-only helper to set a listener for notification on changes to the structure's model.
   * This is used by the LX class to relay model-changes from the structure back to the top-level LX object while
   * keeping that functionality private on the core LX API.
   *
   * @param listener Listener
   */
  public void setModelListener(ModelListener listener) {
    Objects.requireNonNull("LXStructure.setModelListener() cannot be null");
    if (this.modelListener != null) {
      throw new IllegalStateException("Cannot overwrite setModelListener() - should only called once by LX parent object");
    }
    this.modelListener = listener;
  }

  @Override
  public String getPath() {
    return "structure";
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
      throw new IllegalArgumentException("Illegal LXStructure.addFixture() index: " + index + " > " + this.fixtures.size());
    }
    if (index < 0) {
      index = this.fixtures.size();
    }
    this.mutableFixtures.add(index, fixture);
    _reindexFixtures();

    // De-select all other fixtures, select this one
    selectFixture(fixture);

    // This will trigger regeneration of the fixture and models
    fixture.setStructure(this);

    // Notify listeners of the new fixture
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

    // The point ordering is changed, rebuild the model
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
    return translateSelectedFixtures(tx, ty, tz, null);
  }

  public LXStructure translateSelectedFixtures(float tx, float ty, float tz, LXCommand.Structure.ModifyFixturePositions action) {
    for (LXFixture fixture : this.fixtures) {
      if (fixture.selected.isOn()) {
        if (tx != 0) {
          if (action != null) {
            action.update(this.lx, fixture.x, tx);
          } else {
            fixture.x.incrementValue(tx);
          }
        }
        if (ty != 0) {
          if (action != null) {
            action.update(this.lx, fixture.y, ty);
          } else {
            fixture.y.incrementValue(ty);
          }
        }
        if (tz != 0) {
          if (action != null) {
            action.update(this.lx, fixture.z, tz);
          } else {
            fixture.z.incrementValue(tz);
          }
        }
      }
    }
    return this;
  }

  public LXStructure rotateSelectedFixtures(float theta, float phi) {
    return rotateSelectedFixtures(theta, phi, null);
  }

  public LXStructure rotateSelectedFixtures(float theta, float phi, LXCommand.Structure.ModifyFixturePositions action) {
    for (LXFixture fixture : this.fixtures) {
      if (fixture.selected.isOn()) {
        if (theta != 0) {
          if (action != null) {
            action.update(this.lx, fixture.yaw, theta * 180 / Math.PI);
          } else {
            fixture.yaw.incrementValue(theta * 180 / Math.PI);
          }
        }
        if (phi != 0) {
          if (action != null) {
            action.update(this.lx, fixture.pitch, phi * 180 / Math.PI);
          } else {
            fixture.pitch.incrementValue(phi * 180 / Math.PI);
          }
        }
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

  public LXStructure newDynamicModel() {
    this.isLoading = true;
    reset(false);
    this.isLoading = false;
    regenerateModel();
    return this;
  }

  public LXStructure setStaticModel(LXModel model) {
    // Ensure that all the points in this model are properly indexed and normalized
    // to the top level...
    model.reindexPoints();
    model.normalizePoints();
    this.model = this.staticModel = model;
    this.modelFile = null;
    this.modelName.setValue(model.getClass().getSimpleName() + ".class");
    this.isStatic.setValue(true);
    this.modelListener.structureChanged(this.model);
    return this;
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

  private void regenerateModel() {
    if (this.isImmutable) {
      throw new IllegalStateException("Cannot regenerate LXStructure model when in immutable mode");
    }
    if (this.staticModel != null) {
      throw new IllegalStateException("Cannot regenerate LXStructure model when static model is set: " + this.staticModel);
    }

    if (this.isLoading) {
      return;
    }

    LXModel[] submodels = new LXModel[this.fixtures.size()];
    int pointIndex = 0;
    int fixtureIndex = 0;
    for (LXFixture fixture : this.fixtures) {
      fixture.reindex(pointIndex);
      LXModel fixtureModel = fixture.toModel();
      pointIndex += fixtureModel.size;
      submodels[fixtureIndex++] = fixtureModel;
    }
    this.model = new LXModel(submodels).normalizePoints();
    this.modelListener.structureChanged(this.model);

    if (this.modelFile != null) {
      this.modelName.setValue(this.modelFile.getName() + "*");
    }
  }

  public void fixtureGenerationChanged(LXFixture fixture) {
    regenerateModel();
  }

  public void fixtureGeometryChanged(LXFixture fixture) {
    // We need to re-normalize our model, things have changed
    this.model.update(true, true);
    this.modelListener.structureGenerationChanged(this.model);

    // Denote that file is modified
    if (this.modelFile != null) {
      this.modelName.setValue(this.modelFile.getName() + "*");
    }
  }

  private boolean isLoading = false;

  private static final String KEY_FIXTURES = "fixtures";
  private static final String KEY_STATIC_MODEL = "staticModel";
  private static final String KEY_FILE = "file";

  private static final String KEY_OUTPUT = "output";

  @Override
  public void load(LX lx, JsonObject obj) {
    if (this.isImmutable) {
      return;
    }

    this.isLoading = true;

    // Reset everything to complete scratch!
    reset(false);

    // Load parameter values
    super.load(lx, obj);

    // Are we in static model mode? Load that.
    if (obj.has(KEY_STATIC_MODEL)) {

      JsonObject modelObj = obj.get(KEY_STATIC_MODEL).getAsJsonObject();
      String className = modelObj.get(LXComponent.KEY_CLASS).getAsString();
      LXModel model = null;
      try {
        model = lx.instantiateModel(className);
        model.load(lx, modelObj);
      } catch (LX.InstantiationException x) {
        lx.pushError(x, "Could not instantiate model class " + className + ". Check that content files are present?");
      }
      // There was an error... just use an empty static model
      if (model == null) {
        model = new LXModel();
      }
      setStaticModel(model);

    } else {

      // We're using a fixture-driven model
      File loadModelFile = null;
      if (obj.has(KEY_FILE)) {
        loadModelFile = this.lx.getMediaFile(LX.Media.MODELS, obj.get(KEY_FILE).getAsString(), false);
      }
      if (this.syncModelFile.isOn()) {
        if (loadModelFile == null) {
          LX.error("Project specifies external model sync, but no file name was found");
        } else if (!loadModelFile.exists()) {
          LX.error("Referenced external model file does not exist: " + loadModelFile.toURI());
        } else {
          importModel(loadModelFile, true);
        }
      } else {
        this.modelName.setValue(PROJECT_MODEL);
        loadFixtures(lx, obj);
      }
    }

    // We're done loading
    this.isLoading = false;

    // Unless a static model was set, we need to regenerate
    if (this.staticModel == null) {
      regenerateModel();
    }

    if (this.output != null) {
      LXSerializable.Utils.loadObject(lx, this.output, obj, KEY_OUTPUT);
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
    if (this.output != null) {
      obj.add(KEY_OUTPUT, LXSerializable.Utils.toObject(lx, this.output));
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
    this.lx.setModelImportFlag(true);
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
    this.lx.setModelImportFlag(false);
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
