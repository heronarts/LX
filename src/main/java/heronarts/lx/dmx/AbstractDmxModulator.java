package heronarts.lx.dmx;

import heronarts.lx.modulator.LXModulator;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.utils.LXUtils;

/**
 * Abstract DMX Modulator specifying a universe, channel, and number of bytes.
 * The channel value is restricted to fit all bytes within an ArtNet universe.
 */
public abstract class AbstractDmxModulator extends LXModulator {

  public final DiscreteParameter universe =
    new DiscreteParameter("Universe", 0, LXDmxEngine.MAX_UNIVERSE)
    .setDescription("DMX universe");

  public final DiscreteParameter channel =
    new DiscreteParameter("Channel", 0, LXDmxEngine.MAX_CHANNEL)
    .setDescription("DMX channel");

  private int bytes = 1;

  private static int constrainBytes(int bytes) {
    return LXUtils.constrain(bytes, 1, LXDmxEngine.MAX_CHANNEL);
  }

  public AbstractDmxModulator(String label) {
    this(label, 1);
  }

  public AbstractDmxModulator(String label, int bytes) {
    super(label);
    setBytes(bytes);
    addParameter("universe", this.universe);
    addParameter("channel", this.channel);
  }

  protected AbstractDmxModulator setBytes(int bytes) {
    bytes = constrainBytes(bytes);
    if (bytes != this.bytes) {
      this.bytes = bytes;
      updateMaxChannel();
    }
    return this;
  }

  private void updateMaxChannel() {
    this.channel.setRange(0, LXDmxEngine.MAX_CHANNEL - (this.bytes - 1));
  }

}
