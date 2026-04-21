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
import com.tencentcloudapi.ssm.rotation.config.DbConfig;
import com.tencentcloudapi.ssm.rotation.config.DynamicSecretRotationConfig;
import com.tencentcloudapi.ssm.rotation.db.dbcp.SsmRotationDbcpDataSource;
import com.tencentcloudapi.ssm.rotation.db.druid.SsmRotationDruidDataSource;
import com.tencentcloudapi.ssm.rotation.db.hikari.SsmRotationHikariDataSource;
import com.tencentcloudapi.ssm.rotation.ssm.CredentialType;
import com.tencentcloudapi.ssm.rotation.ssm.SsmAccount;

import javax.sql.DataSource;

/**
 * 统一的数据源构建工厂
 */
public final class SsmRotationDataSourceFactory {

    private SsmRotationDataSourceFactory() {
    }

    /**
     * 根据 options 创建轮转数据源
     *
     * @param options 构建参数
     * @return DataSource
     * @throws SsmRotationException 配置无效或初始化失败
     */
    public static DataSource createDataSource(SsmRotationDataSourceOptions options)
            throws SsmRotationException {
        if (options == null) {
            throw new SsmRotationException(SsmRotationException.ERROR_CONFIG,
                    "options cannot be null");
        }

        DynamicSecretRotationConfig rotationConfig = buildRotationConfig(options);
        SsmRotationPoolType poolType = options.getPoolType() == null
                ? SsmRotationPoolType.DRUID
                : options.getPoolType();

        switch (poolType) {
            case DRUID:
                return buildDruidDataSource(rotationConfig, options.getDruid());
            case HIKARI:
                return buildHikariDataSource(rotationConfig, options.getHikari());
            case DBCP:
                return buildDbcpDataSource(rotationConfig, options.getDbcp());
            default:
                throw new SsmRotationException(SsmRotationException.ERROR_CONFIG,
                        "Unsupported poolType: " + poolType);
        }
    }

    private static DynamicSecretRotationConfig buildRotationConfig(SsmRotationDataSourceOptions options)
            throws SsmRotationException {
        DbConfig dbConfig = DbConfig.builder()
                .secretName(options.getSecretName())
                .ipAddress(options.getIpAddress())
                .port(options.getPort())
                .dbName(options.getDbName())
                .paramStr(options.getParamStr())
                .connectTimeoutSeconds(options.getConnectTimeoutSeconds())
                .socketTimeoutSeconds(options.getSocketTimeoutSeconds())
                .build();

        SsmAccount ssmAccount = buildSsmAccount(options);
        DynamicSecretRotationConfig config = DynamicSecretRotationConfig.builder()
                .dbConfig(dbConfig)
                .ssmServiceConfig(ssmAccount)
                .watchChangeIntervalMs(options.getWatchIntervalMs())
                .build();
        config.validate();
        return config;
    }

    private static SsmAccount buildSsmAccount(SsmRotationDataSourceOptions options)
            throws SsmRotationException {
        CredentialType credentialType = options.getCredentialType() == null
                ? CredentialType.PERMANENT
                : options.getCredentialType();

        SsmAccount account;
        switch (credentialType) {
            case CAM_ROLE:
                account = SsmAccount.withCamRole(options.getRoleName(), options.getRegion());
                break;
            case TEMPORARY:
                account = SsmAccount.withTemporaryCredential(
                        options.getSecretId(),
                        options.getSecretKey(),
                        options.getToken(),
                        options.getRegion()
                );
                break;
            case PERMANENT:
                account = SsmAccount.withPermanentCredential(
                        options.getSecretId(),
                        options.getSecretKey(),
                        options.getRegion()
                );
                break;
            default:
                throw new SsmRotationException(SsmRotationException.ERROR_CONFIG,
                        "Unsupported credentialType: " + credentialType);
        }

        if (options.getEndpoint() != null && !options.getEndpoint().trim().isEmpty()) {
            account.withEndpoint(options.getEndpoint());
        }
        account.validate();
        return account;
    }

    private static DataSource buildDruidDataSource(DynamicSecretRotationConfig rotationConfig,
                                                   SsmRotationDataSourceOptions.DruidOptions options)
            throws SsmRotationException {
        SsmRotationDataSourceOptions.DruidOptions druidOptions = options == null
                ? SsmRotationDataSourceOptions.DruidOptions.builder().build()
                : options;

        return SsmRotationDruidDataSource.builder()
                .rotationConfig(rotationConfig)
                .maxActive(druidOptions.getMaxActive())
                .minIdle(druidOptions.getMinIdle())
                .initialSize(druidOptions.getInitialSize())
                .maxWait(druidOptions.getMaxWait())
                .timeBetweenEvictionRunsMillis(druidOptions.getTimeBetweenEvictionRunsMillis())
                .minEvictableIdleTimeMillis(druidOptions.getMinEvictableIdleTimeMillis())
                .validationQuery(druidOptions.getValidationQuery())
                .testWhileIdle(druidOptions.isTestWhileIdle())
                .testOnBorrow(druidOptions.isTestOnBorrow())
                .testOnReturn(druidOptions.isTestOnReturn())
                .keepAlive(druidOptions.isKeepAlive())
                .keepAliveBetweenTimeMillis(druidOptions.getKeepAliveBetweenTimeMillis())
                .extraProperties(druidOptions.getExtraProperties())
                .build();
    }

    private static DataSource buildHikariDataSource(DynamicSecretRotationConfig rotationConfig,
                                                    SsmRotationDataSourceOptions.HikariOptions options)
            throws SsmRotationException {
        SsmRotationDataSourceOptions.HikariOptions hikariOptions = options == null
                ? SsmRotationDataSourceOptions.HikariOptions.builder().build()
                : options;

        return SsmRotationHikariDataSource.builder()
                .rotationConfig(rotationConfig)
                .maximumPoolSize(hikariOptions.getMaximumPoolSize())
                .minimumIdle(hikariOptions.getMinimumIdle())
                .connectionTimeout(hikariOptions.getConnectionTimeout())
                .idleTimeout(hikariOptions.getIdleTimeout())
                .maxLifetime(hikariOptions.getMaxLifetime())
                .connectionTestQuery(hikariOptions.getConnectionTestQuery())
                .poolName(hikariOptions.getPoolName())
                .extraProperties(hikariOptions.getExtraProperties())
                .build();
    }

    private static DataSource buildDbcpDataSource(DynamicSecretRotationConfig rotationConfig,
                                                  SsmRotationDataSourceOptions.DbcpOptions options)
            throws SsmRotationException {
        SsmRotationDataSourceOptions.DbcpOptions dbcpOptions = options == null
                ? SsmRotationDataSourceOptions.DbcpOptions.builder().build()
                : options;

        return SsmRotationDbcpDataSource.builder()
                .rotationConfig(rotationConfig)
                .maxTotal(dbcpOptions.getMaxTotal())
                .minIdle(dbcpOptions.getMinIdle())
                .maxIdle(dbcpOptions.getMaxIdle())
                .initialSize(dbcpOptions.getInitialSize())
                .maxWaitMillis(dbcpOptions.getMaxWaitMillis())
                .timeBetweenEvictionRunsMillis(dbcpOptions.getTimeBetweenEvictionRunsMillis())
                .minEvictableIdleTimeMillis(dbcpOptions.getMinEvictableIdleTimeMillis())
                .softMinEvictableIdleTimeMillis(dbcpOptions.getSoftMinEvictableIdleTimeMillis())
                .validationQuery(dbcpOptions.getValidationQuery())
                .testWhileIdle(dbcpOptions.isTestWhileIdle())
                .testOnBorrow(dbcpOptions.isTestOnBorrow())
                .testOnReturn(dbcpOptions.isTestOnReturn())
                .extraProperties(dbcpOptions.getExtraProperties())
                .build();
    }
}
