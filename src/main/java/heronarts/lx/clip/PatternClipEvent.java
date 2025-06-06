package heronarts.lx.clip;

import com.google.gson.JsonObject;

import heronarts.lx.LX;
import heronarts.lx.pattern.LXPattern;

public class PatternClipEvent extends LXClipEvent<PatternClipEvent> {

  private final PatternClipLane lane;
  private LXPattern pattern;

  public PatternClipEvent(PatternClipLane lane, Cursor cursor, int patternIndex) {
    this(lane, lane.channel.patterns.get(patternIndex));
    setCursor(cursor);
  }

  PatternClipEvent(PatternClipLane lane, LXPattern pattern) {
    super(lane, pattern);
    this.lane = lane;
    this.pattern = pattern;
  }

  PatternClipEvent(PatternClipLane lane, Cursor cursor, LXPattern pattern) {
    this(lane, pattern);
    setCursor(cursor);
  }

  public LXPattern getPattern() {
    return this.pattern;
  }

  public PatternClipEvent setPattern(LXPattern pattern) {
    if (this.pattern != pattern) {
      this.pattern = pattern;
      this.lane.onChange.bang();
    }
    return this;
  }

  @Override
  public void execute() {
    this.lane.playPatternEvent(this);
  }

  protected static final String KEY_PATTERN_INDEX = "patternIndex";

  @Override
  public void save(LX lx, JsonObject obj) {
    super.save(lx, obj);
    obj.addProperty(KEY_PATTERN_INDEX, this.pattern.getIndex());
  }

}
