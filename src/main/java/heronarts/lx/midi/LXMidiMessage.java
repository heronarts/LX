package heronarts.lx.midi;

/**
 * Common interface for messages that are passed through the midi input queue
 */
public interface LXMidiMessage {

  public LXMidiMessage setSource(LXMidiSource source);

  public LXMidiSource getSource();

  public default LXMidiInput getInput() {
    LXMidiSource source = getSource();
    return (source instanceof LXMidiInput) ? (LXMidiInput) source : null;
  }

  public void dispatch(LXMidiListener listener);

}
