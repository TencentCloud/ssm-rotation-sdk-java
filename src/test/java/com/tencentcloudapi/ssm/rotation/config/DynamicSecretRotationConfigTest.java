package com.tencentcloudapi.ssm.rotation.config;

import com.tencentcloudapi.ssm.rotation.SsmRotationException;
import com.tencentcloudapi.ssm.rotation.ssm.SsmAccount;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * DynamicSecretRotationConfig 单元测试
 */
public class DynamicSecretRotationConfigTest {

    private DbConfig validDbConfig() {
        return DbConfig.builder()
                .secretName("test-secret")
                .ipAddress("127.0.0.1")
                .port(3306)
                .dbName("testdb")
                .build();
    }

    private SsmAccount validSsmAccount() {
        return SsmAccount.withPermanentCredential("test-id", "test-key", "ap-guangzhou");
    }

    @Test
    public void testValidConfig() throws SsmRotationException {
        DynamicSecretRotationConfig config = DynamicSecretRotationConfig.builder()
                .dbConfig(validDbConfig())
                .ssmServiceConfig(validSsmAccount())
                .watchChangeIntervalMs(10000)
                .build();

        config.validate();
        assertEquals(10000, config.getWatchChangeIntervalMs());
    }

    @Test
    public void testDefaultWatchInterval() {
        DynamicSecretRotationConfig config = DynamicSecretRotationConfig.builder()
                .dbConfig(validDbConfig())
                .ssmServiceConfig(validSsmAccount())
                .build();

        assertEquals(10000, config.getWatchChangeIntervalMs());
    }

    @Test(expected = SsmRotationException.class)
    public void testNullDbConfig() throws SsmRotationException {
        DynamicSecretRotationConfig config = DynamicSecretRotationConfig.builder()
                .ssmServiceConfig(validSsmAccount())
                .build();
        config.validate();
    }

    @Test(expected = SsmRotationException.class)
    public void testNullSsmServiceConfig() throws SsmRotationException {
        DynamicSecretRotationConfig config = DynamicSecretRotationConfig.builder()
                .dbConfig(validDbConfig())
                .build();
        config.validate();
    }

    @Test(expected = SsmRotationException.class)
    public void testWatchInterval_tooSmall() throws SsmRotationException {
        DynamicSecretRotationConfig config = DynamicSecretRotationConfig.builder()
                .dbConfig(validDbConfig())
                .ssmServiceConfig(validSsmAccount())
                .watchChangeIntervalMs(500)
                .build();
        config.validate();
    }

    @Test
    public void testWatchInterval_tooLarge_allowedWithWarning() throws SsmRotationException {
        DynamicSecretRotationConfig config = DynamicSecretRotationConfig.builder()
                .dbConfig(validDbConfig())
                .ssmServiceConfig(validSsmAccount())
                .watchChangeIntervalMs(120000)
                .build();
        config.validate();
        assertEquals(120000, config.getWatchChangeIntervalMs());
    }

    @Test(expected = SsmRotationException.class)
    public void testWatchInterval_zero() throws SsmRotationException {
        DynamicSecretRotationConfig config = DynamicSecretRotationConfig.builder()
                .dbConfig(validDbConfig())
                .ssmServiceConfig(validSsmAccount())
                .watchChangeIntervalMs(0)
                .build();
        config.validate();
    }

    @Test(expected = SsmRotationException.class)
    public void testWatchInterval_negative() throws SsmRotationException {
        DynamicSecretRotationConfig config = DynamicSecretRotationConfig.builder()
                .dbConfig(validDbConfig())
                .ssmServiceConfig(validSsmAccount())
                .watchChangeIntervalMs(-1000)
                .build();
        config.validate();
    }

    @Test
    public void testWatchInterval_boundary_1000() throws SsmRotationException {
        DynamicSecretRotationConfig config = DynamicSecretRotationConfig.builder()
                .dbConfig(validDbConfig())
                .ssmServiceConfig(validSsmAccount())
                .watchChangeIntervalMs(1000)
                .build();
        config.validate();
        assertEquals(1000, config.getWatchChangeIntervalMs());
    }

    @Test
    public void testWatchInterval_boundary_60000() throws SsmRotationException {
        DynamicSecretRotationConfig config = DynamicSecretRotationConfig.builder()
                .dbConfig(validDbConfig())
                .ssmServiceConfig(validSsmAccount())
                .watchChangeIntervalMs(60000)
                .build();
        config.validate();
        assertEquals(60000, config.getWatchChangeIntervalMs());
    }
}
