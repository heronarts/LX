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
import heronarts.lx.model.LXNormalizationBounds;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.BoundedParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.parameter.LXListenableParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.StringParameter;
import heronarts.lx.structure.view.LXViewDefinition;
import heronarts.lx.structure.view.LXViewEngine;

public class LXStructure extends LXComponent implements LXFixtureContainer {

  private static final String PROJECT_MODEL = "<Embedded in Project>";

  /**
   * Implementation-only interface to relay model changes back to the core LX
   * instance. This is not a user-facing API.
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

  public enum NormalizationMode {
    AUTOMATIC("Auto"),
    MANUAL("Manual");

    public final String label;

    private NormalizationMode(String label) {
      this.label = label;
    }

    @Override
    public String toString() {
      return this.label;
    }
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

  public final StringParameter outputError =
    new StringParameter("Output Error", null);

  public final BooleanParameter allWhite =
    new BooleanParameter("White", false)
    .setDescription("Output full white to all pixels");

  public final BooleanParameter mute =
    new BooleanParameter("Mute", false)
    .setDescription("Send black to all pixels");

  public final EnumParameter<NormalizationMode> normalizationMode =
    new EnumParameter<NormalizationMode>("Normalization", NormalizationMode.AUTOMATIC)
    .setDescription("Whether the normalization space is defined by the model or manually");

  public final BoundedParameter normalizationX =
    new BoundedParameter("X", 0, -LXFixture.POSITION_RANGE, LXFixture.POSITION_RANGE)
    .setDescription("Manual X position of the normalization center");

  public final BoundedParameter normalizationY =
    new BoundedParameter("Y", 0, -LXFixture.POSITION_RANGE, LXFixture.POSITION_RANGE)
    .setDescription("Manual Y position of the normalization center");

  public final BoundedParameter normalizationZ =
    new BoundedParameter("Z", 0, -LXFixture.POSITION_RANGE, LXFixture.POSITION_RANGE)
    .setDescription("Manual Z position of the normalization center");

  public final BoundedParameter normalizationWidth =
    new BoundedParameter("Width", 1000, 0, LXFixture.POSITION_RANGE)
    .setDescription("With of the normalization space");

  public final BoundedParameter normalizationHeight =
    new BoundedParameter("Height", 1000, 0, LXFixture.POSITION_RANGE)
    .setDescription("Height of the normalization space");

  public final BoundedParameter normalizationDepth =
    new BoundedParameter("Depth", 1000, 0, LXFixture.POSITION_RANGE)
    .setDescription("Depth of the normalization space");

  public final BooleanParameter showNormalizationBounds =
    new BooleanParameter("Show Normalization Bounds", false)
    .setDescription("Outline the normalization bounds in the preview window");

  private final List<Listener> listeners = new ArrayList<Listener>();

  private final List<LXFixture> mutableFixtures = new ArrayList<LXFixture>();

  public final List<LXFixture> fixtures = Collections
    .unmodifiableList(this.mutableFixtures);

  private LXModel model;

  private LXModel staticModel = null;

  private boolean isDirty = false;

  // Whether a single immutable model is used, defined at construction time
  private final boolean isImmutable;

  public final LXStructureOutput output;

  public final LXViewEngine views;

  private final LXParameter.Collection normalizationParameters = new LXParameter.Collection();

  public LXStructure(LX lx, LXModel immutable, ModelListener modelListener) {
    super(lx);
    addParameter("syncModelFile", this.syncModelFile);
    addParameter("allWhite", this.allWhite);
    addParameter("mute", this.mute);
    addNormalizationParameter("normalizationMode", this.normalizationMode);
    addNormalizationParameter("normalizationX", this.normalizationX);
    addNormalizationParameter("normalizationY", this.normalizationY);
    addNormalizationParameter("normalizationZ", this.normalizationZ);
    addNormalizationParameter("normalizationWidth", this.normalizationWidth);
    addNormalizationParameter("normalizationHeight", this.normalizationHeight);
    addNormalizationParameter("normalizationDepth", this.normalizationDepth);
    addInternalParameter("showNormalizationBounds", this.showNormalizationBounds);

    addChild("views", this.views = new LXViewEngine(lx));

    if (immutable != null) {
      this.isImmutable = true;
      this.staticModel = this.model = immutable.normalizePoints();
      this.isStatic.setValue(true);
    } else {
      this.isImmutable = false;
      this.model = new LXModel();
    }

    LXStructureOutput output = null;
    try {
      output = new LXStructureOutput(lx, this);
    } catch (SocketException sx) {
      lx.pushError(sx,
        "Serious network error, could not create output socket. Program will continue with no network output.\n"
          + sx.getLocalizedMessage());
      LX.error(sx,
        "Failed to create datagram socket for structure datagram output, will continue with no network output: "
          + sx.getLocalizedMessage());
    }
    this.output = output;

    this.modelListener = modelListener;
  }

  private void addNormalizationParameter(String path, LXListenableParameter p) {
    this.normalizationParameters.put(path, p);
    addParameter(path, p);
    p.addListener(this::normalizationChanged);
  }

  private void normalizationChanged(LXParameter p) {
    if ((p == this.normalizationMode) || (this.normalizationMode.getEnum() == NormalizationMode.MANUAL)) {
      regenerateModel(false);
    }
  }

  private LXNormalizationBounds generateManualNormalizationBounds() {
    if (this.normalizationMode.getEnum() == NormalizationMode.AUTOMATIC) {
      return null;
    }

    final LXNormalizationBounds bounds = new LXNormalizationBounds();
    bounds.cx = this.normalizationX.getValuef();
    bounds.cy = this.normalizationY.getValuef();
    bounds.cz = this.normalizationZ.getValuef();
    bounds.xRange = this.normalizationWidth.getValuef();
    bounds.yRange = this.normalizationHeight.getValuef();
    bounds.zRange = this.normalizationDepth.getValuef();
    bounds.xMin = bounds.cx - .5f * bounds.xRange;
    bounds.xMax = bounds.cx + .5f * bounds.xRange;
    bounds.yMin = bounds.cy - .5f * bounds.yRange;
    bounds.yMax = bounds.cy + .5f * bounds.yRange;
    bounds.zMin = bounds.cz - .5f * bounds.zRange;
    bounds.zMax = bounds.cz + .5f * bounds.zRange;
    return bounds;
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
      throw new IllegalStateException(
        "Cannot add duplicate LXStructure.Listener: " + listener);
    }
    this.listeners.add(listener);
    return this;
  }

  public LXStructure removeListener(Listener listener) {
    if (!this.listeners.contains(listener)) {
      throw new IllegalStateException(
        "Cannot remove non-registered LXStructure.Listener: " + listener);
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
    checkStaticModel(false,
      "Cannot invoke addFixture when static model is in use");
    if (this.mutableFixtures.contains(fixture)) {
      throw new IllegalStateException(
        "LXStructure may not contain two copies of same fixture");
    }
    if (index > this.fixtures.size()) {
      throw new IllegalArgumentException(
        "Illegal LXStructure.addFixture() index: " + index + " > "
          + this.fixtures.size());
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
    checkStaticModel(false,
      "Cannot invoke setFixtureIndex when static model is in use");
    if (!this.mutableFixtures.contains(fixture)) {
      throw new IllegalStateException(
        "Cannot set index on fixture not in structure: " + fixture);
    }
    this.mutableFixtures.remove(fixture);
    this.mutableFixtures.add(index, fixture);
    _reindexFixtures();
    for (Listener l : this.listeners) {
      l.fixtureMoved(fixture, index);
    }

    // The point ordering is changed, rebuild the model and outputs
    regenerateModel(false);
    regenerateOutputs();

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

  public LXStructure selectFixture(LXFixture fixture,
    boolean isMultipleSelection) {
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
    checkStaticModel(false,
      "Cannot invoke removeFixtures when static model is in use");
    List<LXFixture> removed = new ArrayList<LXFixture>();
    for (LXFixture fixture : fixtures) {
      if (!this.mutableFixtures.remove(fixture)) {
        throw new IllegalStateException(
          "Cannot remove fixture not present in structure");
      }
      removed.add(fixture);
    }
    _reindexFixtures();
    for (LXFixture fixture : removed) {
      for (Listener l : this.listeners) {
        l.fixtureRemoved(fixture);
      }
      LX.dispose(fixture);
    }
    fixtureRemoved();
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
      LX.dispose(fixture);
    }
    fixtureRemoved();
    return this;
  }

  public LXStructure removeFixture(LXFixture fixture) {
    checkStaticModel(false, "Cannot invoke removeFixture when static model is in use");
    if (!this.mutableFixtures.contains(fixture)) {
      throw new IllegalStateException(
        "LXStructure does not contain fixture: " + fixture);
    }
    this.mutableFixtures.remove(fixture);
    _reindexFixtures();
    for (Listener l : this.listeners) {
      l.fixtureRemoved(fixture);
    }
    LX.dispose(fixture);
    fixtureRemoved();
    return this;
  }

  private void removeAllFixtures() {
    checkStaticModel(false, "Cannot invoke removeAllFixtures when static model is in use");

    // Do this loop ourselves, rather than calling removeFixture(), so we only
    // regenerate model once...
    for (int i = this.mutableFixtures.size() - 1; i >= 0; --i) {
      LXFixture fixture = this.mutableFixtures.remove(i);
      for (Listener l : this.listeners) {
        l.fixtureRemoved(fixture);
      }
      LX.dispose(fixture);
    }

    fixtureRemoved();
  }

  public LXStructure translateSelectedFixtures(float tx, float ty, float tz) {
    return translateSelectedFixtures(tx, ty, tz, null);
  }

  public LXStructure translateSelectedFixtures(float tx, float ty, float tz,
    LXCommand.Structure.ModifyFixturePositions action) {
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

  public LXStructure rotateSelectedFixtures(float theta, float phi,
    LXCommand.Structure.ModifyFixturePositions action) {
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
        fixture.brightness
          .setNormalized(fixture.brightness.getNormalized() + delta);
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
    regenerateModel(true);
    return this;
  }

  public LXStructure setStaticModel(LXModel model) {
    // Ensure that all the points in this model are properly indexed and
    // normalized
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
    if (this.output != null) {
      this.output.clear();
    }

    // NOTE(mcslee): good chance that there's memory to be reclaimed after
    // this operation
    System.gc();

    return this;
  }

  private void regenerateModel(boolean fromLoad) {
    if (this.isImmutable) {
      throw new IllegalStateException( "Cannot regenerate LXStructure model when in immutable mode");
    }
    if (this.staticModel != null) {
      throw new IllegalStateException("Cannot regenerate LXStructure model when static model is set: " + this.staticModel);
    }

    if (this.isLoading) {
      return;
    }

    // Count active fixtures
    int activeFixtures = 0;
    for (LXFixture fixture : this.fixtures) {
      if (!fixture.deactivate.isOn()) {
        ++activeFixtures;
      }
    }

    final LXModel[] submodels = new LXModel[activeFixtures];
    int pointIndex = 0;
    int fixtureIndex = 0;
    for (LXFixture fixture : this.fixtures) {
      if (!fixture.deactivate.isOn()) {
        fixture.reindex(pointIndex);
        LXModel fixtureModel = fixture.toModel();
        pointIndex += fixtureModel.size;
        submodels[fixtureIndex++] = fixtureModel;
      }
    }

    this.model = new LXModel(submodels, generateManualNormalizationBounds()).normalizePoints();
    this.modelListener.structureChanged(this.model);

    if (!fromLoad) {
      setDirty();
    }
  }

  private void regenerateOutputs() {
    if (this.isLoading) {
      return;
    }
    if (this.output != null) {
      this.output.rebuildOutputs();
    }
  }

  private void fixtureRemoved() {
    // When a fixture is removed we need to rebuild just as if any generational
    // change occurred
    fixtureGenerationChanged(null);
  }

  @Override
  public void fixtureGenerationChanged(LXFixture fixture) {
    regenerateModel(false);
    regenerateOutputs();
  }

  @Override
  public void fixtureGeometryChanged(LXFixture fixture) {
    // We need to re-normalize our model, things have changed
    this.model.update(true, true);
    this.modelListener.structureGenerationChanged(this.model);
    setDirty();
  }

  @Override
  public void fixtureOutputChanged(LXFixture fixture) {
    regenerateOutputs();
    setDirty();
  }

  @Override
  public void fixtureTagsChanged(LXFixture fixture) {
    regenerateModel(false);
  }

  private void setDirty() {
    if (this.modelFile != null) {
      this.modelName.setValue(this.modelFile.getName() + "*");
    }
    this.isDirty = true;
  }

  public boolean isExternalModel() {
    return
      !this.isImmutable &&
      (this.staticModel == null) &&
      (this.modelFile != null);
  }

  public boolean isDirty() {
    return this.isDirty;
  }

  private boolean isLoading = false;

  private static final String KEY_FIXTURES = "fixtures";
  private static final String KEY_NORMALIZATION = "normalization";
  private static final String KEY_STATIC_MODEL = "staticModel";
  private static final String KEY_FILE = "file";

  private static final String KEY_OUTPUT = "output";

  public void reload() {
    if (this.isImmutable) {
      return;
    }

    this.isLoading = true;
    for (LXFixture fixture : this.fixtures) {
      if (fixture instanceof JsonFixture) {
        ((JsonFixture) fixture).reload();
      }
    }
    this.isLoading = false;
    if (this.staticModel == null) {
      regenerateModel(true);
    }
    // Regenerate any dynamic outputs
    regenerateOutputs();

    this.lx.pushStatusMessage("Model reloaded");
  }

  @Override
  public void load(LX lx, JsonObject obj) {
    if (this.isImmutable) {
      // When the model is immutable, clear/load the views
      this.views.reset();
      LXSerializable.Utils.loadObject(this.lx, this.views, obj, this.views.getPath());
      return;
    }

    this.isLoading = true;

    // Reset everything to complete scratch!
    this.views.reset();
    reset(false);

    // Load parameter values, this is also where views will get loaded
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
          this.syncModelFile.setValue(false);
        } else if (!loadModelFile.exists()) {
          LX.error("Referenced external model file does not exist: " + loadModelFile.toURI());
          this.syncModelFile.setValue(false);
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
      regenerateModel(true);
    }

    // Regenerate any dynamic outputs
    regenerateOutputs();

    if (this.output != null) {
      if (obj.has(KEY_OUTPUT)) {
        LXSerializable.Utils.loadObject(lx, this.output, obj, KEY_OUTPUT);
      } else {
        LXSerializable.Utils.resetObject(lx, this.output);
      }
    }

    // Done loading!
    this.isDirty = false;
  }

  private void loadFixtures(LX lx, JsonObject obj) {
    if (obj.has(KEY_FIXTURES)) {
      for (JsonElement fixtureElement : obj.getAsJsonArray(KEY_FIXTURES)) {
        JsonObject fixtureObj = fixtureElement.getAsJsonObject();
        try {
          LXFixture fixture = this.lx
            .instantiateFixture(fixtureObj.get(KEY_CLASS).getAsString());
          fixture.load(lx, fixtureObj);
          addFixture(fixture);
        } catch (LX.InstantiationException x) {
          LX.error(x, "Could not instantiate fixture " + fixtureObj.toString());
        }
      }
      regenerateOutputs();
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
    boolean wasLoading = this.isLoading;
    this.isLoading = true;
    this.lx.setModelImportFlag(true);
    try (FileReader fr = new FileReader(file)) {
      reset(fromSync);
      final JsonObject obj = new Gson().fromJson(fr, JsonObject.class);
      loadFixtures(this.lx, obj);
      if (obj.has(KEY_NORMALIZATION)) {
        loadParameters(this, obj.get(KEY_NORMALIZATION).getAsJsonObject(), this.normalizationParameters);
      }
      this.modelFile = file;
      this.modelName.setValue(file.getName());
      this.isStatic.bang();
    } catch (FileNotFoundException fnfx) {
      LX.error("Model file does not exist: " + file);
      this.lx.pushError(fnfx, "Model file does not exist:" + file);
    } catch (IOException iox) {
      LX.error(iox, "IO error importing model file: " + file);
      this.lx.pushError(iox, "IO error importing model file " + file + ": " + iox.getMessage());
    } catch (Throwable x) {
      LX.error(x, "Exception importing model file: " + file);
      this.lx.pushError(x, "Error importing model file " + file + ": " + x.getMessage());
    }
    this.lx.setModelImportFlag(false);
    this.isLoading = wasLoading;
    if (!wasLoading) {
      regenerateModel(true);
      regenerateOutputs();
    }
    this.isDirty = false;
    return this;
  }

  public LXStructure exportModel(File file) {
    if (!this.lx.permissions.canSave()) {
      return this;
    }

    JsonObject obj = new JsonObject();
    obj.addProperty(LX.KEY_VERSION, LX.VERSION);
    obj.addProperty(LX.KEY_TIMESTAMP, System.currentTimeMillis());
    saveFixtures(this.lx, obj);
    obj.add(KEY_NORMALIZATION, LXSerializable.Utils.saveParameters(this.normalizationParameters));

    try (JsonWriter writer = new JsonWriter(new FileWriter(file))) {
      writer.setIndent("  ");
      new GsonBuilder().create().toJson(obj, writer);
      this.modelFile = file;
      this.modelName.setValue(file.getName());
      this.isDirty = false;
      this.isStatic.bang();
      LX.log("Model exported successfully to " + file);
    } catch (IOException iox) {
      LX.error(iox, "Exception writing model file to " + file);
    }

    return this;
  }

  public void exportViews(File file) {
    final JsonObject obj = LXSerializable.Utils.toObject(this.lx, this.views, true);
    try (JsonWriter writer = new JsonWriter(new FileWriter(file))) {
      writer.setIndent("  ");
      new GsonBuilder().create().toJson(obj, writer);
      LX.log("Views saved successfully to " + file.toString());
    } catch (IOException iox) {
      LX.error(iox, "Could not export views to file: " + file.toString());
    }
  }

  public List<LXViewDefinition> importViews(File file) {
    try (FileReader fr = new FileReader(file)) {
      return this.views.addViews(this.lx, new Gson().fromJson(fr, JsonObject.class));
    } catch (IOException iox) {
      LX.error(iox, "Could not import views from file: " + file.toString());
    }
    return null;
  }
}
