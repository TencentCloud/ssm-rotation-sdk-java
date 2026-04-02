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
import org.apache.commons.dbcp2.BasicDataSource;
import org.junit.Test;
import org.mockito.MockedConstruction;

import java.sql.Connection;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 连接池封装层 Builder 单元测试
 */
public class SsmRotationDataSourceBuilderTest {

    @Test
    public void testHikariBuilder_shouldBindInitialCredentialAndRegisterListener() throws Exception {
        DbAccount initialAccount = new DbAccount("user_a", "pwd_a");
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
                     })) {

            SsmRotationHikariDataSource dataSource = SsmRotationHikariDataSource.builder()
                    .rotationConfig(validRotationConfig())
                    .build();

            HikariConfig hikariConfig = capturedConfig.get();
            assertNotNull(hikariConfig);
            assertEquals("jdbc:mysql://127.0.0.1:3306/testdb", hikariConfig.getJdbcUrl());
            assertEquals("user_a", hikariConfig.getUsername());
            assertEquals("pwd_a", hikariConfig.getPassword());

            DynamicSecretRotationDb rotationDb = rotationDbMocked.constructed().get(0);
            verify(rotationDb).addCredentialChangeListener(any(CredentialChangeListener.class));

            dataSource.close();
            verify(rotationDb).removeCredentialChangeListener(any(CredentialChangeListener.class));
            verify(rotationDb).close();
            verify(hikariMocked.constructed().get(0)).close();
        }
    }

    @Test
    public void testDruidBuilder_withCustomDataSource_shouldInitializeAndRegisterListener() throws Exception {
        DbAccount initialAccount = new DbAccount("user_b", "pwd_b");
        DruidDataSource customDruid = mock(DruidDataSource.class);
        when(customDruid.getMaxActive()).thenReturn(20);
        when(customDruid.getMinIdle()).thenReturn(5);

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

            verify(customDruid).setUrl("jdbc:mysql://127.0.0.1:3306/testdb");
            verify(customDruid).setUsername("user_b");
            verify(customDruid).setPassword("pwd_b");
            verify(customDruid).init();

            DynamicSecretRotationDb rotationDb = rotationDbMocked.constructed().get(0);
            verify(rotationDb).addCredentialChangeListener(any(CredentialChangeListener.class));

            dataSource.close();
            verify(rotationDb).removeCredentialChangeListener(any(CredentialChangeListener.class));
            verify(rotationDb).close();
            verify(customDruid).close();
        }
    }

    @Test
    public void testDbcpBuilder_withCustomDataSource_shouldVerifyConnectionAndRegisterListener() throws Exception {
        DbAccount initialAccount = new DbAccount("user_c", "pwd_c");
        BasicDataSource customDbcp = mock(BasicDataSource.class);
        Connection testConnection = mock(Connection.class);
        when(customDbcp.getConnection()).thenReturn(testConnection);
        when(customDbcp.getMaxTotal()).thenReturn(20);
        when(customDbcp.getMinIdle()).thenReturn(5);

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

            verify(customDbcp).setUrl("jdbc:mysql://127.0.0.1:3306/testdb");
            verify(customDbcp).setUsername("user_c");
            verify(customDbcp).setPassword("pwd_c");
            verify(customDbcp).getConnection();
            verify(testConnection).close();

            DynamicSecretRotationDb rotationDb = rotationDbMocked.constructed().get(0);
            verify(rotationDb).addCredentialChangeListener(any(CredentialChangeListener.class));

            dataSource.close();
            verify(rotationDb).removeCredentialChangeListener(any(CredentialChangeListener.class));
            verify(rotationDb).close();
            verify(customDbcp).close();
        }
    }

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
}
