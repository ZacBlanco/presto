package com.facebook.presto.operator.aggregation.estimatendv;

import com.facebook.presto.common.block.BlockBuilder;
import com.facebook.presto.common.type.BigintType;
import com.facebook.presto.common.type.StandardTypes;
import com.facebook.presto.spi.function.AggregationFunction;
import com.facebook.presto.spi.function.AggregationState;
import com.facebook.presto.spi.function.CombineFunction;
import com.facebook.presto.spi.function.Description;
import com.facebook.presto.spi.function.InputFunction;
import com.facebook.presto.spi.function.OutputFunction;
import com.facebook.presto.spi.function.SqlType;

@AggregationFunction("ndv_estimator")
@Description("Estimate ndv based on frequencies of distinct values in the sample")

public final class NDVEstimatorFunction
{

    @InputFunction
    public static void input(
            @AggregationState NDVEstimatorState state,
            @SqlType(StandardTypes.BIGINT) long freq)
    {
        state.add(freq);
    }

    @CombineFunction
    public static void combine(
            @AggregationState NDVEstimatorState state,
            @AggregationState NDVEstimatorState otherState)
    {
        state.merge(otherState);
    }

    @OutputFunction("bigint")
    public static void output(@AggregationState NDVEstimatorState state, BlockBuilder out)
    {
        BigintType.BIGINT.writeLong(out, state.estimate());
    }
}
