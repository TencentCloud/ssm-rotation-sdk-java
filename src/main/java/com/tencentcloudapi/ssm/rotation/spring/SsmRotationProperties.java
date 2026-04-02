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

import com.tencentcloudapi.ssm.rotation.quickstart.SsmRotationPoolType;
import com.tencentcloudapi.ssm.rotation.ssm.CredentialType;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Spring Boot 自动装配配置模型
 */
@Data
@ConfigurationProperties(prefix = "ssm.rotation")
public class SsmRotationProperties {

    /**
     * 是否启用自动装配。默认关闭，避免意外接管。
     */
    private boolean enabled = false;

    /**
     * single: 单数据源，multi: 多数据源。
     */
    private Mode mode = Mode.SINGLE;

    /**
     * 是否允许覆盖同名 Bean（仅多数据源 secondary 注册场景使用）。
     */
    private boolean allowOverride = false;

    /**
     * 全局轮询间隔（毫秒），可被数据源级别覆盖。
     */
    private long watchIntervalMs = 10000L;

    /**
     * 全局认证配置。
     */
    private CredentialProperties credential = new CredentialProperties();

    /**
     * 单数据源最简写法的快捷字段。
     */
    private String secretName;
    private SsmRotationPoolType poolType = SsmRotationPoolType.DRUID;
    private DbProperties db = new DbProperties();

    /**
     * 单数据源显式配置块。
     */
    private DataSourceProperties datasource;

    /**
     * 多数据源配置块。
     */
    private Map<String, DataSourceProperties> datasources = new LinkedHashMap<>();

    public DataSourceProperties resolveSingleDataSource() {
        DataSourceProperties resolved;
        if (datasource == null) {
            resolved = new DataSourceProperties();
            resolved.setDb(db == null ? new DbProperties() : db.copy());
        } else {
            resolved = datasource.copy();
        }

        if (isBlank(resolved.getSecretName())) {
            resolved.setSecretName(secretName);
        }
        if (resolved.getPoolType() == null) {
            resolved.setPoolType(poolType);
        }
        if (resolved.getDb() == null) {
            resolved.setDb(new DbProperties());
        }
        if (datasource != null) {
            resolved.getDb().mergeMissing(db);
        }
        if (resolved.getWatchIntervalMs() == null) {
            resolved.setWatchIntervalMs(watchIntervalMs);
        }
        if (isBlank(resolved.getBeanName())) {
            resolved.setBeanName("dataSource");
        }
        resolved.setPrimary(true);
        return resolved;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public enum Mode {
        SINGLE,
        MULTI
    }

    @Data
    public static class CredentialProperties {
        private CredentialType type = CredentialType.PERMANENT;
        private String region;
        private String secretId;
        private String secretKey;
        private String token;
        private String roleName;
        private String endpoint;
    }

    @Data
    public static class DataSourceProperties {
        private String beanName;
        private Boolean primary;
        private SsmRotationPoolType poolType;
        private Long watchIntervalMs;
        private String secretName;
        private DbProperties db = new DbProperties();

        private DruidProperties druid = new DruidProperties();
        private HikariProperties hikari = new HikariProperties();
        private DbcpProperties dbcp = new DbcpProperties();

        public DataSourceProperties copy() {
            DataSourceProperties copy = new DataSourceProperties();
            copy.beanName = this.beanName;
            copy.primary = this.primary;
            copy.poolType = this.poolType;
            copy.watchIntervalMs = this.watchIntervalMs;
            copy.secretName = this.secretName;
            copy.db = this.db == null ? null : this.db.copy();
            copy.druid = this.druid == null ? null : this.druid.copy();
            copy.hikari = this.hikari == null ? null : this.hikari.copy();
            copy.dbcp = this.dbcp == null ? null : this.dbcp.copy();
            return copy;
        }
    }

    @Data
    public static class DbProperties {
        private String ip;
        private Integer port = 3306;
        private String name;
        private String paramStr;
        private Integer connectTimeoutSeconds = 5;
        private Integer socketTimeoutSeconds = 5;

        public void mergeMissing(DbProperties source) {
            if (source == null) {
                return;
            }
            if (isBlank(ip)) {
                ip = source.ip;
            }
            if (port == null) {
                port = source.port;
            }
            if (isBlank(name)) {
                name = source.name;
            }
            if (isBlank(paramStr)) {
                paramStr = source.paramStr;
            }
            if (connectTimeoutSeconds == null) {
                connectTimeoutSeconds = source.connectTimeoutSeconds;
            }
            if (socketTimeoutSeconds == null) {
                socketTimeoutSeconds = source.socketTimeoutSeconds;
            }
        }

        public DbProperties copy() {
            DbProperties copy = new DbProperties();
            copy.ip = this.ip;
            copy.port = this.port;
            copy.name = this.name;
            copy.paramStr = this.paramStr;
            copy.connectTimeoutSeconds = this.connectTimeoutSeconds;
            copy.socketTimeoutSeconds = this.socketTimeoutSeconds;
            return copy;
        }
    }

    @Data
    public static class DruidProperties {
        private Integer maxActive = 20;
        private Integer minIdle = 5;
        private Integer initialSize = 5;
        private Long maxWait = 60000L;
        private Long timeBetweenEvictionRunsMillis = 60000L;
        private Long minEvictableIdleTimeMillis = 300000L;
        private String validationQuery = "SELECT 1";
        private Boolean testWhileIdle = true;
        private Boolean testOnBorrow = false;
        private Boolean testOnReturn = false;

        public DruidProperties copy() {
            DruidProperties copy = new DruidProperties();
            copy.maxActive = this.maxActive;
            copy.minIdle = this.minIdle;
            copy.initialSize = this.initialSize;
            copy.maxWait = this.maxWait;
            copy.timeBetweenEvictionRunsMillis = this.timeBetweenEvictionRunsMillis;
            copy.minEvictableIdleTimeMillis = this.minEvictableIdleTimeMillis;
            copy.validationQuery = this.validationQuery;
            copy.testWhileIdle = this.testWhileIdle;
            copy.testOnBorrow = this.testOnBorrow;
            copy.testOnReturn = this.testOnReturn;
            return copy;
        }
    }

    @Data
    public static class HikariProperties {
        private Integer maximumPoolSize = 20;
        private Integer minimumIdle = 5;
        private Long connectionTimeout = 30000L;
        private Long idleTimeout = 600000L;
        private Long maxLifetime = 1800000L;
        private String connectionTestQuery = "SELECT 1";
        private String poolName = "SSM-Rotation-HikariPool";

        public HikariProperties copy() {
            HikariProperties copy = new HikariProperties();
            copy.maximumPoolSize = this.maximumPoolSize;
            copy.minimumIdle = this.minimumIdle;
            copy.connectionTimeout = this.connectionTimeout;
            copy.idleTimeout = this.idleTimeout;
            copy.maxLifetime = this.maxLifetime;
            copy.connectionTestQuery = this.connectionTestQuery;
            copy.poolName = this.poolName;
            return copy;
        }
    }

    @Data
    public static class DbcpProperties {
        private Integer maxTotal = 20;
        private Integer minIdle = 5;
        private Integer maxIdle = 10;
        private Integer initialSize = 5;
        private Long maxWaitMillis = 60000L;
        private Long timeBetweenEvictionRunsMillis = 60000L;
        private Long minEvictableIdleTimeMillis = 300000L;
        private String validationQuery = "SELECT 1";
        private Boolean testWhileIdle = true;
        private Boolean testOnBorrow = false;
        private Boolean testOnReturn = false;

        public DbcpProperties copy() {
            DbcpProperties copy = new DbcpProperties();
            copy.maxTotal = this.maxTotal;
            copy.minIdle = this.minIdle;
            copy.maxIdle = this.maxIdle;
            copy.initialSize = this.initialSize;
            copy.maxWaitMillis = this.maxWaitMillis;
            copy.timeBetweenEvictionRunsMillis = this.timeBetweenEvictionRunsMillis;
            copy.minEvictableIdleTimeMillis = this.minEvictableIdleTimeMillis;
            copy.validationQuery = this.validationQuery;
            copy.testWhileIdle = this.testWhileIdle;
            copy.testOnBorrow = this.testOnBorrow;
            copy.testOnReturn = this.testOnReturn;
            return copy;
        }
    }
}
