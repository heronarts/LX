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

import heronarts.lx.blend.LXBlend;
import heronarts.lx.clipboard.LXClipboard;
import heronarts.lx.color.LXColor;
import heronarts.lx.command.LXCommandEngine;
import heronarts.lx.effect.LXEffect;
import heronarts.lx.mixer.LXChannel;
import heronarts.lx.mixer.LXAbstractChannel;
import heronarts.lx.model.LXModel;
import heronarts.lx.modulator.LXModulator;
import heronarts.lx.output.LXOutput;
import heronarts.lx.parameter.MutableParameter;
import heronarts.lx.parameter.StringParameter;
import heronarts.lx.pattern.LXPattern;
import heronarts.lx.pattern.color.SolidPattern;
import heronarts.lx.scheduler.LXScheduler;
import heronarts.lx.structure.LXFixture;
import heronarts.lx.structure.LXStructure;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Queue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

  public static final String VERSION = "1.1.0";

  public static class InstantiationException extends Exception {

    private static final long serialVersionUID = 2L;

    public enum Type {
      EXCEPTION,
      LICENSE,
      PLUGIN;
    }

    public final Type type;

    public InstantiationException(Type type, String message) {
      super(message);
      this.type = type;
    }

    public InstantiationException(Exception underlying, String message) {
      super(message, underlying);
      this.type = Type.EXCEPTION;
    }

  }

  public interface Permissions {

    public static final int UNLIMITED_POINTS = -1;

    public boolean isEulaRequired();

    public boolean canSave();

    public int getMaxOutputPoints();

    public int getMaxRenderPoints();

    public boolean canRunPlugins();

    public boolean hasPackageLicense(String packageName);

    public static Permissions UNRESTRICTED = new Permissions() {

      @Override
      public boolean isEulaRequired() {
        return false;
      }

      @Override
      public boolean canSave() {
        return true;
      }

      @Override
      public int getMaxOutputPoints() {
        return UNLIMITED_POINTS;
      }

      @Override
      public int getMaxRenderPoints() {
        return UNLIMITED_POINTS;
      }

      @Override
      public boolean canRunPlugins() {
        return true;
      }

      @Override
      public boolean hasPackageLicense(String packageName) {
        return false;
      }
    };
  }

  public static class Flags {

    /**
     * Specifies how the state of live output is restored when a project
     * is loaded.
     */
    public enum OutputMode {
      /**
       * Restore the setting stored in the project
       */
      PROJECT,

      /**
       * Always enable Live output when loading a project
       */
      ACTIVE,

      /**
       * Always suppress Live output when loading a project
       */
      INACTIVE;
    }

    /**
     * Sometimes we need to know if we are P4LX, but we don't want LX library to have
     * any dependency upon P4LX.
     */
    public boolean isP4LX = false;
    public boolean immutableModel = false;
    public boolean focusChannelOnCue = false;
    public boolean focusActivePattern = false;
    public boolean sendCueToOutput = false;
    public boolean autosave = false;
    public long autosaveIntervalMs = 15000;
    public boolean zeroconf = false;
    public String zeroconfServiceName = "LX";
    public LXEngine.ThreadMode threadMode = LXEngine.ThreadMode.SCHEDULED_EXECUTOR_SERVICE;
    public int engineThreadPriority = Thread.MAX_PRIORITY;
    public String mediaPath = ".";
    public LXPlugin initialize = null;
    public boolean loadPreferences = true;
    public List<String> enabledPlugins = new ArrayList<String>();
    public List<String> classpathPlugins = new ArrayList<String>();
    public OutputMode outputMode = OutputMode.PROJECT;
  }

  public static enum Media {
    PACKAGES("Packages"),
    FIXTURES("Fixtures"),
    PROJECTS("Projects"),
    MODELS("Models"),
    VIEWS("Views"),
    PRESETS("Presets"),
    SCRIPTS("Scripts"),
    COLORS("Colors"),
    MIDI_MAPPINGS("MIDI Mappings"),
    LOGS("Logs"),
    AUTOSAVE("Autosave"),
    DELETED("Deleted");

    private final String dirName;

    private Media(String dirName) {
      this.dirName = dirName;
    }

    public String getDirName() {
      return this.dirName;
    }

    private boolean isBootstrap() {
      switch (this) {
      case AUTOSAVE: return false;
      case DELETED: return false;
      default: return true;
      }
    }
  }

  public static class Error {

    public final Throwable cause;
    public final String message;

    private Error(String message) {
      this(null, message);
    }

    private Error(Throwable cause) {
      this(cause, cause.getLocalizedMessage());
    }

    private Error(Throwable cause, String message) {
      this.cause = cause;
      this.message = message;
    }

    public String getStackTrace() {
      if (this.cause != null) {
        try (
          StringWriter sw = new StringWriter();
          PrintWriter pw = new PrintWriter(sw)) {
          this.cause.printStackTrace(pw);
          return sw.toString();
        } catch (IOException e) {
          // Ignored, we really meta-failed hard here.
        }
      }
      return null;
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

  public static final float PIf = (float) Math.PI;
  public static final float HALF_PIf = (float) (Math.PI / 2.);
  public static final float TWO_PIf = (float) (Math.PI * 2.);

  public static class InitProfiler {
    private long lastTime;

    protected void init() {
      this.lastTime = System.nanoTime();
    }

    public void log(String label) {
      long thisTime = System.nanoTime();
      if (LX.LOG_INIT_PROFILER) {
        LX.log(String.format("init: %s: %.2fms", label, (thisTime - lastTime) / 1000000.));
      }
      this.lastTime = thisTime;
    }
  }

  public static final InitProfiler initProfiler = new InitProfiler();

  private static boolean LOG_INIT_PROFILER = false;

  public static void logInitProfiler() {
    LX.LOG_INIT_PROFILER = true;
  }

  /**
   * Listener for top-level events
   */
  public interface Listener {
    /**
     * Fired whenever a new model instance is set on this LX instance. The
     * passed model is an entirely new object that has not been set before.
     *
     * @param lx LX instance
     * @param model Model instance
     */
    default public void modelChanged(LX lx, LXModel model) {}

    /**
     * Fired when the generation of a model has been changed. This is the same
     * model instance that has already been set on LX, but it has been modified.
     * This is also fired the very first time a model is set (e.g. generation 0
     * for the model). Listeners that wish to take generic action based upon any new
     * model geometry, whether it's an existing or new model, may listen to just
     * this method.
     *
     * @param lx LX instance
     * @param model model instance
     */
    default public void modelGenerationChanged(LX lx, LXModel model) {}
  }

  /**
   * Listener for any type of model change
   */
  public interface ModelListener {
    public void modelChanged(LXModel model);
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
   * Permissions
   */
  public final Permissions permissions = getPermissions();;

  protected Permissions getPermissions() {
    return Permissions.UNRESTRICTED;
  }

  /**
   * Error stack
   */
  private final Queue<Error> errorQueue = new ArrayDeque<Error>();

  /**
   * Parameter that is bang()-ed every time errors change
   */
  public final MutableParameter errorChanged = new MutableParameter("Error");

  /**
   * This parameter will be set if a critical, unrecoverable error occurs. It will
   * only be set one time, and it should be assumed that the engine is no longer
   * running if this is the case.
   */
  public StringParameter failure = new StringParameter("Failure", null);

  /**
   * Parameter that can be watched by the UI to push status messages
   */
  public final StringParameter statusMessage = new StringParameter("Status Message", "");

  /**
   * The lighting system structure
   */
  public final LXStructure structure;

  /**
   * The pixel model.
   */
  protected LXModel model;

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
   * Registry for classes
   */
  public final LXRegistry registry;

  /**
   * The project scheduler
   */
  public final LXScheduler scheduler;

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

  public LX(Flags flags, LXModel model) {
    LX.initProfiler.init();
    this.flags = flags;
    this.flags.immutableModel = (model != null);

    // Create structure object
    this.structure = new LXStructure(this, model, new LXStructure.ModelListener() {
      public void structureChanged(LXModel model) {
        setModel(model);
      }
      public void structureGenerationChanged(LXModel model) {
        for (Listener listener : listeners) {
          listener.modelGenerationChanged(LX.this, model);
        }
      }
    });
    if (model == null) {
      this.model = this.structure.getModel();
    } else {
      this.model = model;
    }
    LX.initProfiler.log("Model");

    // Custom content loader
    this.registry = instantiateRegistry(this);
    this.registry.initialize();
    LX.initProfiler.log("Registry");

    // Load the global preferences before plugin initialization
    this.preferences = new LXPreferences(this);
    if (this.flags.loadPreferences) {
      this.preferences.load();
    } else {
      this.preferences.loadEULA();
    }

    // Scheduler
    this.scheduler = new LXScheduler(this);

    // Construct the engine
    this.engine = new LXEngine(this);
    this.command = new LXCommandEngine(this);
    LX.initProfiler.log("Engine");


    // Initialize tempo
    this.engine.tempo.initialize();

    // Midi
    this.engine.midi.initialize();

    // Initialize plugins!
    if ((this instanceof LXPlugin) && (flags.initialize != this)) {
      ((LXPlugin) this).initialize(this);
    }
    if (this.flags.initialize != null) {
      this.flags.initialize.initialize(this);
    }
    this.registry.initializePlugins();
  }

  protected void fail(Throwable x) {
    String logLocation = "the console output.";
    if (LX.EXPLICIT_LOG_FILE != null) {
      logLocation = LX.EXPLICIT_LOG_FILE.getAbsolutePath();
    }

    String message =
      "A serious and unexpected fatal error has occured. The program cannot continue and the UI may no longer be responsive. Please report this issue." +
      "\n\n" +
      "Details of the error have been logged to " + logLocation +
      "\n\n";

    try (StringWriter sw = new StringWriter()) {
      x.printStackTrace(new PrintWriter(sw));
      String stackTrace = sw.toString();
      message += stackTrace;
      setSystemClipboardString(stackTrace);
    } catch (IOException iox) {
      // Ooh, royally fucked and weird if this happens
    }

    this.failure.setValue(message);
  }

  public LX pushError(Throwable exception) {
    return pushError(new Error(exception));
  }

  public LX pushError(Throwable exception, String message) {
    return pushError(new Error(exception, message));
  }

  public LX pushError(String message) {
    return pushError(new Error(message));
  }

  public LX pushError(Error error) {
    this.errorQueue.add(error);
    this.errorChanged.bang();
    return this;
  }

  public LX popError() {
    if (!this.errorQueue.isEmpty()) {
      this.errorQueue.remove();
      this.errorChanged.bang();
    }
    return this;
  }

  public LX.Error getError() {
    return this.errorQueue.peek();
  }

  public LX pushStatusMessage(String message) {
    log(message);
    this.statusMessage.setValue(message, true);
    return this;
  }

  /**
   * Subclasses may override to provide an enhanced registry with support for more types
   *
   * @param lx LX instance
   * @return LXRegistry to use for dynamic class stuff
   */
  protected LXRegistry instantiateRegistry(LX lx) {
    return new LXRegistry(this);
  }

  public LX addListener(Listener listener) {
    Objects.requireNonNull(listener);
    if (this.listeners.contains(listener)) {
      throw new IllegalStateException("Cannot add duplicate LX.Listener: " + listener);
    }
    this.listeners.add(listener);
    return this;
  }

  public LX removeListener(Listener listener) {
    if (!this.listeners.contains(listener)) {
      throw new IllegalStateException("May not remove non-registered LX.Listener: " + listener);
    }
    this.listeners.remove(listener);
    return this;
  }

  /**
   * Registers and returns listener to fire on any change to the model
   *
   * @param listener Model listener for changes to model structure and/or geometry
   * @return The registered listener
   */
  public LX.Listener onModelChanged(LX.ModelListener listener) {
    return onModelChanged(listener, false);
  }

  /**
   * Registers a permanent listener to fire on any change to the model
   *
   * @param listener Model listener for changes to model structure and/or geometry
   * @param fire Whether to fire the listener immeediately
   * @return The registered listener
   */
  public LX.Listener onModelChanged(LX.ModelListener listener, boolean fire) {
    final Listener ret = new Listener() {
      public void modelChanged(LX lx, LXModel model) {
        listener.modelChanged(model);
      }
      public void modelGenerationChanged(LX lx, LXModel model) {
        listener.modelChanged(model);
      }
    };
    addListener(ret);
    if (fire) {
      listener.modelChanged(getModel());
    }
    return ret;
  }

  public LX addProjectListener(ProjectListener listener) {
    Objects.requireNonNull(listener);
    if (this.projectListeners.contains(listener)) {
      throw new IllegalStateException("Cannot add duplicate LX.ProjectListener: " + listener);
    }
    this.projectListeners.add(listener);
    return this;
  }

  public LX removeProjectListener(ProjectListener listener) {
    if (!this.projectListeners.contains(listener)) {
      throw new IllegalStateException("Trying to remove non-registered LX.ProjectListener: " + listener);
    }
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

  // Private internal-only API. User-facing APIs are on LXStructure
  private LX setModel(LXModel model) {
    Objects.requireNonNull(model, "May not set null model on LX instance");
    if (this.model == model) {
      throw new IllegalStateException("Cannot reset same model instance: " + model);
    }
    LXModel oldModel = this.model;

    this.model = model;
    for (Listener listener : this.listeners) {
      listener.modelChanged(this, model);
    }
    for (Listener listener : this.listeners) {
      listener.modelGenerationChanged(this, model);
    }

    // Dispose of the old model after notifying listeners of model change
    if (oldModel != null) {
      oldModel.dispose();
    }

    return this;
  }

  /**
   * Dispose of a component, with an assertion that the disposal
   * succeeds and the base class LXComponent.dispose() method was called.
   *
   * @param component Component to dispose
   */
  public static void dispose(LXComponent component) {
    component.dispose();
    LXComponent.assertDisposed(component);
  }

  /**
   * Shut down resources of the LX instance.
   */
  public void dispose() {
    LX.dispose(this.engine);
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
      channel.goPreviousPattern();
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
      channel.goNextPattern();
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
      channel.goPatternIndex(i);
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

  private final Map<String, LXSerializable> externals = new HashMap<String, LXSerializable>();

  public final static String KEY_VERSION = "version";
  public final static String KEY_TIMESTAMP = "timestamp";

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

    // NOTE(mcslee): This is a great opportunity to reclaim memory from
    // a previously open project
    System.gc();
  }

  public File getProject() {
    return this.file;
  }

  private File getAutoSaveFile() {
    if (this.file != null) {
      return getMediaFile(Media.AUTOSAVE, this.file.getName());
    } else {
      return getMediaFile(Media.AUTOSAVE, "default.lxp");
    }
  }

  public void autoSaveProject() {
    if (!this.permissions.canSave()) {
      return;
    }

    final File autosave = getAutoSaveFile();
    if (autosave != null) {
      // Need to serialize the data here on the engine thread
      final JsonObject obj = saveProjectJson();

      // Write the file on another thread to avoid main thread jitter
      new Thread(() -> {
        try (JsonWriter writer = new JsonWriter(new FileWriter(autosave))) {
          writer.setIndent("  ");
          new GsonBuilder().create().toJson(obj, writer);
          LX.debug("Project auto-saved successfully to " + autosave.toString());
        } catch (IOException iox) {
          LX.error(iox, "Could not auto-save project to output file: " + autosave.toString());
        }
      }).start();
    }
  }

  public void saveProject() {
    if (this.file != null) {
      saveProject(this.file);
    }
  }

  public void saveProject(File file) {
    if (!this.permissions.canSave()) {
      return;
    }

    JsonObject obj = saveProjectJson();
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

    confirmModelSaved();
  }

  private JsonObject saveProjectJson() {
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
    return obj;
  }

  public LX registerExternal(String key, LXSerializable serializable) {
    if (this.externals.containsKey(key)) {
      throw new IllegalStateException("Duplicate external for key: " + key + " (already: " + serializable + ")");
    }
    this.externals.put(key,  serializable);
    return this;
  }

  int getMaxId(JsonObject obj, int max) {
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

  public boolean isLoading() {
    return this.componentRegistry.projectLoading;
  }

  public void newProject() {
    confirmChangesSaved("create a new project", () -> {
      closeProject();
      if (!this.flags.immutableModel) {
        this.structure.load(this, new JsonObject());
      }
      this.engine.load(this, new JsonObject());
      for (LXSerializable external : this.externals.values()) {
        LXSerializable.Utils.resetObject(this, external);
      }

      final LXChannel channel = this.engine.mixer.addChannel(new LXPattern[] { new SolidPattern(this, 0xffff0000) });
      this.engine.mixer.selectChannel(channel);
      this.engine.mixer.setFocusedChannel(channel);
      channel.fader.setValue(1);

      setProject(null, ProjectListener.Change.NEW);
    });
  }

  public void openProject(File file) {
    openProject(file, false);
  }

  private static final Pattern versionPattern = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)(?:-([\\w.-]+))?$");

  private boolean isNewerVersion(String version) {
    return compareVersion(LX.VERSION, version) < 0;
  }

  /**
   * Compares two version strings for mismatch
   *
   * @param thisVersion First version
   * @param thatVersion Second version
   * @return true if thatVersion is newer than thisVersion
   */
  public static int compareVersion(String thisVersion, String thatVersion) {
    try {
      Matcher thisMatcher = versionPattern.matcher(thisVersion);
      Matcher thatMatcher = versionPattern.matcher(thatVersion);
      if (!thisMatcher.matches()) {
        throw new IllegalArgumentException("Couldn't parse: " + thisVersion);
      }
      if (!thatMatcher.matches()) {
        throw new IllegalArgumentException("Couldn't parse: " + thatVersion);
      }
      int thisMajor = Integer.valueOf(thisMatcher.group(1));
      int thisMinor = Integer.valueOf(thisMatcher.group(2));
      int thisPatch = Integer.valueOf(thisMatcher.group(3));

      int thatMajor = Integer.valueOf(thatMatcher.group(1));
      int thatMinor = Integer.valueOf(thatMatcher.group(2));
      int thatPatch = Integer.valueOf(thatMatcher.group(3));

      int majorCompare = thisMajor < thatMajor ? -1 : (thisMajor == thatMajor) ? 0 : 1;
      if (majorCompare != 0) {
        return majorCompare;
      }
      int minorCompare = thisMinor < thatMinor ? -1 : (thisMinor == thatMinor) ? 0 : 1;
      if (minorCompare != 0) {
        return minorCompare;
      }
      return thisPatch < thatPatch ? -1 : (thisPatch == thatPatch) ? 0 : 1;
    } catch (Exception x) {
      error(x, "Failed to parse LX version identifier");
    }
    return 0;
  }

  public void openProject(File file, boolean checkVersion) {
    try (FileReader fr = new FileReader(file)) {
      final JsonObject obj = new Gson().fromJson(fr, JsonObject.class);
      final String fileVersion = obj.has(KEY_VERSION) ? obj.get(KEY_VERSION).getAsString() : null;
      if ((fileVersion != null) && isNewerVersion(fileVersion)) {
        LX.warning(file.getName() + ": project version " + fileVersion + " is newer than app version " + LX.VERSION);
        if (checkVersion) {
          showConfirmDialog(
            "Project version: " + fileVersion + "\n" +
            "App version: " + LX.VERSION + "\n\n" +
            "The project may not load properly, proceed?",
            () -> _openProject(file, obj)
          );
          return;
        }
      }
      _openProject(file, obj);
    } catch (FileNotFoundException fnfx) {
      LX.error(fnfx, "Project file not found: " + fnfx.getLocalizedMessage());
      pushError(fnfx, "Project file not found: " + fnfx.getLocalizedMessage());
    } catch (IOException iox) {
      LX.error("Could not read project file: " + iox.getLocalizedMessage());
      pushError(iox, "Could not read project file: " + iox.getLocalizedMessage());
    }
  }

  private void _openProject(File file, JsonObject obj) {
    for (ProjectListener projectListener : this.projectListeners) {
      projectListener.projectChanged(file, ProjectListener.Change.TRY);
    }
    try {
      closeProject();
      this.componentRegistry.projectLoading = true;
      this.componentRegistry.setIdCounter(getMaxId(obj, this.componentRegistry.getIdCounter()) + 1);
      if (!this.flags.immutableModel) {
        LXSerializable.Utils.loadObject(this, this.structure, obj, KEY_MODEL, true);
      }
      this.engine.load(this, obj.getAsJsonObject(KEY_ENGINE));
      JsonObject externalsObj = obj.has(KEY_EXTERNALS) ? obj.getAsJsonObject(KEY_EXTERNALS) : new JsonObject();
      for (String key : this.externals.keySet()) {
        if (externalsObj.has(key)) {
          LXSerializable.Utils.loadObject(this, this.externals.get(key), externalsObj, key);
        } else {
          LXSerializable.Utils.resetObject(this, this.externals.get(key));
        }
      }
      this.componentRegistry.projectLoading = false;
      setProject(file, ProjectListener.Change.OPEN);
      LX.log("Project loaded successfully from " + file.toString());
    } catch (Exception x) {
      LX.error(x, "Exception in openProject: " + x.getLocalizedMessage());
      pushError(x, "Exception in openProject: " + x.getLocalizedMessage());
    } finally {
      this.componentRegistry.projectLoading = false;

      // NOTE(mcslee): discovered that often heap is not reclaimed automatically
      // when you might think it is. Try a collection whether or not project
      // opening succeeded.
      System.gc();
    }
  }

  private void closeProject() {
    this.command.clear();
    this.command.setDirty(false);
    this.componentRegistry.resetProject();
  }

  public void setModelImportFlag(boolean modelImport) {
    this.componentRegistry.modelImporting = modelImport;
  }

  public void setScheduleLoadingFlag(boolean scheduleLoading) {
    this.componentRegistry.scheduleLoading = scheduleLoading;
  }

  protected final void confirmChangesSaved(String message, Runnable confirm) {
    if (this.command.isDirty()) {
      showConfirmUnsavedProjectDialog(message, confirm);
    } else {
      confirm.run();
    }
  }

  public void showConfirmDialog(String message, Runnable confirm) {
    confirm.run();
  }

  protected void showConfirmUnsavedProjectDialog(String message, Runnable confirm) {
    // Subclasses that have a UI can prompt the user to confirm here...
    // maybe headless could show something on the CLI? But not sure we want to
    // get into that...
    confirm.run();
  }

  protected final void confirmModelSaved() {
    if (this.structure.isExternalModel() && this.structure.isDirty()) {
      final File file = this.structure.getModelFile();
      showConfirmUnsavedModelDialog(file, () -> {
        this.structure.exportModel(file);
      });
    }
  }

  protected void showConfirmUnsavedModelDialog(File file, Runnable confirm) {
    // Subclasses can handle this if they have a UI and prompt to save
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
    return getMediaFile(type, path, true);
  }

  /**
   * Retrieves a file handle to a file that can be saved. Path is given relative
   * to the root LX media directory, unless the given path is absolute.
   *
   * @param type Media type
   * @param path File path relative to LX media dir, or absolute
   * @param create Create folder if true
   * @return File handle to file that can be saved
   */
  public File getMediaFile(Media type, String path, boolean create) {
    File file = new File(path);
    if (file.isAbsolute()) {
      return file;
    }
    return new File(getMediaFolder(type, create), path);
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

  /**
   * Get the folder to hold presets for a device
   *
   * @param device Device
   * @return Folder that holds presets for this device
   */
  public File getPresetFolder(LXComponent device) {
    File presetFolder = getMediaFolder(Media.PRESETS);
    Class<?> deviceClass = device.getClass();
    if (device instanceof LXPresetComponent) {
      deviceClass = ((LXPresetComponent) device).getPresetClass();
    }
    File deviceFolder = new File(presetFolder, deviceClass.getName());
    if (!deviceFolder.exists()) {
      deviceFolder.mkdir();
    }
    return deviceFolder;
  }

  public File getPresetFile(LXComponent device, String name) {
    return new File(getPresetFolder(device), (name != null) ? name : "default.lxd");
  }

  public boolean canInstantiate(Class<? extends LXComponent> clz) {
    LXLicense license = clz.getAnnotation(LXLicense.class);
    if ((license != null) && !this.permissions.hasPackageLicense(license.value())) {
      return false;
    }
    LXComponent.PluginRequired pluginRequired = clz.getAnnotation(LXComponent.PluginRequired.class);
    if ((pluginRequired != null) && !this.registry.isPluginClassEnabled(pluginRequired.value())) {
      return false;
    }
    return true;
  }

  public LXModel instantiateModel(String className) throws InstantiationException {
    try {
      Class<? extends LXModel> cls = Class.forName(className, true, this.registry.classLoader).asSubclass(LXModel.class);
      return cls.getConstructor().newInstance();
    } catch (Exception x) {
      LX.error(x, "Exception in instantiateModel: " + x.getMessage());
      throw new InstantiationException(x, "Model " + className + " could not be loaded. Check that all required content files are present and constructor is public.");
    }
  }

  public <T extends LXComponent> T instantiateComponent(String className, Class<T> type) throws InstantiationException {
    Class<? extends T> cls = null;
    try {
      cls = Class.forName(className, true, this.registry.classLoader).asSubclass(type);
    } catch (Exception x) {
      LX.error(x, "Exception in instantiateComponent: " + x.getMessage());
      throw new InstantiationException(x, className + " could not be loaded. Check that all required content files are present and constructor is public.");
    }
    // NOTE(mcslee): keep this out of above try/catch so as not to pointlessly nest/cascade exceptions
    return instantiateComponent(cls, type);
  }

  public <T extends LXComponent> T instantiateComponent(Class<? extends T> cls, Class<T> type) throws InstantiationException {
    LXLicense license = cls.getAnnotation(LXLicense.class);
    if (license != null) {
      final String pkg = license.value();
      if (!this.permissions.hasPackageLicense(pkg)) {
        throw new InstantiationException(InstantiationException.Type.LICENSE, "Class requires valid license for package: " + pkg);
      }
    }

    LXComponent.PluginRequired pluginRequired = cls.getAnnotation(LXComponent.PluginRequired.class);
    if (pluginRequired != null) {
      final Class<? extends LXPlugin> pluginClass = pluginRequired.value();
      if (!this.registry.isPluginClassEnabled(pluginClass)) {
        throw new InstantiationException(InstantiationException.Type.PLUGIN, LXComponent.getComponentName(cls) + " requires plugin " + LXPlugin.getPluginName(pluginClass));
      }
    }

    try {
      try {
        return cls.getConstructor(LX.class).newInstance(this);
      } catch (NoSuchMethodException nsmx) {
        return cls.getConstructor().newInstance();
      }
    } catch (Exception x) {
      LX.error(x, "Exception in instantiateComponent: " + x.getMessage());
      throw new InstantiationException(x, cls.getSimpleName() + " could not be loaded. Check that all required content files are present and constructor is public.");
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

  public Class<?> instantiateStatic(String className) throws ClassNotFoundException {
    return Class.forName(className, true, this.registry.classLoader);
  }

  public void setSystemClipboardString(String str) {
    // This is not implemented by default, don't want to mess with AWT or any of that noise
    // on systems that might not have it or it may interfere with other UI libraries
  }

  protected static void bootstrapMediaPath(Flags flags) {
    bootstrapMediaPath(flags, "LXStudio");
  }

  protected static File bootstrapMediaPath(Flags flags, String dirName) {
    File studioDir = new File(System.getProperty("user.home"), dirName);
    if (!studioDir.exists()) {
      LX.log("Creating directory: " + studioDir);
      studioDir.mkdir();
    }
    if (studioDir.isDirectory()) {
      flags.mediaPath = studioDir.getPath();
      for (LX.Media type : LX.Media.values()) {
        if (type.isBootstrap()) {
          File contentDir = new File(studioDir, type.getDirName());
          if (!contentDir.exists()) {
            LX.log("Creating directory: " + contentDir);
            contentDir.mkdir();
          }
        }
      }
    } else {
      LX.error("~/" + dirName + " already exists but is not a directory, this will not go well.");
    }
    return studioDir;
  }

  public static void log(String message) {
    _log(System.out, message);
  }

  public static boolean LOG_WARNINGS = false;
  public static boolean LOG_DEBUG = false;

  public static void warning(String message) {
    if (LOG_WARNINGS) {
      _log(System.out, "<WARNING> " + message);
    }
  }

  public static void debug(String message) {
    if (LOG_DEBUG) {
      _log(System.out, "<DEBUG> " + message);
    }
  }

  public static void error(String message) {
    _log(System.err, message);
  }

  public static void error(Throwable x) {
    Throwable cause = x.getCause();
    if (cause != null) {
      x = cause;
    }
    error(x, x.getClass().getName() + ":" + x.getLocalizedMessage());
  }

  public static void error(Throwable x, String message) {
    _log(System.err, x, LOG_PREFIX, message);
  }

  public static void error(String message, boolean trace) {
    if (trace) {
      error(new Exception(message));
    } else {
      error(message);
    }
  }

  private static final DateFormat LOG_DATE_FORMAT = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");

  private static final String LOG_PREFIX = "LX";

  protected static void _log(String prefix, String message) {
    _log(System.out, null, prefix, message);
  }

  protected static void _error(String prefix, Exception x, String message) {
    _log(System.err, x, prefix, message);
  }

  protected static void _error(String prefix, String message) {
    _log(System.err, null, prefix, message);
  }

  protected static void _log(PrintStream stream, String message) {
    _log(stream, null, LOG_PREFIX, message);
  }

  protected static void _log(PrintStream stream, Throwable throwable, String prefix, String message) {
    String logMsg = "[" + prefix + " " + LOG_DATE_FORMAT.format(Calendar.getInstance().getTime()) + "] " + message;
    stream.println(logMsg);
    if (throwable != null) {
      throwable.printStackTrace(stream);
    }
    if (EXPLICIT_LOG_STREAM != null) {
      try {
        EXPLICIT_LOG_STREAM.println(logMsg);
        if (throwable != null) {
          throwable.printStackTrace(EXPLICIT_LOG_STREAM);
        }
      } catch (Exception x) {
        EXPLICIT_LOG_STREAM = null;
        error(x, "Exception writing log file to disk: " + x.getLocalizedMessage());
      }
    }
  }

  static File EXPLICIT_LOG_FILE = null;
  private static PrintStream EXPLICIT_LOG_STREAM = null;

  public static File getLogFile() {
    return EXPLICIT_LOG_FILE;
  }

  public static void setLogFile(File file) {
    try {
      EXPLICIT_LOG_FILE = file;
      EXPLICIT_LOG_STREAM = new PrintStream(new FileOutputStream(file, true));
    } catch (Exception x) {
      EXPLICIT_LOG_FILE = null;
      EXPLICIT_LOG_STREAM = null;
      error(x, "Log file cannot be used: " + file.toURI() + " - " + x.getLocalizedMessage());
    }
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
    for (int i = 0; i < args.length; ++i) {
      if ("--log".equals(args[i])) {
        if (++i < args.length) {
          setLogFile(new File(args[i]));
        }
      } else if ("-zc".equals(args[i]) || ("--zeroconf").equals(args[i])) {
        flags.zeroconf = true;
      } else if (args[i].endsWith(".lxp") || args[i].endsWith(".lxs")) {
        projectFile = new File(args[i]);
      }
    }

    headless(flags, projectFile);
  }

  public static void headless(Flags flags, File projectFile) {
    LX.log("Starting LX headless engine " + VERSION + "...");
    LX lx = new LX(flags);
    if (projectFile != null) {
      boolean isSchedule = projectFile.getName().endsWith(".lxs");
      if (!projectFile.exists()) {
        LX.error((isSchedule ? "Schedule" : "Project") + " file does not exist: " + projectFile);
      } else {
        if (isSchedule) {
          lx.preferences.schedulerEnabled.setValue(true);
          LX.log("Opening schedule file: " + projectFile);
          lx.scheduler.openSchedule(projectFile, true);
        } else {
          LX.log("Opening initial project file: " + projectFile);
          lx.openProject(projectFile);
        }
      }
    }
    lx.engine.start();
  }
}

