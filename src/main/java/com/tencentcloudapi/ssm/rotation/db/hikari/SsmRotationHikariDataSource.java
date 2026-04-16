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

package com.tencentcloudapi.ssm.rotation.db.hikari;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.tencentcloudapi.ssm.rotation.SsmRotationException;
import com.tencentcloudapi.ssm.rotation.config.DynamicSecretRotationConfig;
import com.tencentcloudapi.ssm.rotation.db.AbstractSsmRotationDataSource;
import com.tencentcloudapi.ssm.rotation.db.CredentialChangeListener;
import com.tencentcloudapi.ssm.rotation.db.DynamicSecretRotationDb;
import com.tencentcloudapi.ssm.rotation.ssm.DbAccount;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.SQLException;

/**
 * 基于 HikariCP 连接池的 SSM 凭据轮转数据源
 * 
 * <p>封装 {@link HikariDataSource} 和 {@link DynamicSecretRotationDb}，实现凭据轮转与连接池的自动联动。
 * 用户只需替换原有的 DataSource 配置，即可实现零改造接入 SSM 凭据轮转。</p>
 * 
 * <h2>核心特性</h2>
 * <ul>
 *   <li>实现标准 {@link DataSource} 接口，可直接替换原有数据源</li>
 *   <li>凭据轮转时自动更新 HikariCP 的用户名密码</li>
 *   <li>凭据轮转时自动软淘汰旧连接（softEvictConnections）</li>
 *   <li>支持 Builder 模式配置，提供合理默认值</li>
 *   <li>支持传入自定义 HikariConfig 进行精细配置</li>
 * </ul>
 * 
 * <h2>使用示例</h2>
 * <pre>{@code
 * DataSource dataSource = SsmRotationHikariDataSource.builder()
 *         .rotationConfig(config)
 *         .maximumPoolSize(20)
 *         .minimumIdle(5)
 *         .build();
 * 
 * try (Connection conn = dataSource.getConnection()) {
 *     // 执行数据库操作，凭据轮转完全透明
 * }
 * }</pre>
 * 
 * @author tencentcloud
 * @since 1.0.0
 */
@Slf4j
public class SsmRotationHikariDataSource extends AbstractSsmRotationDataSource<HikariDataSource> {

    private SsmRotationHikariDataSource(DynamicSecretRotationDb rotationDb,
                                        HikariDataSource hikariDataSource,
                                        CredentialChangeListener credentialChangeListener) {
        super(rotationDb, hikariDataSource, credentialChangeListener, "HikariDataSource");
    }

    public static Builder builder() {
        return new Builder();
    }

    // ==================== 高级访问 ====================

    /**
     * 获取底层 HikariCP 数据源
     */
    public HikariDataSource getHikariDataSource() {
        return getDelegateDataSource();
    }

    @Override
    protected boolean isDelegateHealthy(HikariDataSource delegateDataSource) {
        return delegateDataSource.isRunning();
    }

    // ==================== Builder ====================

    /**
     * SsmRotationHikariDataSource 构建器
     * 
     * <p>提供两种使用方式：</p>
     * <ul>
     *   <li><b>简单模式</b>：通过 Builder 参数配置 HikariCP</li>
     *   <li><b>高级模式</b>：传入自定义 HikariConfig</li>
     * </ul>
     */
    public static class Builder {

        private DynamicSecretRotationConfig rotationConfig;

        // HikariCP 连接池参数（可选，提供合理默认值）
        private int maximumPoolSize = 20;
        private int minimumIdle = 5;
        private long connectionTimeout = 30000;
        private long idleTimeout = 600000;
        private long maxLifetime = 1800000;
        private String connectionTestQuery = "SELECT 1";
        private String poolName = "SSM-Rotation-HikariPool";

        /**
         * 高级模式：用户自定义的 HikariConfig
         */
        private HikariConfig customHikariConfig;

        public Builder rotationConfig(DynamicSecretRotationConfig rotationConfig) {
            this.rotationConfig = rotationConfig;
            return this;
        }

        /**
         * 设置最大连接数，默认 20
         */
        public Builder maximumPoolSize(int maximumPoolSize) {
            this.maximumPoolSize = maximumPoolSize;
            return this;
        }

        /**
         * 设置最小空闲连接数，默认 5
         */
        public Builder minimumIdle(int minimumIdle) {
            this.minimumIdle = minimumIdle;
            return this;
        }

        /**
         * 设置连接超时时间（毫秒），默认 30000
         */
        public Builder connectionTimeout(long connectionTimeout) {
            this.connectionTimeout = connectionTimeout;
            return this;
        }

        /**
         * 设置空闲超时时间（毫秒），默认 600000
         */
        public Builder idleTimeout(long idleTimeout) {
            this.idleTimeout = idleTimeout;
            return this;
        }

        /**
         * 设置连接最大生命周期（毫秒），默认 1800000
         * <p>建议设置为小于凭据轮转间隔，确保连接在轮转前自然淘汰</p>
         */
        public Builder maxLifetime(long maxLifetime) {
            this.maxLifetime = maxLifetime;
            return this;
        }

        /**
         * 设置连接测试查询 SQL，默认 "SELECT 1"
         */
        public Builder connectionTestQuery(String connectionTestQuery) {
            this.connectionTestQuery = connectionTestQuery;
            return this;
        }

        /**
         * 设置连接池名称，默认 "SSM-Rotation-HikariPool"
         */
        public Builder poolName(String poolName) {
            this.poolName = poolName;
            return this;
        }

        /**
         * 高级模式：传入自定义的 HikariConfig
         * 
         * <p>SDK 会自动设置 jdbcUrl、username、password，其他配置由用户自行控制。</p>
         */
        public Builder customHikariConfig(HikariConfig customHikariConfig) {
            this.customHikariConfig = customHikariConfig;
            return this;
        }

        public SsmRotationHikariDataSource build() throws SsmRotationException {
            if (rotationConfig == null) {
                throw new SsmRotationException(SsmRotationException.ERROR_CONFIG,
                        "rotationConfig cannot be null");
            }

            // 1. 创建 SSM 凭据轮转连接工厂
            DynamicSecretRotationDb rotationDb = new DynamicSecretRotationDb(rotationConfig);

            // 2. 获取初始凭据
            DbAccount initialAccount = rotationDb.getCurrentAccount();
            String jdbcUrl = rotationDb.buildJdbcUrl();

            // 3. 创建 HikariConfig
            HikariConfig hikariConfig;
            if (customHikariConfig != null) {
                hikariConfig = customHikariConfig;
            } else {
                hikariConfig = new HikariConfig();
                hikariConfig.setMaximumPoolSize(maximumPoolSize);
                hikariConfig.setMinimumIdle(minimumIdle);
                hikariConfig.setConnectionTimeout(connectionTimeout);
                hikariConfig.setIdleTimeout(idleTimeout);
                hikariConfig.setMaxLifetime(maxLifetime);
                hikariConfig.setConnectionTestQuery(connectionTestQuery);
                hikariConfig.setPoolName(poolName);
            }

            // 设置连接信息（无论哪种模式都由 SDK 管理）
            hikariConfig.setJdbcUrl(jdbcUrl);
            hikariConfig.setUsername(initialAccount.getUserName());
            hikariConfig.setPassword(initialAccount.getPassword());

            // 4. 创建 HikariDataSource
            HikariDataSource hikariDataSource = null;
            try {
                hikariDataSource = new HikariDataSource(hikariConfig);
                log.info("HikariDataSource initialized, url={}, username={}, maxPoolSize={}, minIdle={}",
                        jdbcUrl, initialAccount.getUserName(),
                        hikariDataSource.getMaximumPoolSize(), hikariDataSource.getMinimumIdle());
            } catch (Exception e) {
                rotationDb.close();
                // HikariDataSource 构造函数内部可能已部分初始化，需要清理
                try {
                    if (hikariDataSource != null) {
                        hikariDataSource.close();
                    }
                } catch (Exception ignored) {
                }
                throw new SsmRotationException(SsmRotationException.ERROR_CONFIG,
                        "Failed to initialize HikariDataSource: " + e.getMessage(), e);
            }

            // 5. 注册凭据变更监听器
            CredentialChangeListener listener = new HikariCredentialChangeListener(hikariDataSource);
            rotationDb.addCredentialChangeListener(listener);

            log.info("SsmRotationHikariDataSource created successfully");
            return new SsmRotationHikariDataSource(rotationDb, hikariDataSource, listener);
        }
    }

    // ==================== 内部类 ====================

    /**
     * HikariCP 凭据变更监听器
     */
    @Slf4j
    private static class HikariCredentialChangeListener implements CredentialChangeListener {

        private final HikariDataSource hikariDataSource;

        HikariCredentialChangeListener(HikariDataSource hikariDataSource) {
            this.hikariDataSource = hikariDataSource;
        }

        @Override
        public void onCredentialChanged(DbAccount oldAccount, DbAccount newAccount) {
            log.info("Credential rotated, updating HikariDataSource: {} -> {}",
                    oldAccount.getUserName(), newAccount.getUserName());

            // 1. 更新 HikariCP 的用户名和密码
            hikariDataSource.setUsername(newAccount.getUserName());
            hikariDataSource.setPassword(newAccount.getPassword());

            // 2. 软淘汰旧连接
            // HikariCP 的 softEvictConnections 会标记所有当前连接为待淘汰
            // 连接归还时会被关闭，新请求会使用新凭据创建连接
            try {
                if (hikariDataSource.getHikariPoolMXBean() != null) {
                    int totalConnections = hikariDataSource.getHikariPoolMXBean().getTotalConnections();
                    int activeConnections = hikariDataSource.getHikariPoolMXBean().getActiveConnections();
                    int idleConnections = hikariDataSource.getHikariPoolMXBean().getIdleConnections();

                    log.info("HikariCP pool status before eviction: total={}, active={}, idle={}",
                            totalConnections, activeConnections, idleConnections);

                    hikariDataSource.getHikariPoolMXBean().softEvictConnections();

                    log.info("HikariCP credential updated and connections soft-evicted. " +
                            "Active connections will be replaced when returned to pool.");
                }
            } catch (Exception e) {
                log.warn("Error during HikariCP connection eviction: {}", e.getMessage(), e);
            }
        }
    }
}
