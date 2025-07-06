package heronarts.lx.benchmarks;

import java.util.List;

import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

public class BenchmarkSuite {
  static final List<Class<?>> benchmarkTests = List.of(
      AddBlend0005000Points.class,
      AddBlend0100000Points.class,
      AddBlend2000000Points.class
  );

  public static void main(String[] args) throws RunnerException {
    for (Class<?> clazz : benchmarkTests) {
      String simpleName = clazz.getSimpleName();
      System.out.println("\n\n----------------\nPreparing to run: " + simpleName + "\n----------------\n\n");
      String fname = "target/benchmark_" + simpleName + ".json";
      Options opt = new OptionsBuilder()
          .include(simpleName)
          .result(fname)
          .resultFormat(ResultFormatType.JSON)
          .build();
      new Runner(opt).run();
      System.out.println("Results saved to " + fname);
      System.out.println("Upload to https://jmh.morethan.io/ for HTML visualization");
    }
  }
}
