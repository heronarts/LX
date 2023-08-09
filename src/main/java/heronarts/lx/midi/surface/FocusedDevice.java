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

import java.util.HashMap;
import java.util.Map;

import heronarts.lx.LX;
import heronarts.lx.LXDeviceComponent;
import heronarts.lx.effect.LXEffect;
import heronarts.lx.mixer.LXBus;
import heronarts.lx.mixer.LXChannel;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.LXListenableParameter;
import heronarts.lx.parameter.LXParameterListener;
import heronarts.lx.pattern.LXPattern;

/**
 * Utility class for a control surface to subscribe to notifications about which
 * device is focused in the UI. This needs to be registered and unregistered as
 * appropriate to enable/disable notifications. And the listener interface is invoked
 * whenever there is a device focus change.
 */
public class FocusedDevice {

  public interface Listener {
    public void onDeviceFocused(LXDeviceComponent device);
  }

  private final LX lx;
  private final LXMidiSurface surface;
  private final Listener listener;

  private LXBus bus = null;
  private LXDeviceComponent device = null;

  private final Map<LXListenableParameter, LXParameterListener> listeners =
    new HashMap<LXListenableParameter, LXParameterListener>();

  private boolean isAux = false;
  private boolean isAuxSticky = false;

  public FocusedDevice(LX lx, Listener listener) {
    this(lx, null, listener);
  }

  public FocusedDevice(LX lx, LXMidiSurface surface, Listener listener) {
    this.lx = lx;
    this.surface = surface;
    this.listener = listener;
  }

  public boolean isAux() {
    return this.isAux;
  }

  public FocusedDevice toggleAux() {
    return setAux(!this.isAux);
  }

  public FocusedDevice setAux(boolean isAux) {
    this.isAux = isAux;
    onChannelFocusChange();
    updateRemoteControlFocus();
    return this;
  }

  public FocusedDevice setAuxSticky(boolean isAuxSticky) {
    this.isAuxSticky = isAuxSticky;
    return this;
  }

  private void addListener(LXListenableParameter parameter, LXParameterListener listener) {
    if (this.listeners.containsKey(parameter)) {
      throw new IllegalArgumentException("Cannot add duplicate parameter listener for " + parameter);
    }
    parameter.addListener(listener);
    this.listeners.put(parameter, listener);
  }

  private void removeListener(LXListenableParameter parameter) {
    LXParameterListener listener = this.listeners.remove(parameter);
    if (listener == null) {
      throw new IllegalArgumentException("Cannot remove non-existent listener for " + parameter);
    }
    parameter.removeListener(listener);
  }

  private boolean isRegistered = false;

  /**
   * Register the focused device listener, which will result in a listener callback to the
   * presently focused device.
   */
  public void register() {
    if (this.isRegistered) {
      throw new IllegalStateException("Cannot double-register FocusedDevice");
    }
    addListener(this.lx.engine.performanceMode, _onChannelFocusChange);
    addListener(this.lx.engine.mixer.focusedChannel, _onChannelFocusChange);
    addListener(this.lx.engine.mixer.focusedChannelAux, _onChannelFocusChange);

    registerBus(getFocusedChannel());
    this.isRegistered = true;
  }

  /**
   * Unregister the focus listener. Will clear the focused device and alert the listener to
   * null focus before clearing all internal listeners.
   */
  public void unregister() {
    if (!this.isRegistered) {
      throw new IllegalStateException("Cannot unregister non-registered FocusedDevice");
    }
    unregisterDevice(true);
    unregisterBus();
    for (Map.Entry<LXListenableParameter, LXParameterListener> entry : this.listeners.entrySet()) {
      entry.getKey().removeListener(entry.getValue());
    }
    this.listeners.clear();
    this.isRegistered = false;
  }

  /**
   * Returns the currently focused bus
   *
   * @return Current bus focus
   */
  public LXBus getFocusedChannel() {
    int channel = getFocusedChannelTarget().getValuei();
    if (channel == lx.engine.mixer.channels.size()) {
      return lx.engine.mixer.masterBus;
    }
    return lx.engine.mixer.channels.get(channel);
  }

  public boolean isAuxActive() {
    return this.isAux && (this.isAuxSticky || this.lx.engine.performanceMode.isOn());
  }

  public DiscreteParameter getFocusedChannelTarget() {
    if (isAuxActive()) {
      return this.lx.engine.mixer.focusedChannelAux;
    } else {
      return this.lx.engine.mixer.focusedChannel;
    }
  }

  private final LXParameterListener _onChannelFocusChange = p -> { onChannelFocusChange(); };

  private void onChannelFocusChange() {
    registerBus(getFocusedChannel());
  }

  private boolean isBusEffect(LXDeviceComponent device) {
    return (device instanceof LXEffect) && ((LXEffect) device).isBusEffect();
  }

  private void registerBus(LXBus bus) {
    if (this.bus != bus) {
      unregisterBus();
      this.bus = bus;
      if (bus != null) {
        if (bus instanceof LXChannel) {
          LXChannel channel = (LXChannel) bus;
          channel.addListener(this.channelListener);
          addListener(channel.focusedPattern, p -> {
            // Switch to focused pattern unless focus was on
            // a bus effect
            if (!isBusEffect(this.device)) {
              registerDevice(channel.getFocusedPattern());
            }
          });
        } else {
          bus.addListener(this.channelListener);
        }
      }
      registerDefaultBusDevice();
    }
  }

  private void unregisterBus() {
    if (this.bus != null) {
      if (this.bus instanceof LXChannel) {
        LXChannel channel = (LXChannel) bus;
        channel.removeListener(this.channelListener);
        removeListener(channel.focusedPattern);
      } else {
        this.bus.removeListener(this.channelListener);
      }
      this.bus = null;
    }
  }

  private void registerDefaultBusDevice() {
    if (this.bus instanceof LXChannel) {
      final LXPattern focusedPattern = ((LXChannel) this.bus).getFocusedPattern();
      if (focusedPattern != null) {
        registerDevice(focusedPattern);
        return;
      }
    }
    if (!this.bus.effects.isEmpty()) {
      registerDevice(this.bus.effects.get(0));
    } else {
      registerDevice(null);
    }
  }

  private final LXChannel.Listener channelListener = new LXChannel.Listener() {
    @Override
    public void effectRemoved(LXBus channel, LXEffect effect) {
      if (device == effect) {
        registerDefaultBusDevice();
      }
    }

    @Override
    public void patternRemoved(LXChannel channel, LXPattern pattern) {
      if (device == pattern) {
        registerDefaultBusDevice();
      }
    }
  };

  public void previousChannel() {
    getFocusedChannelTarget().decrement();
  }

  public void nextChannel() {
    getFocusedChannelTarget().increment();
  }

  /**
   * Shift focus to the previous device on the channel, if there is one
   */
  public void previousDevice() {
    if (this.bus == null) {
      return;
    }
    if (this.device instanceof LXEffect) {
      final LXEffect effect = (LXEffect) this.device;
      final int effectIndex = effect.getIndex();
      if (effectIndex > 0) {
        if (effect.isBusEffect()) {
          registerDevice(effect.getBus().getEffect(effectIndex - 1));
        } else {
          registerDevice(effect.getPattern().getEffect(effectIndex - 1));
        }
      } else if (this.bus instanceof LXChannel) {
        LXChannel channel = (LXChannel) this.bus;
        LXPattern pattern = channel.getFocusedPattern();
        if (pattern != null) {
          if (effect.isBusEffect() && !pattern.effects.isEmpty()) {
            // We've fallen off the first bus effect and should land on the
            // final pattern effect
            registerDevice(pattern.effects.get(pattern.effects.size() - 1));
          } else {
            // Fallen off bus effects onto the pattern
            registerDevice(pattern);
          }
        }
      }
    }
  }

  /**
   * Shift focus to the next device on the channel, if there is one
   */
  public void nextDevice() {
    if (this.bus == null) {
      return;
    }
    if (this.device instanceof LXEffect) {
      final LXEffect effect = (LXEffect) this.device;
      final int nextIndex = effect.getIndex() + 1;
      if (effect.isPatternEffect()) {
        final LXPattern pattern = effect.getPattern();
        if (nextIndex < pattern.effects.size()) {
          // There are more pattern effects
          registerDevice(pattern.effects.get(nextIndex));
        } else if (!this.bus.effects.isEmpty()) {
          // Go to the first bus effect
          registerDevice(this.bus.effects.get(0));
        }
      } else if (nextIndex < this.bus.effects.size()) {
        // Go to the next bus effect
        registerDevice(this.bus.effects.get(nextIndex));
      }
    } else {
      if (this.bus instanceof LXChannel) {
        // We're focused on the pattern, move to its first effect, or the first bus effect
        final LXPattern focusedPattern = ((LXChannel) this.bus).getFocusedPattern();
        if ((focusedPattern != null) && !focusedPattern.effects.isEmpty()) {
          registerDevice(focusedPattern.effects.get(0));
        } else if (!this.bus.effects.isEmpty()) {
          registerDevice(this.bus.effects.get(0));
        }
      } else if (!this.bus.effects.isEmpty()) {
        // Focus first bus effect
        registerDevice(this.bus.effects.get(0));
      }
    }
  }

  /**
   * Returns the device presently focused, if any
   *
   * @return Focused device, or <code>null</code>
   */
  public LXDeviceComponent getDevice() {
    return this.device;
  }

  public void updateRemoteControlFocus() {
    if (this.device != null && this.surface != null) {
      this.device.controlSurfaceSemaphore.bang();
    }
  }

  private void registerDevice(LXDeviceComponent device) {
    if (this.device != device) {
      unregisterDevice(false);
      this.device = device;
      this.listener.onDeviceFocused(device);
      if (this.device != null && this.surface != null) {
        this.device.addControlSurface(this.surface);
      }
    }
  }

  private void unregisterDevice(boolean notify) {
    if (this.device != null) {
      if (this.surface != null) {
        this.device.removeControlSurface(this.surface);
      }
      this.device = null;
      if (notify) {
        this.listener.onDeviceFocused(null);
      }
    }
  }

  public void dispose() {
    if (this.isRegistered) {
      unregister();
    }
  }
}
