package com.tencentcloudapi.ssm.rotation;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * SsmRotationException 单元测试
 */
public class SsmRotationExceptionTest {

    @Test
    public void testConstructor_messageOnly() {
        SsmRotationException ex = new SsmRotationException("test error");
        assertEquals("test error", ex.getMessage());
        assertNull(ex.getErrorCode());
        assertNull(ex.getCause());
    }

    @Test
    public void testConstructor_messageAndCause() {
        RuntimeException cause = new RuntimeException("root cause");
        SsmRotationException ex = new SsmRotationException("test error", cause);
        assertEquals("test error", ex.getMessage());
        assertNull(ex.getErrorCode());
        assertEquals(cause, ex.getCause());
    }

    @Test
    public void testConstructor_errorCodeAndMessage() {
        SsmRotationException ex = new SsmRotationException(SsmRotationException.ERROR_CONFIG, "config error");
        assertEquals("config error", ex.getMessage());
        assertEquals(SsmRotationException.ERROR_CONFIG, ex.getErrorCode());
    }

    @Test
    public void testConstructor_errorCodeMessageAndCause() {
        RuntimeException cause = new RuntimeException("root cause");
        SsmRotationException ex = new SsmRotationException(SsmRotationException.ERROR_SSM, "ssm error", cause);
        assertEquals("ssm error", ex.getMessage());
        assertEquals(SsmRotationException.ERROR_SSM, ex.getErrorCode());
        assertEquals(cause, ex.getCause());
    }

    @Test
    public void testToString_withErrorCode() {
        SsmRotationException ex = new SsmRotationException(SsmRotationException.ERROR_CONFIG, "config error");
        String str = ex.toString();
        assertTrue(str.contains("CONFIG_ERROR"));
        assertTrue(str.contains("config error"));
    }

    @Test
    public void testToString_withoutErrorCode() {
        SsmRotationException ex = new SsmRotationException("simple error");
        String str = ex.toString();
        assertTrue(str.contains("simple error"));
    }

    @Test
    public void testErrorCodeConstants() {
        assertEquals("CONFIG_ERROR", SsmRotationException.ERROR_CONFIG);
        assertEquals("SSM_ERROR", SsmRotationException.ERROR_SSM);
        assertEquals("DB_DRIVER_ERROR", SsmRotationException.ERROR_DB_DRIVER);
        assertEquals("CAM_ROLE_ERROR", SsmRotationException.ERROR_CAM_ROLE);
        assertEquals("METADATA_TIMEOUT", SsmRotationException.ERROR_METADATA_TIMEOUT);
        assertEquals("METADATA_UNREACHABLE", SsmRotationException.ERROR_METADATA_UNREACHABLE);
        assertEquals("METADATA_ERROR", SsmRotationException.ERROR_METADATA);
    }
}
