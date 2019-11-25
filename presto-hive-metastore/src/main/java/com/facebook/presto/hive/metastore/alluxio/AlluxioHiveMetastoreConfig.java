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
package com.facebook.presto.hive.metastore.alluxio;

import com.facebook.airlift.configuration.Config;
import com.facebook.airlift.configuration.ConfigDescription;

/**
 * Configuration for the Alluxio compatible hive metastore interface.
 */
public class AlluxioHiveMetastoreConfig
{
    private String masterAddress;

    public String getMasterAddress()
    {
        return masterAddress;
    }

    @Config("hive.metastore.alluxio.master.address")
    @ConfigDescription("Alluxio master address")
    public AlluxioHiveMetastoreConfig setMasterAddress(String masterAddress)
    {
        this.masterAddress = masterAddress;
        return this;
    }
}