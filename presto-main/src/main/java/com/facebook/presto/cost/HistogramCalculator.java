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

package com.facebook.presto.cost;

import com.facebook.presto.spi.statistics.ConnectorHistogram;
import com.facebook.presto.spi.statistics.Estimate;

import java.util.Optional;

import static java.lang.Double.isFinite;
import static java.lang.Double.isNaN;
import static java.lang.Math.max;
import static java.lang.Math.min;

public class HistogramCalculator
{
    private HistogramCalculator()
    {}

    /**
     * Calculates the "filter factor" corresponding to the overlap between the statistic range
     * and the histogram distribution.
     * <br>
     * The filter factor is a value between 0 and 1 that represents the proportion of tuples in the
     * source column that would be included from the source column if the source column only
     * included values that existed in the {@code range} parameter of this function.
     *
     * @param range the intersecting range with the histogram
     * @param histogram the source histogram
     *
     * @return an estimate, x,  where 0.0 <= x <= 1.0.
     */
    public static Estimate calculateFilterFactor(StatisticRange range, ConnectorHistogram histogram)
    {
        Estimate min = histogram.inverseCumulativeProbability(0.0);
        Estimate max = histogram.inverseCumulativeProbability(1.0);

        // range is either above or below histogram
        if ((!max.isUnknown() && max.getValue() < range.getLow())
                || (!min.isUnknown() && min.getValue() > range.getHigh())) {
            return Estimate.of(0.0);
        }

        // one of the max/min bounds can't be determined
        if ((max.isUnknown() && !min.isUnknown()) || (!max.isUnknown() && min.isUnknown())) {
            if (range.length() == 0.0) {
                return histogram.cumulativeDistinctValues(1.0).map(distinct -> 1.0 / distinct);
            }
            if (isFinite(range.length())) {
                return Estimate.of(StatisticRange.INFINITE_TO_FINITE_RANGE_INTERSECT_OVERLAP_HEURISTIC_FACTOR);
            }
            return Estimate.of(StatisticRange.INFINITE_TO_INFINITE_RANGE_INTERSECT_OVERLAP_HEURISTIC_FACTOR);
        }

        Estimate lowPercentile = histogram.cumulativeProbability(range.getLow());
        Estimate highPercentile = histogram.cumulativeProbability(range.getHigh());

        // both bounds are probably infinity, use the infinite-infinite heuristic
        if (lowPercentile.isUnknown() || highPercentile.isUnknown()) {
            // in the case the histogram has no values
            if (histogram.cumulativeDistinctValues(1.0).equals(Estimate.zero()) || range.getDistinctValuesCount() == 0.0) {
                return Estimate.of(0.0);
            }

            // in the case only one is unknown
            if (((lowPercentile.isUnknown() && !highPercentile.isUnknown()) ||
                    (!lowPercentile.isUnknown() && highPercentile.isUnknown())) &&
                    isFinite(range.length())) {
                return Estimate.of(StatisticRange.INFINITE_TO_FINITE_RANGE_INTERSECT_OVERLAP_HEURISTIC_FACTOR);
            }

            if (range.length() == 0.0) {
                return histogram.cumulativeDistinctValues(1.0).map(distinct -> 1.0 / distinct);
            }

            if (!isNaN(range.getDistinctValuesCount())) {
                return histogram.cumulativeDistinctValues(1.0).map(distinct -> min(1.0, range.getDistinctValuesCount() / distinct));
            }

            return Estimate.of(StatisticRange.INFINITE_TO_INFINITE_RANGE_INTERSECT_OVERLAP_HEURISTIC_FACTOR);
        }

        // in the case the range is a single value, this can occur if the input
        // filter range is a single value (low == high) OR in the case that the
        // bounds of the filter or this histogram are infinite.
        // in the case of infinite bounds, we should return an estimate that
        // correlates to the overlapping distinct values.
        if (lowPercentile.equals(highPercentile)) {
            // If one of the bounds is unknown, but both percentiles are equal,
            // it's likely that a heuristic value was returned
            if (max.isUnknown() || min.isUnknown()) {
                return histogram.cumulativeDistinctValues(1.0).flatMap(distinct -> lowPercentile.map(lowPercent -> distinct * lowPercent));
            }

            return histogram.cumulativeDistinctValues(1.0).map(distinct -> 1.0 / distinct);
        }

        // in the case that we return the entire range, the returned factor percent should be
        // proportional to the number of distinct values in the range
        if (lowPercentile.equals(Estimate.zero()) && highPercentile.equals(Estimate.of(1.0)) && min.isUnknown() && max.isUnknown()) {
            return histogram.cumulativeDistinctValues(1.0).map(totalDistinct -> min(1.0, range.getDistinctValuesCount() / totalDistinct))
                    // in the case cumulativeDistinctValues(1.0) is NaN
                    .or(() -> Estimate.of(StatisticRange.INFINITE_TO_INFINITE_RANGE_INTERSECT_OVERLAP_HEURISTIC_FACTOR));
        }

        return Optional.of(lowPercentile)
                .filter(x -> !x.isUnknown())
                .map(Estimate::getValue)
                .map(lowPercent -> {
                    return Optional.of(highPercentile)
                            .filter(x -> !x.isUnknown())
                            .map(Estimate::getValue)
                            .map(highPercent -> highPercent - lowPercent)
                            .map(Estimate::of)
                            .orElseGet(() -> Estimate.of(1.0));
                }).orElse(highPercentile);
    }

    /**
     * Calculates the percent of overlap that occurs on the {@code source} parameter by the
     * {@code range} parameter based purely on value bounds.
     * <br>
     * For example: [0,1] overlaps [0, 10] from 0 --> 1 on a number line, representing 10% of the
     * source range. Thus, the value returned here would be 10%.
     * <br>
     * This function is similar to {@link HistogramCalculator#calculateFilterFactor(StatisticRange, ConnectorHistogram)}
     * except that it does not return heuristics and only considers range values to calculate the
     * overlapping proportion of the ranges.
     */
    public static Estimate calculateRangeOverlap(StatisticRange range, ConnectorHistogram source)
    {
        Estimate lowValue = source.inverseCumulativeProbability(0.0);
        Estimate highValue = source.inverseCumulativeProbability(1.0);

        // Attempt to return any kind of estimate first
        if ((!lowValue.isUnknown() && range.getHigh() <= lowValue.getValue()) ||
                (!highValue.isUnknown() && range.getLow() >= highValue.getValue())) {
            return Estimate.of(0.0);
        }

        // return unknown if either bound is NaN as {@link Estimate} will throw
        // an error if we create a value as NaN. Also, use StatisticRange#length
        // rather than StatisticRange#isEmpty because this will return NaN even
        // if only one of the bounds is NaN.
        if (isNaN(range.length())) {
            return Estimate.unknown();
        }

        Estimate sourceLength = highValue.flatMap(high -> lowValue.map(low -> high - low));
        Estimate overlapLow = lowValue.map(low -> max(range.getLow(), low));
        Estimate overlapHigh = highValue.map(high -> min(range.getHigh(), high));
        Estimate overlapLength = overlapHigh.flatMap(high -> overlapLow.map(low -> high - low));
        return overlapLength.flatMap(overlap -> {
            if (overlap < 0) {
                return Estimate.of(0.0);
            }
            return sourceLength.map(src -> {
                // in the case of src representing a single-value domain
                if (overlap == 0.0 && src == 0.0) {
                    return 1.0;
                }
                return overlap / src;
            });
        });
    }
}
