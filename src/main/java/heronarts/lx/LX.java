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

package heronarts.lx;

import heronarts.lx.blend.AddBlend;
import heronarts.lx.blend.DarkestBlend;
import heronarts.lx.blend.DifferenceBlend;
import heronarts.lx.blend.DissolveBlend;
import heronarts.lx.blend.LXBlend;
import heronarts.lx.blend.LightestBlend;
import heronarts.lx.blend.MultiplyBlend;
import heronarts.lx.blend.NormalBlend;
import heronarts.lx.blend.SubtractBlend;
import heronarts.lx.clipboard.LXClipboard;
import heronarts.lx.color.LXColor;
import heronarts.lx.command.LXCommandEngine;
import heronarts.lx.effect.LXEffect;
import heronarts.lx.mixer.LXChannel;
import heronarts.lx.mixer.LXAbstractChannel;
import heronarts.lx.model.GridModel;
import heronarts.lx.model.LXModel;
import heronarts.lx.modulator.LXModulator;
import heronarts.lx.output.LXOutput;
import heronarts.lx.pattern.IteratorPattern;
import heronarts.lx.pattern.LXPattern;
import heronarts.lx.structure.LXFixture;
import heronarts.lx.structure.LXStructure;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonWriter;

/**
 * Core controller for a LX instance. Each instance drives a 3-d collection of
 * nodes defined by a dynamic model.
 */
public class LX {

  public static final String VERSION = "0.2.0-SNAPSHOT";

  public static class InstantiationException extends Exception {

    private static final long serialVersionUID = 1L;

    protected InstantiationException(Exception underlying) {
      super(underlying);
    }

  }

  public static class Flags {
    /**
     * Sometimes we need to know if we are P3LX, but we don't want LX library to have
     * any dependency upon P3LX.
     */
    public boolean isP3LX = false;
    public boolean immutableModel = false;
    public boolean focusChannelOnCue = false;
    public boolean focusActivePattern = false;
    public boolean sendCueToOutput = false;
    public String mediaPath = ".";
  }

  public static enum Media {
    CONTENT("Content"),
    FIXTURES("Fixtures"),
    PROJECTS("Projects"),
    MODELS("Models");

    private final String dirName;

    private Media(String dirName) {
      this.dirName = dirName;
    }

    public String getDirName() {
      return this.dirName;
    }
  }

  /**
   * Returns the version of the library.
   *
   * @return String
   */
  public static String version() {
    return VERSION;
  }

  public static final double HALF_PI = Math.PI / 2.;
  public static final double TWO_PI = Math.PI * 2.;

  public static class InitTimer {
    private long lastTime;

    protected void init() {
      this.lastTime = System.nanoTime();
    }

    public void log(String label) {
      long thisTime = System.nanoTime();
      if (LX.LOG_INIT_TIMING) {
        LX.log(String.format("init: %s: %.2fms", label, (thisTime - lastTime) / 1000000.));
      }
      this.lastTime = thisTime;
    }
  }

  public static final InitTimer initTimer = new InitTimer();

  private static boolean LOG_INIT_TIMING = false;

  public static void logInitTiming() {
    LX.LOG_INIT_TIMING = true;
  }

  /**
   * Listener for top-level events
   */
  public interface Listener {
    default public void modelChanged(LX lx, LXModel model) {}
    default public void contentChanged(LX lx) {}
    default public void pluginChanged(LX lx, LXClassLoader.Plugin plugin) {}
  }

  private final List<Listener> listeners = new ArrayList<Listener>();

  public interface ProjectListener {

    enum Change {
      TRY,
      NEW,
      SAVE,
      OPEN
    };

    public void projectChanged(File file, Change change);
  }

  private final List<ProjectListener> projectListeners = new ArrayList<ProjectListener>();

  final LXComponent.Registry componentRegistry = new LXComponent.Registry();

  /**
   * Global preferences stored in persistent file
   */
  public final LXPreferences preferences;

  /**
   * Configuration flags
   */
  public final Flags flags;

  /**
   * The width of the grid, immutable.
   */
  public final int width;

  /**
   * The height of the grid, immutable.
   */
  public final int height;

  /**
   * The midpoint of the x-space.
   */
  @Deprecated
  public final float cx;

  /**
   * This midpoint of the y-space.
   */
  @Deprecated
  public final float cy;

  /**
   * The lighting system structure
   */
  public final LXStructure structure;

  /**
   * The pixel model.
   */
  protected LXModel model;

  /**
   * The total number of pixels in the grid, immutable.
   */
  public final int total;

  /**
   * Clipboard for copy/paste
   */
  public final LXClipboard clipboard = new LXClipboard(this);

  /**
   * The animation engine.
   */
  public final LXEngine engine;

  /**
   * Command engine, utilized by higher-level UIs to manage
   * engine state and undo operations.
   */
  public final LXCommandEngine command;

  /**
   * The list of globally registered pattern classes
   */
  private final List<Class<? extends LXPattern>> registeredPatterns =
    new ArrayList<Class<? extends LXPattern>>();

  /**
   * The list of globally registered effects
   */
  private final List<Class<? extends LXEffect>> registeredEffects =
    new ArrayList<Class<? extends LXEffect>>();

  /**
   * The list of globally registered fixture types
   */
  private final List<String> registeredFixtures =
    new ArrayList<String>();

  /**
   * The list of globally registered channel blend classes
   */
  public final List<Class<? extends LXBlend>> registeredChannelBlends =
    new ArrayList<Class<? extends LXBlend>>();

  /**
   * The list of globally registered transition blend classes
   */
  public final List<Class<? extends LXBlend>> registeredTransitionBlends =
    new ArrayList<Class<? extends LXBlend>>();

  /**
   * The list of globally registered crossfader blend classes
   */
  public final List<Class<? extends LXBlend>> registeredCrossfaderBlends =
    new ArrayList<Class<? extends LXBlend>>();

  protected LXClassLoader contentLoader;
  private boolean contentReloading = false;

  /**
   * Creates an LX instance with no nodes.
   */
  public LX() {
    this(new Flags());
  }

  /**
   * Constructs an LX instance with the given pixel model
   *
   * @param model Pixel model
   */
  public LX(LXModel model) {
    this(new Flags(), model);
  }

  public LX(Flags flags) {
    this(flags, null);
  }

  protected LX(Flags flags, LXModel model) {
    LX.initTimer.init();
    this.flags = flags;
    this.flags.immutableModel = (model != null);

    // Create structure object
    this.structure = new LXStructure(this, model);
    if (model == null) {
      this.total = this.width = this.height = 0;
      this.model = this.structure.getModel();
      this.cx = this.cy = 0;
    } else {
      this.model = model;
      this.total = model.points.length;
      this.cx = model.cx;
      this.cy = model.cy;
      if (model instanceof GridModel) {
        GridModel grid = (GridModel) model;
        this.width = grid.width;
        this.height = grid.height;
      } else {
        this.width = this.height = 0;
      }
    }
    LX.initTimer.log("Model");

    // Default blends
    registerDefaultBlends();

    // Construct the engine
    this.engine = new LXEngine(this);
    this.command = new LXCommandEngine(this);
    LX.initTimer.log("Engine");

    // Custom content loader
    this.contentLoader = LXClassLoader.createNew(this);
    this.structure.registerFixtures(getMediaFolder(LX.Media.FIXTURES, false));
    LX.initTimer.log("Custom Content");

    // Midi
    this.engine.midi.initialize();

    // Add a default channel
    this.engine.mixer.addChannel(new LXPattern[] { new IteratorPattern(this) }).fader.setValue(1);
    LX.initTimer.log("Default Channel");

    // Load the global preferences before plugin initialization
    this.preferences = new LXPreferences(this);
    this.preferences.load();

    // Initialize plugins!
    this.contentLoader.initializePlugins();
  }

  private void registerDefaultBlends() {
    this.registeredChannelBlends.add(AddBlend.class);
    this.registeredChannelBlends.add(MultiplyBlend.class);
    this.registeredChannelBlends.add(SubtractBlend.class);
    this.registeredChannelBlends.add(DifferenceBlend.class);
    this.registeredChannelBlends.add(NormalBlend.class);

    this.registeredTransitionBlends.add(DissolveBlend.class);
    this.registeredTransitionBlends.add(AddBlend.class);
    this.registeredTransitionBlends.add(MultiplyBlend.class);
    this.registeredTransitionBlends.add(LightestBlend.class);
    this.registeredTransitionBlends.add(DarkestBlend.class);
    this.registeredTransitionBlends.add(DifferenceBlend.class);

    this.registeredCrossfaderBlends.addAll(this.registeredTransitionBlends);
  }

  public LX addListener(Listener listener) {
    this.listeners.add(listener);
    return this;
  }

  public LX removeListener(Listener listener) {
    this.listeners.add(listener);
    return this;
  }

  public LX addProjectListener(ProjectListener listener) {
    this.projectListeners.add(listener);
    return this;
  }

  public LX removeProjectListener(ProjectListener listener) {
    this.projectListeners.remove(listener);
    return this;
  }

  /**
   * Gets a component by its raw component id
   *
   * @param componentId Component ID
   * @return Component with that ID, or null if none exists
   */
  public LXComponent getComponent(int componentId) {
    return this.componentRegistry.getComponent(componentId);
  }

  /**
   * Gets a component by its id from the project file (which may have been remapped)
   *
   * @param projectId Component ID from loaded project file
   * @return Component with that ID in the project file, may have a different component ID now
   */
  public LXComponent getProjectComponent(int projectId) {
    return this.componentRegistry.getProjectComponent(projectId);
  }

  /**
   * Returns the model in use
   *
   * @return model
   */
  public LXModel getModel() {
    return this.model;
  }

  /**
   * Updates the geometric model that is being rendered to
   *
   * @param model Model to be set
   * @return this
   */
  public LX setModel(LXModel model) {
    if (this.model == model) {
      throw new IllegalStateException("Cannot reset same model instance: " + model);
    }
    if (this.model != null) {
      this.model.dispose();
    }
    this.model = model;
    for (Listener listener : this.listeners) {
      listener.modelChanged(this, model);
    }
    return this;
  }

  protected void pluginChanged(LXClassLoader.Plugin plugin) {
    for (Listener listener : this.listeners) {
      listener.pluginChanged(this, plugin);
    }
  }

  /**
   * Shut down resources of the LX instance.
   */
  public void dispose() {
    this.engine.audio.dispose();
    this.engine.midi.dispose();
    this.engine.osc.dispose();
  }

  /**
   * Utility function to return the row of a given index
   *
   * @param i Index into colors array
   * @return Which row this index is in
   */
  @Deprecated
  public int row(int i) {
    return (this.width == 0) ? 0 : (i / this.width);
  }

  /**
   * Utility function to return the column of a given index
   *
   * @param i Index into colors array
   * @return Which column this index is in
   */
  @Deprecated
  public int column(int i) {
    return (this.width == 0) ? 0 : (i % this.width);
  }

  /**
   * Utility function to get the x-coordinate of a pixel
   *
   * @param i Node index
   * @return x coordinate
   */
  @Deprecated
  public int x(int i) {
    return (this.width == 0) ? 0 : (i % this.width);
  }

  /**
   * Utility function to return the position of an index in x coordinate space
   * normalized from 0 to 1.
   *
   * @param i Node index
   * @return Position of this node in x space, from 0 to 1
   */
  @Deprecated
  public double xn(int i) {
    return (this.width == 0) ? 0 : ((i % this.width) / (double) (this.width - 1));
  }

  /**
   * Utility function to return the position of an index in x coordinate space
   * normalized from 0 to 1, as a floating point.
   *
   * @param i Node index
   * @return Position of this node in x space, from 0 to 1
   */
  @Deprecated
  public float xnf(int i) {
    return (float) this.xn(i);
  }

  /**
   * Utility function to get the y-coordinate of a pixel
   *
   * @param i Node index
   * @return y coordinate
   */
  @Deprecated
  public int y(int i) {
    return (this.width == 0) ? 0 : (i / this.width);
  }

  /**
   * Utility function to return the position of an index in y coordinate space
   * normalized from 0 to 1.
   *
   * @param i Node index
   * @return Position of this node in y space, from 0 to 1
   */
  @Deprecated
  public double yn(int i) {
    return (this.width == 0) ? 0 : ((i / this.width) / (double) (this.height - 1));
  }

  /**
   * Utility function to return the position of an index in y coordinate space
   * normalized from 0 to 1, as a floating point.
   *
   * @param i Node index
   * @return Position of this node in y space, from 0 to 1
   */
  @Deprecated
  public float ynf(int i) {
    return (float) this.yn(i);
  }

  /**
   * Shorthand for LXColor.hsb()
   *
   * @param h Hue 0-360
   * @param s Saturation 0-100
   * @param b Brightness 0-100
   * @return Color
   */
  public static int hsb(float h, float s, float b) {
    return LXColor.hsb(h, s, b);
  }

  /**
   * Shorthand for LXColor.hsa()
   *
   * @param h Hue 0-360
   * @param s Saturation 0-100
   * @param a Alpha 0-1
   * @return Color
   */
  public static int hsa(float h, float s, float a) {
    return LXColor.hsba(h, s, 100, a);
  }

  /**
   * Shorthand for LXColor.rgb()
   *
   * @param r Red 0-255
   * @param g Green 0-255
   * @param b Blue 0-255
   * @return color
   */
  public static int rgb(int r, int g, int b) {
    return LXColor.rgb(r, g, b);
  }

  /**
   * Sets the speed of the entire system. Default is 1.0, any modification will
   * mutate deltaMs values system-wide.
   *
   * @param speed Coefficient, 1 is normal speed
   * @return this
   */
  public LX setSpeed(double speed) {
    this.engine.setSpeed(speed);
    return this;
  }

  /**
   * Add multiple effects to the chain
   *
   * @param effects Array of effects
   * @return this
   */
  public LX addEffects(LXEffect[] effects) {
    for (LXEffect effect : effects) {
      addEffect(effect);
    }
    return this;
  }

  /**
   * Add an effect to the FX chain.
   *
   * @param effect Effect
   * @return this
   */
  public LX addEffect(LXEffect effect) {
    this.engine.mixer.masterBus.addEffect(effect);
    return this;
  }

  /**
   * Remove an effect from the chain
   *
   * @param effect Effect
   * @return this
   */
  public LX removeEffect(LXEffect effect) {
    this.engine.mixer.masterBus.removeEffect(effect);
    return this;
  }

  /**
   * Pause the engine from running
   *
   * @param paused Whether to pause the engine to pause
   * @return this
   */
  public LX setPaused(boolean paused) {
    this.engine.setPaused(paused);
    return this;
  }

  /**
   * Whether the engine is currently running.
   *
   * @return State of the engine
   */
  public boolean isPaused() {
    return this.engine.isPaused();
  }

  /**
   * Toggles the running state of the engine.
   *
   * @return this
   */
  public LX togglePaused() {
    return setPaused(!this.engine.isPaused());
  }

  private LXChannel getChannel() {
    for (LXAbstractChannel channel : this.engine.mixer.channels) {
      if (channel instanceof LXChannel) {
        return (LXChannel) channel;
      }
    }
    return null;
  }

  /**
   * Sets the main channel to the previous pattern.
   *
   * @return this
   */
  public LX goPrev() {
    LXChannel channel = getChannel();
    if (channel != null) {
      channel.goPrev();
    }
    return this;
  }

  /**
   * Sets the main channel to the next pattern.
   *
   * @return this
   */
  public LX goNext() {
    LXChannel channel = getChannel();
    if (channel != null) {
      channel.goNext();
    }
    return this;
  }

  /**
   * Sets the main channel to a given pattern instance.
   *
   * @param pattern The pattern instance to run
   * @return this
   */
  public LX goPattern(LXPattern pattern) {
    LXChannel channel = getChannel();
    if (channel != null) {
      channel.goPattern(pattern);
    }
    return this;
  }

  /**
   * Sets the main channel to a pattern of the given index
   *
   * @param i Index of the pattern to run
   * @return this
   */
  public LX goIndex(int i) {
    LXChannel channel = getChannel();
    if (channel != null) {
      channel.goIndex(i);
    }
    return this;
  }

  /**
   * Stops patterns from automatically rotating
   *
   * @return this
   */
  public LX disableAutoCycle() {
    LXChannel channel = getChannel();
    if (channel != null) {
      channel.disableAutoCycle();
    }
    return this;
  }

  /**
   * Sets the patterns to rotate automatically
   *
   * @param autoCycleThreshold Number of milliseconds after which to cycle
   * @return this
   */
  public LX enableAutoCycle(int autoCycleThreshold) {
    LXChannel channel = getChannel();
    if (channel != null) {
      channel.enableAutoCycle(autoCycleThreshold);
    }
    return this;
  }

  /**
   * Adds an output driver
   *
   * @param output Output
   * @return this
   */
  public LX addOutput(LXOutput output) {
    this.engine.addOutput(output);
    return this;
  }

  /**
   * Specifies the set of patterns to be run.
   *
   * @param patterns Array of patterns
   * @return this
   */
  public LX setPatterns(LXPattern[] patterns) {
    LXChannel channel = getChannel();
    if (channel != null) {
      channel.setPatterns(patterns);
    }
    return this;
  }

  /**
   * Gets the current set of patterns on the main channel.
   *
   * @return The list of patters
   */
  public List<LXPattern> getPatterns() {
    LXChannel channel = getChannel();
    if (channel != null) {
      return channel.getPatterns();
    }
    return null;
  }

  public void checkRegistration() {
    if (!this.contentReloading && this.engine.hasStarted) {
      throw new IllegalStateException("May not register components outside of initialize() callback");
    }
  }

  /**
   * Register a pattern class with the engine
   *
   * @param pattern Pattern class
   * @return this
   */
  public LX registerPattern(Class<? extends LXPattern> pattern) {
    checkRegistration();
    this.registeredPatterns.add(pattern);
    return this;
  }

  /**
   * Register a pattern class with the engine
   *
   * @param patterns List of pattern classes
   * @return this
   */
  public LX registerPatterns(Class<LXPattern>[] patterns) {
    checkRegistration();
    for (Class<LXPattern> pattern : patterns) {
      registerPattern(pattern);
    }
    return this;
  }

  /**
   * Unregister pattern classes with the engine
   *
   * @param patterns Pattern classes
   * @return this
   */
  public LX unregisterPatterns(List<Class<? extends LXPattern>> patterns) {
    for (Class<? extends LXPattern> pattern : patterns) {
      this.registeredPatterns.remove(pattern);
    }
    return this;
  }

  /**
   * Gets the list of registered pattern classes
   *
   * @return Pattern classes
   */
  public List<Class<? extends LXPattern>> getRegisteredPatterns() {
    return this.registeredPatterns;
  }

  /**
   * Register an effect class with the engine
   *
   * @param effect Effect class
   * @return this
   */
  public LX registerEffect(Class<? extends LXEffect> effect) {
    checkRegistration();
    this.registeredEffects.add(effect);
    return this;
  }

  /**
   * Register an effect class with the engine
   *
   * @param effects List of effect classes
   * @return this
   */
  public LX registerEffects(Class<? extends LXEffect>[] effects) {
    checkRegistration();
    for (Class<? extends LXEffect> effect : effects) {
      registerEffect(effect);
    }
    return this;
  }

  /**
   * Unregister effect classes with the engine
   *
   * @param effects Effect classes
   * @return this
   */
  public LX unregisterEffects(List<Class<? extends LXEffect>> effects) {
    for (Class<? extends LXEffect> effect : effects) {
      this.registeredEffects.remove(effect);
    }
    return this;
  }

  /**
   * Gets the list of registered effect classes
   *
   * @return Effect classes
   */
  public List<Class<? extends LXEffect>> getRegisteredEffects() {
    return this.registeredEffects;
  }

  public LX registerFixture(String fixtureName) {
    checkRegistration();
    if (this.registeredFixtures.contains(fixtureName)) {
      throw new IllegalStateException("Cannot double-register fixture: " + fixtureName);
    }
    this.registeredFixtures.add(fixtureName);
    return this;
  }

  public List<String> getRegisteredFixtures() {
    return this.registeredFixtures;
  }

  public List<Class<? extends LXModel>> getRegisteredModels() {
    return this.contentLoader.getRegisteredModels();
  }

  /**
   * Register a [channel and crossfader] blend class with the engine
   *
   * @param blend Blend class
   * @return this
   */
  public LX registerBlend(Class<? extends LXBlend> blend) {
    checkRegistration();
    registerChannelBlend(blend);
    registerTransitionBlend(blend);
    registerCrossfaderBlend(blend);
    return this;
  }

  /**
   * Register multiple [channel and crossfader] blend classes with the engine
   *
   * @param blends List of blend classes
   * @return this
   */
  public LX registerBlends(Class<LXBlend>[] blends) {
    checkRegistration();
    registerChannelBlends(blends);
    registerTransitionBlends(blends);
    registerCrossfaderBlends(blends);
    return this;
  }

  /**
   * Register a channel blend class with the engine
   *
   * @param blend Blend class
   * @return this
   */
  public LX registerChannelBlend(Class<? extends LXBlend> blend) {
    checkRegistration();
    this.registeredChannelBlends.add(blend);
    this.engine.mixer.updateChannelBlendOptions();
    return this;
  }

  /**
   * Register a transition blend class with the engine
   *
   * @param blend Blend class
   * @return this
   */
  public LX registerTransitionBlend(Class<? extends LXBlend> blend) {
    checkRegistration();
    this.registeredTransitionBlends.add(blend);
    this.engine.mixer.updateTransitionBlendOptions();
    return this;
  }

  /**
   * Register multiple channel blend classes with the engine
   *
   * @param blends List of blend classes
   * @return this
   */
  public LX registerChannelBlends(Class<LXBlend>[] blends) {
    checkRegistration();
    for (Class<LXBlend> blend : blends) {
      this.registeredChannelBlends.add(blend);
    }
    this.engine.mixer.updateChannelBlendOptions();
    return this;
  }

  /**
   * Register multiple channel blend classes with the engine
   *
   * @param blends List of blend classes
   * @return this
   */
  public LX registerTransitionBlends(Class<LXBlend>[] blends) {
    checkRegistration();
    for (Class<LXBlend> blend : blends) {
      this.registeredTransitionBlends.add(blend);
    }
    this.engine.mixer.updateTransitionBlendOptions();
    return this;
  }

  /**
   * Gets the list of registered channel blend classes
   *
   * @return Blend classes
   */
  public List<Class<? extends LXBlend>> getRegisteredChannelBlends() {
    return this.registeredChannelBlends;
  }

  /**
   * Register a crossfader blend class with the engine
   *
   * @param blend Blend class
   * @return this
   */
  public LX registerCrossfaderBlend(Class<? extends LXBlend> blend) {
    checkRegistration();
    this.registeredCrossfaderBlends.add(blend);
    this.engine.mixer.updateCrossfaderBlendOptions();
    return this;
  }

  /**
   * Register multiple crossfader blend classes with the engine
   *
   * @param blends List of blend classes
   * @return this
   */
  public LX registerCrossfaderBlends(Class<LXBlend>[] blends) {
    checkRegistration();
    for (Class<LXBlend> blend : blends) {
      this.registeredCrossfaderBlends.add(blend);
    }
    this.engine.mixer.updateCrossfaderBlendOptions();
    return this;
  }

  /**
   * Gets the list of registered crossfader blend classes
   *
   * @return Blend classes
   */
  public List<Class<? extends LXBlend>> getRegisteredCrossfaderBlends() {
    return this.registeredCrossfaderBlends;
  }

  public List<LXClassLoader.Plugin> getPlugins() {
    return this.contentLoader.getPlugins();
  }

  private final Map<String, LXSerializable> externals = new HashMap<String, LXSerializable>();

  private final static String KEY_VERSION = "version";
  private final static String KEY_TIMESTAMP = "timestamp";
  private final static String KEY_MODEL = "model";
  private final static String KEY_ENGINE = "engine";
  private final static String KEY_EXTERNALS = "externals";

  private File file;

  protected void setProject(File file, ProjectListener.Change change) {
    this.file = file;
    for (ProjectListener projectListener : this.projectListeners) {
      projectListener.projectChanged(file, change);
    }
    this.preferences.setProject(file);
  }

  public File getProject() {
    return this.file;
  }

  public void saveProject() {
    if (this.file != null) {
      saveProject(this.file);
    }
  }

  public void saveProject(File file) {
    JsonObject obj = new JsonObject();
    obj.addProperty(KEY_VERSION, LX.VERSION);
    obj.addProperty(KEY_TIMESTAMP, System.currentTimeMillis());
    obj.add(KEY_MODEL, LXSerializable.Utils.toObject(this, this.structure));
    obj.add(KEY_ENGINE, LXSerializable.Utils.toObject(this, this.engine));
    JsonObject externalsObj = new JsonObject();
    for (String key : this.externals.keySet()) {
      externalsObj.add(key, LXSerializable.Utils.toObject(this, this.externals.get(key)));
    }
    obj.add(KEY_EXTERNALS, externalsObj);
    try (JsonWriter writer = new JsonWriter(new FileWriter(file))) {
      writer.setIndent("  ");
      new GsonBuilder().create().toJson(obj, writer);
      LX.log("Project saved successfully to " + file.toString());
      this.componentRegistry.resetProject();
      setProject(file, ProjectListener.Change.SAVE);
      this.command.setDirty(false);
    } catch (IOException iox) {
      LX.error(iox, "Could not write project to output file: " + file.toString());
    }
  }

  public LX registerExternal(String key, LXSerializable serializable) {
    if (this.externals.containsKey(key)) {
      throw new IllegalStateException("Duplicate external for key: " + key + " (already: " + serializable + ")");
    }
    this.externals.put(key,  serializable);
    return this;
  }

  private int getMaxId(JsonObject obj, int max) {
    for (Entry<String, JsonElement> entry : obj.entrySet()) {
      if (entry.getKey().equals(LXComponent.KEY_ID)) {
        int id = entry.getValue().getAsInt();
        if (id > max) {
          max = id;
        }
      } else if (entry.getValue().isJsonArray()) {
        for (JsonElement arrElement : entry.getValue().getAsJsonArray()) {
          if (arrElement.isJsonObject()) {
            max = getMaxId(arrElement.getAsJsonObject(), max);
          }
        }
      } else if (entry.getValue().isJsonObject()) {
        max = getMaxId(entry.getValue().getAsJsonObject(), max);
      }
    }
    return max;
  }

  private void closeProject() {
    this.command.clear();
    this.command.setDirty(false);
    this.componentRegistry.resetProject();
  }

  protected final void confirmChangesSaved(String message, Runnable confirm) {
    if (this.command.isDirty()) {
      showConfirmUnsavedProjectDialog(message, confirm);
    } else {
      confirm.run();
    }
  }

  protected void showConfirmUnsavedProjectDialog(String message, Runnable confirm) {
    // Subclasses that have a UI can prompt the user to confirm here...
    // maybe headless could show something on the CLI? But not sure we want to
    // get into that...
    confirm.run();
  }

  /**
   * Get the root media path for storage of LX-related objects and extensions
   *
   * @return File path to root storage location of LX-related content
   */
  public String getMediaPath() {
    return this.flags.mediaPath;
  }

  /**
   * Gets the path to a file relative to a base media path. Useful for writing file names into project files.
   *
   * @param type Media type
   * @param file File
   * @return Relative path to file, from media type base, or absolute if outside of media container
   */
  public String getMediaPath(Media type, File file) {
    return getMediaFolder(type).getAbsoluteFile().toURI().relativize(file.getAbsoluteFile().toURI()).getPath();
  }

  /**
   * Retrieves a file handle to the folder used to store the given type of media
   *
   * @param type Media type
   * @return File handle to directory for storage of this type of media
   */
  public File getMediaFolder(Media type) {
    return getMediaFolder(type, true);
  }

  /**
   * Retrieves a file handle to the folder used to store the given type of media
   *
   * @param type Media type
   * @param create Create folder if true
   * @return File handle to directory for storage of this type of media
   */
  public File getMediaFolder(Media type, boolean create) {
    File folder = new File(getMediaPath(), type.getDirName());
    if (folder.exists()) {
      if (folder.isFile()) {
        throw new IllegalStateException("LX media folder already exists, but contains a plain file: " + folder);
      }
    } else if (create) {
      folder.mkdir();
    }
    return folder;
  }

  /**
   * Retrieves a file handle to a file that can be saved. Path is given relative
   * to the root LX media directory, unless the given path is absolute.
   *
   * @param type Media type
   * @param path File path relative to LX media dir, or absolute
   * @return File handle to file that can be saved
   */
  public File getMediaFile(Media type, String path) {
    File file = new File(path);
    if (file.isAbsolute()) {
      return file;
    }
    return new File(getMediaFolder(type), path);
  }

  /**
   * Retrieves a file handle to a file that can be saved. Path is given relative
   * to the root LX media directory, unless the given path is absolute.
   *
   * @param path File path relative to LX media dir, or absolute
   * @return File handle to file that can be saved
   */
  public File getMediaFile(String path) {
    File file = new File(path);
    if (file.isAbsolute()) {
      return file;
    }
    return new File(getMediaPath(), path);
  }

  public void newProject() {
    confirmChangesSaved("create a new project", () -> {
      closeProject();
      if (!this.flags.immutableModel) {
        this.structure.load(this, new JsonObject());
      }
      this.engine.load(this, new JsonObject());
      setProject(null, ProjectListener.Change.NEW);
    });
  }

  public void openProject(File file) {
    for (ProjectListener projectListener : this.projectListeners) {
      projectListener.projectChanged(file, ProjectListener.Change.TRY);
    }

    try (FileReader fr = new FileReader(file)) {
      JsonObject obj = new Gson().fromJson(fr, JsonObject.class);
      closeProject();
      this.componentRegistry.loading = true;
      this.componentRegistry.setIdCounter(getMaxId(obj, this.componentRegistry.getIdCounter()) + 1);
      if (!this.flags.immutableModel) {
        LXSerializable.Utils.loadObject(this, this.structure, obj, KEY_MODEL, true);
      }
      this.engine.load(this, obj.getAsJsonObject(KEY_ENGINE));
      if (obj.has(KEY_EXTERNALS)) {
        JsonObject externalsObj = obj.getAsJsonObject(KEY_EXTERNALS);
        for (String key : this.externals.keySet()) {
          if (externalsObj.has(key)) {
            this.externals.get(key).load(this, externalsObj.getAsJsonObject(key));
          }
        }
      }
      this.componentRegistry.loading = false;
      setProject(file, ProjectListener.Change.OPEN);
      LX.log("Project loaded successfully from " + file.toString());
    } catch (IOException iox) {
      LX.error("Could not load project file: " + iox.getLocalizedMessage());
      this.command.pushError("Could not load project file: " + iox.getLocalizedMessage(), iox);
    } catch (Exception x) {
      LX.error(x, "Exception in openProject: " + x.getLocalizedMessage());
      this.command.pushError("Exception in openProject: " + x.getLocalizedMessage(), x);
    }
  }

  public LXModel instantiateModel(String className) throws InstantiationException {
    try {
      Class<? extends LXModel> cls = Class.forName(className, true, this.contentLoader).asSubclass(LXModel.class);
      return cls.getConstructor().newInstance();
    } catch (Exception x) {
      LX.error(x, "Exception in instantiateModel: " + x.getMessage());
      throw new InstantiationException(x);
    }
  }

  public <T extends LXComponent> T instantiateComponent(String className, Class<T> type) throws InstantiationException {
    try {
      Class<? extends T> cls = Class.forName(className, true, this.contentLoader).asSubclass(type);
      return instantiateComponent(cls, type);
    } catch (Exception x) {
      LX.error(x, "Exception in instantiateComponent: " + x.getMessage());
      throw new InstantiationException(x);
    }
  }

  public <T extends LXComponent> T instantiateComponent(Class<? extends T> cls, Class<T> type) throws InstantiationException {
    try {
      try {
        return cls.getConstructor(LX.class).newInstance(this);
      } catch (NoSuchMethodException nsmx) {
        return cls.getConstructor().newInstance();
      }
    } catch (Exception x) {
      LX.error(x, "Exception in instantiateComponent: " + x.getMessage());
      throw new InstantiationException(x);
    }
  }

  public LXFixture instantiateFixture(String className) throws InstantiationException {
    return instantiateComponent(className, LXFixture.class);
  }

  public LXFixture instantiateFixture(Class<? extends LXFixture> cls) throws InstantiationException {
    return instantiateComponent(cls, LXFixture.class);
  }

  public LXModulator instantiateModulator(String className) throws InstantiationException {
    return instantiateComponent(className, LXModulator.class);
  }

  public LXModulator instantiateModulator(Class<? extends LXModulator> cls) throws InstantiationException {
    return instantiateComponent(cls, LXModulator.class);
  }

  public LXPattern instantiatePattern(String className) throws InstantiationException {
    return instantiateComponent(className, LXPattern.class);
  }

  public LXPattern instantiatePattern(Class<? extends LXPattern> cls) throws InstantiationException {
    return instantiateComponent(cls, LXPattern.class);
  }

  public LXEffect instantiateEffect(String className) throws InstantiationException {
    return instantiateComponent(className, LXEffect.class);
  }

  public LXEffect instantiateEffect(Class<? extends LXEffect> cls) throws InstantiationException {
    return instantiateComponent(cls, LXEffect.class);
  }

  public LXBlend instantiateBlend(String className) throws InstantiationException {
    return instantiateComponent(className, LXBlend.class);
  }

  public LXBlend instantiateBlend(Class<? extends LXBlend> cls) throws InstantiationException {
    return instantiateComponent(cls, LXBlend.class);
  }

  public void reloadContent() {
    LX.log("Reloading custom content folders");
    this.contentLoader.dispose();
    this.contentReloading = true;

    // The previous contentLoader is now disposed. Note that the classes it defined
    // may still be in use, e.g. via live patterns or effects. But we've released our
    // handle to it. Those classes will be garbage collected when they have no more
    // references. And all our new instantiations will use the new version of the Class
    // objects defined by a new instance of the LXClassLoader.
    this.contentLoader = LXClassLoader.createNew(this);
    this.contentReloading = false;
    for (Listener listener : this.listeners) {
      listener.contentChanged(this);
    }
  }

  public void setSystemClipboardString(String str) {
    // This is not implemented by default, don't want to mess with AWT or any of that noise
    // on systems that might not have it or it may interfere with other UI libraries
  }

  protected static void bootstrapMediaPath(Flags flags) {
    File studioDir = new File(System.getProperty("user.home"), "LXStudio");
    if (!studioDir.exists()) {
      LX.log("Creating directory: " + studioDir);
      studioDir.mkdir();
    }
    if (studioDir.isDirectory()) {
      flags.mediaPath = studioDir.getPath();
      for (LX.Media type : LX.Media.values()) {
        File contentDir = new File(studioDir, type.getDirName());
        if (!contentDir.exists()) {
          LX.log("Creating directory: " + contentDir);
          contentDir.mkdir();
        }
      }
    } else {
      LX.error("~/LXStudio already exists but is not a directory, this will not go well.");
    }
  }

  public static void log(String message) {
    _log(System.out, message);
  }

  public static void error(String message) {
    _log(System.err, message);
  }

  public static void error(Exception x) {
    error(x, x.getCause().getClass().getName() + ":" + x.getLocalizedMessage());
  }

  public static void error(String prefix, Throwable x) {
    error(x, x.getCause().getClass().getName() + ":" + x.getLocalizedMessage());
  }

  public static void error(Throwable x, String message) {
    _log(System.err, message);
    x.printStackTrace(System.err);
  }

  private static final DateFormat logDateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");

  protected static void _log(String prefix, String message) {
    _log(System.out, prefix, message);
  }

  protected static void _error(String prefix, Exception x, String message) {
    _log(System.err, prefix, message);
    x.printStackTrace(System.err);
  }

  protected static void _error(String prefix, String message) {
    _log(System.err, prefix, message);
  }

  protected static void _log(PrintStream stream, String message) {
    _log(stream, "LX", message);
  }

  protected static void _log(PrintStream stream, String prefix, String message) {
    stream.println("[" + prefix + " " + logDateFormat.format(Calendar.getInstance().getTime()) + "] " + message);
  }

  /**
   * Runs a headless version of LX
   *
   * @param args Command line arguments
   */
  public static void main(String[] args) {
    Flags flags = new Flags();
    bootstrapMediaPath(flags);
    File projectFile = null;
    for (String arg : args) {
      if (arg.endsWith(".lxp")) {
        projectFile = new File(arg);
        if (!projectFile.exists()) {
          LX.error("Project file does not exist: " + projectFile);
          projectFile = null;
        }
      }
    }
    headless(flags, projectFile);
  }

  protected static void headless(Flags flags, File projectFile) {
    LX lx = new LX(flags);
    if (projectFile != null) {
      lx.openProject(projectFile);
    }
    LX.log("Starting headless engine...");
    lx.engine.start();
  }
}

