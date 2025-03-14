/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.hive.metastore.client.builder;

import org.apache.commons.collections.CollectionUtils;

import org.apache.hadoop.hive.metastore.api.GetProjectionsSpec;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

/**
 * Builder for the GetProjectionsSpec. This is a projection specification for partitions returned from the HMS.
 */
public class GetPartitionProjectionsSpecBuilder {

    private List<String> fieldList = new ArrayList<>();
    private String includePartitionPattern = null;
    private String excludePartitionPattern = null;

    public GetPartitionProjectionsSpecBuilder() {

    }

    public GetPartitionProjectionsSpecBuilder addProjectField(String field) {
        fieldList.add(field);
        return this;
    }

    public GetPartitionProjectionsSpecBuilder addProjectFieldList(List<String> fields) {
        fieldList.addAll(Arrays.asList("catName","dbName","tableName"));
        if (CollectionUtils.isNotEmpty(fields)) {
            fieldList.addAll(fields);
        }
        return this;
    }

    public GetPartitionProjectionsSpecBuilder setIncludePartitionPattern(String includePartitionPattern) {
        this.includePartitionPattern = includePartitionPattern;
        return this;
    }

    public GetPartitionProjectionsSpecBuilder setExcludePartitionPattern(String excludePartitionPattern) {
        this.excludePartitionPattern = excludePartitionPattern;
        return this;
    }

    public GetProjectionsSpec build() {
        return new GetProjectionsSpec(fieldList, includePartitionPattern, excludePartitionPattern);
    }
}