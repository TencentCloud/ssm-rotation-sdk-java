package com.tencentcloudapi.ssm.rotation.config;

import com.tencentcloudapi.ssm.rotation.SsmRotationException;
import com.tencentcloudapi.ssm.rotation.ssm.SsmAccount;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * DbConfig 单元测试
 */
public class DbConfigTest {

    @Test
    public void testValidConfig() throws SsmRotationException {
        DbConfig config = DbConfig.builder()
                .secretName("test-secret")
                .ipAddress("127.0.0.1")
                .port(3306)
                .dbName("testdb")
                .paramStr("useSSL=false")
                .build();

        config.validate();
        assertEquals("test-secret", config.getSecretName());
        assertEquals("127.0.0.1", config.getIpAddress());
        assertEquals(3306, config.getPort());
        assertEquals("testdb", config.getDbName());
    }

    @Test(expected = SsmRotationException.class)
    public void testNullSecretName() throws SsmRotationException {
        DbConfig config = DbConfig.builder()
                .ipAddress("127.0.0.1")
                .port(3306)
                .build();
        config.validate();
    }

    @Test(expected = SsmRotationException.class)
    public void testEmptySecretName() throws SsmRotationException {
        DbConfig config = DbConfig.builder()
                .secretName("")
                .ipAddress("127.0.0.1")
                .port(3306)
                .build();
        config.validate();
    }

    @Test(expected = SsmRotationException.class)
    public void testNullIpAddress() throws SsmRotationException {
        DbConfig config = DbConfig.builder()
                .secretName("test-secret")
                .port(3306)
                .build();
        config.validate();
    }

    @Test(expected = SsmRotationException.class)
    public void testInvalidPort_zero() throws SsmRotationException {
        DbConfig config = DbConfig.builder()
                .secretName("test-secret")
                .ipAddress("127.0.0.1")
                .port(0)
                .build();
        config.validate();
    }

    @Test(expected = SsmRotationException.class)
    public void testInvalidPort_tooLarge() throws SsmRotationException {
        DbConfig config = DbConfig.builder()
                .secretName("test-secret")
                .ipAddress("127.0.0.1")
                .port(70000)
                .build();
        config.validate();
    }

    @Test(expected = SsmRotationException.class)
    public void testInvalidDbName_specialChars() throws SsmRotationException {
        DbConfig config = DbConfig.builder()
                .secretName("test-secret")
                .ipAddress("127.0.0.1")
                .port(3306)
                .dbName("test;DROP TABLE users")
                .build();
        config.validate();
    }

    @Test
    public void testValidDbName_withUnderscoreAndHyphen() throws SsmRotationException {
        DbConfig config = DbConfig.builder()
                .secretName("test-secret")
                .ipAddress("127.0.0.1")
                .port(3306)
                .dbName("my_test-db123")
                .build();
        config.validate();
    }

    @Test(expected = SsmRotationException.class)
    public void testDangerousJdbcParam_autoDeserialize() throws SsmRotationException {
        DbConfig config = DbConfig.builder()
                .secretName("test-secret")
                .ipAddress("127.0.0.1")
                .port(3306)
                .paramStr("autoDeserialize=true")
                .build();
        config.validate();
    }

    @Test(expected = SsmRotationException.class)
    public void testDangerousJdbcParam_allowLoadLocalInfile() throws SsmRotationException {
        DbConfig config = DbConfig.builder()
                .secretName("test-secret")
                .ipAddress("127.0.0.1")
                .port(3306)
                .paramStr("allowLoadLocalInfile=true")
                .build();
        config.validate();
    }

    @Test
    public void testSafeJdbcParams() throws SsmRotationException {
        DbConfig config = DbConfig.builder()
                .secretName("test-secret")
                .ipAddress("127.0.0.1")
                .port(3306)
                .paramStr("useSSL=false&characterEncoding=utf8&serverTimezone=UTC")
                .build();
        config.validate();
    }

    @Test(expected = SsmRotationException.class)
    public void testConnectTimeout_tooLarge() throws SsmRotationException {
        DbConfig config = DbConfig.builder()
                .secretName("test-secret")
                .ipAddress("127.0.0.1")
                .port(3306)
                .connectTimeoutSeconds(5000)
                .build();
        config.validate();
    }

    @Test(expected = SsmRotationException.class)
    public void testSocketTimeout_zero() throws SsmRotationException {
        DbConfig config = DbConfig.builder()
                .secretName("test-secret")
                .ipAddress("127.0.0.1")
                .port(3306)
                .socketTimeoutSeconds(0)
                .build();
        config.validate();
    }

    @Test
    public void testDefaultTimeouts() {
        DbConfig config = DbConfig.builder()
                .secretName("test-secret")
                .ipAddress("127.0.0.1")
                .port(3306)
                .build();
        assertEquals(5, config.getConnectTimeoutSeconds());
        assertEquals(5, config.getSocketTimeoutSeconds());
    }

    @Test
    public void testNullDbNameIsValid() throws SsmRotationException {
        DbConfig config = DbConfig.builder()
                .secretName("test-secret")
                .ipAddress("127.0.0.1")
                .port(3306)
                .build();
        config.validate();
        assertNull(config.getDbName());
    }

    @Test
    public void testNullParamStrIsValid() throws SsmRotationException {
        DbConfig config = DbConfig.builder()
                .secretName("test-secret")
                .ipAddress("127.0.0.1")
                .port(3306)
                .build();
        config.validate();
        assertNull(config.getParamStr());
    }
}
