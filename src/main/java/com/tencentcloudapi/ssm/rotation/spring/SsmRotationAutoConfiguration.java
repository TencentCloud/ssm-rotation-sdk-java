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
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;

import javax.sql.DataSource;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

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

    private final ConfigurableEnvironment environment;

    public SsmRotationAutoConfiguration(ConfigurableEnvironment environment) {
        this.environment = environment;
    }

    @Bean(name = "dataSource")
    @Primary
    @ConditionalOnMissingBean(DataSource.class)
    @ConditionalOnProperty(prefix = "ssm.rotation", name = "mode", havingValue = "single", matchIfMissing = true)
    public DataSource ssmRotationSingleDataSource(SsmRotationProperties properties) throws SsmRotationException {
        SsmRotationProperties.DataSourceProperties dataSource = properties.resolveSingleDataSource();
        // 自动收集配置文件中平级的未知属性到 extraProperties
        collectExtraProperties(dataSource, "ssm.rotation.datasource");
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

        try {
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

                // 自动收集配置文件中平级的未知属性到 extraProperties
                collectExtraProperties(value, "ssm.rotation.datasources." + key);
                SsmRotationDataSourceOptions options = toOptions(properties, value);
                dataSourceMap.put(key, SsmRotationDataSourceFactory.createDataSource(options));
            }
        } catch (Exception e) {
            // 部分数据源创建失败，清理已创建的数据源，防止资源泄漏
            for (DataSource ds : dataSourceMap.values()) {
                if (ds instanceof AutoCloseable) {
                    try {
                        ((AutoCloseable) ds).close();
                    } catch (Exception ignored) {
                    }
                }
            }
            if (e instanceof SsmRotationException) {
                throw (SsmRotationException) e;
            }
            throw e;
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
                .keepAlive(valueOrDefault(source.getKeepAlive(), true))
                .keepAliveBetweenTimeMillis(valueOrDefault(source.getKeepAliveBetweenTimeMillis(), 120000L))
                .extraProperties(source.getExtraProperties() == null ? new java.util.LinkedHashMap<>() : source.getExtraProperties())
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
                .extraProperties(source.getExtraProperties() == null ? new java.util.LinkedHashMap<>() : source.getExtraProperties())
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
                .softMinEvictableIdleTimeMillis(valueOrDefault(source.getSoftMinEvictableIdleTimeMillis(), 120000L))
                .validationQuery(valueOrDefault(source.getValidationQuery(), "SELECT 1"))
                .testWhileIdle(valueOrDefault(source.getTestWhileIdle(), true))
                .testOnBorrow(valueOrDefault(source.getTestOnBorrow(), true))
                .testOnReturn(valueOrDefault(source.getTestOnReturn(), false))
                .extraProperties(source.getExtraProperties() == null ? new java.util.LinkedHashMap<>() : source.getExtraProperties())
                .build();
    }

    private <T> T valueOrDefault(T value, T defaultValue) {
        return value == null ? defaultValue : value;
    }

    // ==================== 自动收集未知属性 ====================

    /**
     * 从 Spring Environment 中自动收集连接池配置下未被 SDK 显式字段匹配的属性，
     * 放入对应 Properties 的 extraProperties 中。
     *
     * <p>这样用户可以直接在 druid/hikari/dbcp 下平级配置任意原生参数，
     * 无需使用 extra-properties 层级，降低理解成本。例如：</p>
     * <pre>
     * ssm.rotation.datasource.druid.max-evictable-idle-time-millis=900000
     * ssm.rotation.datasource.druid.phy-timeout-millis=0
     * </pre>
     *
     * <p>同时也兼容显式的 extra-properties 写法（向后兼容）。</p>
     */
    private void collectExtraProperties(SsmRotationProperties.DataSourceProperties ds, String prefix) {
        if (ds == null) {
            return;
        }
        collectPoolExtraProperties(ds.getDruid(), prefix + ".druid",
                SsmRotationProperties.DruidProperties.getKnownFields());
        collectPoolExtraProperties(ds.getHikari(), prefix + ".hikari",
                SsmRotationProperties.HikariProperties.getKnownFields());
        collectPoolExtraProperties(ds.getDbcp(), prefix + ".dbcp",
                SsmRotationProperties.DbcpProperties.getKnownFields());
    }

    /**
     * 从 Environment 中收集指定前缀下的未知属性
     *
     * @param poolProps  连接池 Properties 对象
     * @param prefix     配置前缀，如 "ssm.rotation.datasource.druid"
     * @param knownFields SDK 已显式暴露的字段名集合
     */
    private void collectPoolExtraProperties(Object poolProps, String prefix, Set<String> knownFields) {
        if (poolProps == null) {
            return;
        }

        // 获取 extraProperties Map
        Map<String, Object> extraProperties;
        if (poolProps instanceof SsmRotationProperties.DruidProperties) {
            extraProperties = ((SsmRotationProperties.DruidProperties) poolProps).getExtraProperties();
        } else if (poolProps instanceof SsmRotationProperties.HikariProperties) {
            extraProperties = ((SsmRotationProperties.HikariProperties) poolProps).getExtraProperties();
        } else if (poolProps instanceof SsmRotationProperties.DbcpProperties) {
            extraProperties = ((SsmRotationProperties.DbcpProperties) poolProps).getExtraProperties();
        } else {
            return;
        }

        String dotPrefix = prefix + ".";
        String extraPrefix = prefix + ".extra-properties.";

        for (PropertySource<?> propertySource : environment.getPropertySources()) {
            if (!(propertySource instanceof EnumerablePropertySource)) {
                continue;
            }
            for (String key : ((EnumerablePropertySource<?>) propertySource).getPropertyNames()) {
                // 跳过 extra-properties 下的属性（已由 Spring Boot 自动绑定到 extraProperties Map）
                if (key.startsWith(extraPrefix)) {
                    continue;
                }
                if (!key.startsWith(dotPrefix)) {
                    continue;
                }
                // 提取属性名（去掉前缀），只处理直接子属性，不处理嵌套属性
                String remainder = key.substring(dotPrefix.length());
                if (remainder.contains(".")) {
                    continue;
                }

                // 将 kebab-case 转换为 camelCase
                String camelCaseName = kebabToCamelCase(remainder);

                // 跳过 SDK 已显式暴露的字段
                if (knownFields.contains(camelCaseName)) {
                    continue;
                }

                // 收集到 extraProperties（不覆盖已有值，显式 extra-properties 优先）
                if (!extraProperties.containsKey(camelCaseName)) {
                    Object value = environment.getProperty(key);
                    if (value != null) {
                        extraProperties.put(camelCaseName, value);
                        log.debug("Collected extra pool property: {} = {}", camelCaseName, value);
                    }
                }
            }
        }
    }

    /**
     * 将 kebab-case（如 max-evictable-idle-time-millis）转换为 camelCase（如 maxEvictableIdleTimeMillis）
     */
    private static String kebabToCamelCase(String kebab) {
        if (kebab == null || !kebab.contains("-")) {
            return kebab;
        }
        StringBuilder sb = new StringBuilder();
        boolean nextUpper = false;
        for (char c : kebab.toCharArray()) {
            if (c == '-') {
                nextUpper = true;
            } else {
                sb.append(nextUpper ? Character.toUpperCase(c) : c);
                nextUpper = false;
            }
        }
        return sb.toString();
    }
}
