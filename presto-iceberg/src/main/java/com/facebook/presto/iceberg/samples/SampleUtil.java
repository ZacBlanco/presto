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
package com.facebook.presto.iceberg.samples;

import com.facebook.presto.hive.HdfsContext;
import com.facebook.presto.hive.HdfsEnvironment;
import com.facebook.presto.hive.metastore.ExtendedHiveMetastore;
import com.facebook.presto.iceberg.IcebergHiveMetadata;
import com.facebook.presto.iceberg.IcebergResourceFactory;
import com.facebook.presto.iceberg.util.SinglePathCatalog;
import com.facebook.presto.spi.ConnectorSession;
import com.facebook.presto.spi.SchemaTableName;
import org.apache.hadoop.fs.Path;
import org.apache.iceberg.Table;
import org.apache.iceberg.catalog.TableIdentifier;

import java.util.HashMap;

import static com.facebook.presto.iceberg.IcebergUtil.getHiveIcebergTable;
import static com.facebook.presto.iceberg.util.IcebergPrestoModelConverters.toIcebergTableIdentifier;

public class SampleUtil
{
    private SampleUtil() {}

    public static final TableIdentifier SAMPLE_TABLE_ID = toIcebergTableIdentifier("sample", "sample-table");

    public static Table getNativeTable(ConnectorSession session, IcebergResourceFactory resourceFactory, SchemaTableName table)
    {
        return resourceFactory.getCatalog(session).loadTable(toIcebergTableIdentifier(table.getSchemaName(), table.getTableName()));
    }

    public static Table getHiveTable(ConnectorSession session, IcebergHiveMetadata metadata, HdfsEnvironment env, SchemaTableName table)
    {
        ExtendedHiveMetastore metastore = metadata.getMetastore();
        return getHiveIcebergTable(metastore, env, session, table);
    }

    public static SinglePathCatalog getCatalogForSampleTable(Table icebergSource, String prestoSchema, HdfsEnvironment env, ConnectorSession session)
    {
        Path tableLocation = new Path(icebergSource.location());
        HdfsContext ctx = new HdfsContext(session, prestoSchema, icebergSource.name(), icebergSource.location(), false);
        SinglePathCatalog c = new SinglePathCatalog(tableLocation, env.getConfiguration(ctx, tableLocation));
        // initializing the catalog can be expensive. See if we can somehow store the catalog reference
        // or wrap a delegate catalog.
        c.initialize(tableLocation.getName(), new HashMap<>());
        return c;
    }

    public static boolean sampleTableExists(Table icebergSource, String prestoSchema, HdfsEnvironment env, ConnectorSession session)
    {
        try (SinglePathCatalog c = getCatalogForSampleTable(icebergSource, prestoSchema, env, session)) {
            return c.tableExists(SAMPLE_TABLE_ID);
        }
    }

    public static Table getSampleTableFromActual(Table icebergSource, String prestoSchema, HdfsEnvironment env, ConnectorSession session)
    {
        try (SinglePathCatalog c = getCatalogForSampleTable(icebergSource, prestoSchema, env, session)) {
            return c.loadTable(SAMPLE_TABLE_ID);
        }
    }
}
