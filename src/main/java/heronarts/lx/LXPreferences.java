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
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.LXParameterListener;

public class LXPreferences implements LXSerializable, LXParameterListener, LX.Listener {

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

  private String projectFileName = null;
  private float uiWidth = -1;
  private float uiHeight = -1;

  private boolean inLoad = false;

  protected LXPreferences(LX lx) {
    this.lx = lx;
    this.file = lx.getMediaFile(PREFERENCES_FILE_NAME);
    this.focusChannelOnCue.addListener(this);
    this.focusActivePattern.addListener(this);
    this.sendCueToOutput.addListener(this);

    lx.addListener(this);
  }

  @Override
  public void onParameterChanged(LXParameter p) {
    // Update LX parameter flags
    this.lx.flags.focusChannelOnCue = this.focusChannelOnCue.isOn();
    this.lx.flags.focusActivePattern = this.focusActivePattern.isOn();
    this.lx.flags.sendCueToOutput = this.sendCueToOutput.isOn();
    save();
  }

  @Override
  public void pluginChanged(LX lx, LXClassLoader.Plugin plugin) {
    save();
  }

  public float getUIWidth() {
    return this.uiWidth;
  }

  public float getUIHeight() {
    return this.uiHeight;
  }

  public void setUISize(float uiWidth, float uiHeight) {
    this.uiWidth = uiWidth;
    this.uiHeight = uiHeight;
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

  private static final String KEY_PROJECT_FILE_NAME = "projectFileName";
  private static final String KEY_UI_WIDTH = "uiWidth";
  private static final String KEY_UI_HEIGHT = "uiHeight";
  private static final String KEY_FOCUS_CHANNEL_ON_CUE = "focusChannelOnCue";
  private static final String KEY_FOCUS_ACTIVE_PATTERN = "focusActivePattern";
  private static final String KEY_SEND_CUE_TO_OUTPUT = "sendCueToOutput";
  private static final String KEY_CONTENT_LOADER = "contentLoader";

  @Override
  public void save(LX lx, JsonObject object) {
    if (this.projectFileName != null) {
      object.addProperty(KEY_PROJECT_FILE_NAME, this.projectFileName);
    }
    object.addProperty(KEY_UI_WIDTH, this.uiWidth);
    object.addProperty(KEY_UI_HEIGHT, this.uiHeight);
    object.addProperty(KEY_FOCUS_CHANNEL_ON_CUE, this.focusChannelOnCue.isOn());
    object.addProperty(KEY_FOCUS_ACTIVE_PATTERN, this.focusActivePattern.isOn());
    object.addProperty(KEY_SEND_CUE_TO_OUTPUT, this.sendCueToOutput.isOn());
    object.add(KEY_CONTENT_LOADER, LXSerializable.Utils.toObject(this.lx, this.lx.contentLoader));

  }

  @Override
  public void load(LX lx, JsonObject object) {
    LXSerializable.Utils.loadBoolean(this.focusChannelOnCue, object, KEY_FOCUS_CHANNEL_ON_CUE);
    LXSerializable.Utils.loadBoolean(this.focusActivePattern, object, KEY_FOCUS_ACTIVE_PATTERN);
    LXSerializable.Utils.loadBoolean(this.sendCueToOutput, object, KEY_SEND_CUE_TO_OUTPUT);
    if (object.has(KEY_UI_WIDTH)) {
      this.uiWidth = object.get(KEY_UI_WIDTH).getAsInt();
    }
    if (object.has(KEY_UI_HEIGHT)) {
      this.uiHeight = object.get(KEY_UI_HEIGHT).getAsInt();
    }
    if (object.has(KEY_PROJECT_FILE_NAME)) {
      this.projectFileName = object.get(KEY_PROJECT_FILE_NAME).getAsString();
    } else {
      this.projectFileName = null;
    }
    LXSerializable.Utils.loadObject(this.lx, this.lx.contentLoader, object, KEY_CONTENT_LOADER);
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

}