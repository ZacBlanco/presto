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

package com.facebook.presto.sql.analyzer.preplanned;

import com.facebook.presto.common.analyzer.PreparedQuery;
import com.facebook.presto.spi.analyzer.AnalyzerContext;
import com.facebook.presto.spi.analyzer.QueryAnalysis;
import com.facebook.presto.spi.analyzer.QueryAnalyzer;
import com.facebook.presto.spi.plan.PlanNode;
import com.google.inject.Inject;

public class PrePlannedQueryAnalyzer
        implements QueryAnalyzer
{
    @Inject
    public PrePlannedQueryAnalyzer()
    {
    }

    @Override
    public QueryAnalysis analyze(AnalyzerContext analyzerContext, PreparedQuery preparedQuery)
    {
        return null;
    }

    @Override
    public PlanNode plan(AnalyzerContext analyzerContext, QueryAnalysis queryAnalysis)
    {
        return null;
    }
}
