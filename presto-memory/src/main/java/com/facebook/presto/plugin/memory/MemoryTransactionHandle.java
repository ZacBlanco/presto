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
package com.facebook.presto.plugin.memory;

import com.facebook.presto.spi.connector.ConnectorTransactionHandle;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;
import java.util.UUID;

import static java.util.UUID.randomUUID;

public class MemoryTransactionHandle
        implements ConnectorTransactionHandle
{
    @JsonProperty
    public UUID getId()
    {
        return id;
    }

    private final UUID id;

    public MemoryTransactionHandle()
    {
        id = randomUUID();
    }

    @JsonCreator
    public MemoryTransactionHandle(@JsonProperty("id") UUID id)
    {
        this.id = id;
    }

    @Override
    public int hashCode()
    {
        return id.hashCode();
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        MemoryTransactionHandle other = (MemoryTransactionHandle) obj;
        return Objects.equals(id, other.id);
    }
}
