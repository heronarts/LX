package heronarts.lx.midi;

/**
 * Common interface for messages that are passed through the midi input queue
 */
public interface LXMidiMessage {

  LXMidiMessage setSource(LXMidiSource source);

  LXMidiSource getSource();

  LXMidiInput getInput();  // Clashes with non-public getInput() methods

  void dispatch(LXMidiListener listener);

}
