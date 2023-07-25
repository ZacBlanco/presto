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
package com.facebook.presto.operator.aggregation.reservoirsample;

import com.facebook.presto.common.block.Block;
import com.facebook.presto.common.block.BlockBuilder;
import com.facebook.presto.common.type.ArrayType;
import com.facebook.presto.common.type.Type;
import com.facebook.presto.operator.aggregation.arrayagg.ArrayAggregationState;
import com.facebook.presto.operator.aggregation.state.SetAggregationState;
import com.facebook.presto.spi.function.AccumulatorStateSerializer;

public class ReservoirSampleStateSerializer implements AccumulatorStateSerializer<ReservoirSampleState> {

    private final Type elementType;
    private final Type arrayType;

    public ReservoirSampleStateSerializer(Type elementType)
    {
        this.elementType = elementType;
        this.arrayType = new ArrayType(elementType);
    }

    @Override
    public Type getSerializedType()
    {
        return arrayType;
    }

    @Override
    public void serialize(ReservoirSampleState state, BlockBuilder out)
    {
        Block[] samples = state.getSamples();
        if (state.isEmpty()) {
            out.appendNull();
        }
        else {
            BlockBuilder entryBuilder = out.beginBlockEntry();
            for (int i = 0; i < samples.length; i++) {
                elementType.appendTo(samples[i], 0, entryBuilder);
            }
            out.closeEntry();
        }

    }

    @Override
    public void deserialize(Block block, int index, ReservoirSampleState state)
    {
        Block stateBlock = (Block) arrayType.getObject(block, index);
        int sampleSize = stateBlock.getPositionCount();
        state.reset(sampleSize);
        for (int i = 0; i < sampleSize; i++) {
            state.add(stateBlock, i);
        }
    }
}
