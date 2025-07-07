package heronarts.lx.benchmarks;

import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Timeout;


@BenchmarkMode(Mode.All)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Fork(value = 1, warmups = 1)
@Timeout(time = 10, timeUnit = TimeUnit.SECONDS)
public class AddBlend0005000Points extends BlendingHarness {
  static final int NUM_CHANNELS = 16;
  static final int NUM_POINTS_PER_CHANNEL = 5_000;

  @Setup(Level.Trial)
  public void setupWholeTrial() {
    setupTrialBase(NUM_CHANNELS, NUM_POINTS_PER_CHANNEL);
  }

  @Benchmark
  public void measureLXBlend() {
    blendToTest.blend(dst, src, alpha, actual, model);
  }

  
}
