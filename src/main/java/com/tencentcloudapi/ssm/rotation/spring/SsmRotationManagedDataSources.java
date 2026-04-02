/*
 * Copyright (c) 2017-2026 Tencent. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tencentcloudapi.ssm.rotation.spring;

import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 多数据源容器，负责生命周期托管
 */
@Slf4j
public class SsmRotationManagedDataSources implements AutoCloseable {
    private final String primaryKey;
    private final Map<String, DataSource> dataSources;

    public SsmRotationManagedDataSources(String primaryKey, Map<String, DataSource> dataSources) {
        this.primaryKey = primaryKey;
        this.dataSources = Collections.unmodifiableMap(new LinkedHashMap<>(dataSources));
    }

    public Map<String, DataSource> getDataSources() {
        return dataSources;
    }

    public DataSource getPrimaryDataSource() {
        DataSource primary = dataSources.get(primaryKey);
        if (primary == null) {
            throw new IllegalStateException("Primary datasource not found, key=" + primaryKey);
        }
        return primary;
    }

    public String getPrimaryKey() {
        return primaryKey;
    }

    @Override
    public void close() {
        for (Map.Entry<String, DataSource> entry : dataSources.entrySet()) {
            DataSource dataSource = entry.getValue();
            if (dataSource instanceof AutoCloseable) {
                try {
                    ((AutoCloseable) dataSource).close();
                } catch (Exception e) {
                    log.warn("Failed to close datasource '{}': {}", entry.getKey(), e.getMessage(), e);
                }
            }
        }
    }
}
