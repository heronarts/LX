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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonWriter;

import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.LXParameterListener;
import heronarts.lx.parameter.StringParameter;

public class LXPreferences implements LXSerializable, LXParameterListener {

  private static final String PREFERENCES_FILE_NAME = ".lxpreferences";
  private static final String DEFAULT_PROJECT_FILE = "default.lxp";

  private final LX lx;

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

  public final StringParameter uiTheme =
    new StringParameter("UI Theme", null)
    .setDescription("Which UI theme is used");

  private String projectFileName = null;
  private String scheduleFileName = null;

  private int windowWidth = -1;
  private int windowHeight = -1;
  private int windowPosX = -1;
  private int windowPosY = -1;

  private boolean inLoad = false;

  protected LXPreferences(LX lx) {
    this.lx = lx;
    this.file = lx.getMediaFile(PREFERENCES_FILE_NAME);
    this.eulaAccepted.addListener(this);
    this.focusChannelOnCue.addListener(this);
    this.focusActivePattern.addListener(this);
    this.sendCueToOutput.addListener(this);
    this.uiZoom.addListener(this);
    this.uiTheme.addListener(this);
    this.showHelpMessages.addListener(this);
    this.schedulerEnabled.addListener(this);
    this.showCpuLoad.addListener(this);

    lx.registry.addListener(new LXRegistry.Listener() {
      @Override
      public void pluginChanged(LX lx, LXRegistry.Plugin plugin) {
        save();
      }
    });
  }

  @Override
  public void onParameterChanged(LXParameter p) {
    // Update LX parameter flags
    this.lx.flags.focusChannelOnCue = this.focusChannelOnCue.isOn();
    this.lx.flags.focusActivePattern = this.focusActivePattern.isOn();
    this.lx.flags.sendCueToOutput = this.sendCueToOutput.isOn();
    save();
  }

  public int getWindowWidth() {
    return this.windowWidth;
  }

  public int getWindowHeight() {
    return this.windowHeight;
  }

  public void setWindowSize(int windowWidth, int windowHeight) {
    this.windowWidth = windowWidth;
    this.windowHeight = windowHeight;
    save();
  }

  public void setWindowSize(int windowWidth, int windowHeight, int windowPosX, int windowPosY) {
    this.windowWidth = windowWidth;
    this.windowHeight = windowHeight;
    this.windowPosX = windowPosX;
    this.windowPosY = windowPosY;
    save();
  }

  public int getWindowPosX() {
    return this.windowPosX;
  }

  public int getWindowPosY() {
    return this.windowPosY;
  }

  public void setWindowPosition(int windowPosX, int windowPosY) {
    this.windowPosX = windowPosX;
    this.windowPosY = windowPosY;
    save();
  }

  protected void setProject(File project) {
    if (project != null) {
      this.projectFileName = this.lx.getMediaPath(LX.Media.PROJECTS, project);
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
  private static final String KEY_SHOW_HELP_MESSAGES = "showHelpMessages";
  private static final String KEY_SCHEDULER_ENABLED = "schedulerEnabled";
  private static final String KEY_SHOW_CPU_LOAD = "showCpuLoad";
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
    object.addProperty(KEY_EULA_ACCEPTED, this.eulaAccepted.isOn());
    object.addProperty(KEY_WINDOW_WIDTH, this.windowWidth);
    object.addProperty(KEY_WINDOW_HEIGHT, this.windowHeight);
    object.addProperty(KEY_WINDOW_POS_X, this.windowPosX);
    object.addProperty(KEY_WINDOW_POS_Y, this.windowPosY);
    object.addProperty(KEY_UI_ZOOM, this.uiZoom.getValuei());
    object.addProperty(KEY_UI_THEME, this.uiTheme.getString());
    object.addProperty(KEY_FOCUS_CHANNEL_ON_CUE, this.focusChannelOnCue.isOn());
    object.addProperty(KEY_FOCUS_ACTIVE_PATTERN, this.focusActivePattern.isOn());
    object.addProperty(KEY_SEND_CUE_TO_OUTPUT, this.sendCueToOutput.isOn());
    object.addProperty(KEY_SHOW_HELP_MESSAGES, this.showHelpMessages.isOn());
    object.addProperty(KEY_SCHEDULER_ENABLED, this.schedulerEnabled.isOn());
    object.addProperty(KEY_SHOW_CPU_LOAD, this.showCpuLoad.isOn());

    object.add(KEY_REGISTRY, LXSerializable.Utils.toObject(this.lx, this.lx.registry));
  }

  @Override
  public void load(LX lx, JsonObject object) {
    LXSerializable.Utils.loadBoolean(this.eulaAccepted, object, KEY_EULA_ACCEPTED);
    LXSerializable.Utils.loadBoolean(this.focusChannelOnCue, object, KEY_FOCUS_CHANNEL_ON_CUE);
    LXSerializable.Utils.loadBoolean(this.focusActivePattern, object, KEY_FOCUS_ACTIVE_PATTERN);
    LXSerializable.Utils.loadBoolean(this.sendCueToOutput, object, KEY_SEND_CUE_TO_OUTPUT);
    LXSerializable.Utils.loadBoolean(this.showHelpMessages, object, KEY_SHOW_HELP_MESSAGES);
    LXSerializable.Utils.loadBoolean(this.schedulerEnabled, object, KEY_SCHEDULER_ENABLED);
    LXSerializable.Utils.loadBoolean(this.showCpuLoad, object, KEY_SHOW_CPU_LOAD);
    LXSerializable.Utils.loadInt(this.uiZoom, object, KEY_UI_ZOOM);
    LXSerializable.Utils.loadString(this.uiTheme, object, KEY_UI_THEME);
    if (object.has(KEY_WINDOW_WIDTH)) {
      this.windowWidth = object.get(KEY_WINDOW_WIDTH).getAsInt();
    } else if (object.has(KEY_WINDOW_WIDTH_LEGACY)) {
      this.windowWidth = object.get(KEY_WINDOW_WIDTH_LEGACY).getAsInt();
    }
    if (object.has(KEY_WINDOW_HEIGHT)) {
      this.windowHeight = object.get(KEY_WINDOW_HEIGHT).getAsInt();
    }
    if (object.has(KEY_WINDOW_POS_X)) {
      this.windowPosX = object.get(KEY_WINDOW_POS_X).getAsInt();
    }
    if (object.has(KEY_WINDOW_POS_Y)) {
      this.windowPosY = object.get(KEY_WINDOW_POS_Y).getAsInt();
    }
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
    LXSerializable.Utils.loadObject(this.lx, this.lx.registry, object, KEY_REGISTRY);
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
      // Fall back to default project file...
      if (projectFile == null) {
        projectFile = this.lx.getMediaFile(LX.Media.PROJECTS, DEFAULT_PROJECT_FILE);
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
