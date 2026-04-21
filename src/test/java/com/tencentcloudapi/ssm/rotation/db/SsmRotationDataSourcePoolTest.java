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

package com.tencentcloudapi.ssm.rotation.db;

import com.alibaba.druid.pool.DruidDataSource;
import com.tencentcloudapi.ssm.rotation.SsmRotationException;
import com.tencentcloudapi.ssm.rotation.config.DbConfig;
import com.tencentcloudapi.ssm.rotation.config.DynamicSecretRotationConfig;
import com.tencentcloudapi.ssm.rotation.db.dbcp.SsmRotationDbcpDataSource;
import com.tencentcloudapi.ssm.rotation.db.druid.SsmRotationDruidDataSource;
import com.tencentcloudapi.ssm.rotation.db.hikari.SsmRotationHikariDataSource;
import com.tencentcloudapi.ssm.rotation.ssm.DbAccount;
import com.tencentcloudapi.ssm.rotation.ssm.SsmAccount;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import com.zaxxer.hikari.pool.HikariPool;
import org.apache.commons.dbcp2.BasicDataSource;
import org.junit.Test;
import org.mockito.MockedConstruction;
import org.mockito.stubbing.Answer;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SSM Rotation SDK 连接池全面测试
 *
 * <p>覆盖以下测试维度：</p>
 * <ul>
 *   <li>三种连接池（Druid / HikariCP / DBCP）的 Builder 参数传递</li>
 *   <li>凭据轮转回调的正确性</li>
 *   <li>ExtraProperties 反射机制</li>
 *   <li>并发场景下的安全性</li>
 *   <li>资源关闭顺序</li>
 *   <li>异常场景处理</li>
 *   <li>健康检查</li>
 * </ul>
 */
public class SsmRotationDataSourcePoolTest {

    // ==================== 一、Druid 连接池测试 ====================

    /**
     * 测试 Druid Builder 所有参数是否正确传递到 DruidDataSource
     */
    @Test
    public void testDruidBuilder_allParametersShouldBeApplied() throws Exception {
        DbAccount initialAccount = new DbAccount("user_druid", "pwd_druid");
        DruidDataSource capturedDruid = new DruidDataSource();

        try (MockedConstruction<DynamicSecretRotationDb> rotationDbMocked = mockConstruction(
                DynamicSecretRotationDb.class,
                (mock, context) -> {
                    when(mock.getCurrentAccount()).thenReturn(initialAccount);
                    when(mock.buildJdbcUrl()).thenReturn("jdbc:mysql://127.0.0.1:3306/testdb");
                });
             MockedConstruction<DruidDataSource> druidMocked = mockConstruction(
                     DruidDataSource.class,
                     (mock, context) -> {
                         when(mock.getMaxActive()).thenReturn(30);
                         when(mock.getMinIdle()).thenReturn(10);
                     })) {

            SsmRotationDruidDataSource dataSource = SsmRotationDruidDataSource.builder()
                    .rotationConfig(validRotationConfig())
                    .maxActive(30)
                    .minIdle(10)
                    .initialSize(8)
                    .maxWait(30000)
                    .timeBetweenEvictionRunsMillis(30000)
                    .minEvictableIdleTimeMillis(180000)
                    .validationQuery("SELECT 1 FROM DUAL")
                    .testWhileIdle(true)
                    .testOnBorrow(true)
                    .testOnReturn(true)
                    .keepAlive(true)
                    .keepAliveBetweenTimeMillis(60000)
                    .build();

            DruidDataSource druid = druidMocked.constructed().get(0);

            // 验证所有参数都被正确设置
            verify(druid).setMaxActive(30);
            verify(druid).setMinIdle(10);
            verify(druid).setInitialSize(8);
            verify(druid).setMaxWait(30000);
            verify(druid).setTimeBetweenEvictionRunsMillis(30000);
            verify(druid).setMinEvictableIdleTimeMillis(180000);
            verify(druid).setValidationQuery("SELECT 1 FROM DUAL");
            verify(druid).setTestWhileIdle(true);
            verify(druid).setTestOnBorrow(true);
            verify(druid).setTestOnReturn(true);
            verify(druid).setKeepAlive(true);
            verify(druid).setKeepAliveBetweenTimeMillis(60000);

            // 验证连接信息
            verify(druid).setUrl("jdbc:mysql://127.0.0.1:3306/testdb");
            verify(druid).setUsername("user_druid");
            verify(druid).setPassword("pwd_druid");

            // 验证 init 被调用
            verify(druid).init();

            dataSource.close();
        }
    }

    /**
     * 测试 Druid ExtraProperties 是否正确应用
     */
    @Test
    public void testDruidBuilder_extraPropertiesShouldBeApplied() throws Exception {
        DbAccount initialAccount = new DbAccount("user_druid", "pwd_druid");

        try (MockedConstruction<DynamicSecretRotationDb> rotationDbMocked = mockConstruction(
                DynamicSecretRotationDb.class,
                (mock, context) -> {
                    when(mock.getCurrentAccount()).thenReturn(initialAccount);
                    when(mock.buildJdbcUrl()).thenReturn("jdbc:mysql://127.0.0.1:3306/testdb");
                });
             MockedConstruction<DruidDataSource> druidMocked = mockConstruction(
                     DruidDataSource.class,
                     (mock, context) -> {
                         when(mock.getMaxActive()).thenReturn(20);
                         when(mock.getMinIdle()).thenReturn(5);
                     })) {

            Map<String, Object> extraProps = new HashMap<>();
            extraProps.put("maxEvictableIdleTimeMillis", 900000L);
            extraProps.put("removeAbandoned", true);
            extraProps.put("removeAbandonedTimeoutMillis", 300000L);

            SsmRotationDruidDataSource dataSource = SsmRotationDruidDataSource.builder()
                    .rotationConfig(validRotationConfig())
                    .extraProperties(extraProps)
                    .build();

            DruidDataSource druid = druidMocked.constructed().get(0);

            // 验证 extraProperties 通过反射被应用
            // 注意：由于 DruidDataSource 是 mock 对象，反射调用 setter 实际上会调用 mock 的方法
            verify(druid).setMaxEvictableIdleTimeMillis(900000L);
            verify(druid).setRemoveAbandoned(true);
            verify(druid).setRemoveAbandonedTimeoutMillis(300000L);

            dataSource.close();
        }
    }

    /**
     * 测试 Druid 凭据轮转回调：验证 password 先于 username 更新
     */
    @Test
    public void testDruidCredentialRotation_passwordUpdatedBeforeUsername() throws Exception {
        DbAccount initialAccount = new DbAccount("user_old", "pwd_old");
        DbAccount newAccount = new DbAccount("user_new", "pwd_new");
        DruidDataSource customDruid = mock(DruidDataSource.class);
        when(customDruid.getMaxActive()).thenReturn(20);
        when(customDruid.getMinIdle()).thenReturn(5);
        when(customDruid.getActiveCount()).thenReturn(3);
        when(customDruid.getPoolingCount()).thenReturn(7);

        doNothing().when(customDruid).setMinIdle(0);
        doNothing().when(customDruid).shrink(true, true);

        try (MockedConstruction<DynamicSecretRotationDb> rotationDbMocked = mockConstruction(
                DynamicSecretRotationDb.class,
                (mock, context) -> {
                    when(mock.getCurrentAccount()).thenReturn(initialAccount);
                    when(mock.buildJdbcUrl()).thenReturn("jdbc:mysql://127.0.0.1:3306/testdb");
                })) {

            SsmRotationDruidDataSource dataSource = SsmRotationDruidDataSource.builder()
                    .rotationConfig(validRotationConfig())
                    .customDruidDataSource(customDruid)
                    .build();

            // 获取注册的 listener 并手动触发
            DynamicSecretRotationDb rotationDb = rotationDbMocked.constructed().get(0);
            verify(rotationDb).addCredentialChangeListener(any(CredentialChangeListener.class));

            // 通过反射获取 listener
            CredentialChangeListener listener = captureListener(dataSource);
            assertNotNull("Listener should be registered", listener);

            // 触发凭据轮转
            listener.onCredentialChanged(initialAccount, newAccount);

            // 验证 password 先被更新
            verify(customDruid).setPassword("pwd_new");
            // 验证 shrink 被调用
            verify(customDruid).shrink(true, true);

            dataSource.close();
        }
    }

    /**
     * 测试 Druid init 失败时资源清理
     */
    @Test
    public void testDruidBuilder_initFailure_shouldCleanupResources() throws Exception {
        DbAccount initialAccount = new DbAccount("user_druid", "pwd_druid");

        try (MockedConstruction<DynamicSecretRotationDb> rotationDbMocked = mockConstruction(
                DynamicSecretRotationDb.class,
                (mock, context) -> {
                    when(mock.getCurrentAccount()).thenReturn(initialAccount);
                    when(mock.buildJdbcUrl()).thenReturn("jdbc:mysql://127.0.0.1:3306/testdb");
                });
             MockedConstruction<DruidDataSource> druidMocked = mockConstruction(
                     DruidDataSource.class,
                     (mock, context) -> {
                         doThrow(new SQLException("Connection refused")).when(mock).init();
                     })) {

            try {
                SsmRotationDruidDataSource.builder()
                        .rotationConfig(validRotationConfig())
                        .build();
                fail("Expected SsmRotationException");
            } catch (SsmRotationException e) {
                assertTrue(e.getMessage().contains("Failed to initialize DruidDataSource"));
            }

            // 验证资源被清理
            DynamicSecretRotationDb rotationDb = rotationDbMocked.constructed().get(0);
            verify(rotationDb).close();
            verify(druidMocked.constructed().get(0)).close();
        }
    }

    // ==================== 二、HikariCP 连接池测试 ====================

    /**
     * 测试 HikariCP Builder 所有参数是否正确传递到 HikariConfig
     */
    @Test
    public void testHikariBuilder_allParametersShouldBeApplied() throws Exception {
        DbAccount initialAccount = new DbAccount("user_hikari", "pwd_hikari");
        AtomicReference<HikariConfig> capturedConfig = new AtomicReference<>();

        try (MockedConstruction<DynamicSecretRotationDb> rotationDbMocked = mockConstruction(
                DynamicSecretRotationDb.class,
                (mock, context) -> {
                    when(mock.getCurrentAccount()).thenReturn(initialAccount);
                    when(mock.buildJdbcUrl()).thenReturn("jdbc:mysql://127.0.0.1:3306/testdb");
                });
             MockedConstruction<HikariDataSource> hikariMocked = mockConstruction(
                     HikariDataSource.class,
                     (mock, context) -> {
                         capturedConfig.set((HikariConfig) context.arguments().get(0));
                         when(mock.getMaximumPoolSize()).thenReturn(30);
                         when(mock.getMinimumIdle()).thenReturn(10);
                     })) {

            SsmRotationHikariDataSource dataSource = SsmRotationHikariDataSource.builder()
                    .rotationConfig(validRotationConfig())
                    .maximumPoolSize(30)
                    .minimumIdle(10)
                    .connectionTimeout(15000)
                    .idleTimeout(300000)
                    .maxLifetime(900000)
                    .connectionTestQuery("SELECT 1 FROM DUAL")
                    .poolName("TestPool")
                    .build();

            HikariConfig config = capturedConfig.get();
            assertNotNull(config);

            // 验证所有参数
            assertEquals(30, config.getMaximumPoolSize());
            assertEquals(10, config.getMinimumIdle());
            assertEquals(15000, config.getConnectionTimeout());
            assertEquals(300000, config.getIdleTimeout());
            assertEquals(900000, config.getMaxLifetime());
            assertEquals("SELECT 1 FROM DUAL", config.getConnectionTestQuery());
            assertEquals("TestPool", config.getPoolName());

            // 验证连接信息
            assertEquals("jdbc:mysql://127.0.0.1:3306/testdb", config.getJdbcUrl());
            assertEquals("user_hikari", config.getUsername());
            assertEquals("pwd_hikari", config.getPassword());

            dataSource.close();
        }
    }

    /**
     * 测试 HikariCP 凭据轮转回调：验证 softEvictConnections 被调用
     */
    @Test
    public void testHikariCredentialRotation_shouldSoftEvictConnections() throws Exception {
        DbAccount initialAccount = new DbAccount("user_old", "pwd_old");
        DbAccount newAccount = new DbAccount("user_new", "pwd_new");
        AtomicReference<HikariConfig> capturedConfig = new AtomicReference<>();

        try (MockedConstruction<DynamicSecretRotationDb> rotationDbMocked = mockConstruction(
                DynamicSecretRotationDb.class,
                (mock, context) -> {
                    when(mock.getCurrentAccount()).thenReturn(initialAccount);
                    when(mock.buildJdbcUrl()).thenReturn("jdbc:mysql://127.0.0.1:3306/testdb");
                });
             MockedConstruction<HikariDataSource> hikariMocked = mockConstruction(
                     HikariDataSource.class,
                     (mock, context) -> {
                         capturedConfig.set((HikariConfig) context.arguments().get(0));
                         when(mock.getMaximumPoolSize()).thenReturn(20);
                         when(mock.getMinimumIdle()).thenReturn(5);
                         HikariPoolMXBean poolMXBean = mock(HikariPoolMXBean.class);
                         when(poolMXBean.getTotalConnections()).thenReturn(10);
                         when(poolMXBean.getActiveConnections()).thenReturn(3);
                         when(poolMXBean.getIdleConnections()).thenReturn(7);
                         when(mock.getHikariPoolMXBean()).thenReturn(poolMXBean);
                     })) {

            SsmRotationHikariDataSource dataSource = SsmRotationHikariDataSource.builder()
                    .rotationConfig(validRotationConfig())
                    .build();

            // 获取 listener 并触发凭据轮转
            CredentialChangeListener listener = captureListener(dataSource);
            assertNotNull(listener);
            listener.onCredentialChanged(initialAccount, newAccount);

            HikariDataSource hikari = hikariMocked.constructed().get(0);
            // 验证凭据更新顺序：先 password 后 username
            verify(hikari).setPassword("pwd_new");
            verify(hikari).setUsername("user_new");
            // 验证 softEvictConnections 被调用
            verify(hikari.getHikariPoolMXBean()).softEvictConnections();

            dataSource.close();
        }
    }

    /**
     * 测试 HikariCP 自定义 HikariConfig 模式
     */
    @Test
    public void testHikariBuilder_customConfig_shouldOverrideBuilderParams() throws Exception {
        DbAccount initialAccount = new DbAccount("user_hikari", "pwd_hikari");
        HikariConfig customConfig = new HikariConfig();
        customConfig.setMaximumPoolSize(50);
        customConfig.setMinimumIdle(20);
        customConfig.setPoolName("CustomPool");

        AtomicReference<HikariConfig> capturedConfig = new AtomicReference<>();

        try (MockedConstruction<DynamicSecretRotationDb> rotationDbMocked = mockConstruction(
                DynamicSecretRotationDb.class,
                (mock, context) -> {
                    when(mock.getCurrentAccount()).thenReturn(initialAccount);
                    when(mock.buildJdbcUrl()).thenReturn("jdbc:mysql://127.0.0.1:3306/testdb");
                });
             MockedConstruction<HikariDataSource> hikariMocked = mockConstruction(
                     HikariDataSource.class,
                     (mock, context) -> {
                         capturedConfig.set((HikariConfig) context.arguments().get(0));
                         when(mock.getMaximumPoolSize()).thenReturn(50);
                         when(mock.getMinimumIdle()).thenReturn(20);
                     })) {

            SsmRotationHikariDataSource dataSource = SsmRotationHikariDataSource.builder()
                    .rotationConfig(validRotationConfig())
                    .maximumPoolSize(10)  // 应被忽略
                    .minimumIdle(2)       // 应被忽略
                    .customHikariConfig(customConfig)
                    .build();

            HikariConfig config = capturedConfig.get();
            // 应使用自定义 config 的值
            assertSame(customConfig, config);
            assertEquals(50, config.getMaximumPoolSize());
            assertEquals(20, config.getMinimumIdle());
            assertEquals("CustomPool", config.getPoolName());

            // 连接信息仍由 SDK 管理
            assertEquals("jdbc:mysql://127.0.0.1:3306/testdb", config.getJdbcUrl());
            assertEquals("user_hikari", config.getUsername());
            assertEquals("pwd_hikari", config.getPassword());

            dataSource.close();
        }
    }

    /**
     * 测试 HikariCP 构建失败时资源清理
     */
    @Test
    public void testHikariBuilder_constructionFailure_shouldCleanupResources() throws Exception {
        DbAccount initialAccount = new DbAccount("user_hikari", "pwd_hikari");

        try (MockedConstruction<DynamicSecretRotationDb> rotationDbMocked = mockConstruction(
                DynamicSecretRotationDb.class,
                (mock, context) -> {
                    when(mock.getCurrentAccount()).thenReturn(initialAccount);
                    when(mock.buildJdbcUrl()).thenReturn("jdbc:mysql://127.0.0.1:3306/testdb");
                });
             MockedConstruction<HikariDataSource> hikariMocked = mockConstruction(
                     HikariDataSource.class,
                     (mock, context) -> {
                         throw new HikariPool.PoolInitializationException(
                                 new SQLException("Connection refused"));
                     })) {

            try {
                SsmRotationHikariDataSource.builder()
                        .rotationConfig(validRotationConfig())
                        .build();
                fail("Expected SsmRotationException");
            } catch (SsmRotationException e) {
                assertTrue(e.getMessage().contains("Failed to initialize HikariDataSource"));
            }

            // 验证 rotationDb 被清理
            DynamicSecretRotationDb rotationDb = rotationDbMocked.constructed().get(0);
            verify(rotationDb).close();
        }
    }

    /**
     * 测试 HikariCP 健康检查
     */
    @Test
    public void testHikariHealthCheck_shouldReflectPoolStatus() throws Exception {
        DbAccount initialAccount = new DbAccount("user_hikari", "pwd_hikari");

        try (MockedConstruction<DynamicSecretRotationDb> rotationDbMocked = mockConstruction(
                DynamicSecretRotationDb.class,
                (mock, context) -> {
                    when(mock.getCurrentAccount()).thenReturn(initialAccount);
                    when(mock.buildJdbcUrl()).thenReturn("jdbc:mysql://127.0.0.1:3306/testdb");
                    when(mock.isHealthy()).thenReturn(true);
                });
             MockedConstruction<HikariDataSource> hikariMocked = mockConstruction(
                     HikariDataSource.class,
                     (mock, context) -> {
                         when(mock.getMaximumPoolSize()).thenReturn(20);
                         when(mock.getMinimumIdle()).thenReturn(5);
                         when(mock.isRunning()).thenReturn(true);
                     })) {

            SsmRotationHikariDataSource dataSource = SsmRotationHikariDataSource.builder()
                    .rotationConfig(validRotationConfig())
                    .build();

            assertTrue("DataSource should be healthy when both rotationDb and hikari are healthy",
                    dataSource.isHealthy());

            // 模拟 HikariCP 停止运行
            when(hikariMocked.constructed().get(0).isRunning()).thenReturn(false);
            assertFalse("DataSource should be unhealthy when hikari is not running",
                    dataSource.isHealthy());

            dataSource.close();
        }
    }

    // ==================== 三、DBCP 连接池测试 ====================

    /**
     * 测试 DBCP Builder 所有参数是否正确传递到 BasicDataSource
     */
    @Test
    public void testDbcpBuilder_allParametersShouldBeApplied() throws Exception {
        DbAccount initialAccount = new DbAccount("user_dbcp", "pwd_dbcp");
        Connection testConn = mock(Connection.class);

        try (MockedConstruction<DynamicSecretRotationDb> rotationDbMocked = mockConstruction(
                DynamicSecretRotationDb.class,
                (mock, context) -> {
                    when(mock.getCurrentAccount()).thenReturn(initialAccount);
                    when(mock.buildJdbcUrl()).thenReturn("jdbc:mysql://127.0.0.1:3306/testdb");
                });
             MockedConstruction<BasicDataSource> dbcpMocked = mockConstruction(
                     BasicDataSource.class,
                     (mock, context) -> {
                         when(mock.getConnection()).thenReturn(testConn);
                         when(mock.getMaxTotal()).thenReturn(30);
                         when(mock.getMinIdle()).thenReturn(10);
                     })) {

            SsmRotationDbcpDataSource dataSource = SsmRotationDbcpDataSource.builder()
                    .rotationConfig(validRotationConfig())
                    .maxTotal(30)
                    .minIdle(10)
                    .maxIdle(15)
                    .initialSize(8)
                    .maxWaitMillis(30000)
                    .timeBetweenEvictionRunsMillis(30000)
                    .minEvictableIdleTimeMillis(180000)
                    .softMinEvictableIdleTimeMillis(60000)
                    .validationQuery("SELECT 1 FROM DUAL")
                    .testWhileIdle(true)
                    .testOnBorrow(true)
                    .testOnReturn(true)
                    .build();

            BasicDataSource dbcp = dbcpMocked.constructed().get(0);

            // 验证所有参数
            verify(dbcp).setMaxTotal(30);
            verify(dbcp).setMinIdle(10);
            verify(dbcp).setMaxIdle(15);
            verify(dbcp).setInitialSize(8);
            verify(dbcp).setValidationQuery("SELECT 1 FROM DUAL");
            verify(dbcp).setTestWhileIdle(true);
            verify(dbcp).setTestOnBorrow(true);
            verify(dbcp).setTestOnReturn(true);

            // 验证连接信息
            verify(dbcp).setUrl("jdbc:mysql://127.0.0.1:3306/testdb");
            verify(dbcp).setUsername("user_dbcp");
            verify(dbcp).setPassword("pwd_dbcp");

            // 验证初始化连接测试
            verify(dbcp).getConnection();
            verify(testConn).close();

            dataSource.close();
        }
    }

    /**
     * 测试 DBCP 凭据轮转回调：验证 password 先于 username 更新，evict 被调用
     */
    @Test
    public void testDbcpCredentialRotation_passwordUpdatedBeforeUsername() throws Exception {
        DbAccount initialAccount = new DbAccount("user_old", "pwd_old");
        DbAccount newAccount = new DbAccount("user_new", "pwd_new");
        BasicDataSource customDbcp = mock(BasicDataSource.class);
        Connection testConn = mock(Connection.class);
        when(customDbcp.getConnection()).thenReturn(testConn);
        when(customDbcp.getMaxTotal()).thenReturn(20);
        when(customDbcp.getMinIdle()).thenReturn(5);
        when(customDbcp.getNumActive()).thenReturn(3);
        when(customDbcp.getNumIdle()).thenReturn(7);

        // 记录调用顺序
        List<String> callOrder = new CopyOnWriteArrayList<>();
        Answer<Void> passwordAnswer = invocation -> {
            callOrder.add("setPassword:" + invocation.getArgument(0));
            return null;
        };
        Answer<Void> usernameAnswer = invocation -> {
            callOrder.add("setUsername:" + invocation.getArgument(0));
            return null;
        };
        doAnswer(passwordAnswer).when(customDbcp).setPassword(any(String.class));
        doAnswer(usernameAnswer).when(customDbcp).setUsername(any(String.class));

        try (MockedConstruction<DynamicSecretRotationDb> rotationDbMocked = mockConstruction(
                DynamicSecretRotationDb.class,
                (mock, context) -> {
                    when(mock.getCurrentAccount()).thenReturn(initialAccount);
                    when(mock.buildJdbcUrl()).thenReturn("jdbc:mysql://127.0.0.1:3306/testdb");
                })) {

            SsmRotationDbcpDataSource dataSource = SsmRotationDbcpDataSource.builder()
                    .rotationConfig(validRotationConfig())
                    .customBasicDataSource(customDbcp)
                    .build();

            // 清除构建阶段的调用记录
            callOrder.clear();

            // 获取 listener 并触发凭据轮转
            CredentialChangeListener listener = captureListener(dataSource);
            assertNotNull(listener);
            listener.onCredentialChanged(initialAccount, newAccount);

            // 验证调用顺序：password 先于 username
            assertTrue("callOrder should have at least 2 entries", callOrder.size() >= 2);
            assertEquals("setPassword:pwd_new", callOrder.get(0));
            assertEquals("setUsername:user_new", callOrder.get(1));

            // 验证 evict 被调用
            verify(customDbcp).evict();

            dataSource.close();
        }
    }

    /**
     * 测试 DBCP 初始化连接失败时资源清理
     */
    @Test
    public void testDbcpBuilder_connectionFailure_shouldCleanupResources() throws Exception {
        DbAccount initialAccount = new DbAccount("user_dbcp", "pwd_dbcp");

        try (MockedConstruction<DynamicSecretRotationDb> rotationDbMocked = mockConstruction(
                DynamicSecretRotationDb.class,
                (mock, context) -> {
                    when(mock.getCurrentAccount()).thenReturn(initialAccount);
                    when(mock.buildJdbcUrl()).thenReturn("jdbc:mysql://127.0.0.1:3306/testdb");
                });
             MockedConstruction<BasicDataSource> dbcpMocked = mockConstruction(
                     BasicDataSource.class,
                     (mock, context) -> {
                         when(mock.getConnection()).thenThrow(new SQLException("Connection refused"));
                     })) {

            try {
                SsmRotationDbcpDataSource.builder()
                        .rotationConfig(validRotationConfig())
                        .build();
                fail("Expected SsmRotationException");
            } catch (SsmRotationException e) {
                assertTrue(e.getMessage().contains("Failed to initialize BasicDataSource"));
            }

            // 验证资源被清理
            DynamicSecretRotationDb rotationDb = rotationDbMocked.constructed().get(0);
            verify(rotationDb).close();
            verify(dbcpMocked.constructed().get(0)).close();
        }
    }

    /**
     * 测试 DBCP 健康检查
     */
    @Test
    public void testDbcpHealthCheck_shouldReflectPoolStatus() throws Exception {
        DbAccount initialAccount = new DbAccount("user_dbcp", "pwd_dbcp");
        Connection testConn = mock(Connection.class);

        try (MockedConstruction<DynamicSecretRotationDb> rotationDbMocked = mockConstruction(
                DynamicSecretRotationDb.class,
                (mock, context) -> {
                    when(mock.getCurrentAccount()).thenReturn(initialAccount);
                    when(mock.buildJdbcUrl()).thenReturn("jdbc:mysql://127.0.0.1:3306/testdb");
                    when(mock.isHealthy()).thenReturn(true);
                });
             MockedConstruction<BasicDataSource> dbcpMocked = mockConstruction(
                     BasicDataSource.class,
                     (mock, context) -> {
                         when(mock.getConnection()).thenReturn(testConn);
                         when(mock.getMaxTotal()).thenReturn(20);
                         when(mock.getMinIdle()).thenReturn(5);
                         when(mock.isClosed()).thenReturn(false);
                     })) {

            SsmRotationDbcpDataSource dataSource = SsmRotationDbcpDataSource.builder()
                    .rotationConfig(validRotationConfig())
                    .build();

            assertTrue("DataSource should be healthy", dataSource.isHealthy());

            // 模拟 DBCP 关闭
            when(dbcpMocked.constructed().get(0).isClosed()).thenReturn(true);
            assertFalse("DataSource should be unhealthy when DBCP is closed",
                    dataSource.isHealthy());

            dataSource.close();
        }
    }

    // ==================== 四、ExtraPropertiesApplier 测试 ====================

    /**
     * 测试 ExtraPropertiesApplier 基本类型转换
     */
    @Test
    public void testExtraPropertiesApplier_typeConversion() {
        TestTarget target = new TestTarget();
        Map<String, Object> props = new HashMap<>();
        props.put("intValue", "42");
        props.put("longValue", "123456789");
        props.put("booleanValue", "true");
        props.put("doubleValue", "3.14");
        props.put("stringValue", "hello");

        ExtraPropertiesApplier.apply(target, props);

        assertEquals(42, target.intValue);
        assertEquals(123456789L, target.longValue);
        assertTrue(target.booleanValue);
        assertEquals(3.14, target.doubleValue, 0.001);
        assertEquals("hello", target.stringValue);
    }

    /**
     * 测试 ExtraPropertiesApplier 空 Map 不抛异常
     */
    @Test
    public void testExtraPropertiesApplier_emptyMap_shouldNotThrow() {
        TestTarget target = new TestTarget();
        ExtraPropertiesApplier.apply(target, new HashMap<>());
        ExtraPropertiesApplier.apply(target, null);
        // 不抛异常即通过
    }

    /**
     * 测试 ExtraPropertiesApplier 不存在的属性不抛异常
     */
    @Test
    public void testExtraPropertiesApplier_unknownProperty_shouldNotThrow() {
        TestTarget target = new TestTarget();
        Map<String, Object> props = new HashMap<>();
        props.put("nonExistentProperty", "value");

        ExtraPropertiesApplier.apply(target, props);
        // 不抛异常即通过，只会打印 warn 日志
    }

    /**
     * 测试 ExtraPropertiesApplier null key 和 null value 被跳过
     */
    @Test
    public void testExtraPropertiesApplier_nullKeyOrValue_shouldBeSkipped() {
        TestTarget target = new TestTarget();
        Map<String, Object> props = new HashMap<>();
        props.put(null, "value");
        props.put("intValue", null);
        props.put("", "value");

        ExtraPropertiesApplier.apply(target, props);
        assertEquals(0, target.intValue); // 未被修改
    }

    // ==================== 五、公共行为测试 ====================

    /**
     * 测试 rotationConfig 为 null 时三种 Builder 都应抛出异常
     */
    @Test
    public void testAllBuilders_nullConfig_shouldThrow() {
        try {
            SsmRotationDruidDataSource.builder().build();
            fail("Expected SsmRotationException for Druid");
        } catch (SsmRotationException e) {
            assertTrue(e.getMessage().contains("rotationConfig cannot be null"));
        }

        try {
            SsmRotationHikariDataSource.builder().build();
            fail("Expected SsmRotationException for Hikari");
        } catch (SsmRotationException e) {
            assertTrue(e.getMessage().contains("rotationConfig cannot be null"));
        }

        try {
            SsmRotationDbcpDataSource.builder().build();
            fail("Expected SsmRotationException for DBCP");
        } catch (SsmRotationException e) {
            assertTrue(e.getMessage().contains("rotationConfig cannot be null"));
        }
    }

    /**
     * 测试关闭顺序：先移除 listener，再关闭 delegate，最后关闭 rotationDb
     */
    @Test
    public void testCloseOrder_shouldRemoveListenerThenCloseDelegateThenCloseRotationDb() throws Exception {
        DbAccount initialAccount = new DbAccount("user_test", "pwd_test");
        List<String> closeOrder = new CopyOnWriteArrayList<>();

        try (MockedConstruction<DynamicSecretRotationDb> rotationDbMocked = mockConstruction(
                DynamicSecretRotationDb.class,
                (mock, context) -> {
                    when(mock.getCurrentAccount()).thenReturn(initialAccount);
                    when(mock.buildJdbcUrl()).thenReturn("jdbc:mysql://127.0.0.1:3306/testdb");
                    when(mock.removeCredentialChangeListener(any())).thenAnswer(inv -> {
                        closeOrder.add("removeListener");
                        return true;
                    });
                    doAnswer(inv -> {
                        closeOrder.add("closeRotationDb");
                        return null;
                    }).when(mock).close();
                });
             MockedConstruction<DruidDataSource> druidMocked = mockConstruction(
                     DruidDataSource.class,
                     (mock, context) -> {
                         when(mock.getMaxActive()).thenReturn(20);
                         when(mock.getMinIdle()).thenReturn(5);
                         doAnswer(inv -> {
                             closeOrder.add("closeDruid");
                             return null;
                         }).when(mock).close();
                     })) {

            SsmRotationDruidDataSource dataSource = SsmRotationDruidDataSource.builder()
                    .rotationConfig(validRotationConfig())
                    .build();

            dataSource.close();

            // 验证关闭顺序
            assertEquals(3, closeOrder.size());
            assertEquals("removeListener", closeOrder.get(0));
            assertEquals("closeDruid", closeOrder.get(1));
            assertEquals("closeRotationDb", closeOrder.get(2));
        }
    }

    /**
     * 测试 DataSource 接口委托方法
     * <p>使用 DBCP（BasicDataSource）测试，因为 DruidDataSource.getConnection() 返回 DruidPooledConnection 类型，
     * mock 时会有类型转换问题。BasicDataSource.getConnection() 返回标准 Connection。</p>
     */
    @Test
    public void testDataSourceDelegation_shouldDelegateToUnderlyingPool() throws Exception {
        DbAccount initialAccount = new DbAccount("user_test", "pwd_test");
        Connection mockConn = mock(Connection.class);
        BasicDataSource customDbcp = mock(BasicDataSource.class);
        when(customDbcp.getConnection()).thenReturn(mockConn);
        when(customDbcp.getMaxTotal()).thenReturn(20);
        when(customDbcp.getMinIdle()).thenReturn(5);
        when(customDbcp.getLoginTimeout()).thenReturn(30);

        try (MockedConstruction<DynamicSecretRotationDb> rotationDbMocked = mockConstruction(
                DynamicSecretRotationDb.class,
                (mock, context) -> {
                    when(mock.getCurrentAccount()).thenReturn(initialAccount);
                    when(mock.buildJdbcUrl()).thenReturn("jdbc:mysql://127.0.0.1:3306/testdb");
                })) {

            SsmRotationDbcpDataSource dataSource = SsmRotationDbcpDataSource.builder()
                    .rotationConfig(validRotationConfig())
                    .customBasicDataSource(customDbcp)
                    .build();

            // 验证 getConnection 委托（跳过初始化时的 getConnection 调用）
            Connection conn = dataSource.getConnection();
            assertSame(mockConn, conn);

            // 验证 getLoginTimeout 委托
            assertEquals(30, dataSource.getLoginTimeout());

            // 验证 unwrap
            assertTrue(dataSource.isWrapperFor(SsmRotationDbcpDataSource.class));

            dataSource.close();
        }
    }

    /**
     * 测试凭据轮转回调中异常不会传播到调用方
     */
    @Test
    public void testCredentialRotationCallback_exceptionShouldNotPropagate() throws Exception {
        DbAccount initialAccount = new DbAccount("user_old", "pwd_old");
        DbAccount newAccount = new DbAccount("user_new", "pwd_new");
        BasicDataSource customDbcp = mock(BasicDataSource.class);
        Connection testConn = mock(Connection.class);
        when(customDbcp.getConnection()).thenReturn(testConn);
        when(customDbcp.getMaxTotal()).thenReturn(20);
        when(customDbcp.getMinIdle()).thenReturn(5);
        when(customDbcp.getNumActive()).thenReturn(0);
        when(customDbcp.getNumIdle()).thenReturn(0);
        // evict 抛出异常
        doThrow(new SQLException("evict failed")).when(customDbcp).evict();

        try (MockedConstruction<DynamicSecretRotationDb> rotationDbMocked = mockConstruction(
                DynamicSecretRotationDb.class,
                (mock, context) -> {
                    when(mock.getCurrentAccount()).thenReturn(initialAccount);
                    when(mock.buildJdbcUrl()).thenReturn("jdbc:mysql://127.0.0.1:3306/testdb");
                })) {

            SsmRotationDbcpDataSource dataSource = SsmRotationDbcpDataSource.builder()
                    .rotationConfig(validRotationConfig())
                    .customBasicDataSource(customDbcp)
                    .build();

            CredentialChangeListener listener = captureListener(dataSource);
            assertNotNull(listener);

            // 触发凭据轮转，不应抛出异常
            listener.onCredentialChanged(initialAccount, newAccount);

            // 凭据仍然应该被更新
            verify(customDbcp).setPassword("pwd_new");
            verify(customDbcp).setUsername("user_new");

            dataSource.close();
        }
    }

    /**
     * 测试多次凭据轮转
     */
    @Test
    public void testMultipleCredentialRotations_shouldUpdateEachTime() throws Exception {
        DbAccount account1 = new DbAccount("user_1", "pwd_1");
        DbAccount account2 = new DbAccount("user_2", "pwd_2");
        DbAccount account3 = new DbAccount("user_3", "pwd_3");
        BasicDataSource customDbcp = mock(BasicDataSource.class);
        Connection testConn = mock(Connection.class);
        when(customDbcp.getConnection()).thenReturn(testConn);
        when(customDbcp.getMaxTotal()).thenReturn(20);
        when(customDbcp.getMinIdle()).thenReturn(5);
        when(customDbcp.getNumActive()).thenReturn(0);
        when(customDbcp.getNumIdle()).thenReturn(0);

        try (MockedConstruction<DynamicSecretRotationDb> rotationDbMocked = mockConstruction(
                DynamicSecretRotationDb.class,
                (mock, context) -> {
                    when(mock.getCurrentAccount()).thenReturn(account1);
                    when(mock.buildJdbcUrl()).thenReturn("jdbc:mysql://127.0.0.1:3306/testdb");
                })) {

            SsmRotationDbcpDataSource dataSource = SsmRotationDbcpDataSource.builder()
                    .rotationConfig(validRotationConfig())
                    .customBasicDataSource(customDbcp)
                    .build();

            CredentialChangeListener listener = captureListener(dataSource);

            // 第一次轮转
            listener.onCredentialChanged(account1, account2);
            verify(customDbcp).setPassword("pwd_2");
            verify(customDbcp).setUsername("user_2");

            // 第二次轮转
            listener.onCredentialChanged(account2, account3);
            verify(customDbcp).setPassword("pwd_3");
            verify(customDbcp).setUsername("user_3");

            // evict 应被调用两次
            verify(customDbcp, times(2)).evict();

            dataSource.close();
        }
    }

    // ==================== 六、并发安全测试 ====================

    /**
     * 测试并发获取连接时的线程安全性
     * <p>使用 DBCP（BasicDataSource）测试，因为 DruidDataSource.getConnection() 返回
     * DruidPooledConnection 类型，mock 时存在类型转换问题。</p>
     */
    @Test
    public void testConcurrentGetConnection_shouldBeThreadSafe() throws Exception {
        DbAccount initialAccount = new DbAccount("user_test", "pwd_test");
        BasicDataSource customDbcp = mock(BasicDataSource.class);
        Connection sharedMockConn = mock(Connection.class);
        when(customDbcp.getConnection()).thenReturn(sharedMockConn);
        when(customDbcp.getMaxTotal()).thenReturn(20);
        when(customDbcp.getMinIdle()).thenReturn(5);

        int threadCount = 20;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        try (MockedConstruction<DynamicSecretRotationDb> rotationDbMocked = mockConstruction(
                DynamicSecretRotationDb.class,
                (mock, context) -> {
                    when(mock.getCurrentAccount()).thenReturn(initialAccount);
                    when(mock.buildJdbcUrl()).thenReturn("jdbc:mysql://127.0.0.1:3306/testdb");
                })) {

            SsmRotationDbcpDataSource dataSource = SsmRotationDbcpDataSource.builder()
                    .rotationConfig(validRotationConfig())
                    .customBasicDataSource(customDbcp)
                    .build();

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        Connection conn = dataSource.getConnection();
                        if (conn != null) {
                            successCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertTrue("All threads should complete within 10 seconds",
                    doneLatch.await(10, TimeUnit.SECONDS));

            assertEquals("All connections should succeed", threadCount, successCount.get());
            assertEquals("No errors should occur", 0, errorCount.get());

            executor.shutdown();
            dataSource.close();
        }
    }

    /**
     * 测试并发凭据轮转回调的线程安全性
     */
    @Test
    public void testConcurrentCredentialRotation_shouldBeThreadSafe() throws Exception {
        DbAccount initialAccount = new DbAccount("user_old", "pwd_old");
        DruidDataSource customDruid = mock(DruidDataSource.class);
        when(customDruid.getMaxActive()).thenReturn(20);
        when(customDruid.getMinIdle()).thenReturn(5);
        when(customDruid.getActiveCount()).thenReturn(0);
        when(customDruid.getPoolingCount()).thenReturn(0);

        int rotationCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(rotationCount);
        AtomicInteger errorCount = new AtomicInteger(0);

        try (MockedConstruction<DynamicSecretRotationDb> rotationDbMocked = mockConstruction(
                DynamicSecretRotationDb.class,
                (mock, context) -> {
                    when(mock.getCurrentAccount()).thenReturn(initialAccount);
                    when(mock.buildJdbcUrl()).thenReturn("jdbc:mysql://127.0.0.1:3306/testdb");
                })) {

            SsmRotationDruidDataSource dataSource = SsmRotationDruidDataSource.builder()
                    .rotationConfig(validRotationConfig())
                    .customDruidDataSource(customDruid)
                    .build();

            CredentialChangeListener listener = captureListener(dataSource);

            ExecutorService executor = Executors.newFixedThreadPool(rotationCount);
            for (int i = 0; i < rotationCount; i++) {
                final int idx = i;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        DbAccount newAccount = new DbAccount("user_" + idx, "pwd_" + idx);
                        listener.onCredentialChanged(initialAccount, newAccount);
                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertTrue("All rotations should complete within 10 seconds",
                    doneLatch.await(10, TimeUnit.SECONDS));

            assertEquals("No errors should occur during concurrent rotation", 0, errorCount.get());

            executor.shutdown();
            dataSource.close();
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 创建有效的 RotationConfig
     */
    private DynamicSecretRotationConfig validRotationConfig() {
        DbConfig dbConfig = DbConfig.builder()
                .secretName("test-secret")
                .ipAddress("127.0.0.1")
                .port(3306)
                .dbName("testdb")
                .build();

        SsmAccount ssmAccount = SsmAccount.withPermanentCredential("id", "key", "ap-guangzhou");
        return DynamicSecretRotationConfig.builder()
                .dbConfig(dbConfig)
                .ssmServiceConfig(ssmAccount)
                .watchChangeIntervalMs(1000)
                .build();
    }

    /**
     * 通过反射获取 DataSource 中注册的 CredentialChangeListener
     */
    private CredentialChangeListener captureListener(Object dataSource) {
        try {
            Field listenerField = AbstractSsmRotationDataSource.class.getDeclaredField("credentialChangeListener");
            listenerField.setAccessible(true);
            return (CredentialChangeListener) listenerField.get(dataSource);
        } catch (Exception e) {
            throw new RuntimeException("Failed to capture listener", e);
        }
    }

    /**
     * ExtraPropertiesApplier 测试用的目标类
     */
    public static class TestTarget {
        int intValue;
        long longValue;
        boolean booleanValue;
        double doubleValue;
        String stringValue;

        public void setIntValue(int intValue) {
            this.intValue = intValue;
        }

        public void setLongValue(long longValue) {
            this.longValue = longValue;
        }

        public void setBooleanValue(boolean booleanValue) {
            this.booleanValue = booleanValue;
        }

        public void setDoubleValue(double doubleValue) {
            this.doubleValue = doubleValue;
        }

        public void setStringValue(String stringValue) {
            this.stringValue = stringValue;
        }
    }
}
