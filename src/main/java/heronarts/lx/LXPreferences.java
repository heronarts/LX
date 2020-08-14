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

public class LXPreferences implements LXSerializable, LXParameterListener {

  private static final String PREFERENCES_FILE_NAME = ".lxpreferences";
  private static final String DEFAULT_PROJECT_FILE = "default.lxp";

  private final LX lx;

  private final File file;

  public final BooleanParameter focusChannelOnCue =
    new BooleanParameter("Focus On Cue", false)
    .setDescription("Whether a channel should be automatically focused when its cue is set to active");

  public final BooleanParameter focusActivePattern =
    new BooleanParameter("Auto-Focus Pattern", false)
    .setDescription("Whether a pattern should be automatically focused when it becomes active");

  public final BooleanParameter sendCueToOutput =
    new BooleanParameter("Cue applies to Live Output", false)
    .setDescription("Whether Cue selection applies to live output, not just the preview window");

  public final DiscreteParameter uiZoom = (DiscreteParameter)
    new DiscreteParameter("UI Scale", 100, 100, 201)
    .setDescription("Percentage by which the UI should be scaled")
    .setUnits(LXParameter.Units.PERCENT)
    .setMappable(false);

  public final BooleanParameter showHelpBar =
    new BooleanParameter("Help Bar", true)
    .setDescription("Whether to show a bottom bar on the UI with helpful tips");

  public final BooleanParameter schedulerEnabled =
    new BooleanParameter("Project Scheduler Enabed", false)
    .setDescription("Whether the project scheduler is enabled");

  private String projectFileName = null;
  private String scheduleFileName = null;

  private int windowWidth = -1;
  private int windowHeight = -1;

  private boolean inLoad = false;

  protected LXPreferences(LX lx) {
    this.lx = lx;
    this.file = lx.getMediaFile(PREFERENCES_FILE_NAME);
    this.focusChannelOnCue.addListener(this);
    this.focusActivePattern.addListener(this);
    this.sendCueToOutput.addListener(this);
    this.uiZoom.addListener(this);
    this.showHelpBar.addListener(this);
    this.schedulerEnabled.addListener(this);

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

  public void setWindowSize(int uiWidth, int uiHeight) {
    this.windowWidth = uiWidth;
    this.windowHeight = uiHeight;
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
  private static final String KEY_PROJECT_FILE_NAME = "projectFileName";
  private static final String KEY_SCHEDULE_FILE_NAME = "scheduleFileName";
  private static final String KEY_WINDOW_WIDTH = "windwWidth";
  private static final String KEY_WINDOW_HEIGHT = "windowHeight";
  private static final String KEY_UI_ZOOM = "uiZoom";
  private static final String KEY_FOCUS_CHANNEL_ON_CUE = "focusChannelOnCue";
  private static final String KEY_FOCUS_ACTIVE_PATTERN = "focusActivePattern";
  private static final String KEY_SEND_CUE_TO_OUTPUT = "sendCueToOutput";
  private static final String KEY_SHOW_HELP_BAR = "showHelpBar";
  private static final String KEY_SCHEDULER_ENABLED = "schedulerEnabled";
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
    object.addProperty(KEY_WINDOW_WIDTH, this.windowWidth);
    object.addProperty(KEY_WINDOW_HEIGHT, this.windowHeight);
    object.addProperty(KEY_UI_ZOOM, this.uiZoom.getValuei());
    object.addProperty(KEY_FOCUS_CHANNEL_ON_CUE, this.focusChannelOnCue.isOn());
    object.addProperty(KEY_FOCUS_ACTIVE_PATTERN, this.focusActivePattern.isOn());
    object.addProperty(KEY_SEND_CUE_TO_OUTPUT, this.sendCueToOutput.isOn());
    object.addProperty(KEY_SHOW_HELP_BAR, this.showHelpBar.isOn());
    object.addProperty(KEY_SCHEDULER_ENABLED, this.schedulerEnabled.isOn());

    object.add(KEY_REGISTRY, LXSerializable.Utils.toObject(this.lx, this.lx.registry));
  }

  @Override
  public void load(LX lx, JsonObject object) {
    LXSerializable.Utils.loadBoolean(this.focusChannelOnCue, object, KEY_FOCUS_CHANNEL_ON_CUE);
    LXSerializable.Utils.loadBoolean(this.focusActivePattern, object, KEY_FOCUS_ACTIVE_PATTERN);
    LXSerializable.Utils.loadBoolean(this.sendCueToOutput, object, KEY_SEND_CUE_TO_OUTPUT);
    LXSerializable.Utils.loadBoolean(this.showHelpBar, object, KEY_SHOW_HELP_BAR);
    LXSerializable.Utils.loadBoolean(this.schedulerEnabled, object, KEY_SCHEDULER_ENABLED);
    LXSerializable.Utils.loadInt(this.uiZoom, object, KEY_UI_ZOOM);
    if (object.has(KEY_WINDOW_WIDTH)) {
      this.windowWidth = object.get(KEY_WINDOW_WIDTH).getAsInt();
    }
    if (object.has(KEY_WINDOW_HEIGHT)) {
      this.windowHeight = object.get(KEY_WINDOW_HEIGHT).getAsInt();
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
    try {
      JsonWriter writer = new JsonWriter(new FileWriter(this.file));
      writer.setIndent("  ");
      new GsonBuilder().create().toJson(obj, writer);
      writer.close();
    } catch (IOException iox) {
      LX.error(iox, "Exception writing the preferences file: " + this.file);
    }
  }

  public void load() {
    this.inLoad = true;
    if (this.file.exists()) {
      try {
        FileReader fr = new FileReader(this.file);
        JsonObject obj = new Gson().fromJson(fr, JsonObject.class);

        // Load parameters and settings from file
        load(this.lx, obj);

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
        this.lx.scheduler.openSchedule(scheduleFile);
      }
    }
  }

}
