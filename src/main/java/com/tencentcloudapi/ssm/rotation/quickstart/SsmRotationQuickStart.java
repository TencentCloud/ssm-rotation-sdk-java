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

package com.tencentcloudapi.ssm.rotation.quickstart;

import com.tencentcloudapi.ssm.rotation.SsmRotationException;
import com.tencentcloudapi.ssm.rotation.ssm.CredentialType;

import javax.sql.DataSource;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 非 Spring 场景的最简接入入口
 */
public final class SsmRotationQuickStart {

    private SsmRotationQuickStart() {
    }

    /**
     * 单数据源构建入口
     */
    public static Builder single() {
        return new Builder();
    }

    /**
     * 多数据源构建入口
     */
    public static MultiBuilder multi() {
        return new MultiBuilder();
    }

    /**
     * 单数据源构建器
     */
    public static class Builder {
        private final SsmRotationDataSourceOptions.SsmRotationDataSourceOptionsBuilder delegate =
                SsmRotationDataSourceOptions.builder();

        private Builder() {
        }

        public Builder poolType(SsmRotationPoolType poolType) {
            delegate.poolType(poolType);
            return this;
        }

        public Builder poolType(String poolType) {
            return poolType(SsmRotationPoolType.from(poolType));
        }

        public Builder watchIntervalMs(long watchIntervalMs) {
            delegate.watchIntervalMs(watchIntervalMs);
            return this;
        }

        public Builder region(String region) {
            delegate.region(region);
            return this;
        }

        public Builder endpoint(String endpoint) {
            delegate.endpoint(endpoint);
            return this;
        }

        public Builder permanentCredential(String secretId, String secretKey) {
            delegate.credentialType(CredentialType.PERMANENT);
            delegate.secretId(secretId);
            delegate.secretKey(secretKey);
            delegate.token(null);
            delegate.roleName(null);
            return this;
        }

        public Builder temporaryCredential(String secretId, String secretKey, String token) {
            delegate.credentialType(CredentialType.TEMPORARY);
            delegate.secretId(secretId);
            delegate.secretKey(secretKey);
            delegate.token(token);
            delegate.roleName(null);
            return this;
        }

        public Builder camRole(String roleName) {
            delegate.credentialType(CredentialType.CAM_ROLE);
            delegate.roleName(roleName);
            delegate.secretId(null);
            delegate.secretKey(null);
            delegate.token(null);
            return this;
        }

        public Builder secretName(String secretName) {
            delegate.secretName(secretName);
            return this;
        }

        public Builder db(String ipAddress, int port, String dbName) {
            delegate.ipAddress(ipAddress);
            delegate.port(port);
            delegate.dbName(dbName);
            return this;
        }

        public Builder ipAddress(String ipAddress) {
            delegate.ipAddress(ipAddress);
            return this;
        }

        public Builder port(int port) {
            delegate.port(port);
            return this;
        }

        public Builder dbName(String dbName) {
            delegate.dbName(dbName);
            return this;
        }

        public Builder paramStr(String paramStr) {
            delegate.paramStr(paramStr);
            return this;
        }

        public Builder connectTimeoutSeconds(int connectTimeoutSeconds) {
            delegate.connectTimeoutSeconds(connectTimeoutSeconds);
            return this;
        }

        public Builder socketTimeoutSeconds(int socketTimeoutSeconds) {
            delegate.socketTimeoutSeconds(socketTimeoutSeconds);
            return this;
        }

        public Builder druidOptions(SsmRotationDataSourceOptions.DruidOptions options) {
            delegate.druid(options);
            return this;
        }

        public Builder hikariOptions(SsmRotationDataSourceOptions.HikariOptions options) {
            delegate.hikari(options);
            return this;
        }

        public Builder dbcpOptions(SsmRotationDataSourceOptions.DbcpOptions options) {
            delegate.dbcp(options);
            return this;
        }

        public SsmRotationDataSourceOptions buildOptions() {
            return delegate.build();
        }

        public DataSource build() throws SsmRotationException {
            return SsmRotationDataSourceFactory.createDataSource(buildOptions());
        }
    }

    /**
     * 多数据源构建器
     */
    public static class MultiBuilder {
        private final Map<String, SsmRotationDataSourceOptions> optionsByName = new LinkedHashMap<>();

        private MultiBuilder() {
        }

        public MultiBuilder add(String name, SsmRotationDataSourceOptions options) {
            if (name == null || name.trim().isEmpty()) {
                throw new IllegalArgumentException("name cannot be blank");
            }
            if (options == null) {
                throw new IllegalArgumentException("options cannot be null");
            }
            optionsByName.put(name, options);
            return this;
        }

        public MultiBuilder add(String name, Consumer<Builder> customizer) {
            if (customizer == null) {
                throw new IllegalArgumentException("customizer cannot be null");
            }
            Builder builder = SsmRotationQuickStart.single();
            customizer.accept(builder);
            return add(name, builder.buildOptions());
        }

        public Map<String, DataSource> build() throws SsmRotationException {
            Map<String, DataSource> result = new LinkedHashMap<>();
            try {
                for (Map.Entry<String, SsmRotationDataSourceOptions> entry : optionsByName.entrySet()) {
                    result.put(entry.getKey(),
                            SsmRotationDataSourceFactory.createDataSource(entry.getValue()));
                }
            } catch (SsmRotationException e) {
                // 部分数据源创建失败，清理已创建的数据源，防止资源泄漏
                for (DataSource ds : result.values()) {
                    if (ds instanceof AutoCloseable) {
                        try {
                            ((AutoCloseable) ds).close();
                        } catch (Exception ignored) {
                        }
                    }
                }
                throw e;
            }
            return Collections.unmodifiableMap(result);
        }
    }
}
