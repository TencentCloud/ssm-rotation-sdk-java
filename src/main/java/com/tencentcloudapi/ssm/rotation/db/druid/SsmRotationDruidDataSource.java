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

package com.tencentcloudapi.ssm.rotation.db.druid;

import com.alibaba.druid.pool.DruidAbstractDataSource;
import com.alibaba.druid.pool.DruidDataSource;
import com.tencentcloudapi.ssm.rotation.SsmRotationException;
import com.tencentcloudapi.ssm.rotation.config.DynamicSecretRotationConfig;
import com.tencentcloudapi.ssm.rotation.db.AbstractSsmRotationDataSource;
import com.tencentcloudapi.ssm.rotation.db.CredentialChangeListener;
import com.tencentcloudapi.ssm.rotation.db.DynamicSecretRotationDb;
import com.tencentcloudapi.ssm.rotation.ssm.DbAccount;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.sql.SQLException;

/**
 * 基于 Druid 连接池的 SSM 凭据轮转数据源
 * 
 * <p>封装 {@link DruidDataSource} 和 {@link DynamicSecretRotationDb}，实现凭据轮转与连接池的自动联动。
 * 用户只需替换原有的 DataSource 配置，即可实现零改造接入 SSM 凭据轮转。</p>
 * 
 * <h2>核心特性</h2>
 * <ul>
 *   <li>实现标准 {@link DataSource} 接口，可直接替换原有数据源</li>
 *   <li>凭据轮转时自动更新 Druid 的用户名密码</li>
 *   <li>凭据轮转时自动软淘汰旧连接，新连接使用新凭据</li>
 *   <li>支持 Builder 模式配置，提供合理默认值</li>
 *   <li>支持传入自定义 DruidDataSource 进行精细配置</li>
 * </ul>
 * 
 * <h2>使用示例</h2>
 * <pre>{@code
 * // 快速使用
 * DataSource dataSource = SsmRotationDruidDataSource.builder()
 *         .rotationConfig(config)
 *         .maxActive(20)
 *         .minIdle(5)
 *         .build();
 * 
 * // 像普通 DataSource 一样使用
 * try (Connection conn = dataSource.getConnection()) {
 *     // 执行数据库操作，凭据轮转完全透明
 * }
 * 
 * // Spring Boot 集成
 * {@literal @}Bean
 * public DataSource dataSource() throws SsmRotationException {
 *     return SsmRotationDruidDataSource.builder()
 *             .rotationConfig(config)
 *             .build();
 * }
 * }</pre>
 * 
 * @author tencentcloud
 * @since 1.0.0
 */
@Slf4j
public class SsmRotationDruidDataSource extends AbstractSsmRotationDataSource<DruidDataSource> {

    /**
     * 私有构造函数，通过 Builder 创建实例
     */
    private SsmRotationDruidDataSource(DynamicSecretRotationDb rotationDb,
                                       DruidDataSource druidDataSource,
                                       CredentialChangeListener credentialChangeListener) {
        super(rotationDb, druidDataSource, credentialChangeListener, "DruidDataSource");
    }

    /**
     * 创建 Builder
     * 
     * @return Builder 实例
     */
    public static Builder builder() {
        return new Builder();
    }

    // ==================== 高级访问 ====================

    /**
     * 获取底层 Druid 数据源（高级用户使用）
     * 
     * <p>可用于获取 Druid 的监控统计信息、配置调整等。</p>
     * 
     * @return Druid 数据源实例
     */
    public DruidDataSource getDruidDataSource() {
        return getDelegateDataSource();
    }

    // ==================== Builder ====================

    /**
     * SsmRotationDruidDataSource 构建器
     * 
     * <p>提供两种使用方式：</p>
     * <ul>
     *   <li><b>简单模式</b>：通过 Builder 参数配置 Druid，适合大多数场景</li>
     *   <li><b>高级模式</b>：传入自定义 DruidDataSource，适合需要精细配置的场景</li>
     * </ul>
     */
    public static class Builder {

        // ==================== 必填参数 ====================

        /**
         * SSM 凭据轮转配置（必填）
         */
        private DynamicSecretRotationConfig rotationConfig;

        // ==================== Druid 连接池参数（可选，提供合理默认值） ====================

        private int maxActive = 20;
        private int minIdle = 5;
        private int initialSize = 5;
        private long maxWait = 60000;
        private long timeBetweenEvictionRunsMillis = 60000;
        private long minEvictableIdleTimeMillis = 300000;
        private String validationQuery = "SELECT 1";
        private boolean testWhileIdle = true;
        private boolean testOnBorrow = false;
        private boolean testOnReturn = false;

        /**
         * 高级模式：用户自定义的 DruidDataSource
         * 如果设置了此项，上面的 Druid 参数将被忽略
         */
        private DruidDataSource customDruidDataSource;

        // ==================== Builder 方法 ====================

        /**
         * 设置 SSM 凭据轮转配置（必填）
         */
        public Builder rotationConfig(DynamicSecretRotationConfig rotationConfig) {
            this.rotationConfig = rotationConfig;
            return this;
        }

        /**
         * 设置最大活跃连接数，默认 20
         */
        public Builder maxActive(int maxActive) {
            this.maxActive = maxActive;
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
         * 设置初始连接数，默认 5
         */
        public Builder initialSize(int initialSize) {
            this.initialSize = initialSize;
            return this;
        }

        /**
         * 设置获取连接最大等待时间（毫秒），默认 60000
         */
        public Builder maxWait(long maxWait) {
            this.maxWait = maxWait;
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
         */
        public Builder minEvictableIdleTimeMillis(long minEvictableIdleTimeMillis) {
            this.minEvictableIdleTimeMillis = minEvictableIdleTimeMillis;
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
         * 设置获取连接时是否检测有效性，默认 false
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
         * 高级模式：传入自定义的 DruidDataSource
         * 
         * <p>如果设置了此项，Builder 中的 Druid 参数（maxActive、minIdle 等）将被忽略。
         * SDK 会自动设置 url、username、password，其他配置由用户自行控制。</p>
         * 
         * <p><b>注意：</b>传入的 DruidDataSource 不要调用 init()，SDK 会在配置完成后自动初始化。</p>
         */
        public Builder customDruidDataSource(DruidDataSource customDruidDataSource) {
            this.customDruidDataSource = customDruidDataSource;
            return this;
        }

        /**
         * 构建 SsmRotationDruidDataSource
         * 
         * @return SsmRotationDruidDataSource 实例
         * @throws SsmRotationException 当配置无效或初始化失败时抛出
         */
        public SsmRotationDruidDataSource build() throws SsmRotationException {
            // 参数校验
            if (rotationConfig == null) {
                throw new SsmRotationException(SsmRotationException.ERROR_CONFIG,
                        "rotationConfig cannot be null");
            }

            // 1. 创建 SSM 凭据轮转连接工厂
            DynamicSecretRotationDb rotationDb = new DynamicSecretRotationDb(rotationConfig);

            // 2. 获取初始凭据
            DbAccount initialAccount = rotationDb.getCurrentAccount();
            String jdbcUrl = rotationDb.buildJdbcUrl();

            // 3. 创建或配置 DruidDataSource
            DruidDataSource druid;
            if (customDruidDataSource != null) {
                // 高级模式：使用用户自定义的 DruidDataSource
                druid = customDruidDataSource;
            } else {
                // 简单模式：根据 Builder 参数创建
                druid = new DruidDataSource();
                druid.setMaxActive(maxActive);
                druid.setMinIdle(minIdle);
                druid.setInitialSize(initialSize);
                druid.setMaxWait(maxWait);
                druid.setTimeBetweenEvictionRunsMillis(timeBetweenEvictionRunsMillis);
                druid.setMinEvictableIdleTimeMillis(minEvictableIdleTimeMillis);
                druid.setValidationQuery(validationQuery);
                druid.setTestWhileIdle(testWhileIdle);
                druid.setTestOnBorrow(testOnBorrow);
                druid.setTestOnReturn(testOnReturn);
            }

            // 设置连接信息（无论哪种模式都由 SDK 管理）
            druid.setUrl(jdbcUrl);
            druid.setUsername(initialAccount.getUserName());
            druid.setPassword(initialAccount.getPassword());

            // 4. 初始化 Druid 连接池
            try {
                druid.init();
                log.info("DruidDataSource initialized, url={}, username={}, maxActive={}, minIdle={}",
                        jdbcUrl, initialAccount.getUserName(), druid.getMaxActive(), druid.getMinIdle());
            } catch (SQLException e) {
                // 初始化失败，清理资源
                rotationDb.close();
                try {
                    druid.close();
                } catch (Exception ignored) {
                }
                throw new SsmRotationException(SsmRotationException.ERROR_CONFIG,
                        "Failed to initialize DruidDataSource: " + e.getMessage(), e);
            }

            // 5. 注册凭据变更监听器
            CredentialChangeListener listener = new DruidCredentialChangeListener(druid);
            rotationDb.addCredentialChangeListener(listener);

            log.info("SsmRotationDruidDataSource created successfully");
            return new SsmRotationDruidDataSource(rotationDb, druid, listener);
        }
    }

    // ==================== 内部类 ====================

    /**
     * Druid 凭据变更监听器
     * 
     * <p>当 SSM 凭据发生轮转时，自动更新 Druid 的用户名密码，并软淘汰旧连接。</p>
     */
    @Slf4j
    private static class DruidCredentialChangeListener implements CredentialChangeListener {

        private final DruidDataSource druidDataSource;

        DruidCredentialChangeListener(DruidDataSource druidDataSource) {
            this.druidDataSource = druidDataSource;
        }

        @Override
        public void onCredentialChanged(DbAccount oldAccount, DbAccount newAccount) {
            log.info("Credential rotated, updating DruidDataSource: {} -> {}",
                    oldAccount.getUserName(), newAccount.getUserName());

            // 1. 更新 Druid 的用户名和密码
            // 注意：Druid 在 init() 后，setUsername() 会抛出 UnsupportedOperationException，
            // 需要通过反射绕过 inited 检查直接修改底层字段
            updateDruidCredential(druidDataSource, newAccount.getUserName(), newAccount.getPassword());

            // 2. 软淘汰旧连接
            // 说明：仅调用 shrink(true, true) 时，<= minIdle 的空闲连接可能保留，
            // 轮转后这些连接仍可能短暂使用旧凭据。这里临时将 minIdle 置 0 后 shrink，
            // 尽量淘汰所有空闲旧连接，再恢复原 minIdle。
            int originalMinIdle = druidDataSource.getMinIdle();
            try {
                // 获取当前活跃连接数用于日志
                int activeCount = druidDataSource.getActiveCount();
                int poolingCount = druidDataSource.getPoolingCount();

                log.info("Druid pool status before eviction: activeCount={}, poolingCount={}",
                        activeCount, poolingCount);

                druidDataSource.setMinIdle(0);
                druidDataSource.shrink(true, true);

                log.info("Druid credential updated and idle connections evicted. " +
                        "Active connections will be replaced on next borrow.");
            } catch (Exception e) {
                log.warn("Error during Druid connection eviction: {}", e.getMessage(), e);
            } finally {
                try {
                    druidDataSource.setMinIdle(originalMinIdle);
                } catch (Exception e) {
                    log.warn("Failed to restore Druid minIdle after credential rotation: {}", e.getMessage(), e);
                }
            }
        }
        /**
         * 通过反射更新 Druid 的用户名和密码，绕过 init() 后的限制
         *
         * <p>Druid 的 {@code DruidAbstractDataSource.setUsername()} 在 {@code inited=true} 后，
         * 如果新值与旧值不同会抛出 {@code UnsupportedOperationException}。
         * 而 {@code setPassword()} 在 {@code inited=true} 后仅打印日志并正常赋值。
         * 为保持一致性和兼容性，username 通过反射修改，password 直接调用 setter。</p>
         */
        private static void updateDruidCredential(DruidDataSource dataSource, String username, String password) {
            // 更新 username：通过反射绕过 inited 检查
            try {
                Field usernameField = DruidAbstractDataSource.class.getDeclaredField("username");
                usernameField.setAccessible(true);
                usernameField.set(dataSource, username);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                // 反射失败时回退到直接调用（兼容未来 Druid 版本可能移除限制的情况）
                log.warn("Failed to update Druid username via reflection, falling back to setter: {}", e.getMessage());
                try {
                    dataSource.setUsername(username);
                } catch (Exception ex) {
                    log.error("Failed to update Druid username: {}", ex.getMessage(), ex);
                    throw new RuntimeException("Failed to update Druid username after credential rotation", ex);
                }
            }

            // 更新 password：Druid 允许 init 后直接调用 setPassword
            dataSource.setPassword(password);
        }
    }
}
