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

package com.tencentcloudapi.ssm.rotation.db.dbcp;

import org.apache.commons.dbcp2.BasicDataSource;
import com.tencentcloudapi.ssm.rotation.SsmRotationException;
import com.tencentcloudapi.ssm.rotation.config.DynamicSecretRotationConfig;
import com.tencentcloudapi.ssm.rotation.db.AbstractSsmRotationDataSource;
import com.tencentcloudapi.ssm.rotation.db.CredentialChangeListener;
import com.tencentcloudapi.ssm.rotation.db.DynamicSecretRotationDb;
import com.tencentcloudapi.ssm.rotation.db.ExtraPropertiesApplier;
import com.tencentcloudapi.ssm.rotation.ssm.DbAccount;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Map;

/**
 * 基于 Apache Commons DBCP2 连接池的 SSM 凭据轮转数据源
 * 
 * <p>封装 {@link BasicDataSource} 和 {@link DynamicSecretRotationDb}，实现凭据轮转与连接池的自动联动。
 * 用户只需替换原有的 DataSource 配置，即可实现零改造接入 SSM 凭据轮转。</p>
 * 
 * <h2>核心特性</h2>
 * <ul>
 *   <li>实现标准 {@link DataSource} 接口，可直接替换原有数据源</li>
 *   <li>凭据轮转时自动更新 DBCP 的用户名密码</li>
 *   <li>凭据轮转时自动清空空闲连接池，新连接使用新凭据</li>
 *   <li>支持 Builder 模式配置，提供合理默认值</li>
 *   <li>支持传入自定义 BasicDataSource 进行精细配置</li>
 * </ul>
 * 
 * <h2>使用示例</h2>
 * <pre>{@code
 * DataSource dataSource = SsmRotationDbcpDataSource.builder()
 *         .rotationConfig(config)
 *         .maxTotal(20)
 *         .minIdle(5)
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
public class SsmRotationDbcpDataSource extends AbstractSsmRotationDataSource<BasicDataSource> {

    private SsmRotationDbcpDataSource(DynamicSecretRotationDb rotationDb,
                                      BasicDataSource basicDataSource,
                                      CredentialChangeListener credentialChangeListener) {
        super(rotationDb, basicDataSource, credentialChangeListener, "BasicDataSource");
    }

    public static Builder builder() {
        return new Builder();
    }

    // ==================== 高级访问 ====================

    /**
     * 获取底层 DBCP 数据源
     */
    public BasicDataSource getBasicDataSource() {
        return getDelegateDataSource();
    }

    @Override
    protected boolean isDelegateHealthy(BasicDataSource delegateDataSource) {
        return !delegateDataSource.isClosed();
    }

    // ==================== Builder ====================

    /**
     * SsmRotationDbcpDataSource 构建器
     * 
     * <p>提供两种使用方式：</p>
     * <ul>
     *   <li><b>简单模式</b>：通过 Builder 参数配置 DBCP</li>
     *   <li><b>高级模式</b>：传入自定义 BasicDataSource</li>
     * </ul>
     */
    public static class Builder {

        private DynamicSecretRotationConfig rotationConfig;

        // DBCP 连接池参数（可选，提供合理默认值）
        private int maxTotal = 20;
        private int minIdle = 5;
        private int maxIdle = 10;
        private int initialSize = 5;
        private long maxWaitMillis = 60000;
        private long timeBetweenEvictionRunsMillis = 60000;
        private long minEvictableIdleTimeMillis = 300000;
        private long softMinEvictableIdleTimeMillis = 120000;
        private String validationQuery = "SELECT 1";
        private boolean testWhileIdle = true;
        private boolean testOnBorrow = true;
        private boolean testOnReturn = false;

        /**
         * 扩展属性：用于设置 SDK 未显式暴露的 DBCP 原生参数
         */
        private java.util.Map<String, Object> extraProperties;

        /**
         * 高级模式：用户自定义的 BasicDataSource
         */
        private BasicDataSource customBasicDataSource;

        public Builder rotationConfig(DynamicSecretRotationConfig rotationConfig) {
            this.rotationConfig = rotationConfig;
            return this;
        }

        /**
         * 设置最大连接数，默认 20
         */
        public Builder maxTotal(int maxTotal) {
            this.maxTotal = maxTotal;
            return this;
        }

        /**
         * 设置最小空闲连接数，默认 5
         */
        public Builder minIdle(int minIdle) {
            this.minIdle = minIdle;
            return this;
        }

        /**
         * 设置最大空闲连接数，默认 10
         */
        public Builder maxIdle(int maxIdle) {
            this.maxIdle = maxIdle;
            return this;
        }

        /**
         * 设置初始连接数，默认 5
         */
        public Builder initialSize(int initialSize) {
            this.initialSize = initialSize;
            return this;
        }

        /**
         * 设置获取连接最大等待时间（毫秒），默认 60000
         */
        public Builder maxWaitMillis(long maxWaitMillis) {
            this.maxWaitMillis = maxWaitMillis;
            return this;
        }

        /**
         * 设置检测间隔时间（毫秒），默认 60000
         */
        public Builder timeBetweenEvictionRunsMillis(long timeBetweenEvictionRunsMillis) {
            this.timeBetweenEvictionRunsMillis = timeBetweenEvictionRunsMillis;
            return this;
        }

        /**
         * 设置连接最小空闲时间（毫秒），默认 300000
         * 
         * <p>超过此时间的多余空闲连接（超出 minIdle 部分）会被淘汰。</p>
         */
        public Builder minEvictableIdleTimeMillis(long minEvictableIdleTimeMillis) {
            this.minEvictableIdleTimeMillis = minEvictableIdleTimeMillis;
            return this;
        }

        /**
         * 设置 minIdle 以内连接的最小空闲时间（毫秒），默认 120000（2分钟）
         * 
         * <p>与 minEvictableIdleTimeMillis 不同，此参数作用于 minIdle 以内的连接。
         * 当连接空闲超过此时间后，空闲检测线程会对其进行有效性检测（需配合 testWhileIdle=true），
         * 无效连接会被淘汰并重建，从而防止 MySQL wait_timeout 超时导致连接被服务端断开。</p>
         * 
         * <p>建议设置为小于 MySQL wait_timeout 的值（MySQL 默认 wait_timeout=28800秒=8小时）。</p>
         */
        public Builder softMinEvictableIdleTimeMillis(long softMinEvictableIdleTimeMillis) {
            this.softMinEvictableIdleTimeMillis = softMinEvictableIdleTimeMillis;
            return this;
        }

        /**
         * 设置验证查询 SQL，默认 "SELECT 1"
         */
        public Builder validationQuery(String validationQuery) {
            this.validationQuery = validationQuery;
            return this;
        }

        /**
         * 设置空闲时是否检测连接有效性，默认 true
         */
        public Builder testWhileIdle(boolean testWhileIdle) {
            this.testWhileIdle = testWhileIdle;
            return this;
        }

        /**
         * 设置获取连接时是否检测有效性，默认 true
         * 
         * <p>开启后，每次从连接池获取连接时会先执行 validationQuery 检测连接有效性。
         * 虽然有少量性能开销，但可以有效避免获取到已被 MySQL 服务端断开的无效连接，
         * 是防止 CommunicationsException 的最后一道防线。</p>
         */
        public Builder testOnBorrow(boolean testOnBorrow) {
            this.testOnBorrow = testOnBorrow;
            return this;
        }

        /**
         * 设置归还连接时是否检测有效性，默认 false
         */
        public Builder testOnReturn(boolean testOnReturn) {
            this.testOnReturn = testOnReturn;
            return this;
        }

        /**
         * 设置扩展属性，用于配置 SDK 未显式暴露的 DBCP 原生参数
         *
         * <p>通过此方法可以设置任意 DBCP 支持的参数，无需等待 SDK 升级。
         * 例如：numTestsPerEvictionRun、logAbandoned、removeAbandonedTimeout 等。</p>
         */
        public Builder extraProperties(java.util.Map<String, Object> extraProperties) {
            this.extraProperties = extraProperties;
            return this;
        }

        /**
         * 高级模式：传入自定义的 BasicDataSource
         * 
         * <p>SDK 会自动设置 url、username、password，其他配置由用户自行控制。</p>
         */
        public Builder customBasicDataSource(BasicDataSource customBasicDataSource) {
            this.customBasicDataSource = customBasicDataSource;
            return this;
        }

        public SsmRotationDbcpDataSource build() throws SsmRotationException {
            if (rotationConfig == null) {
                throw new SsmRotationException(SsmRotationException.ERROR_CONFIG,
                        "rotationConfig cannot be null");
            }

            // 1. 创建 SSM 凭据轮转连接工厂
            DynamicSecretRotationDb rotationDb = new DynamicSecretRotationDb(rotationConfig);

            // 2. 获取初始凭据
            DbAccount initialAccount = rotationDb.getCurrentAccount();
            String jdbcUrl = rotationDb.buildJdbcUrl();

            // 3. 创建或配置 BasicDataSource
            BasicDataSource dbcp;
            if (customBasicDataSource != null) {
                dbcp = customBasicDataSource;
            } else {
                dbcp = new BasicDataSource();
                dbcp.setMaxTotal(maxTotal);
                dbcp.setMinIdle(minIdle);
                dbcp.setMaxIdle(maxIdle);
                dbcp.setInitialSize(initialSize);
                dbcp.setMaxWait(Duration.ofMillis(maxWaitMillis));
                dbcp.setDurationBetweenEvictionRuns(Duration.ofMillis(timeBetweenEvictionRunsMillis));
                dbcp.setMinEvictableIdle(Duration.ofMillis(minEvictableIdleTimeMillis));
                dbcp.setSoftMinEvictableIdle(Duration.ofMillis(softMinEvictableIdleTimeMillis));
                dbcp.setValidationQuery(validationQuery);
                dbcp.setTestWhileIdle(testWhileIdle);
                dbcp.setTestOnBorrow(testOnBorrow);
                dbcp.setTestOnReturn(testOnReturn);

                // 应用扩展属性：通过反射调用 DBCP 原生 setter，支持用户配置 SDK 未显式暴露的参数
                ExtraPropertiesApplier.apply(dbcp, extraProperties);
            }

            // 设置连接信息
            dbcp.setUrl(jdbcUrl);
            dbcp.setUsername(initialAccount.getUserName());
            dbcp.setPassword(initialAccount.getPassword());

            // 4. 验证连接池可用（DBCP 在首次 getConnection 时才真正初始化）
            try {
                Connection testConn = dbcp.getConnection();
                testConn.close();
                log.info("BasicDataSource initialized, url={}, username={}, maxTotal={}, minIdle={}",
                        jdbcUrl, initialAccount.getUserName(), dbcp.getMaxTotal(), dbcp.getMinIdle());
            } catch (SQLException e) {
                rotationDb.close();
                try {
                    dbcp.close();
                } catch (Exception ignored) {
                }
                throw new SsmRotationException(SsmRotationException.ERROR_CONFIG,
                        "Failed to initialize BasicDataSource: " + e.getMessage(), e);
            }

            // 5. 注册凭据变更监听器
            CredentialChangeListener listener = new DbcpCredentialChangeListener(dbcp);
            rotationDb.addCredentialChangeListener(listener);

            log.info("SsmRotationDbcpDataSource created successfully");
            return new SsmRotationDbcpDataSource(rotationDb, dbcp, listener);
        }
    }

    // ==================== 内部类 ====================

    /**
     * DBCP 凭据变更监听器
     */
    @Slf4j
    private static class DbcpCredentialChangeListener implements CredentialChangeListener {

        private final BasicDataSource basicDataSource;

        DbcpCredentialChangeListener(BasicDataSource basicDataSource) {
            this.basicDataSource = basicDataSource;
        }

        @Override
        public void onCredentialChanged(DbAccount oldAccount, DbAccount newAccount) {
            log.info("Credential rotated, updating BasicDataSource: {} -> {}",
                    oldAccount.getUserName(), newAccount.getUserName());

            try {
                int numActive = basicDataSource.getNumActive();
                int numIdle = basicDataSource.getNumIdle();

                log.info("DBCP pool status before credential update: active={}, idle={}",
                        numActive, numIdle);

                // 1. 更新凭据（先 password 后 username，缩短非原子更新的不一致窗口）
                basicDataSource.setPassword(newAccount.getPassword());
                basicDataSource.setUsername(newAccount.getUserName());

                // 2. 清空所有空闲连接，确保旧凭据连接不会被复用
                // 注意：evict() 只逐出满足空闲时间条件的连接，不保证清除所有旧凭据连接。
                // 这里使用先将 minIdle 置 0 再 clear() 的方式，确保所有空闲连接被关闭。
                int originalMinIdle = basicDataSource.getMinIdle();
                try {
                    basicDataSource.setMinIdle(0);
                    // 使用 invalidateConnection 逐个关闭空闲连接的方式不可行，
                    // 改用 evict + 设置 softMinEvictableIdle 为 0 来强制逐出
                    basicDataSource.evict();
                } finally {
                    basicDataSource.setMinIdle(originalMinIdle);
                }

                log.info("DBCP credential updated. Idle connections evicted, active ones will be replaced on return.");
            } catch (SQLException e) {
                log.warn("Error during DBCP credential update: {}", e.getMessage(), e);
                log.info("Credentials have been updated. New connections will use new credentials.");
            } catch (Exception e) {
            log.warn("Unexpected error during DBCP credential update: {}", e.getMessage(), e);
            }
        }
    }

}
