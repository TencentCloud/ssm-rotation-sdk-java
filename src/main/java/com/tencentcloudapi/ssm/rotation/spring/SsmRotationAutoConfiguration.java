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

import com.tencentcloudapi.ssm.rotation.SsmRotationException;
import com.tencentcloudapi.ssm.rotation.quickstart.SsmRotationDataSourceFactory;
import com.tencentcloudapi.ssm.rotation.quickstart.SsmRotationDataSourceOptions;
import com.tencentcloudapi.ssm.rotation.quickstart.SsmRotationPoolType;
import com.tencentcloudapi.ssm.rotation.ssm.CredentialType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SSM Rotation Spring Boot 自动装配
 */
@Slf4j
@Configuration
@ConditionalOnClass(DataSource.class)
@AutoConfigureBefore(DataSourceAutoConfiguration.class)
@EnableConfigurationProperties(SsmRotationProperties.class)
@ConditionalOnProperty(prefix = "ssm.rotation", name = "enabled", havingValue = "true")
public class SsmRotationAutoConfiguration {

    @Bean(name = "dataSource")
    @Primary
    @ConditionalOnMissingBean(DataSource.class)
    @ConditionalOnProperty(prefix = "ssm.rotation", name = "mode", havingValue = "single", matchIfMissing = true)
    public DataSource ssmRotationSingleDataSource(SsmRotationProperties properties) throws SsmRotationException {
        SsmRotationProperties.DataSourceProperties dataSource = properties.resolveSingleDataSource();
        SsmRotationDataSourceOptions options = toOptions(properties, dataSource);
        DataSource created = SsmRotationDataSourceFactory.createDataSource(options);
        log.info("SSM rotation single datasource initialized, beanName=dataSource, poolType={}",
                options.getPoolType());
        return created;
    }

    @Bean(name = "ssmRotationManagedDataSources", destroyMethod = "close")
    @ConditionalOnProperty(prefix = "ssm.rotation", name = "mode", havingValue = "multi")
    public SsmRotationManagedDataSources ssmRotationManagedDataSources(SsmRotationProperties properties)
            throws SsmRotationException {
        if (properties.getDatasources() == null || properties.getDatasources().isEmpty()) {
            throw new BeanCreationException("ssm.rotation.datasources cannot be empty in multi mode");
        }

        int primaryCount = 0;
        String primaryKey = null;
        Map<String, DataSource> dataSourceMap = new LinkedHashMap<>();

        for (Map.Entry<String, SsmRotationProperties.DataSourceProperties> entry : properties.getDatasources().entrySet()) {
            String key = entry.getKey();
            SsmRotationProperties.DataSourceProperties value = entry.getValue();
            if (value == null) {
                throw new BeanCreationException("ssm.rotation.datasources." + key + " cannot be null");
            }

            boolean primary = Boolean.TRUE.equals(value.getPrimary());
            if (primary) {
                primaryCount++;
                primaryKey = key;
            }

            SsmRotationDataSourceOptions options = toOptions(properties, value);
            dataSourceMap.put(key, SsmRotationDataSourceFactory.createDataSource(options));
        }

        if (primaryCount != 1) {
            throw new BeanCreationException("Exactly one datasource must set primary=true in multi mode, current: "
                    + primaryCount);
        }

        log.info("SSM rotation multi datasource initialized, count={}, primary={}",
                dataSourceMap.size(), primaryKey);
        return new SsmRotationManagedDataSources(primaryKey, dataSourceMap);
    }

    @Bean(name = "dataSource")
    @Primary
    @ConditionalOnMissingBean(DataSource.class)
    @ConditionalOnProperty(prefix = "ssm.rotation", name = "mode", havingValue = "multi")
    public DataSource ssmRotationPrimaryDataSource(SsmRotationManagedDataSources managedDataSources) {
        return managedDataSources.getPrimaryDataSource();
    }

    @Bean(name = "ssmRotationDataSources")
    @ConditionalOnProperty(prefix = "ssm.rotation", name = "mode", havingValue = "multi")
    public Map<String, DataSource> ssmRotationDataSources(SsmRotationManagedDataSources managedDataSources) {
        return managedDataSources.getDataSources();
    }

    private SsmRotationDataSourceOptions toOptions(SsmRotationProperties root,
                                                   SsmRotationProperties.DataSourceProperties ds) {
        SsmRotationProperties.DbProperties db = ds.getDb() == null
                ? new SsmRotationProperties.DbProperties()
                : ds.getDb();

        SsmRotationProperties.CredentialProperties credential = root.getCredential() == null
                ? new SsmRotationProperties.CredentialProperties()
                : root.getCredential();

        SsmRotationPoolType poolType = ds.getPoolType() == null
                ? SsmRotationPoolType.DRUID
                : ds.getPoolType();
        long watchIntervalMs = ds.getWatchIntervalMs() == null
                ? root.getWatchIntervalMs()
                : ds.getWatchIntervalMs();

        return SsmRotationDataSourceOptions.builder()
                .poolType(poolType)
                .watchIntervalMs(watchIntervalMs)
                .credentialType(defaultCredentialType(credential.getType()))
                .region(credential.getRegion())
                .secretId(credential.getSecretId())
                .secretKey(credential.getSecretKey())
                .token(credential.getToken())
                .roleName(credential.getRoleName())
                .endpoint(credential.getEndpoint())
                .secretName(ds.getSecretName())
                .ipAddress(db.getIp())
                .port(db.getPort() == null ? 3306 : db.getPort())
                .dbName(db.getName())
                .paramStr(db.getParamStr())
                .connectTimeoutSeconds(db.getConnectTimeoutSeconds() == null ? 5 : db.getConnectTimeoutSeconds())
                .socketTimeoutSeconds(db.getSocketTimeoutSeconds() == null ? 5 : db.getSocketTimeoutSeconds())
                .druid(buildDruidOptions(ds.getDruid()))
                .hikari(buildHikariOptions(ds.getHikari()))
                .dbcp(buildDbcpOptions(ds.getDbcp()))
                .build();
    }

    private CredentialType defaultCredentialType(CredentialType type) {
        return type == null ? CredentialType.PERMANENT : type;
    }

    private SsmRotationDataSourceOptions.DruidOptions buildDruidOptions(
            SsmRotationProperties.DruidProperties druid) {
        SsmRotationProperties.DruidProperties source = druid == null
                ? new SsmRotationProperties.DruidProperties()
                : druid;
        return SsmRotationDataSourceOptions.DruidOptions.builder()
                .maxActive(valueOrDefault(source.getMaxActive(), 20))
                .minIdle(valueOrDefault(source.getMinIdle(), 5))
                .initialSize(valueOrDefault(source.getInitialSize(), 5))
                .maxWait(valueOrDefault(source.getMaxWait(), 60000L))
                .timeBetweenEvictionRunsMillis(valueOrDefault(source.getTimeBetweenEvictionRunsMillis(), 60000L))
                .minEvictableIdleTimeMillis(valueOrDefault(source.getMinEvictableIdleTimeMillis(), 300000L))
                .validationQuery(valueOrDefault(source.getValidationQuery(), "SELECT 1"))
                .testWhileIdle(valueOrDefault(source.getTestWhileIdle(), true))
                .testOnBorrow(valueOrDefault(source.getTestOnBorrow(), false))
                .testOnReturn(valueOrDefault(source.getTestOnReturn(), false))
                .build();
    }

    private SsmRotationDataSourceOptions.HikariOptions buildHikariOptions(
            SsmRotationProperties.HikariProperties hikari) {
        SsmRotationProperties.HikariProperties source = hikari == null
                ? new SsmRotationProperties.HikariProperties()
                : hikari;
        return SsmRotationDataSourceOptions.HikariOptions.builder()
                .maximumPoolSize(valueOrDefault(source.getMaximumPoolSize(), 20))
                .minimumIdle(valueOrDefault(source.getMinimumIdle(), 5))
                .connectionTimeout(valueOrDefault(source.getConnectionTimeout(), 30000L))
                .idleTimeout(valueOrDefault(source.getIdleTimeout(), 600000L))
                .maxLifetime(valueOrDefault(source.getMaxLifetime(), 1800000L))
                .connectionTestQuery(valueOrDefault(source.getConnectionTestQuery(), "SELECT 1"))
                .poolName(valueOrDefault(source.getPoolName(), "SSM-Rotation-HikariPool"))
                .build();
    }

    private SsmRotationDataSourceOptions.DbcpOptions buildDbcpOptions(
            SsmRotationProperties.DbcpProperties dbcp) {
        SsmRotationProperties.DbcpProperties source = dbcp == null
                ? new SsmRotationProperties.DbcpProperties()
                : dbcp;
        return SsmRotationDataSourceOptions.DbcpOptions.builder()
                .maxTotal(valueOrDefault(source.getMaxTotal(), 20))
                .minIdle(valueOrDefault(source.getMinIdle(), 5))
                .maxIdle(valueOrDefault(source.getMaxIdle(), 10))
                .initialSize(valueOrDefault(source.getInitialSize(), 5))
                .maxWaitMillis(valueOrDefault(source.getMaxWaitMillis(), 60000L))
                .timeBetweenEvictionRunsMillis(valueOrDefault(source.getTimeBetweenEvictionRunsMillis(), 60000L))
                .minEvictableIdleTimeMillis(valueOrDefault(source.getMinEvictableIdleTimeMillis(), 300000L))
                .validationQuery(valueOrDefault(source.getValidationQuery(), "SELECT 1"))
                .testWhileIdle(valueOrDefault(source.getTestWhileIdle(), true))
                .testOnBorrow(valueOrDefault(source.getTestOnBorrow(), false))
                .testOnReturn(valueOrDefault(source.getTestOnReturn(), false))
                .build();
    }

    private <T> T valueOrDefault(T value, T defaultValue) {
        return value == null ? defaultValue : value;
    }
}
