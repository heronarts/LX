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

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonWriter;

import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.BoundedParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.LXParameterListener;
import heronarts.lx.parameter.StringParameter;
import heronarts.lx.utils.LXUtils;

public class LXPreferences implements LXSerializable, LXParameterListener {

  private static final String PREFERENCES_FILE_NAME = ".lxpreferences";
  private static final String DEFAULT_PROJECT_FILE = "default.lxp";

  private LX lx;

  private final File file;

  public final BooleanParameter eulaAccepted =
    new BooleanParameter("EULA Accepted", false)
    .setDescription("Whether the EULA has been accepted");

  public final BooleanParameter focusChannelOnCue =
    new BooleanParameter("Focus On Cue", false)
    .setDescription("Whether a channel should be automatically focused when its cue is set to active");

  public final BooleanParameter focusActivePattern =
    new BooleanParameter("Auto-Focus Pattern", false)
    .setDescription("Whether a pattern should be automatically focused when it becomes active");

  public final BooleanParameter sendCueToOutput =
    new BooleanParameter("Cue applies to Live Output", false)
    .setDescription("Whether Cue selection applies to live output, not just the preview window");

  public final BooleanParameter suppressOutput =
    new BooleanParameter("Suppress Live Output", false)
    .setDescription("Suppresses network output for local development");

  public final BooleanParameter oscQuery =
    new BooleanParameter("Enable OSCQuery / Zeroconf", false)
    .setDescription("Enable OSC discovery with OSCQuery and Zeroconf");

  public final DiscreteParameter uiZoom =
    new DiscreteParameter("UI Scale", 100, 50, 201)
    .setDescription("Percentage by which the UI should be scaled")
    .setUnits(LXParameter.Units.PERCENT)
    .setMappable(false);

  public final BooleanParameter showHelpMessages =
    new BooleanParameter("Help Messages", true)
    .setDescription("Whether to show contextual help messages in the status bar");

  public final BooleanParameter schedulerEnabled =
    new BooleanParameter("Project Scheduler Enabed", false)
    .setDescription("Whether the project scheduler is enabled");

  public final BooleanParameter showCpuLoad =
    new BooleanParameter("Show CPU Load %")
    .setDescription("Whether CPU load percentage is shown in toolbar");

  public final BooleanParameter autoReloadPackages =
    new BooleanParameter("Auto-Reload Packages", false)
    .setDescription("When true, package content automatically updates when changed on disk");

  public final StringParameter uiTheme =
    new StringParameter("UI Theme", null)
    .setDescription("Which UI theme is used");

  public final BoundedParameter scrollSensitivity =
    new BoundedParameter("Scroll Sensitivity", 1, .1, 100)
    .setDescription("Scrolling sensitivity");

  private String projectFileName = null;
  private String scheduleFileName = null;

  private static final int MAX_RECENT_PROJECTS = 12;

  public final List<String> recentProjects = new ArrayList<>(MAX_RECENT_PROJECTS);

  private final WindowSettings windowSettingsMain = new WindowSettings(Window.MAIN);
  private final WindowSettings windowSettingsAlt = new WindowSettings(Window.ALT);

  public final WindowSettings getWindowSettings(Window window) {
    return switch (window) {
      case MAIN -> this.windowSettingsMain;
      case ALT -> this.windowSettingsAlt;
      case null -> null;
    };
  }

  public enum Window {
    MAIN("main"),
    ALT("alt");

    public final String key;

    private Window(String key) {
      this.key = key;
    }
  }

  /**
   * Helper class for storing position (x,y) and size (width,height) of a window or monitor.
   */
  public static class WindowSettings implements LXSerializable {

    public final Window window;

    private static final int DEFAULT = -1;

    private int x = DEFAULT;
    private int y = DEFAULT;
    private int width = DEFAULT;
    private int height = DEFAULT;

    private boolean hasPosition = false;
    private boolean hasSize = false;

    public WindowSettings() {
      this(null);
    }

    public WindowSettings(Window window) {
      this.window = window;
    }

    public WindowSettings setPosition(int x, int y) {
      this.x = x;
      this.y = y;
      this.hasPosition = this.x > 0 && this.y > 0;
      return this;
    }

    public WindowSettings setSize(int width, int height) {
      this.width = width;
      this.height = height;
      this.hasSize = this.width > 0 && this.height > 0;
      return this;
    }

    /**
     * Whether the current settings include a position.
     */
    public boolean hasPosition() {
      return this.hasPosition;
    }

    /**
     * Whether the current settings include a size.
     */
    public boolean hasSize() {
      return this.hasSize;
    }

    /**
     * Position (x) of the left edge of the display, in screen pixels.
     */
    public int getX() {
      return this.x;
    }

    /**
     * Position (y) of the top edge of the display, in screen pixels.
     */
    public int getY() {
      return this.y;
    }

    /**
     * Width in screen pixels. Does not include UI scaling.
     */
    public int getWidth() {
      return this.width;
    }

    /**
     * Height in screen pixels. Does not include UI scaling.
     */
    public int getHeight() {
      return this.height;
    }

    /**
     * Position (x) of the right edge of the display, in screen pixels.
     */
    private int getXMax() {
      return this.x + this.width;
    }

    /**
     * Position (y) of the bottom edge of the display, in screen pixels.
     */
    private int getYMax() {
      return this.y + this.height;
    }

    /**
     * Determines if a set of (x,y) coordinates are within the display bounds.
     *
     * @param x X-value to check
     * @param y Y-value to check
     * @return True if the coordinates are within the display bounds
     */
    public boolean contains(int x, int y) {
      if (!this.hasPosition || !this.hasSize) {
        return false;
      }
      return
        (x >= this.x) &&
        (x < (this.x + this.width)) &&
        (y >= this.y) &&
        (y < (this.y + this.height));
    }

    /**
     * Determines whether the X/Y upper-bounds of this setting exceeds the container
     *
     * @param that Countainer bounds
     * @return True if these bounds go past the container bounds
     */
    public boolean exceeds(WindowSettings that) {
      return (getXMax() > that.getXMax()) || (getYMax() > that.getYMax());
    }

    /**
     * Resize these bounds so that they will fit within the container
     *
     * @param that Container bounds
     * @return These bounds, updated to fit within the container
     */
    public WindowSettings constrain(WindowSettings that) {
      int width = LXUtils.min(getWidth(), that.getWidth());
      int x = that.getXMax() - width;

      int height = LXUtils.min(getHeight(), that.getHeight());
      int y = that.getYMax() - height;

      setPosition(x, y);
      setSize(width, height);
      return this;
    }

    @Override
    public String toString() {
      return toSizeString() + ", " + toPositionString();
    }

    public String toPositionString() {
      return this.hasPosition ? "pos(" + this.x + "," + this.y + ")" : "pos(n/a)";
    }

    public String toSizeString() {
      return this.hasSize ? "size(" + this.width + "x" + this.height + ")" : "size(n/a)";
    }

    private static final String KEY_X = "x";
    private static final String KEY_Y = "y";
    private static final String KEY_WIDTH = "width";
    private static final String KEY_HEIGHT = "height";

    @Override
    public void load(LX lx, JsonObject object) {
      if (object.has(KEY_WIDTH) && object.has(KEY_HEIGHT)) {
        int width = object.get(KEY_WIDTH).getAsInt();
        int height = object.get(KEY_HEIGHT).getAsInt();
        setSize(width, height);
      } else {
        this.width = this.height = DEFAULT;
        this.hasSize = false;
      }
      if (object.has(KEY_X) && object.has(KEY_Y)) {
        int x = object.get(KEY_X).getAsInt();
        int y = object.get(KEY_Y).getAsInt();
        setPosition(x, y);
      } else {
        this.x = this.y = DEFAULT;
        this.hasPosition = false;
      }
    }

    @Override
    public void save(LX lx, JsonObject object) {
      if (hasPosition()) {
        object.addProperty(KEY_X, this.x);
        object.addProperty(KEY_Y, this.y);
      }
      if (hasSize()) {
        object.addProperty(KEY_WIDTH, this.width);
        object.addProperty(KEY_HEIGHT, this.height);
      }
    }
  }

  private boolean inLoad = false;

  /**
   * Legacy constructor, now deprecated
   *
   * @param lx LX instance
   * @deprecated Use flags-constructor
   */
  @Deprecated
  protected LXPreferences(LX lx) {
    this(lx.flags);
    setLX(lx);
  }

  public LXPreferences(LX.Flags flags) {
    this.file = new File(flags.mediaPath, PREFERENCES_FILE_NAME);
    this.eulaAccepted.addListener(this);
    this.focusChannelOnCue.addListener(this);
    this.focusActivePattern.addListener(this);
    this.sendCueToOutput.addListener(this);
    this.suppressOutput.addListener(this);
    this.oscQuery.addListener(this);
    this.uiZoom.addListener(this);
    this.uiTheme.addListener(this);
    this.showHelpMessages.addListener(this);
    this.schedulerEnabled.addListener(this);
    this.showCpuLoad.addListener(this);
    this.autoReloadPackages.addListener(this);
    this.scrollSensitivity.addListener(this);
  }

  public void setLX(LX lx) {
    this.lx = lx;
    lx.registry.addListener(new LXRegistry.Listener() {
      @Override
      public void pluginChanged(LX lx, LXRegistry.Plugin plugin) {
        save();
      }
    });
  }

  @Override
  public void onParameterChanged(LXParameter p) {
    if (this.lx == null) {
      throw new IllegalStateException("LXPreferences.onParameterChanged() invoked before LX instance was set");
    }
    if ((p == this.oscQuery) && !this.lx.flags.zeroconfForce) {
      this.lx.flags.zeroconf = this.oscQuery.isOn();
    }
    this.lx.flags.focusChannelOnCue = this.focusChannelOnCue.isOn();
    this.lx.flags.focusActivePattern = this.focusActivePattern.isOn();
    this.lx.flags.sendCueToOutput = this.sendCueToOutput.isOn();
    this.lx.flags.scrollMultiplier = this.scrollSensitivity.getValuef();
    save();
  }

  public void setWindowSettings(Window window, int windowWidth, int windowHeight, int windowPosX, int windowPosY) {
    getWindowSettings(window).setSize(windowWidth, windowHeight);
    getWindowSettings(window).setPosition(windowPosX, windowPosY);
    save();
  }

  public void setWindowPosition(Window window, int windowPosX, int windowPosY) {
    getWindowSettings(window).setPosition(windowPosX, windowPosY);
    save();
  }

  protected void setProject(File project) {
    if (project != null) {
      this.projectFileName = this.lx.getMediaPath(LX.Media.PROJECTS, project);
      this.recentProjects.remove(this.projectFileName);
      while (this.recentProjects.size() >= MAX_RECENT_PROJECTS) {
        this.recentProjects.remove(this.recentProjects.size()-1);
      }
      this.recentProjects.add(0, this.projectFileName);
    } else {
      this.projectFileName = null;
    }
    save();
  }

  public void setSchedule(File schedule) {
    if (schedule != null) {
      this.scheduleFileName = this.lx.getMediaPath(LX.Media.PROJECTS, schedule);
    } else {
      this.scheduleFileName = null;
    }
    save();
  }

  private static final String KEY_VERSION = "version";
  private static final String KEY_EULA_ACCEPTED = "eulaAccepted";
  private static final String KEY_PROJECT_FILE_NAME = "projectFileName";
  private static final String KEY_SCHEDULE_FILE_NAME = "scheduleFileName";
  private static final String KEY_RECENT_PROJECTS = "recentProjects";
  private static final String KEY_WINDOW_WIDTH = "windowWidth";
  private static final String KEY_WINDOW_WIDTH_LEGACY = "windwWidth";
  private static final String KEY_WINDOW_HEIGHT = "windowHeight";
  private static final String KEY_WINDOW_POS_X = "windowPosX";
  private static final String KEY_WINDOW_POS_Y = "windowPosY";
  private static final String KEY_UI_ZOOM = "uiZoom";
  private static final String KEY_UI_THEME = "uiTheme";
  private static final String KEY_FOCUS_CHANNEL_ON_CUE = "focusChannelOnCue";
  private static final String KEY_FOCUS_ACTIVE_PATTERN = "focusActivePattern";
  private static final String KEY_SEND_CUE_TO_OUTPUT = "sendCueToOutput";
  private static final String KEY_SUPPRESS_OUTPUT = "suppressOutput";
  private static final String KEY_OSC_QUERY = "oscQuery";
  private static final String KEY_SHOW_HELP_MESSAGES = "showHelpMessages";
  private static final String KEY_SCHEDULER_ENABLED = "schedulerEnabled";
  private static final String KEY_SHOW_CPU_LOAD = "showCpuLoad";
  private static final String KEY_AUTO_RELOAD_PACKAGES = "autoReloadPackages";
  private static final String KEY_SCROLL_SENSITIVITY = "scrollSensitivity";
  private static final String KEY_WINDOWS = "windows";
  private static final String KEY_REGISTRY = "registry";

  @Override
  public void save(LX lx, JsonObject object) {
    object.addProperty(KEY_VERSION, LX.VERSION);
    if (this.projectFileName != null) {
      object.addProperty(KEY_PROJECT_FILE_NAME, this.projectFileName);
    }
    if (this.scheduleFileName != null) {
      object.addProperty(KEY_SCHEDULE_FILE_NAME, this.scheduleFileName);
    }
    final JsonArray recentProjectsArr = new JsonArray();
    for (String recentProject : this.recentProjects) {
      recentProjectsArr.add(recentProject);
    }
    object.add(KEY_RECENT_PROJECTS, recentProjectsArr);
    object.addProperty(KEY_EULA_ACCEPTED, this.eulaAccepted.isOn());
    object.addProperty(KEY_UI_ZOOM, this.uiZoom.getValuei());
    object.addProperty(KEY_UI_THEME, this.uiTheme.getString());
    object.addProperty(KEY_FOCUS_CHANNEL_ON_CUE, this.focusChannelOnCue.isOn());
    object.addProperty(KEY_FOCUS_ACTIVE_PATTERN, this.focusActivePattern.isOn());
    object.addProperty(KEY_SEND_CUE_TO_OUTPUT, this.sendCueToOutput.isOn());
    object.addProperty(KEY_SUPPRESS_OUTPUT, this.suppressOutput.isOn());
    object.addProperty(KEY_OSC_QUERY, this.oscQuery.isOn());
    object.addProperty(KEY_SHOW_HELP_MESSAGES, this.showHelpMessages.isOn());
    object.addProperty(KEY_SCHEDULER_ENABLED, this.schedulerEnabled.isOn());
    object.addProperty(KEY_SHOW_CPU_LOAD, this.showCpuLoad.isOn());
    object.addProperty(KEY_AUTO_RELOAD_PACKAGES, this.autoReloadPackages.isOn());
    object.addProperty(KEY_SCROLL_SENSITIVITY, this.scrollSensitivity.getValue());

    final JsonObject windows = new JsonObject();
    windows.add(Window.MAIN.key, LXSerializable.Utils.toObject(this.lx, this.windowSettingsMain));
    windows.add(Window.ALT.key, LXSerializable.Utils.toObject(this.lx, this.windowSettingsAlt));
    object.add(KEY_WINDOWS, windows);

    object.add(KEY_REGISTRY, LXSerializable.Utils.toObject(this.lx, this.lx.registry));
  }

  @Override
  public void load(LX lx, JsonObject object) {
    LXSerializable.Utils.loadBoolean(this.eulaAccepted, object, KEY_EULA_ACCEPTED);
    LXSerializable.Utils.loadBoolean(this.focusChannelOnCue, object, KEY_FOCUS_CHANNEL_ON_CUE);
    LXSerializable.Utils.loadBoolean(this.focusActivePattern, object, KEY_FOCUS_ACTIVE_PATTERN);
    LXSerializable.Utils.loadBoolean(this.sendCueToOutput, object, KEY_SEND_CUE_TO_OUTPUT);
    LXSerializable.Utils.loadBoolean(this.suppressOutput, object, KEY_SUPPRESS_OUTPUT);
    LXSerializable.Utils.loadBoolean(this.oscQuery, object, KEY_OSC_QUERY);
    LXSerializable.Utils.loadBoolean(this.showHelpMessages, object, KEY_SHOW_HELP_MESSAGES);
    LXSerializable.Utils.loadBoolean(this.schedulerEnabled, object, KEY_SCHEDULER_ENABLED);
    LXSerializable.Utils.loadBoolean(this.showCpuLoad, object, KEY_SHOW_CPU_LOAD);
    LXSerializable.Utils.loadBoolean(this.autoReloadPackages, object, KEY_AUTO_RELOAD_PACKAGES);
    LXSerializable.Utils.loadInt(this.uiZoom, object, KEY_UI_ZOOM);
    LXSerializable.Utils.loadString(this.uiTheme, object, KEY_UI_THEME);
    LXSerializable.Utils.loadDouble(this.scrollSensitivity, object, KEY_SCROLL_SENSITIVITY);
    loadWindowSettings(object);
    if (object.has(KEY_PROJECT_FILE_NAME)) {
      this.projectFileName = object.get(KEY_PROJECT_FILE_NAME).getAsString();
    } else {
      this.projectFileName = null;
    }
    if (object.has(KEY_SCHEDULE_FILE_NAME)) {
      this.scheduleFileName = object.get(KEY_SCHEDULE_FILE_NAME).getAsString();
    } else {
      this.scheduleFileName = null;
    }
    if (object.has(KEY_RECENT_PROJECTS)) {
      final JsonArray recentProjectsArr = object.getAsJsonArray(KEY_RECENT_PROJECTS);
      this.recentProjects.clear();
      for (int i = 0; i < recentProjectsArr.size(); ++i) {
        this.recentProjects.add(recentProjectsArr.get(i).getAsString());
      }
    }
    LXSerializable.Utils.loadObject(this.lx, this.lx.registry, object, KEY_REGISTRY);
  }

  private void loadWindowSettings(JsonObject object) {
    boolean foundMain = false;
    if (object.has(KEY_WINDOWS)) {
      final JsonObject windows = object.getAsJsonObject(KEY_WINDOWS);
      for (Window window : Window.values()) {
        if (windows.has(window.key)) {
          getWindowSettings(window).load(this.lx, windows.getAsJsonObject(window.key));
          if (window == Window.MAIN) {
            foundMain = true;
          }
        }
      }
    }

    // Check for window settings in legacy format
    if (!foundMain) {
      if ((object.has(KEY_WINDOW_WIDTH) || object.has(KEY_WINDOW_WIDTH_LEGACY)) && object.has(KEY_WINDOW_HEIGHT)) {
        int width = object.has(KEY_WINDOW_WIDTH) ? object.get(KEY_WINDOW_WIDTH).getAsInt() : object.get(KEY_WINDOW_WIDTH_LEGACY).getAsInt();
        int height = object.get(KEY_WINDOW_HEIGHT).getAsInt();
        this.windowSettingsMain.setSize(width, height);
      }
      if (object.has(KEY_WINDOW_POS_X) && object.has(KEY_WINDOW_POS_Y)) {
        int x = object.get(KEY_WINDOW_POS_X).getAsInt();
        int y = object.get(KEY_WINDOW_POS_Y).getAsInt();
        this.windowSettingsMain.setPosition(x, y);
      }
    }
  }

  private void save() {
    // Don't re-save the file on updates caused by loading it...
    if (this.inLoad) {
      return;
    }

    JsonObject obj = new JsonObject();
    save(this.lx, obj);
    try (JsonWriter writer = new JsonWriter(new FileWriter(this.file))) {
      writer.setIndent("  ");
      new GsonBuilder().create().toJson(obj, writer);
    } catch (IOException iox) {
      LX.error(iox, "Exception writing the preferences file: " + this.file);
    }
  }

  public void loadWindowSettings() {
    this.inLoad = true;
    if (this.file.exists()) {
      try (FileReader fr = new FileReader(this.file)) {
        loadWindowSettings(new Gson().fromJson(fr, JsonObject.class));
      } catch (Exception x) {
        LX.error(x, "Exception loading window settings from file: " + this.file);
      }
    }
    this.inLoad = false;
  }

  public void loadEULA() {
    this.inLoad = true;
    if (this.file.exists()) {
      try (FileReader fr = new FileReader(this.file)) {
        LXSerializable.Utils.loadBoolean(this.eulaAccepted, new Gson().fromJson(fr, JsonObject.class), KEY_EULA_ACCEPTED);
      } catch (Exception x) {
        LX.error(x, "Exception loading EULA state file: " + this.file);
      }
    }
    this.inLoad = false;
  }

  public void load() {
    this.inLoad = true;
    if (this.file.exists()) {
      try (FileReader fr = new FileReader(this.file)) {
        // Load parameters and settings from file
        load(this.lx, new Gson().fromJson(fr, JsonObject.class));
      } catch (Exception x) {
        LX.error(x, "Exception loading preferences file: " + this.file);
      }
    }
    this.inLoad = false;
  }

  public void loadInitialProject(File overrideProjectFile) {
    loadInitialProject(overrideProjectFile, null);
  }

  public void loadInitialProject(File overrideProjectFile, File fallbackProjectFile) {
    try {
      File projectFile = null;
      if (overrideProjectFile != null) {
        projectFile = overrideProjectFile;
        if (!projectFile.exists()) {
          LX.error("Project file does not exist: " + overrideProjectFile);
          projectFile = null;
        }
      } else if (this.projectFileName != null) {
        projectFile = this.lx.getMediaFile(LX.Media.PROJECTS, this.projectFileName);
        if (!projectFile.exists()) {
          LX.error("Last saved project file no longer exists: " + this.projectFileName);
          projectFile = null;
        }
      }

      // Nothing found?
      if (projectFile == null) {
        // Try an explicit fallback file
        if ((fallbackProjectFile != null) && fallbackProjectFile.exists()) {
          projectFile = fallbackProjectFile;
        }
        // Or fall back to default project file...
        if (projectFile == null) {
          projectFile = this.lx.getMediaFile(LX.Media.PROJECTS, DEFAULT_PROJECT_FILE);
        }
      }

      if (projectFile.exists()) {
        LX.log("Opening project file: " + projectFile);
        this.lx.openProject(projectFile);
      }
    } catch (Exception x) {
      LX.error(x, "Unhandled exception loading initial project");
    }
  }

  public void loadInitialSchedule() {
    if (this.scheduleFileName != null) {
      File scheduleFile = this.lx.getMediaFile(LX.Media.PROJECTS, this.scheduleFileName);
      if (!scheduleFile.exists()) {
        LX.error("Last saved schedule file no longer exists: " + this.scheduleFileName);
      } else {
        LX.log("Restoring schedule file: " + this.scheduleFileName);
        this.lx.scheduler.openSchedule(scheduleFile);
      }
    }
  }

}
