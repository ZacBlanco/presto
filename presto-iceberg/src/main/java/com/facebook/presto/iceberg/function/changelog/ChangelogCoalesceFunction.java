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
package com.facebook.presto.iceberg.function.changelog;

import com.facebook.presto.bytecode.DynamicClassLoader;
import com.facebook.presto.common.block.Block;
import com.facebook.presto.common.block.BlockBuilder;
import com.facebook.presto.common.type.BigintType;
import com.facebook.presto.common.type.Type;
import com.facebook.presto.common.type.VarcharType;
import com.facebook.presto.metadata.BoundVariables;
import com.facebook.presto.metadata.FunctionAndTypeManager;
import com.facebook.presto.metadata.SqlAggregationFunction;
import com.facebook.presto.operator.aggregation.AccumulatorCompiler;
import com.facebook.presto.operator.aggregation.BuiltInAggregationFunctionImplementation;
import com.facebook.presto.spi.function.AccumulatorState;
import com.facebook.presto.spi.function.AccumulatorStateFactory;
import com.facebook.presto.spi.function.AccumulatorStateSerializer;
import com.facebook.presto.spi.function.FunctionInstance;
import com.facebook.presto.spi.function.aggregation.Accumulator;
import com.facebook.presto.spi.function.aggregation.AggregationMetadata;
import com.facebook.presto.spi.function.aggregation.GroupedAccumulator;
import com.google.common.collect.ImmutableList;
import io.airlift.slice.Slice;

import java.lang.invoke.MethodHandle;
import java.util.List;
import java.util.Optional;

import static com.facebook.presto.common.type.StandardTypes.BIGINT;
import static com.facebook.presto.common.type.StandardTypes.VARCHAR;
import static com.facebook.presto.common.type.TypeSignature.parseTypeSignature;
import static com.facebook.presto.operator.aggregation.AggregationUtils.generateAggregationName;
import static com.facebook.presto.spi.function.Signature.typeVariable;
import static com.facebook.presto.spi.function.aggregation.AggregationMetadata.ParameterMetadata.ParameterType.BLOCK_INDEX;
import static com.facebook.presto.spi.function.aggregation.AggregationMetadata.ParameterMetadata.ParameterType.BLOCK_INPUT_CHANNEL;
import static com.facebook.presto.spi.function.aggregation.AggregationMetadata.ParameterMetadata.ParameterType.INPUT_CHANNEL;
import static com.facebook.presto.spi.function.aggregation.AggregationMetadata.ParameterMetadata.ParameterType.STATE;
import static com.facebook.presto.util.Reflection.methodHandle;
import static com.google.common.collect.ImmutableList.toImmutableList;

public class ChangelogCoalesceFunction
        extends SqlAggregationFunction
{
    @FunctionInstance
    public static final ChangelogCoalesceFunction CHANGELOG_COALESCE_FUNCTION = new ChangelogCoalesceFunction();
    private static final String NAME = "coalesce_changelog";

    private static final MethodHandle INPUT_FUNCTION = methodHandle(ChangelogCoalesceFunction.class, "input", Type.class, ChangelogCoalesceState.class, long.class, Slice.class, int.class, Block.class);
    private static final MethodHandle COMBINE_FUNCTION = methodHandle(ChangelogCoalesceFunction.class, "combine", Type.class, ChangelogCoalesceState.class, ChangelogCoalesceState.class);
    private static final MethodHandle OUTPUT_FUNCTION = methodHandle(ChangelogCoalesceFunction.class, "output", Type.class, ChangelogCoalesceState.class, BlockBuilder.class);

    protected ChangelogCoalesceFunction()
    {
        super(NAME, ImmutableList.of(typeVariable("T")),
                ImmutableList.of(),
                parseTypeSignature("T"),
                ImmutableList.of(parseTypeSignature(BIGINT), parseTypeSignature(VARCHAR), parseTypeSignature("T")));
    }

    private static BuiltInAggregationFunctionImplementation generateAggregation(Type type)
    {
        DynamicClassLoader classLoader = new DynamicClassLoader(ChangelogCoalesceFunction.class.getClassLoader());
        AccumulatorStateSerializer<?> stateSerializer = new ChangelogCoalesceStateSerializer(type);
        AccumulatorStateFactory<?> stateFactory = new ChangelogCoalesceStateFactory(type);
        List<Type> inputTypes = ImmutableList.of(BigintType.BIGINT, VarcharType.VARCHAR, type);
        Type intermediateType = stateSerializer.getSerializedType();
        List<AggregationMetadata.ParameterMetadata> inputParameterMetadata = createInputParameterMetadata(type);
        MethodHandle inputFunction = INPUT_FUNCTION.bindTo(type);
        MethodHandle combineFunction = COMBINE_FUNCTION.bindTo(type);
        MethodHandle outputFunction = OUTPUT_FUNCTION.bindTo(type);
        Class<? extends AccumulatorState> stateInterface = ChangelogCoalesceState.class;

        AggregationMetadata metadata = new AggregationMetadata(
                generateAggregationName(NAME, type.getTypeSignature(), inputTypes.stream().map(Type::getTypeSignature).collect(toImmutableList())),
                inputParameterMetadata,
                inputFunction,
                combineFunction,
                outputFunction,
                ImmutableList.of(new AggregationMetadata.AccumulatorStateDescriptor(
                        stateInterface,
                        stateSerializer,
                        stateFactory)),
                type);

        Class<? extends Accumulator> accumulatorClass = AccumulatorCompiler.generateAccumulatorClass(
                Accumulator.class,
                metadata,
                classLoader);
        Class<? extends GroupedAccumulator> groupedAccumulatorClass = AccumulatorCompiler.generateAccumulatorClass(
                GroupedAccumulator.class,
                metadata,
                classLoader);

        return new BuiltInAggregationFunctionImplementation(NAME, inputTypes, ImmutableList.of(intermediateType), type,
                true, false, metadata, accumulatorClass, groupedAccumulatorClass);
    }

    private static List<AggregationMetadata.ParameterMetadata> createInputParameterMetadata(Type type)
    {
        return ImmutableList.of(
                new AggregationMetadata.ParameterMetadata(STATE),
                new AggregationMetadata.ParameterMetadata(INPUT_CHANNEL, BigintType.BIGINT),
                new AggregationMetadata.ParameterMetadata(INPUT_CHANNEL, VarcharType.VARCHAR),
                new AggregationMetadata.ParameterMetadata(BLOCK_INDEX),
                new AggregationMetadata.ParameterMetadata(BLOCK_INPUT_CHANNEL, type));
    }

    public static void input(Type type, ChangelogCoalesceState state, long ordinal, Slice operation, int position, Block value)
    {
        ChangelogRecord record = state.get();
        if (record == null) {
            record = new ChangelogRecord(type);
        }
        record.add((int) ordinal, operation, value.getSingleValueBlock(position));
        state.set(record);
    }

    public static void combine(Type type, ChangelogCoalesceState state, ChangelogCoalesceState otherState)
    {
        ChangelogRecord r = Optional.ofNullable(state.get())
                .map(actual -> Optional.ofNullable(otherState.get()).map(actual::merge).orElse(actual)).orElse(otherState.get());
        state.set(r);
    }

    public static void output(Type elementType, ChangelogCoalesceState state, BlockBuilder out)
    {
        ChangelogRecord record = state.get();
        if (record == null) {
            out.appendNull();
            return;
        }

        if (record.getLastOperation().toStringUtf8().equalsIgnoreCase("DELETE")) {
            out.appendNull();
        }
        else {
            elementType.appendTo(record.getRow(), 0, out);
        }
    }

    @Override
    public BuiltInAggregationFunctionImplementation specialize(BoundVariables boundVariables, int arity, FunctionAndTypeManager functionAndTypeManager)
    {
        Type type = boundVariables.getTypeVariable("T");
        return generateAggregation(type);
    }

    @Override
    public String getDescription()
    {
        return "coalesces a set of records from a changelog into a single update or delete. Writes null if the record is deleted";
    }

    public interface State
            extends AccumulatorState
    {
        ChangelogRecord get();

        void set(ChangelogRecord value);
    }
}
