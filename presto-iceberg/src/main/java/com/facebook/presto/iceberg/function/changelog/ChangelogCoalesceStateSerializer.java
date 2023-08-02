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

import com.facebook.presto.common.block.Block;
import com.facebook.presto.common.block.BlockBuilder;
import com.facebook.presto.common.type.BigintType;
import com.facebook.presto.common.type.RowType;
import com.facebook.presto.common.type.Type;
import com.facebook.presto.common.type.VarcharType;
import com.facebook.presto.spi.function.AccumulatorStateSerializer;
import com.google.common.collect.ImmutableList;

import static java.util.Objects.requireNonNull;

final class ChangelogCoalesceStateSerializer
        implements AccumulatorStateSerializer<ChangelogCoalesceState>
{
    private final Type innerType;

    private final Type serializedType;

    public ChangelogCoalesceStateSerializer(Type innerType)
    {
        this.innerType = requireNonNull(innerType, "innerType is null");
        this.serializedType = getSerializedRowType(innerType);
    }

    private static Type getSerializedRowType(Type inner)
    {
        return RowType.anonymous(ImmutableList.of(BigintType.BIGINT, VarcharType.VARCHAR, inner));
    }

    @Override
    public Type getSerializedType()
    {
        return serializedType;
    }

    @Override
    public void serialize(ChangelogCoalesceState state, BlockBuilder out)
    {
        if (state.get() == null) {
            out.appendNull();
        }
        else {
            state.get().serialize(out);
        }
    }

    @Override
    public void deserialize(Block block, int index, ChangelogCoalesceState state)
    {
        ChangelogRecord record = new ChangelogRecord(state.getType());
        record.deserialize(block, index);
        state.set(record);
    }
}
