package com.tencentcloudapi.ssm.rotation.ssm;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * CredentialType 单元测试
 */
public class CredentialTypeTest {

    @Test
    public void testEnumValues() {
        CredentialType[] values = CredentialType.values();
        assertEquals(3, values.length);
    }

    @Test
    public void testEnumValueOf() {
        assertEquals(CredentialType.PERMANENT, CredentialType.valueOf("PERMANENT"));
        assertEquals(CredentialType.TEMPORARY, CredentialType.valueOf("TEMPORARY"));
        assertEquals(CredentialType.CAM_ROLE, CredentialType.valueOf("CAM_ROLE"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEnumValueOf_invalid() {
        CredentialType.valueOf("INVALID");
    }

    @Test
    public void testEnumOrdinal() {
        // 确保枚举顺序稳定（序列化兼容性）
        assertEquals(0, CredentialType.CAM_ROLE.ordinal());
        assertEquals(1, CredentialType.TEMPORARY.ordinal());
        assertEquals(2, CredentialType.PERMANENT.ordinal());
    }
}
