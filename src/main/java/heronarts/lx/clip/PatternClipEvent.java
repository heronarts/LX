package heronarts.lx.clip;

import com.google.gson.JsonObject;

import heronarts.lx.LX;
import heronarts.lx.pattern.LXPattern;

public class PatternClipEvent extends LXClipEvent<PatternClipEvent> {

  private final PatternClipLane lane;
  private LXPattern pattern;

  PatternClipEvent(PatternClipLane lane, LXPattern pattern) {
    super(lane, pattern);
    this.lane = lane;
    this.pattern = pattern;
  }

  public LXPattern getPattern() {
    return this.pattern;
  }

  public PatternClipEvent setPattern(LXPattern pattern) {
    this.pattern = pattern;
    this.lane.onChange.bang();
    return this;
  }

  @Override
  public void execute() {
    this.lane.channel.goPattern(this.pattern);
  }

  protected static final String KEY_PATTERN_INDEX = "patternIndex";

  @Override
  public void save(LX lx, JsonObject obj) {
    super.save(lx, obj);
    obj.addProperty(KEY_PATTERN_INDEX, this.pattern.getIndex());
  }

}
