package com.tencentcloudapi.ssm.rotation.quickstart;

import com.alibaba.druid.pool.DruidDataSource;
import com.tencentcloudapi.ssm.rotation.db.CredentialChangeListener;
import com.tencentcloudapi.ssm.rotation.db.DynamicSecretRotationDb;
import com.tencentcloudapi.ssm.rotation.ssm.DbAccount;
import org.junit.Test;
import org.mockito.MockedConstruction;

import javax.sql.DataSource;

import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mockConstruction;

/**
 * 统一数据源工厂单元测试
 */
public class SsmRotationDataSourceFactoryTest {

    @Test
    public void testCreateDataSource_shouldBuildDruidDataSource() throws Exception {
        DbAccount initialAccount = new DbAccount("user_a", "pwd_a");

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

            SsmRotationDataSourceOptions options = SsmRotationDataSourceOptions.builder()
                    .poolType(SsmRotationPoolType.DRUID)
                    .region("ap-guangzhou")
                    .secretId("id")
                    .secretKey("key")
                    .secretName("test-secret")
                    .ipAddress("127.0.0.1")
                    .port(3306)
                    .dbName("testdb")
                    .build();

            DataSource dataSource = SsmRotationDataSourceFactory.createDataSource(options);
            assertNotNull(dataSource);

            DruidDataSource druidDataSource = druidMocked.constructed().get(0);
            verify(druidDataSource).setUrl("jdbc:mysql://127.0.0.1:3306/testdb");
            verify(druidDataSource).setUsername("user_a");
            verify(druidDataSource).setPassword("pwd_a");
            verify(druidDataSource).init();

            DynamicSecretRotationDb rotationDb = rotationDbMocked.constructed().get(0);
            verify(rotationDb).addCredentialChangeListener(any(CredentialChangeListener.class));

            ((AutoCloseable) dataSource).close();
            verify(rotationDb).removeCredentialChangeListener(any(CredentialChangeListener.class));
            verify(rotationDb).close();
            verify(druidDataSource).close();
        }
    }
}
