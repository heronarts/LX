/**
 * Copyright 2025- Justin K. Belcher, Heron Arts LLC
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
 * @author Justin K. Belcher <justin@jkb.studio>
 */

package heronarts.lx.clip;

import heronarts.lx.LX;
import heronarts.lx.audio.LXAudioEngine;

/**
 * Manages audio playback for composition audio lanes via coordination with the LXAudioEngine
 */
class AudioPlayer {

  private final LXAudioEngine audio;

  private boolean playing = false;
  private boolean disposed = false;

  AudioPlayer(LX lx) {
    this.audio = lx.engine.audio;
  }

  /**
   * Ensure the audio output is running. Does nothing if already playing.
   */
  void ensureRunning() {
    if (!this.playing) {
      start();
    }
  }

  /**
   * Start audio playback
   */
  void start() {
    if (this.disposed) {
      return;
    }
    this.playing = true;
    this.audio.mode.setValue(LXAudioEngine.Mode.TIMELINE);
    this.audio.timeline.play.setValue(true);
    this.audio.enabled.setValue(true);
  }

  /**
   * Stop audio playback
   */
  void stop() {
    this.playing = false;
    this.audio.timeline.play.setValue(false);
  }

  /**
   * Close the audio line and terminate the playback thread
   */
  public void dispose() {
    stop();
    this.disposed = true;
  }
}
