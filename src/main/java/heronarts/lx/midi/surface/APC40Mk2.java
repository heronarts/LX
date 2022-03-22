/**
 * Copyright 2017- Mark C. Slee, Heron Arts LLC
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

package heronarts.lx.midi.surface;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import heronarts.lx.LX;
import heronarts.lx.LXDeviceComponent;
import heronarts.lx.clip.LXClip;
import heronarts.lx.color.*;
import heronarts.lx.effect.LXEffect;
import heronarts.lx.midi.LXMidiEngine;
import heronarts.lx.midi.LXMidiInput;
import heronarts.lx.midi.LXMidiOutput;
import heronarts.lx.midi.LXShortMessage;
import heronarts.lx.midi.MidiControlChange;
import heronarts.lx.midi.MidiNote;
import heronarts.lx.midi.MidiNoteOn;
import heronarts.lx.mixer.LXBus;
import heronarts.lx.mixer.LXChannel;
import heronarts.lx.mixer.LXAbstractChannel;
import heronarts.lx.mixer.LXGroup;
import heronarts.lx.mixer.LXMixerEngine;
import heronarts.lx.parameter.*;
import heronarts.lx.pattern.LXPattern;
import heronarts.lx.utils.LXUtils;

public class APC40Mk2 extends LXMidiSurface implements LXMidiSurface.Bidirectional {

  public static final String DEVICE_NAME = "APC40 mkII";

  public static final byte GENERIC_MODE = 0x40;
  public static final byte ABLETON_MODE = 0x41;
  public static final byte ABLETON_ALTERNATE_MODE = 0x42;

  protected static final int LED_STYLE_OFF = 0;
  protected static final int LED_STYLE_SINGLE = 1;
  protected static final int LED_STYLE_UNIPOLAR = 2;
  protected static final int LED_STYLE_BIPOLAR = 3;

  public static final int NUM_CHANNELS = 8;
  public static final int CLIP_LAUNCH_ROWS = 5;
  public static final int CLIP_LAUNCH_COLUMNS = NUM_CHANNELS;
  public static final int PALETTE_SWATCH_ROWS = 5;
  public static final int PALETTE_SWATCH_COLUMNS = NUM_CHANNELS;
  public static final int MASTER_SWATCH = -1;
  public static final int RAINBOW_GRID_COLUMNS = 72;  // Should be a factor of MAX_HUE
  public static final int RAINBOW_GRID_ROWS = 5;

  // How much hue should increase from column to column
  public static final int RAINBOW_HUE_STEP = LXColor.MAX_HUE / RAINBOW_GRID_COLUMNS;

  // Saturation and brightness values to use for each row of the grid when in Rainbow Mode.
  // Each should have RAINBOW_HUE_ROWS elements.
  private static final int[] RAINBOW_GRID_SAT = { 100,  70,  50, 100, 100 };
  private static final int[] RAINBOW_GRID_BRI = { 100, 100, 100,  50,  25 };

  // CCs
  public static final int CHANNEL_FADER = 7;
  public static final int TEMPO = 13;
  public static final int MASTER_FADER = 14;
  public static final int CROSSFADER = 15;
  public static final int CUE_LEVEL = 47;

  public static final int DEVICE_KNOB = 16;
  public static final int DEVICE_KNOB_NUM = 8;
  public static final int DEVICE_KNOB_MAX = DEVICE_KNOB + DEVICE_KNOB_NUM;

  public static final int DEVICE_KNOB_STYLE = 24;
  public static final int DEVICE_KNOB_STYLE_MAX = DEVICE_KNOB_STYLE + DEVICE_KNOB_NUM;

  public static final int CHANNEL_KNOB = 48;
  public static final int CHANNEL_KNOB_NUM = 8;
  public static final int CHANNEL_KNOB_MAX = CHANNEL_KNOB + CHANNEL_KNOB_NUM - 1;

  public static final int CHANNEL_KNOB_STYLE = 56;
  public static final int CHANNEL_KNOB_STYLE_MAX = CHANNEL_KNOB_STYLE + CHANNEL_KNOB_NUM;

  // Notes
  public static final int CLIP_LAUNCH = 0;
  public static final int CLIP_LAUNCH_NUM = 40;
  public static final int CLIP_LAUNCH_MAX = CLIP_LAUNCH + CLIP_LAUNCH_NUM - 1;

  public static final int CHANNEL_ARM = 48;
  public static final int CHANNEL_SOLO = 49;
  public static final int CHANNEL_ACTIVE = 50;
  public static final int CHANNEL_FOCUS = 51;
  public static final int CLIP_STOP = 52;

  public static final int DEVICE_LEFT = 58;
  public static final int DEVICE_RIGHT = 59;
  public static final int BANK_LEFT = 60;
  public static final int BANK_RIGHT = 61;

  public static final int DEVICE_ON_OFF = 62;
  public static final int DEVICE_LOCK = 63;
  public static final int CLIP_DEVICE_VIEW = 64;
  public static final int DETAIL_VIEW = 65;

  public static final int CHANNEL_CROSSFADE_GROUP = 66;
  public static final int MASTER_FOCUS = 80;

  public static final int STOP_ALL_CLIPS = 81;
  public static final int SCENE_LAUNCH = 82;
  public static final int SCENE_LAUNCH_NUM = 5;
  public static final int SCENE_LAUNCH_MAX = SCENE_LAUNCH + SCENE_LAUNCH_NUM - 1;

  public static final int PAN = 87;
  public static final int SENDS = 88;
  public static final int USER = 89;

  public static final int PLAY = 91;
  public static final int RECORD = 93;
  public static final int SESSION = 102;

  public static final int BANK_SELECT_UP = 94;
  public static final int BANK_SELECT_DOWN = 95;
  public static final int BANK_SELECT_RIGHT = 96;
  public static final int BANK_SELECT_LEFT = 97;

  public static final int SHIFT = 98;

  public static final int METRONOME = 90;
  public static final int TAP_TEMPO = 99;
  public static final int NUDGE_MINUS = 100;
  public static final int NUDGE_PLUS = 101;

  public static final int BANK = 103;

  // LED color + mode definitions
  public static final int LED_OFF = 0;
  public static final int LED_ON = 1;
  public static final int LED_GRAY = 2;
  public static final int LED_CYAN = 114;
  public static final int LED_GRAY_DIM = 117;
  public static final int LED_RED = 120;
  public static final int LED_RED_HALF = 121;
  public static final int LED_ORANGE_RED = 60;
  public static final int LED_GREEN = 122;
  public static final int LED_GREEN_HALF = 123;
  public static final int LED_YELLOW = 124;
  public static final int LED_AMBER = 126;
  public static final int LED_AMBER_HALF = 9;
  public static final int LED_AMBER_DIM = 10;

  public static final int LED_MODE_PRIMARY = 0;
  public static final int LED_MODE_PULSE = 10;
  public static final int LED_MODE_BLINK = 15;

  // We use three modifier keys:
  // SHIFT: Momentary, can't be lit. Used for all sorts of purposes.
  private boolean shiftOn = false;
  // BANK: Toggle, lit when on. Makes the grid control patterns instead of clips.
  private boolean bankOn = true;
  // DEV. LOCK: Toggle, lit when on. Repurposes much of the hardware for color control.
  private boolean deviceLockOn = false;
  // "<-- BANK (3)": Toggle, lit when on. Makes LinkedColor knobs affect Saturation instead of Hue
  private boolean bankLeftOn = false;
  // "BANK --> (4)": Toggle, lit when on. Makes LinkedColor knobs affect Brightness instead of Hue
  private boolean bankRightOn = false;

  // "Copies" a color for pasting into the main swatch
  private Integer colorClipboard = null;
  // The entry in the main swatch that CUE LEVEL adjusts
  private LXDynamicColor focusColor = null;
  // Display full spectrum of colors for use with copy/paste
  private boolean rainbowMode = false;
  // Scroll offset for Rainbow Mode
  private int rainbowColumnOffset = 0;

  private final APC40Mk2Colors apc40Mk2Colors = new APC40Mk2Colors();

  private final Map<LXAbstractChannel, ChannelListener> channelListeners = new HashMap<LXAbstractChannel, ChannelListener>();

  private final DeviceListener deviceListener = new DeviceListener();

  private enum GridMode {
    PATTERN,
    CLIP,
    PALETTE;
  }

  private GridMode gridMode = getGridMode();

  private GridMode getGridMode() {
    if (this.deviceLockOn) {
      return GridMode.PALETTE;
    } else if (this.bankOn) {
      return GridMode.PATTERN;
    } else {
      return GridMode.CLIP;
    }
  }

  private void updateGridMode() {
    this.gridMode = getGridMode();
    sendChannelGrid();
    sendChannelFocus();
  }

  // When turning a knob that sometimes controls Hue, sometimes Brightness,
  // sometimes Saturation, look up the current mode of the surface and return
  // the appropriate subparameter that this knob currently controls.
  private CompoundParameter getColorSubparam(ColorParameter colorParameter) {
    if (this.shiftOn) {
      if (this.bankLeftOn || this.bankRightOn) {
        return colorParameter.hue;
      } else {
        return colorParameter.saturation;
      }
    } else if (this.bankLeftOn) {
      return colorParameter.saturation;
    } else if (this.bankRightOn) {
      return colorParameter.brightness;
    } else {
      return colorParameter.hue;
    }
  }

  // When non-null, touching a LinkedColorParameter knob sets it to this
  // instead of updating the value in the usual fashion.
  // It's either an Integer, in which case the LCP is made static
  // and initialized to this value, or it's a LXDynamicColor that belongs
  // to a swatch entry, in which case the LCP is linked to it.
  private Object getStickyColor() {
    if (this.gridMode != GridMode.PALETTE) {
      return null;
    } else if (this.focusColor == null) {
      return this.colorClipboard;
    } else {
      return this.focusColor;
    }
  }

  private void updateBankLeftRightLights() {
    sendNoteOn(0, BANK_LEFT, this.bankLeftOn ? LED_ON : LED_OFF);
    sendNoteOn(0, BANK_RIGHT, this.bankRightOn ? LED_ON : LED_OFF);
  }

  private class DeviceListener implements LXParameterListener {

    private LXDeviceComponent device = null;
    private LXEffect effect = null;
    private LXPattern pattern = null;
    private LXBus channel = null;

    private final LXListenableParameter[] knobs =
            new LXListenableParameter[DEVICE_KNOB_NUM];

    DeviceListener() {
      Arrays.fill(this.knobs, null);
    }

    void resend() {
      for (int i = 0; i < this.knobs.length; ++i) {
        // If the knob is a LinkedColorParameter, we need to look up which subparameter
        // to pull the value from to send to the surface to display.
        LXListenableNormalizedParameter parameter = parameterForKnob(this.knobs[i]);
        if (parameter != null) {
          sendControlChange(0, DEVICE_KNOB_STYLE + i, parameter.getPolarity() == LXParameter.Polarity.BIPOLAR ? LED_STYLE_BIPOLAR : LED_STYLE_UNIPOLAR);
          double normalized = (parameter instanceof CompoundParameter) ?
            ((CompoundParameter) parameter).getBaseNormalized() :
            parameter.getNormalized();
          sendControlChange(0, DEVICE_KNOB + i, (int) (normalized * 127));
        } else {
          sendControlChange(0, DEVICE_KNOB_STYLE + i, LED_STYLE_OFF);
        }
      }
      boolean isEnabled = false;
      if (this.effect != null) {
        isEnabled = this.effect.enabled.isOn();
      } else if (this.pattern != null) {
        isEnabled = this.pattern == ((LXChannel) this.channel).getActivePattern();
      }
      sendNoteOn(0, DEVICE_ON_OFF, isEnabled ? LED_ON : LED_OFF);
    }

    void registerChannel(LXBus channel) {
      unregisterChannel();
      this.channel = channel;
      if (channel instanceof LXChannel) {
        ((LXChannel) channel).focusedPattern.addListener(this);
        register(((LXChannel) channel).getFocusedPattern());
      } else if (channel.effects.size() > 0) {
        register(channel.getEffect(0));
      } else {
        register(null);
      }
      updateLCPKnobs();
    }

    void registerPrevious() {
      if (this.effect != null) {
        int effectIndex = this.effect.getIndex();
        if (effectIndex > 0) {
          register(this.effect.getBus().getEffect(effectIndex - 1));
        } else if (this.channel instanceof LXChannel) {
          register(((LXChannel) this.channel).getFocusedPattern());
        }
      }
    }

    void registerNext() {
      if (this.effect != null) {
        int effectIndex = this.effect.getIndex();
        if (effectIndex < this.effect.getBus().effects.size() - 1) {
          register(this.effect.getBus().getEffect(effectIndex + 1));
        }
      } else if (this.pattern != null) {
        if (channel.effects.size() > 0) {
          register(channel.getEffect(0));
        }
      }
    }

    private void clearKnobsAfter(int i) {
      while (i < this.knobs.length) {
        sendControlChange(0, DEVICE_KNOB_STYLE + i, LED_STYLE_OFF);
        ++i;
      }
    }

    void register(LXDeviceComponent device) {
      if (this.device == device) {
        return;
      }
      unregister();
      this.device = device;

      if (this.device == null) {
        clearKnobsAfter(0);
        return;
      } else if (this.device instanceof LXEffect) {
        this.effect = (LXEffect) this.device;
        this.effect.enabled.addListener(this);
      } else if (this.device instanceof LXPattern) {
        this.pattern = (LXPattern) this.device;
      }

      this.device.controlSurfaceSemaphore.increment();

      boolean isEnabled = false;
      if (this.effect != null) {
        isEnabled = this.effect.isEnabled();
      } else if (this.pattern != null) {
        isEnabled = this.pattern == ((LXChannel) this.channel).getActivePattern();
      }
      sendNoteOn(0, DEVICE_ON_OFF, isEnabled ? LED_ON : LED_OFF);

      int i = 0;
      LXListenableParameter currentParentParameter = null;
      for (LXListenableNormalizedParameter parameter : this.device.getRemoteControls()) {
        if (parameter == null) {
          this.knobs[i] = null;
          sendControlChange(0, DEVICE_KNOB_STYLE + i, LED_STYLE_OFF);
          continue;
        }

        if (i >= this.knobs.length) {
          // Subtle: Even if all the knobs are full, we need to keep adding
          // listeners for all children of the final knob. So only break
          // if we're up to a parameter with no parent or a different parent.
          if (parameter.parentParameter == null || parameter.parentParameter != currentParentParameter) {
            break;
          }
        }

        parameter.addListener(this);

        LXListenableParameter knobParam = parameter;

        if (parameter.parentParameter != null) {
          if (parameter.parentParameter != currentParentParameter) {
            // This parameter is the first child to a parent.
            // Attach the parent to the knob, and keep track of it
            // so we can skip this step for upcoming siblings.
            currentParentParameter = parameter.parentParameter;
            knobParam = currentParentParameter;
          } else {
            // This parameter is the non-first child to a parent.
            // The parent's was already attached to a knob when
            // we processed its oldest sibling.
            continue;
          }
        }
        this.knobs[i] = knobParam;

        sendControlChange(0, DEVICE_KNOB_STYLE + i,
                parameter.getPolarity() == LXParameter.Polarity.BIPOLAR ?
                        LED_STYLE_BIPOLAR : LED_STYLE_UNIPOLAR);
        double normalized = (parameter instanceof CompoundParameter) ?
                ((CompoundParameter) parameter).getBaseNormalized() :
                parameter.getNormalized();
        sendControlChange(0, DEVICE_KNOB + i, (int) (normalized * 127));
        ++i;
      }
      clearKnobsAfter(i);
    }

    @Override
    public void onParameterChanged(LXParameter parameter) {
      if ((this.channel != null) &&
          (this.channel instanceof LXChannel) &&
          (parameter == ((LXChannel)this.channel).focusedPattern)) {
        if ((this.device == null) || (this.device instanceof LXPattern)) {
          register(((LXChannel) this.channel).getFocusedPattern());
        }
      } else if ((this.effect != null) && (parameter == this.effect.enabled)) {
        sendNoteOn(0, DEVICE_ON_OFF, this.effect.enabled.isOn() ? LED_ON : LED_OFF);
      } else {
        for (int i = 0; i < this.knobs.length; ++i) {
          LXListenableNormalizedParameter knobParam = parameterForKnob(this.knobs[i]);
          if (parameter == knobParam) {
            double normalized = (parameter instanceof CompoundParameter) ?
              ((CompoundParameter) parameter).getBaseNormalized() :
                    knobParam.getNormalized();
            sendControlChange(0, DEVICE_KNOB + i, (int) (normalized * 127));
            break;
          }
        }
      }
    }

    // Returns what parameter should be changed when a physical knob is turned. This
    // is trivial except for LinkedColorParameters, whose function changes depending
    // on the surface's mode.
    private LXListenableNormalizedParameter parameterForKnob(LXListenableParameter knob) {
      if (knob == null || knob instanceof LXListenableNormalizedParameter) {
        // If the knob is unused or linked to a vanilla parameter, we're done.
        return (LXListenableNormalizedParameter) knob;
      }

      // The knob is attached to an LinkableColorParameter.
      LinkedColorParameter lcp = (LinkedColorParameter) knob;

      if (lcp.mode.getEnum() == LinkedColorParameter.Mode.PALETTE) {
        // If it's palette-linked, the knob always controls the palette index.
        return lcp.index;
      } else {
        // Otherwise, it controls H, S, or B, based on the current mode.
        return getColorSubparam(lcp);
      }
    }

    void onDeviceOnOff() {
      if (this.pattern != null) {
        this.pattern.getChannel().goPatternIndex(this.pattern.getIndex());
        sendNoteOn(0, DEVICE_ON_OFF, 1);
      } else if (this.effect != null) {
        this.effect.enabled.toggle();
      }
    }

    void onKnob(int index, double normalized) {
      LXListenableParameter knob = this.knobs[index];

      if (knob == null) {
        return;
      }

      Object stickyColor = getStickyColor();
      if (knob instanceof LinkedColorParameter) {
        LinkedColorParameter lcp = (LinkedColorParameter) knob;
        // If a Sticky Color was set, override the usual effect.
        if (stickyColor instanceof Integer) {
          lcp.mode.setValue(LinkedColorParameter.Mode.STATIC);
          lcp.setColor((Integer) stickyColor);
          return;
        } else if (stickyColor instanceof LXDynamicColor) {
          int palIndex = lx.engine.palette.swatch.colors.indexOf(stickyColor);
          // It could be -1 if the focus color is from a side swatch.
          if (palIndex >= 0) {
            lcp.mode.setValue(LinkedColorParameter.Mode.PALETTE);
            lcp.index.setValue(palIndex + 1);
            return;
          }
        }
      }

      // There was no Sticky Color set, or this is just a mundane knob.

      LXListenableNormalizedParameter knobParam = parameterForKnob(knob);

      // If it's a wrappable parameter, let it wrap
      if (knobParam.isWrappable()) {
        if (normalized == 0.0) {
          normalized = 1.0;
        } else {
          normalized = (normalized + 1.0) % 1.0;
        }
      }

      if (knobParam != null) {
        knobParam.setNormalized(normalized);
      }
    }

    private void unregister() {
      if (this.effect != null) {
        this.effect.enabled.removeListener(this);
      }
      if (this.device != null) {
        for (int i = 0; i < this.knobs.length; ++i) {
          if (this.knobs[i] == null) {
            continue;
          } else if (this.knobs[i] instanceof AggregateParameter) {
            AggregateParameter ap = (AggregateParameter) this.knobs[i];
            for (LXListenableParameter sub : ap.subparameters.values()) {
              sub.removeListener(this);
            }
          } else {
            this.knobs[i].removeListener(this);
          }
          this.knobs[i] = null;
          sendControlChange(0, DEVICE_KNOB + i, 0);
          sendControlChange(0, DEVICE_KNOB_STYLE + i, LED_STYLE_OFF);
        }
        this.device.controlSurfaceSemaphore.decrement();
      }
      this.pattern = null;
      this.effect = null;
      this.device = null;
    }

    private void unregisterChannel() {
      if (this.channel != null) {
        if (this.channel instanceof LXChannel) {
          ((LXChannel) this.channel).focusedPattern.removeListener(this);
        }
      }
      this.channel = null;
    }

    private void dispose() {
      unregister();
      unregisterChannel();
    }

  }

  private class ChannelListener implements LXChannel.Listener, LXBus.ClipListener, LXParameterListener {

    private final LXAbstractChannel channel;

    ChannelListener(LXAbstractChannel channel) {
      this.channel = channel;
      if (channel instanceof LXChannel) {
        ((LXChannel) channel).addListener(this);
      } else {
        channel.addListener(this);
      }
      channel.addClipListener(this);
      channel.cueActive.addListener(this);
      channel.enabled.addListener(this);
      channel.crossfadeGroup.addListener(this);
      channel.arm.addListener(this);
      if (channel instanceof LXChannel) {
        LXChannel c = (LXChannel) channel;
        c.focusedPattern.addListener(this);
        c.controlSurfaceFocusLength.setValue(CLIP_LAUNCH_ROWS);
        int focusedPatternIndex = c.getFocusedPatternIndex();
        c.controlSurfaceFocusIndex.setValue(focusedPatternIndex < CLIP_LAUNCH_ROWS ? 0 : (focusedPatternIndex - CLIP_LAUNCH_ROWS + 1));
      }
      for (LXClip clip : this.channel.clips) {
        if (clip != null) {
          clip.running.addListener(this);
          clip.loop.addListener(this);
        }
      }
    }

    public void dispose() {
      if (this.channel instanceof LXChannel) {
        ((LXChannel) this.channel).removeListener(this);
      } else {
        this.channel.removeListener(this);
      }
      this.channel.removeClipListener(this);
      this.channel.cueActive.removeListener(this);
      this.channel.enabled.removeListener(this);
      this.channel.crossfadeGroup.removeListener(this);
      this.channel.arm.removeListener(this);
      if (this.channel instanceof LXChannel) {
        LXChannel c = (LXChannel) this.channel;
        c.focusedPattern.removeListener(this);
        c.controlSurfaceFocusLength.setValue(0);
        c.controlSurfaceFocusIndex.setValue(0);
      }
      for (LXClip clip : this.channel.clips) {
        if (clip != null) {
          clip.running.removeListener(this);
          clip.loop.removeListener(this);
        }
      }
    }

    public void onParameterChanged(LXParameter p) {
      int index = this.channel.getIndex();
      if (index >= CLIP_LAUNCH_COLUMNS) {
        return;
      }

      if (p == this.channel.cueActive) {
        sendNoteOn(index, CHANNEL_SOLO, this.channel.cueActive.isOn() ? LED_ON : LED_OFF);
      } else if (p == this.channel.enabled) {
        sendNoteOn(index, CHANNEL_ACTIVE, this.channel.enabled.isOn() ? LED_ON : LED_OFF);
      } else if (p == this.channel.crossfadeGroup) {
        sendNoteOn(index, CHANNEL_CROSSFADE_GROUP, this.channel.crossfadeGroup.getValuei());
      } else if (p == this.channel.arm) {
        sendNoteOn(index, CHANNEL_ARM, this.channel.arm.isOn() ? LED_ON : LED_OFF);
        sendChannelClips(this.channel.getIndex(), this.channel);
      } else if (p.getParent() instanceof LXClip) {
        LXClip clip = (LXClip)p.getParent();
        sendClip(index, this.channel, clip.getIndex(), clip);
      }
      if (this.channel instanceof LXChannel) {
        LXChannel c = (LXChannel) this.channel;
        if (p == c.focusedPattern) {
          int focusedPatternIndex = c.getFocusedPatternIndex();
          int channelSurfaceIndex = c.controlSurfaceFocusIndex.getValuei();
          if (focusedPatternIndex < channelSurfaceIndex) {
            c.controlSurfaceFocusIndex.setValue(focusedPatternIndex);
          } else if (focusedPatternIndex >= channelSurfaceIndex + CLIP_LAUNCH_ROWS) {
            c.controlSurfaceFocusIndex.setValue(focusedPatternIndex - CLIP_LAUNCH_ROWS + 1);
          }
          sendChannelPatterns(index, c);
        }
      }
    }

    @Override
    public void effectAdded(LXBus channel, LXEffect effect) {
    }

    @Override
    public void effectRemoved(LXBus channel, LXEffect effect) {
    }

    @Override
    public void effectMoved(LXBus channel, LXEffect effect) {
      // TODO(mcslee): update device focus??
    }

    @Override
    public void indexChanged(LXAbstractChannel channel) {
      // Handled by the engine channelMoved listener.
    }

    @Override
    public void groupChanged(LXChannel channel, LXGroup group) {

    }

    @Override
    public void patternAdded(LXChannel channel, LXPattern pattern) {
      sendChannelPatterns(channel.getIndex(), channel);
    }

    @Override
    public void patternRemoved(LXChannel channel, LXPattern pattern) {
      sendChannelPatterns(channel.getIndex(), channel);
    }

    @Override
    public void patternMoved(LXChannel channel, LXPattern pattern) {
      sendChannelPatterns(channel.getIndex(), channel);
    }

    @Override
    public void patternWillChange(LXChannel channel, LXPattern pattern, LXPattern nextPattern) {
      sendChannelPatterns(channel.getIndex(), channel);
    }

    @Override
    public void patternDidChange(LXChannel channel, LXPattern pattern) {
      sendChannelPatterns(channel.getIndex(), channel);
    }

    @Override
    public void clipAdded(LXBus bus, LXClip clip) {
      clip.running.addListener(this);
      clip.loop.addListener(this);
      sendClip(this.channel.getIndex(), this.channel, clip.getIndex(), clip);
    }

    @Override
    public void clipRemoved(LXBus bus, LXClip clip) {
      clip.running.removeListener(this);
      clip.loop.removeListener(this);
      sendChannelClips(this.channel.getIndex(), this.channel);
    }

  }

  public APC40Mk2(LX lx, LXMidiInput input, LXMidiOutput output) {
    super(lx, input, output);
  }

  @Override
  protected void onEnable(boolean on) {
    if (on) {
      setApcMode(ABLETON_ALTERNATE_MODE);
      initialize(false);
      register();
    } else {
      this.deviceListener.register(null);
      for (LXAbstractChannel channel : this.lx.engine.mixer.channels) {
        if (channel instanceof LXChannel) {
          ((LXChannel)channel).controlSurfaceFocusLength.setValue(0);
        }
      }
      if (this.isRegistered) {
        unregister();
      }
      setApcMode(GENERIC_MODE);
    }
  }

  @Override
  protected void onReconnect() {
    if (this.enabled.isOn()) {
      setApcMode(ABLETON_ALTERNATE_MODE);
      initialize(true);
      this.deviceListener.resend();
    }
  }

  private void setApcMode(byte mode) {
    this.output.sendSysex(new byte[] {
      (byte) 0xf0, // sysex start
      0x47, // manufacturers id
      0x00, // device id
      0x29, // product model id
      0x60, // message
      0x00, // bytes MSB
      0x04, // bytes LSB
      mode,
      0x09, // version maj
      0x03, // version min
      0x01, // version bugfix
      (byte) 0xf7, // sysex end
    });
  }

  private void initialize(boolean reconnect) {
    this.output.sendNoteOn(0, BANK, this.bankOn ? LED_ON : LED_OFF);
    this.output.sendNoteOn(0, DEVICE_LOCK, this.deviceLockOn ? LED_ON : LED_OFF);

    if (!reconnect) {
      resetPaletteVars();
      this.bankLeftOn = false;
      this.bankRightOn = false;
    }

    this.updateBankLeftRightLights();

    for (int i = 0; i < DEVICE_KNOB_NUM; ++i) {
      sendControlChange(0, DEVICE_KNOB_STYLE+i, LED_STYLE_OFF);
    }
    for (int i = 0; i < CHANNEL_KNOB_NUM; ++i) {
      // Initialize channel knobs for generic control, but don't
      // reset their values if we're in a reconnect situation
      sendControlChange(0, CHANNEL_KNOB_STYLE+i, LED_STYLE_SINGLE);
      if (!reconnect) {
        sendControlChange(0, CHANNEL_KNOB+i, 64);
      }
    }
    sendChannels();
    this.cueDown = 0;
    this.singleCueStartedOn = false;
  }

  private void resetPaletteVars() {
    this.colorClipboard = null;
    this.focusColor = null;
    this.rainbowMode = false;

    clearSceneLaunch();
  }

  private void sendChannels() {
    for (int i = 0; i < NUM_CHANNELS; ++i) {
      sendChannel(i, getChannel(i));
    }
    sendChannelFocus();
  }

  private void sendChannelGrid() {
    if (!this.rainbowMode) {
      for (int i = 0; i < NUM_CHANNELS; ++i) {
        LXAbstractChannel channel = getChannel(i);
        sendChannelPatterns(i, channel);
        sendChannelClips(i, channel);
        sendSwatch(i);
      }
    }

    sendSwatch(MASTER_SWATCH);
  }

  private void clearChannelGrid() {
    for (int i = 0; i < NUM_CHANNELS; ++i) {
      sendChannel(i, null);
    }
  }

  private void sendChannel(int index, LXAbstractChannel channel) {
    if (channel != null) {
      sendNoteOn(index, CHANNEL_ACTIVE, channel.enabled.isOn() ? LED_ON : LED_OFF);
      sendNoteOn(index, CHANNEL_CROSSFADE_GROUP, channel.crossfadeGroup.getValuei());
      sendNoteOn(index, CHANNEL_SOLO, channel.cueActive.isOn() ? LED_ON : LED_OFF);
      sendNoteOn(index, CHANNEL_ARM, channel.arm.isOn() ? LED_ON : LED_OFF);
    } else {
      sendNoteOn(index, CHANNEL_ACTIVE, LED_OFF);
      sendNoteOn(index, CHANNEL_CROSSFADE_GROUP, LED_OFF);
      sendNoteOn(index, CHANNEL_SOLO, LED_OFF);
      sendNoteOn(index, CHANNEL_ARM, LED_OFF);
    }
    sendChannelPatterns(index, channel);
    sendChannelClips(index, channel);
  }

  private void sendChannelPatterns(int index, LXAbstractChannel channelBus) {
    if (index >= CLIP_LAUNCH_COLUMNS || this.gridMode != GridMode.PATTERN) {
      return;
    }

    if (channelBus instanceof LXChannel) {
      LXChannel channel = (LXChannel) channelBus;
      int baseIndex = channel.controlSurfaceFocusIndex.getValuei();
      int endIndex = channel.patterns.size() - baseIndex;
      int activeIndex = channel.getActivePatternIndex() - baseIndex;
      int nextIndex = channel.getNextPatternIndex() - baseIndex;
      int focusedIndex = channel.focusedPattern.getValuei() - baseIndex;
      if (channel.patterns.size() == 0) {
        focusedIndex = -1;
      }
      for (int y = 0; y < CLIP_LAUNCH_ROWS; ++y) {
        int note = CLIP_LAUNCH + CLIP_LAUNCH_COLUMNS * (CLIP_LAUNCH_ROWS - 1 - y) + index;
        int midiChannel = LED_MODE_PRIMARY;
        int color = LED_OFF;
        if (y == activeIndex) {
          // This pattern is active (may also be focused)
          color = LED_ORANGE_RED;
        } else if (y == nextIndex) {
          // This pattern is being transitioned to
          sendNoteOn(LED_MODE_PRIMARY, note, 60);
          midiChannel = LED_MODE_PULSE;
          color = LED_AMBER_HALF;
        } else if (y == focusedIndex) {
          // This pattern is not active, but it is focused
          color = LED_AMBER_DIM;
        } else if (y < endIndex) {
          // There is a pattern present
          color = LED_GRAY_DIM;
        }

        sendNoteOn(midiChannel, note, color);
      }
    } else {
      for (int y = 0; y < CLIP_LAUNCH_ROWS; ++y) {
        sendNoteOn(
          LED_MODE_PRIMARY,
          CLIP_LAUNCH + CLIP_LAUNCH_COLUMNS * (CLIP_LAUNCH_ROWS - 1 - y) + index,
          LED_OFF
        );
      }
    }
  }

  private void sendChannelClips(int index, LXAbstractChannel channel) {
    for (int i = 0; i < CLIP_LAUNCH_ROWS; ++i) {
      LXClip clip = null;
      if (channel != null) {
        clip = channel.getClip(i);
      }
      sendClip(index, channel, i, clip);
    }
  }

  private void sendClip(int channelIndex, LXAbstractChannel channel, int clipIndex, LXClip clip) {
    if (this.gridMode != GridMode.CLIP ||
            channelIndex >= CLIP_LAUNCH_COLUMNS || clipIndex >= CLIP_LAUNCH_ROWS) {
      return;
    }
    int color = LED_OFF;
    int mode = LED_MODE_PRIMARY;
    int pitch = CLIP_LAUNCH + channelIndex + CLIP_LAUNCH_COLUMNS * (CLIP_LAUNCH_ROWS - 1 - clipIndex);
    if (channel != null && clip != null) {
      color = channel.arm.isOn() ? LED_RED_HALF :
              clip.loop.isOn() ? LED_CYAN : LED_GRAY;
      if (clip.isRunning()) {
        color = channel.arm.isOn() ? LED_RED : LED_GREEN;
        sendNoteOn(LED_MODE_PRIMARY, pitch, color);
        mode = LED_MODE_PULSE;
        color = channel.arm.isOn() ? LED_RED_HALF :
                clip.loop.isOn() ? LED_CYAN : LED_GREEN_HALF;
      }
    }
    sendNoteOn(mode, pitch, color);
  }

  private void clearSceneLaunch() {
    for (int i = 0; i < SCENE_LAUNCH_NUM; i++) {
      sendNoteOn(LED_MODE_PRIMARY, SCENE_LAUNCH + i, LED_OFF);
    }
  }

  private void sendSwatches() {
    for (int i = 0; i < NUM_CHANNELS; ++i) {
      sendSwatch(i);
    }
    sendSwatch(MASTER_SWATCH);
  }

  // Sends the specified swatch index to the channel grid, or
  // if MASTER_SWATCH, sends the main palette swatch to the SCENE LAUNCH column
  private void sendSwatch(int index) {
    if (this.gridMode != GridMode.PALETTE || (index >= PALETTE_SWATCH_COLUMNS)) {
      return;
    }

    LXSwatch swatch = getSwatch(index);

    for (int i = 0; i < PALETTE_SWATCH_ROWS; ++i) {
      int color = LED_OFF;
      int mode = LED_MODE_PRIMARY;

      int pitch;
      if (index == MASTER_SWATCH) {
        pitch = SCENE_LAUNCH + i;
      } else {
        pitch = CLIP_LAUNCH + index + CLIP_LAUNCH_COLUMNS * (CLIP_LAUNCH_ROWS - 1 - i);
      }
      if (swatch != null && i < swatch.colors.size()) {
        int palColor = swatch.colors.get(i).getColor();
        color = this.apc40Mk2Colors.nearest(palColor);
      }
      sendNoteOn(mode, pitch, color);
    }
  }

  private static int[] makeRainbowGrid() {
    int[] rainbowGrid = new int[RAINBOW_GRID_COLUMNS * RAINBOW_GRID_ROWS];
    for (int col = 0; col < RAINBOW_GRID_COLUMNS; ++col) {
      int hue = col * RAINBOW_HUE_STEP;
      for (int row = 0; row < RAINBOW_GRID_ROWS; ++row) {
        int i = col * RAINBOW_GRID_ROWS + row;
        rainbowGrid[i++] = LXColor.hsb(hue, RAINBOW_GRID_SAT[row], RAINBOW_GRID_BRI[row]);
      }
    }
    return rainbowGrid;
  }

  // Grouped by column
  private static final int[] RAINBOW_GRID = makeRainbowGrid();

  private int rainbowGridColor(int relCol, int row) {
    int absCol = (RAINBOW_GRID_COLUMNS + relCol + this.rainbowColumnOffset) % RAINBOW_GRID_COLUMNS;
    return RAINBOW_GRID[absCol * RAINBOW_GRID_ROWS + row];
  }

  // Cover the grid with a rainbox of APC colors
  private void sendRainbowPickerGrid() {
    for (int col = 0; col < NUM_CHANNELS; ++col) {
      for (int row = 0; row < PALETTE_SWATCH_ROWS; ++row) {
        int pitch = CLIP_LAUNCH + col + CLIP_LAUNCH_COLUMNS * (CLIP_LAUNCH_ROWS - 1 - row);
        int color = rainbowGridColor(col, row);
        int colorId = this.apc40Mk2Colors.nearest(color);
        sendNoteOn(LED_MODE_PRIMARY, pitch, colorId);
      }
    }
  }

  // For each knob controlling a LinkedColorParameter, update its lighting to match
  // the value it would change if turned right now. If we ever have an
  // AggregateParameter knob where some subparameters are unipolar and others
  // bipolar, we'll need to do a sendControlChange() as well here. For now,
  // though, that never happens.
  private void updateLCPKnobs() {
    for (int i = 0; i < this.deviceListener.knobs.length; i++) {
      LXListenableParameter knob = this.deviceListener.knobs[i];
      if (knob instanceof LinkedColorParameter) {
        LinkedColorParameter lcp = (LinkedColorParameter) knob;

        LXListenableNormalizedParameter subparam;

        if (lcp.mode.getEnum() == LinkedColorParameter.Mode.PALETTE) {
          subparam = lcp.index;
        } else {
          subparam = getColorSubparam(lcp);
        }
        double normalized = subparam.getNormalized();
        sendControlChange(0, DEVICE_KNOB + i, (int) (normalized * 127));
      };
    }
  }

  private void sendChannelFocus() {
    int focusedChannel = this.lx.engine.mixer.focusedChannel.getValuei();
    boolean masterFocused = (focusedChannel == this.lx.engine.mixer.channels.size());
    for (int i = 0; i < NUM_CHANNELS; ++i) {
      boolean on;
      if (this.rainbowMode) {
        on = false;
      } else if (this.gridMode == GridMode.PALETTE) {
        on = i < lx.engine.palette.swatches.size();
      } else {
        on = !masterFocused && (i == focusedChannel);
      }
      sendNoteOn(i, CHANNEL_FOCUS, on ? LED_ON : LED_OFF);
    }
    sendNoteOn(0, MASTER_FOCUS, masterFocused ? LED_ON : LED_OFF);
  }

  private final LXMixerEngine.Listener mixerEngineListener = new LXMixerEngine.Listener() {
    @Override
    public void channelRemoved(LXMixerEngine mixer, LXAbstractChannel channel) {
      unregisterChannel(channel);
      sendChannels();
    }

    @Override
    public void channelMoved(LXMixerEngine mixer, LXAbstractChannel channel) {
      sendChannels();
    }

    @Override
    public void channelAdded(LXMixerEngine mixer, LXAbstractChannel channel) {
      sendChannels();
      registerChannel(channel);
    }
  };

  private final LXParameterListener focusedChannelListener = (p) -> {
    sendChannelFocus();
    this.deviceListener.registerChannel(this.lx.engine.mixer.getFocusedChannel());
  };

  private final LXParameterListener cueAListener = (p) -> {
    sendNoteOn(0, CLIP_DEVICE_VIEW, this.lx.engine.mixer.cueA.isOn() ? 1 : 0);
  };

  private final LXParameterListener cueBListener = (p) -> {
    sendNoteOn(0, DETAIL_VIEW, this.lx.engine.mixer.cueB.isOn() ? 1 : 0);
  };

  private final LXParameterListener tempoListener = (p) -> {
    sendNoteOn(0, METRONOME, this.lx.engine.tempo.enabled.isOn() ? LED_ON : LED_OFF);
  };

  private boolean isRegistered = false;

  private void register() {
    isRegistered = true;

    for (LXAbstractChannel channel : this.lx.engine.mixer.channels) {
      registerChannel(channel);
    }

    this.lx.engine.mixer.addListener(this.mixerEngineListener);
    this.lx.engine.mixer.focusedChannel.addListener(this.focusedChannelListener);

    this.deviceListener.registerChannel(this.lx.engine.mixer.getFocusedChannel());

    this.lx.engine.mixer.cueA.addListener(this.cueAListener, true);
    this.lx.engine.mixer.cueB.addListener(this.cueBListener, true);
    this.lx.engine.tempo.enabled.addListener(this.tempoListener, true);
  }

  private void unregister() {
    isRegistered = false;

    for (LXAbstractChannel channel : this.lx.engine.mixer.channels) {
      unregisterChannel(channel);
    }

    this.lx.engine.mixer.removeListener(this.mixerEngineListener);
    this.lx.engine.mixer.focusedChannel.removeListener(this.focusedChannelListener);
    this.lx.engine.mixer.cueA.removeListener(this.cueAListener);
    this.lx.engine.mixer.cueB.removeListener(this.cueBListener);
    this.lx.engine.tempo.enabled.removeListener(this.tempoListener);

    clearChannelGrid();
  }

  private void registerChannel(LXAbstractChannel channel) {
    ChannelListener channelListener = new ChannelListener(channel);
    this.channelListeners.put(channel, channelListener);
  }

  private void unregisterChannel(LXAbstractChannel channel) {
    ChannelListener channelListener = this.channelListeners.remove(channel);
    if (channelListener != null) {
      channelListener.dispose();
    }
  }

  private LXAbstractChannel getChannel(int index) {
    if (index < this.lx.engine.mixer.channels.size()) {
      return this.lx.engine.mixer.channels.get(index);
    }
    return null;
  }

  private LXAbstractChannel getChannel(LXShortMessage message) {
    return getChannel(message.getChannel());
  }

  private LXSwatch getSwatch(int index) {
    if (index < 0) {
      return this.lx.engine.palette.swatch;
    }
    if (index < this.lx.engine.palette.swatches.size()) {
      return this.lx.engine.palette.swatches.get(index);
    }
    return null;
  }

  private int cueDown = 0;
  private boolean singleCueStartedOn = false;

  private void noteReceived(MidiNote note, boolean on) {
    int pitch = note.getPitch();

    // Global toggle messages
    switch (pitch) {
    case SHIFT:
      this.shiftOn = on;
      updateLCPKnobs();
      return;
    case BANK:
      if (on) {
        this.bankOn = !this.bankOn;
        sendNoteOn(note.getChannel(), pitch, this.bankOn ? LED_ON : LED_OFF);
        if (this.deviceLockOn) {
          this.deviceLockOn = false;
          sendNoteOn(note.getChannel(), DEVICE_LOCK, LED_OFF);
          resetPaletteVars();
        }
        updateGridMode();
      }
      return;
    case DEVICE_LOCK:
      if (on) {
        this.deviceLockOn = !this.deviceLockOn;
        sendNoteOn(note.getChannel(), pitch, this.deviceLockOn ? LED_ON : LED_OFF);
        if (this.bankOn) {
          sendNoteOn(note.getChannel(), BANK, LED_OFF);
          this.bankOn = false;
        }
        if (!this.deviceLockOn) {
          resetPaletteVars();
        }
        updateGridMode();
      }
      return;
    case METRONOME:
      if (on) {
        lx.engine.tempo.enabled.toggle();
      }
      return;
    case TAP_TEMPO:
      if (this.rainbowMode) {
        this.rainbowMode = false;
        this.focusColor = null;
        this.colorClipboard = null;
        sendChannelGrid();
      } else {
        lx.engine.tempo.tap.setValue(on);
      }
      return;
    case NUDGE_MINUS:
      lx.engine.tempo.nudgeDown.setValue(on);
      return;
    case NUDGE_PLUS:
      lx.engine.tempo.nudgeUp.setValue(on);
      return;
    }

    // Global momentary light-up buttons
    switch (pitch) {
    case CLIP_STOP:
      sendNoteOn(note.getChannel(), pitch, on ? LED_ON : LED_OFF);
      break;
    }
    if (pitch >= SCENE_LAUNCH && pitch <= SCENE_LAUNCH_MAX && this.gridMode != GridMode.PALETTE) {
      sendNoteOn(note.getChannel(), pitch, on ? LED_GREEN : LED_OFF);
    }

    // Global momentary
    if (on) {
      LXBus bus;
      switch (pitch) {
      case MASTER_FOCUS:
        lx.engine.mixer.selectChannel(lx.engine.mixer.masterBus);
        lx.engine.mixer.focusedChannel.setValue(lx.engine.mixer.channels.size());
        return;
      case BANK_SELECT_LEFT:
        this.lx.engine.mixer.focusedChannel.decrement(false);
        lx.engine.mixer.selectChannel(lx.engine.mixer.getFocusedChannel());
        return;
      case BANK_SELECT_RIGHT:
        this.lx.engine.mixer.focusedChannel.increment(false);
        lx.engine.mixer.selectChannel(lx.engine.mixer.getFocusedChannel());
        return;
      case BANK_SELECT_UP:
        bus = this.lx.engine.mixer.getFocusedChannel();
        if (bus instanceof LXChannel) {
          ((LXChannel) bus).focusedPattern.decrement(this.shiftOn ? CLIP_LAUNCH_ROWS : 1, false);
        }
        return;
      case BANK_SELECT_DOWN:
        bus = this.lx.engine.mixer.getFocusedChannel();
        if (bus instanceof LXChannel) {
          ((LXChannel) bus).focusedPattern.increment(this.shiftOn ? CLIP_LAUNCH_ROWS : 1, false);
        }
        return;
      case CLIP_DEVICE_VIEW:
        this.lx.engine.mixer.cueA.toggle();
        return;
      case DETAIL_VIEW:
        this.lx.engine.mixer.cueB.toggle();
        return;
      case STOP_ALL_CLIPS:
        if (this.gridMode == GridMode.PALETTE) {
          this.colorClipboard = null;
          this.focusColor = null;
        } else {
          this.lx.engine.mixer.stopClips();
        }
        return;
      case BANK_LEFT:
        this.bankLeftOn = !this.bankLeftOn;
        this.bankRightOn = false;
        this.updateBankLeftRightLights();
        updateLCPKnobs();
        return;
      case BANK_RIGHT:
        this.bankRightOn = !this.bankRightOn;
        this.bankLeftOn = false;
        this.updateBankLeftRightLights();
        updateLCPKnobs();
        return;
      }

      if (pitch >= SCENE_LAUNCH && pitch <= SCENE_LAUNCH_MAX) {
        int index = pitch - SCENE_LAUNCH;
        if (this.gridMode == GridMode.PALETTE) {
          // A button corresponding to an entry in the main palette swatch has been tapped.
          // Make it the focusColor (i.e., turning the CUE LEVEL knob will tweak it), and
          // if there's a color on the clipboard, paste it here.
          boolean colorChanged = false;
          LXSwatch swatch = getSwatch(MASTER_SWATCH);
          if (index > swatch.colors.size()-1 && index < LXSwatch.MAX_COLORS) {
            swatch.addColor();
            colorChanged = true;
          }
          this.focusColor = swatch.getColor(index);
          if (this.colorClipboard != null) {
            this.focusColor.primary.setColor(this.colorClipboard);
            colorChanged = true;
          }
          if (colorChanged) {
            sendSwatch(MASTER_SWATCH);
          }
        } else {
          this.lx.engine.mixer.launchScene(index);
        }
        return;
      }

      if (pitch >= CLIP_LAUNCH && pitch <= CLIP_LAUNCH_MAX) {
        int channelIndex = (pitch - CLIP_LAUNCH) % CLIP_LAUNCH_COLUMNS;
        int index = CLIP_LAUNCH_ROWS - 1 - ((pitch - CLIP_LAUNCH) / CLIP_LAUNCH_COLUMNS);

        if (this.rainbowMode) {
          this.colorClipboard = rainbowGridColor(channelIndex, index);
          return;
        }

        if (this.gridMode == GridMode.PALETTE) {
          LXSwatch swatch = getSwatch(channelIndex);
          if (swatch != null) {
            if (index < swatch.colors.size()) {
              this.focusColor = swatch.colors.get(index);
              this.colorClipboard = this.focusColor.primary.getColor();
            } else if (index < LXSwatch.MAX_COLORS) {
              LXDynamicColor color = swatch.addColor();
              if (this.colorClipboard != null) {
                color.primary.setColor(this.colorClipboard);
              } else {
                this.colorClipboard = color.primary.getColor();
              }
              sendSwatch(channelIndex);
            } else {
              this.colorClipboard = null;
            }
          }
          return;
        }
        LXAbstractChannel channel = getChannel(channelIndex);
        if (channel != null) {
          if (this.gridMode == GridMode.PATTERN) {
            if (channel instanceof LXChannel) {
              LXChannel c = (LXChannel) channel;
              index += c.controlSurfaceFocusIndex.getValuei();
              if (index < c.getPatterns().size()) {
                c.focusedPattern.setValue(index);
                if (!this.shiftOn) {
                  c.goPatternIndex(index);
                }
              }
            }
          } else {
            LXClip clip = channel.getClip(index);
            if (clip == null) {
              clip = channel.addClip(index);
              clip.loop.setValue(this.shiftOn);
            } else if (this.shiftOn) {
              clip.loop.toggle();
            } else if (clip.isRunning()) {
              clip.stop();
            } else {
              clip.trigger();
              this.lx.engine.clips.setFocusedClip(clip);
            }
          }
        }
        return;
      }
    }


    // Channel messages

    if (this.rainbowMode) {
      return;
    }

    if (this.gridMode == GridMode.PALETTE) {
      if (!on) {
        return;
      }
      switch (note.getPitch()) {
      case CHANNEL_FOCUS:
        int swatchNum = note.getChannel();
        if (swatchNum < lx.engine.palette.swatches.size()) {
          lx.engine.palette.setSwatch(lx.engine.palette.swatches.get(swatchNum));
          sendSwatch(MASTER_SWATCH);
        }
        break;
      default:
        LXMidiEngine.error("APC40mk2 in DEV_LOCK received unmapped note: " + note);
      }
      return;
    }

    LXAbstractChannel channel = getChannel(note);

    if (channel != null) {
      if (note.getPitch() == CHANNEL_SOLO) {
        if (on) {
          boolean cueAlreadyOn = channel.cueActive.isOn();

          // First cue pressed, if active, could be un-cue or start of multi-select
          this.singleCueStartedOn = (this.cueDown == 0) && cueAlreadyOn;
          if (cueAlreadyOn) {
            if (this.cueDown == 0) {
              // Turn off all other cues on the first fresh press, leave this one on
              this.lx.engine.mixer.enableChannelCue(channel, true);
            } else {
              channel.cueActive.setValue(false);
            }
          } else {
            this.lx.engine.mixer.enableChannelCue(channel, this.cueDown == 0);
          }
          ++this.cueDown;
        } else {
          // Play defense here, just in case a button was down *before* control surface mode
          // was activated and gets released after
          this.cueDown = LXUtils.max(0, this.cueDown - 1);

          if (this.singleCueStartedOn) {
            // Turn this one off.  Already got the others on the cue down
            channel.cueActive.setValue(false);
            this.singleCueStartedOn = false;
          }
        }
        return;
      }

      if (!on) {
        return;
      }
      switch (note.getPitch()) {
      case CHANNEL_ARM:
        channel.arm.toggle();
        return;
      case CHANNEL_ACTIVE:
        channel.enabled.toggle();
        return;
      case CHANNEL_CROSSFADE_GROUP:
        if (this.shiftOn) {
          channel.blendMode.increment();
        } else {
          channel.crossfadeGroup.increment();
        }
        return;
      case CLIP_STOP:
        channel.stopClips();
        return;
      case CHANNEL_FOCUS:
        if (this.shiftOn) {
          if (channel instanceof LXChannel) {
            ((LXChannel) channel).autoCycleEnabled.toggle();
          }
        } else {
          this.lx.engine.mixer.focusedChannel.setValue(channel.getIndex());
          lx.engine.mixer.selectChannel(lx.engine.mixer.getFocusedChannel());
        }
        return;
      case DEVICE_ON_OFF:
        this.deviceListener.onDeviceOnOff();
        return;
      case DEVICE_LEFT:
        this.deviceListener.registerPrevious();
        return;
      case DEVICE_RIGHT:
        this.deviceListener.registerNext();
        return;
      }
    }

    LXMidiEngine.error("APC40mk2 received unmapped note: " + note);
  }

  @Override
  public void noteOnReceived(MidiNoteOn note) {
    noteReceived(note, true);
  }

  @Override
  public void noteOffReceived(MidiNote note) {
    noteReceived(note, false);
  }

  @Override
  public void controlChangeReceived(MidiControlChange cc) {
    int number = cc.getCC();
    switch (number) {
    case TEMPO:
      if (this.gridMode == GridMode.PALETTE) {
        if (this.rainbowMode) {
          this.rainbowColumnOffset = (this.rainbowColumnOffset + cc.getRelative())
                  % RAINBOW_GRID_COLUMNS;
          this.focusColor = null;
          this.colorClipboard = null;
        } else {
          this.rainbowMode = true;
          sendChannelFocus();
        }
        sendRainbowPickerGrid();
      } else if (this.shiftOn) {
        this.lx.engine.tempo.adjustBpm(.1 * cc.getRelative());
      } else {
        this.lx.engine.tempo.adjustBpm(cc.getRelative());
      }
      return;
    case CUE_LEVEL:
      if (this.focusColor == null) {
        this.focusColor = this.lx.engine.palette.color;
      }
      CompoundParameter subparam = getColorSubparam(this.focusColor.primary);
      subparam.incrementValue(cc.getRelative(), subparam.isWrappable());
      this.colorClipboard = this.focusColor.primary.getColor();
      if (this.gridMode == GridMode.PALETTE) {
        if (this.rainbowMode) {
          sendSwatch(MASTER_SWATCH);
        } else {
          sendSwatches();
        }
      }
      return;
    case CHANNEL_FADER:
      int channel = cc.getChannel();
      if (channel < this.lx.engine.mixer.channels.size()) {
        this.lx.engine.mixer.channels.get(channel).fader.setNormalized(cc.getNormalized());
      }
      return;
    case MASTER_FADER:
      this.lx.engine.output.brightness.setNormalized(cc.getNormalized());
      return;
    case CROSSFADER:
      this.lx.engine.mixer.crossfader.setNormalized(cc.getNormalized());
      return;
    }

    if (number >= DEVICE_KNOB && number <= DEVICE_KNOB_MAX) {
      this.deviceListener.onKnob(number - DEVICE_KNOB, cc.getNormalized());
      return;
    }

    if (number >= CHANNEL_KNOB && number <= CHANNEL_KNOB_MAX) {
      sendControlChange(cc.getChannel(), cc.getCC(), cc.getValue());
      return;
    }

    // LXMidiEngine.error("APC40mk2 UNMAPPED: " + cc);
  }

  @Override
  public void dispose() {
    if (this.isRegistered) {
      unregister();
    }
    this.deviceListener.dispose();
    super.dispose();
  }

}
