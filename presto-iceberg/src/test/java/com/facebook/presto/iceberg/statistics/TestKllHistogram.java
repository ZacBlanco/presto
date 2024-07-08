/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.facebook.presto.iceberg.statistics;

import com.facebook.presto.common.type.CharType;
import com.facebook.presto.common.type.Type;
import com.facebook.presto.common.type.VarcharType;
import com.google.common.base.VerifyException;
import io.airlift.slice.Slices;
import org.apache.datasketches.common.ArrayOfDoublesSerDe;
import org.apache.datasketches.common.ArrayOfLongsSerDe;
import org.apache.datasketches.common.ArrayOfStringsSerDe;
import org.apache.datasketches.kll.KllItemsSketch;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import static com.facebook.presto.common.type.BigintType.BIGINT;
import static com.facebook.presto.common.type.BooleanType.BOOLEAN;
import static com.facebook.presto.common.type.DateType.DATE;
import static com.facebook.presto.common.type.DecimalType.createDecimalType;
import static com.facebook.presto.common.type.DoubleType.DOUBLE;
import static com.facebook.presto.common.type.IntegerType.INTEGER;
import static com.facebook.presto.common.type.RealType.REAL;
import static com.facebook.presto.common.type.TimeType.TIME;
import static com.facebook.presto.common.type.TimestampType.TIMESTAMP;
import static com.facebook.presto.common.type.TimestampType.TIMESTAMP_MICROSECONDS;
import static com.facebook.presto.common.type.TimestampWithTimeZoneType.TIMESTAMP_WITH_TIME_ZONE;
import static com.facebook.presto.common.type.VarcharType.VARCHAR;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertThrows;

public class TestKllHistogram
{
    private final KllHistogram basicHistogram;

    public TestKllHistogram()
    {
        basicHistogram = generateDoublesHistogram();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testSimpleCreation()
    {
        KllItemsSketch<Double> sketch = KllItemsSketch.newHeapInstance(Double::compareTo, new ArrayOfDoublesSerDe());
        DoubleStream.iterate(0.0, i -> i + 1).limit(100).forEach(sketch::update);
        KllHistogram histogram = new KllHistogram(Slices.wrappedBuffer(ByteBuffer.wrap(sketch.toByteArray())), DOUBLE);
        assertSketchesEqual(histogram.getKllSketch(), sketch);
    }

    @Test
    public void testInverseCumulativeProbMin()
    {
        assertEquals(basicHistogram.inverseCumulativeProbability(0.0).getValue(), 0.0, 1E-8);
    }

    @Test
    public void testInverseCumulativeProbMax()
    {
        assertEquals(basicHistogram.inverseCumulativeProbability(1.0).getValue(), 99.0, 1E-8);
    }

    @Test
    public void testInverseCumulativeMiddle()
    {
        assertEquals(basicHistogram.inverseCumulativeProbability(0.5).getValue(), 49.0, 1E-8);
    }

    @Test
    public void testCumulativeMinInclusive()
    {
        assertEquals(basicHistogram.cumulativeProbability(0.0, true).getValue(), 0.01, 1E-8);
    }

    @Test
    public void testCumulativeMinExclusive()
    {
        assertEquals(basicHistogram.cumulativeProbability(0.0, false).getValue(), 0.0, 1E-8);
    }

    @Test
    public void testCumulativeMidExclusive()
    {
        assertEquals(basicHistogram.cumulativeProbability(49.0, false).getValue(), 0.49, 1E-8);
    }

    @Test
    public void testCumulativeMidInclusive()
    {
        assertEquals(basicHistogram.cumulativeProbability(49.0, true).getValue(), 0.5, 1E-8);
    }

    @Test
    public void testCumulativeMaxExclusive()
    {
        assertEquals(basicHistogram.cumulativeProbability(99.0, false).getValue(), 0.99, 1E-8);
    }

    @Test
    public void testCumulativeMaxInclusive()
    {
        assertEquals(basicHistogram.cumulativeProbability(99.0, true).getValue(), 1.0, 1E-8);
    }

    @DataProvider(name = "kllSupportedTypes")
    public static Object[][] kllHistogramTypeDataProvider()
    {
        return new Object[][] {
                // long decimal (represented by Slice.class), currently not supported
                // {createDecimalType(), TestKllHistogram.generateLongSketch()},
                {INTEGER, TestKllHistogram.generateLongSketch()},
                {BIGINT, TestKllHistogram.generateLongSketch()},
                {DOUBLE, TestKllHistogram.generateDoubleSketch()},
                {createDecimalType(3, 1), TestKllHistogram.generateDoubleSketch()},
                {DATE, TestKllHistogram.generateLongSketch()},
                {createDecimalType(38, 0), TestKllHistogram.generateDoubleSketch()},
                {TIME, generateLongSketch()},
                {TIMESTAMP_WITH_TIME_ZONE, generateLongSketch()},
                {TIMESTAMP, generateLongSketch()},
                {REAL, generateDoubleSketch()},
                {TIMESTAMP_MICROSECONDS, generateLongSketch()},
        };
    }

    @DataProvider(name = "kllUnsupportedTypes")
    public static Object[][] unsupportedKllHistogramTypes()
    {
        return new Object[][] {
                // long decimal (represented by Slice.class), currently not supported
                {CharType.createCharType(0)},
                {CharType.createCharType(100)},
                {BOOLEAN},
                {VARCHAR},
                {VarcharType.createVarcharType(10)}
        };
    }

    @SuppressWarnings("rawtypes")
    @Test(dataProvider = "kllSupportedTypes")
    public void testTypeCreation(Type type, KllItemsSketch sketch)
    {
        KllHistogram histogram = new KllHistogram(Slices.wrappedBuffer(sketch.toByteArray()), type);
        double value = histogram.inverseCumulativeProbability(0.5).getValue();
        double probability = histogram.cumulativeProbability(49.0, true).getValue();
        assertEquals(probability, 0.5);
        assertEquals(value, 49.0);
    }

    @Test(dataProvider = "kllUnsupportedTypes")
    public void testUnsupportedKllTypes(Type type)
    {
        assertThrows(VerifyException.class, () -> {
            new KllHistogram(null, type);
        });
    }

    /**
     * @return generates a histogram of doubles from [0.0, 99.9] in intervals of 1.0
     */
    private static KllHistogram generateDoublesHistogram()
    {
        return new KllHistogram(Slices.wrappedBuffer(ByteBuffer.wrap(generateDoubleSketch().toByteArray())), DOUBLE);
    }

    private static KllItemsSketch<Long> generateLongSketch()
    {
        KllItemsSketch<Long> sketch = KllItemsSketch.newHeapInstance(Long::compareTo, new ArrayOfLongsSerDe());
        LongStream.iterate(0, i -> i + 1).limit(100).forEach(sketch::update);
        return sketch;
    }

    private static KllItemsSketch<Double> generateDoubleSketch()
    {
        KllItemsSketch<Double> sketch = KllItemsSketch.newHeapInstance(Double::compareTo, new ArrayOfDoublesSerDe());
        DoubleStream.iterate(0.0, i -> i + 1).limit(100).forEach(sketch::update);
        return sketch;
    }

    private static KllItemsSketch<String> generateStringSketch(int maxLen)
    {
        KllItemsSketch<String> sketch = KllItemsSketch.newHeapInstance(String::compareTo, new ArrayOfStringsSerDe());
        List<String> strings = LongStream.iterate(0, i -> i + 1).boxed()
                .limit(100)
                .map(idx -> IntStream.iterate(0, i -> 1 + 1)
                        .limit(ThreadLocalRandom.current().nextInt(1, maxLen))
                        .mapToObj(i -> 'a' + (char) (ThreadLocalRandom.current().nextInt() % 26))
                        .collect(Collector.of(StringBuilder::new,
                                StringBuilder::append,
                                StringBuilder::append,
                                StringBuilder::toString)))
                .collect(Collectors.toList());
        strings.forEach(sketch::update);
        return sketch;
    }

    private static <T> void assertSketchesEqual(KllItemsSketch<T> sketch, KllItemsSketch<T> other)
    {
        assertEquals(other.getK(), sketch.getK());
        assertEquals(other.getN(), sketch.getN());
        assertEquals(other.getMinItem(), sketch.getMinItem());
        assertEquals(other.getMaxItem(), sketch.getMaxItem());
        assertEquals(other.getSortedView().getCumulativeWeights(), sketch.getSortedView().getCumulativeWeights());
        assertEquals(other.getSortedView().getQuantiles(), sketch.getSortedView().getQuantiles());
    }
}
