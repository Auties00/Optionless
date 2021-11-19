package it.auties.main;

import org.openjdk.jmh.annotations.*;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.LongStream;

@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 2)
@Measurement(iterations = 5)
public class OptionBenchmark {

    private final long MAGIC_NUMBER = 7;

    // Variant 1.
    // Probably the simplest way to sum numbers.
    // No boxing, no objects involved, just primitive long values everywhere.
    // This is probably what a C-programmer converted to Java would write ;)
    private long getNumber(long i) {
        return i & 0xFF;
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public long sumSimple() {
        long sum = 0;
        for (long i = 0; i < 1_000_000; ++i) {
            long n = getNumber(i);
            if (n != MAGIC_NUMBER)
                sum += n;
        }
        return sum;
    }

    // Variant 2.
    // Replace MAGIC_NUMBER with a null.
    // To be able to return null, we need to box long into a Long object.
    private Long getNumberOrNull(long i) {
        long n = i & 0xFF;
        return n == MAGIC_NUMBER ? null : n;
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public long sumNulls() {
        long sum = 0;
        for (long i = 0; i < 1_000_000; ++i) {
            Long n = getNumberOrNull(i);
            if (n != null) {
                sum += n;
            }
        }
        return sum;
    }

    // Variant 3.
    // Replace MAGIC_NUMBER with Optional.empty().
    // Now we not only need to box the value into a Long, but also create the Optional wrapper.
    private long getOptionalNumber(long i) {
        return Optional.of(i)
                .map((number) -> number & 0xFF)
                .filter(number -> number != MAGIC_NUMBER)
                .orElse(0L);
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public long sumOptional() {
        return LongStream.range(0, 1_000_000).map(this::getOptionalNumber).sum();
    }
}