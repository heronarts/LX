/**
 * Copyright 2026Mark C. Slee, Heron Arts LLC
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

package heronarts.lx.parameter;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;

import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import heronarts.lx.clip.AudioClipEvent;
import heronarts.lx.clip.AudioClipLane;
import heronarts.lx.clip.LXTimelineEngine;
import heronarts.lx.utils.LXUtils;

public class MediaPathParameter extends StringParameter {

  private final LX lx;

  public MediaPathParameter(LX lx, String label, String string) {
    super(label, string);
    this.lx = lx;
  }

  public MediaPathParameter(LX lx, String label) {
    super(label);
    this.lx = lx;
  }

  @Override
  public MediaPathParameter setDescription(String description) {
    super.setDescription(description);
    return this;
  }

  @Override
  public MediaPathParameter setValue(String path) {
    if (!LXUtils.isEmpty(path)) {
      path = this.lx.getMediaPath(new File(path));
    }
    super.setValue(path);
    return this;
  }

  public File getMediaFile() {
    final String path = getString();
    return (LXUtils.isEmpty(path)) ? null : this.lx.getMediaFile(getString());
  }

  public String getAbsoluteMediaPath() {
    final File file = getMediaFile();
    return (file == null) ? null : file.getAbsolutePath();
  }

  public static void consolidateProjectMedia(LX lx, File mediaFolder, LXComponent component) {
    if (component == null) {
      return;
    }
    for (LXParameter p : component.getParameters()) {
      if (p instanceof MediaPathParameter mediaParameter) {
        consolidateProjectMedia(lx, mediaFolder, mediaParameter);
      }
    }
    // NOTE(mcslee): Fugly hack while composition is not a first-class child
    if (component instanceof LXTimelineEngine timeline) {
      consolidateProjectMedia(lx, mediaFolder, timeline.getComposition());
    }
    // NOTE(mcslee): bit of a special-case hack here... but more straightforward than
    // implementing generic hierarchy for all lane event types, none of which need it
    if (component instanceof AudioClipLane audioLane) {
      for (AudioClipEvent event : audioLane.events) {
        consolidateProjectMedia(lx, mediaFolder, event.filePath);
      }
    }
    for (LXComponent child : component.children.values()) {
      consolidateProjectMedia(lx, mediaFolder, child);
    }
    for (List<? extends LXComponent> childArr : component.childArrays.values()) {
      childArr.forEach(child -> consolidateProjectMedia(lx, mediaFolder, child));
    }
  }

  public static void consolidateProjectMedia(LX lx, File mediaFolder, MediaPathParameter media) {
    final String path = media.getString();
    if (LXUtils.isEmpty(path)) {
      return;
    }
    File source = new File(path);
    if (!source.isAbsolute()) {
      return;
    }
    source = media.getMediaFile();
    if (!source.exists()) {
      LX.error("Media file does not exist: " + source + " - " + media.getCanonicalPath());
      return;
    }
    final String sourceName = source.getName();
    File target = new File(mediaFolder, sourceName);
    boolean needsCopy = true;
    int counter = 1;
    while (target.exists()) {
      try {
        if (Files.mismatch(source.toPath(), target.toPath()) < 0) {
          // Use this target, it's the same file, no need to copy again
          LX.log("Re-using media file: " + target);
          needsCopy = false;
          break;
        }
        target = new File(mediaFolder, fileNameVersioned(sourceName, counter++));
      } catch (Exception x) {
        LX.error("Failed to compare target file: " + target);
        target = null;
        break;
      }
    }
    if (target != null) {
      try {
        if (needsCopy) {
          Files.copy(source.toPath(), target.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
          LX.log("Copied media file: " + target);
        }
        media.setValue(target.getPath());
      } catch (Exception x) {
        lx.pushError(x, "Could not copy media file: " + source, true);
      }
    }
  }

  private static String fileNameVersioned(String name, int version) {
    final int dot = name.lastIndexOf(".");
    return (dot < 0) ?
      (name + "." + version) :
      (name.substring(0, dot) + "." + version + name.substring(dot));
  }
}
