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

import com.google.gson.JsonObject;

import heronarts.lx.LX;
import heronarts.lx.LXSerializable;
import heronarts.lx.audio.LXAudioBuffer;
import heronarts.lx.audio.LXAudioTimeline;
import heronarts.lx.parameter.StringParameter;

import java.io.File;
import java.io.IOException;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

/**
 * A composition event that plays back an audio file
 */
public class AudioClipEvent extends LXCompositionEvent<AudioClipEvent> {

  public final AudioClipLane lane;

  private File file;
  private final Cursor sourceLength = new Cursor();
  public final Cursor playbackOffset = new Cursor();

  // Waveform data for UI display (original format)
  private float[] waveformData;
  private boolean waveformDataLoaded = false;

  // Playback data, uses LXAudioTimeline format
  private float[] playbackData = null;
  private boolean playbackDataLoaded = false;

  public final StringParameter fileName =
    new StringParameter("File Name")
    .setDescription("Display name of the audio file");

  public final StringParameter filePath =
    new StringParameter("File Path")
    .setDescription("Absolute path to the audio file on the local machine");


  AudioClipEvent(LX lx, AudioClipLane lane) {
    this(lx, lane, null);
  }

  AudioClipEvent(LX lx, AudioClipLane lane, File file) {
    super(lane);
    this.lane = lane;
    if (file != null) {
      setFile(file);
    }
  }

  // Initialization

  public AudioClipEvent setFile(File file) {
    releaseSampleData();
    this.file = file;
    this.filePath.setValue(this.file.getAbsolutePath());
    this.fileName.setValue(this.file.getName());

    try (AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(this.file)) {
      AudioFormat format = audioInputStream.getFormat();
      long frames = audioInputStream.getFrameLength();
      double durationMs = (frames / format.getFrameRate()) * 1000;

      this.sourceLength.set(this.lane.clip.constructAbsoluteCursor(durationMs));
      setLength(this.sourceLength);
    } catch (UnsupportedAudioFileException | IOException e) {
      LX.error(e,
        "Failed to read audio file duration: " + this.file.getAbsolutePath());
    }

    return this;
  }

  /**
   * Load file data in native format for visual display
   */
  private float[] loadWaveform() {
    try (AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(this.file)) {
      AudioFormat format = audioInputStream.getFormat();
      AudioFormat pcmFormat = new AudioFormat(
        AudioFormat.Encoding.PCM_SIGNED,
        format.getSampleRate(),
        16,
        format.getChannels(),
        format.getChannels() * 2, // 2 bytes per sample per channel (16-bit)
        format.getSampleRate(),
        false
      );
      return loadAudioSamples(pcmFormat);
    } catch (UnsupportedAudioFileException | IOException e) {
      LX.error(e, "Failed to read waveform data: " + this.file.getAbsolutePath());
      return NO_DATA;
    }
  }

  /**
   * Load file data formatted for playback (currently 44.1kHz)
   */
  private float[] loadPlaybackData() {
    return loadAudioSamples(LXAudioTimeline.AUDIO_OUTPUT_FORMAT);
  }

  private float[] loadAudioSamples(AudioFormat targetFormat) {
    if (targetFormat.getSampleSizeInBits() != 16) {
      throw new IllegalArgumentException("AudioClipEvent can only load 16-bit sample data");
    }
    try (AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(this.file)) {
      AudioInputStream pcmStream = AudioSystem.getAudioInputStream(targetFormat, audioInputStream);
      byte[] audioBytes = pcmStream.readAllBytes();
      int numSamples = audioBytes.length / 2; // 2 bytes per 16-bit sample
      float[] samples = new float[numSamples];

      // Convert bytes to normalized float values
      for (int i = 0; i < numSamples; i++) {
        int sample = (audioBytes[i * 2 + 1] << 8) | (audioBytes[i * 2] & 0xFF);
        samples[i] = sample * LXAudioBuffer.INV_16_BIT; // Normalize to -1.0 to 1.0
      }

      return samples;

    } catch (UnsupportedAudioFileException | IOException e) {
      LX.error(e, "Failed to load audio data: " + this.file.getAbsolutePath());
      return null;
    }
  }

  // Accessors

  public String getLabel() {
    return this.fileName.getString();
  }

  public double getSourceLengthMs() {
    return this.sourceLength.getMillis();
  }

  /**
   * Retrieve the waveform data for visual display.
   */
  public float[] getWaveform() {
    if (this.file == null || !this.file.exists()) {
      return null;
    }
    if (this.waveformData == null) {
      this.waveformData = loadWaveform();
    }
    return this.waveformData;
  }

  private static final float[] NO_DATA = new float[0];

  /**
   * Retrieve the decoded playback data for this audio event. Data is stereo interleaved
   * (L,R,L,R) at 44.1kHz, normalized to -1.0 to 1.0. Lazy-loaded on first access.
   *
   * TODO(mcslee): consider refactoring this to use some kind of seekable read or placing
   * limits on number and size of audio lanes, this potentially loads masses of WAV data
   * into memory
   *
   * @return Stereo interleaved float samples, or empty array if file unavailable
   */
  public float[] getPlaybackData() {
    if (this.file == null || !this.file.exists()) {
      return NO_DATA;
    }
    if (!this.playbackDataLoaded) {
      this.playbackData = loadPlaybackData();
      this.playbackDataLoaded = true;
    }
    return this.playbackData;
  }

  @Override
  public void execute() {}

  // Resizing

  /**
   * Moves the event start to the position that results in zero playbackOffset,
   * keeping the end fixed. This aligns the event start with the audio file start.
   */
  public void resetPlaybackOffset() {
    Cursor newStart, newLength;
    if (CursorOp().isAfter(this.playbackOffset, this.cursor)) {
      newStart = Cursor.ZERO;
      newLength = this.length.add(this.cursor);
    } else {
      newStart = this.cursor.subtract(this.playbackOffset);
      newLength = this.length.add(this.playbackOffset);
    }

    this.playbackOffset.set(Cursor.ZERO);
    setCursor(newStart, newLength);

    this.lane.onChange.bang();
  }

  /**
   * Move event start without moving the end (trims audio beginning).
   * Adjusts playbackOffset so audio alignment on the parent composition remains the same.
   *
   * @param start new value for the event cursor, in composition time
   */
  @Override
  public void setEventStart(Cursor start) {
    final Cursor.Operator c = CursorOp();
    c.constrain(start, this.cursor.subtract(this.playbackOffset), this.end.subtract(Cursor.MIN_LOOP));

    // Adjust playback offset by the same delta
    if (c.isAfter(start, this.cursor)) {
      Cursor delta = start.subtract(this.cursor);
      this.playbackOffset.advance(delta);
    } else if (c.isBefore(start, this.cursor)) {
      Cursor delta = this.cursor.subtract(start);
      this.playbackOffset.rewind(delta);
    }

    // Apply new start position
    Cursor newLength = this.end.subtract(start);
    setCursor(start, newLength);

    this.lane.onChange.bang();
  }

  /**
   * Move event end without moving the start (trims audio end)
   *
   * @param endCursor new value for the end cursor, in composition time
   */
  @Override
  public void setEventEnd(Cursor endCursor) {
    final Cursor.Operator c = CursorOp();

    // Enforce minimum length, and don't go past end of file
    endCursor = c.constrain(
      endCursor,
      this.cursor.add(Cursor.MIN_LOOP),
      this.cursor.add(this.sourceLength).subtract(this.playbackOffset)
    );

    // Simply adjust the event length
    setLength(endCursor.subtract(this.cursor));
    this.lane.onChange.bang();
  }

  // Disposal

  private void releaseSampleData() {
    if (this.waveformDataLoaded) {
      this.waveformDataLoaded = false;
      this.waveformData = null;
    }
    if (this.playbackDataLoaded) {
      this.playbackDataLoaded = false;
      this.playbackData = null;
    }
  }

  public void dispose() {
    releaseSampleData();
  }

  // Serialization

  private static final String KEY_FILE_NAME = "fileName";
  private static final String KEY_FILE_PATH = "filePath";
  private static final String KEY_PLAYBACK_OFFSET = "playbackOffset";

  @Override
  public void save(LX lx, JsonObject obj) {
    super.save(lx, obj);
    obj.addProperty(KEY_FILE_NAME, this.fileName.getString());
    obj.addProperty(KEY_FILE_PATH, this.filePath.getString());
    obj.add(KEY_PLAYBACK_OFFSET, LXSerializable.Utils.toObject(lx, this.playbackOffset));
  }

  @Override
  public void load(LX lx, JsonObject obj) {
    super.load(lx, obj);
    if (obj.has(KEY_FILE_NAME)) {
      this.fileName.setValue(obj.get(KEY_FILE_NAME).getAsString());
    }
    if (obj.has(KEY_FILE_PATH)) {
      String path = obj.get(KEY_FILE_PATH).getAsString();
      this.filePath.setValue(path);
      if (path != null && !path.isEmpty()) {
        File file = new File(path);
        if (file.exists()) {
          setFile(file);
        }
      }
    }
    if (obj.has(KEY_PLAYBACK_OFFSET)) {
      this.playbackOffset.load(lx, obj.getAsJsonObject(KEY_PLAYBACK_OFFSET));
    }
  }
}
