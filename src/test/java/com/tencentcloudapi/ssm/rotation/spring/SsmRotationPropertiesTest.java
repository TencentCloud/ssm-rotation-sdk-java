package com.tencentcloudapi.ssm.rotation.spring;

import com.tencentcloudapi.ssm.rotation.quickstart.SsmRotationPoolType;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Spring 配置模型单元测试
 */
public class SsmRotationPropertiesTest {

    @Test
    public void testResolveSingleDataSource_shouldApplyShortcutFields() {
        SsmRotationProperties properties = new SsmRotationProperties();
        properties.setSecretName("shortcut-secret");
        properties.setPoolType(SsmRotationPoolType.HIKARI);
        properties.getDb().setIp("10.0.0.1");
        properties.getDb().setPort(3307);
        properties.getDb().setName("shortcut_db");

        SsmRotationProperties.DataSourceProperties resolved = properties.resolveSingleDataSource();

        assertEquals("shortcut-secret", resolved.getSecretName());
        assertEquals(SsmRotationPoolType.HIKARI, resolved.getPoolType());
        assertEquals("10.0.0.1", resolved.getDb().getIp());
        assertEquals(Integer.valueOf(3307), resolved.getDb().getPort());
        assertEquals("shortcut_db", resolved.getDb().getName());
        assertEquals("dataSource", resolved.getBeanName());
    }

    @Test
    public void testResolveSingleDataSource_shouldPreferDatasourceBlock() {
        SsmRotationProperties properties = new SsmRotationProperties();
        properties.setSecretName("shortcut-secret");
        properties.getDb().setIp("10.0.0.1");
        properties.getDb().setName("shortcut_db");

        SsmRotationProperties.DataSourceProperties explicit = new SsmRotationProperties.DataSourceProperties();
        explicit.setSecretName("explicit-secret");
        explicit.setPoolType(SsmRotationPoolType.DBCP);
        explicit.getDb().setIp("127.0.0.1");
        explicit.getDb().setPort(3308);
        explicit.getDb().setName("explicit_db");
        properties.setDatasource(explicit);

        SsmRotationProperties.DataSourceProperties resolved = properties.resolveSingleDataSource();

        assertEquals("explicit-secret", resolved.getSecretName());
        assertEquals(SsmRotationPoolType.DBCP, resolved.getPoolType());
        assertEquals("127.0.0.1", resolved.getDb().getIp());
        assertEquals(Integer.valueOf(3308), resolved.getDb().getPort());
        assertEquals("explicit_db", resolved.getDb().getName());
    }
}
