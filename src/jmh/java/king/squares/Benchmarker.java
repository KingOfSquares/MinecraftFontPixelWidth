package king.squares;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import king.squares.fontwidth.FontWidthFunction;
import net.kyori.adventure.text.format.Style;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

@Fork(value = 1, warmups = 1)
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
public class Benchmarker {

  private final FontWidthFunction f = FontWidthFunction.INSTANCE;
  private final int[] testSet = "ABCDEFG12436!#$&/←⇧□■ᬐ᭗㔇㔸".codePoints().toArray();

  @Benchmark
  public float testFunction() throws IOException {
    float width = 0;
    for (final int i : this.testSet) width += this.f.widthOf(i, Style.empty());
    return width;
  }
}
