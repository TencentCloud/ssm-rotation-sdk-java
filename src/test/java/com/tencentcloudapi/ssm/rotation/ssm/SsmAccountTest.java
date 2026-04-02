package com.tencentcloudapi.ssm.rotation.ssm;

import com.tencentcloudapi.ssm.rotation.SsmRotationException;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * SsmAccount 单元测试
 */
public class SsmAccountTest {

    // ==================== 工厂方法测试 ====================

    @Test
    public void testWithPermanentCredential() {
        SsmAccount account = SsmAccount.withPermanentCredential("id", "key", "ap-guangzhou");
        assertEquals(CredentialType.PERMANENT, account.getCredentialType());
        assertEquals("id", account.getSecretId());
        assertEquals("key", account.getSecretKey());
        assertEquals("ap-guangzhou", account.getRegion());
        assertNull(account.getToken());
        assertNull(account.getRoleName());
    }

    @Test
    public void testWithTemporaryCredential() {
        SsmAccount account = SsmAccount.withTemporaryCredential("id", "key", "token", "ap-beijing");
        assertEquals(CredentialType.TEMPORARY, account.getCredentialType());
        assertEquals("id", account.getSecretId());
        assertEquals("key", account.getSecretKey());
        assertEquals("token", account.getToken());
        assertEquals("ap-beijing", account.getRegion());
    }

    @Test
    public void testWithCamRole() {
        SsmAccount account = SsmAccount.withCamRole("my-role", "ap-shanghai");
        assertEquals(CredentialType.CAM_ROLE, account.getCredentialType());
        assertEquals("my-role", account.getRoleName());
        assertEquals("ap-shanghai", account.getRegion());
        assertNull(account.getSecretId());
    }

    @Test
    public void testWithEndpoint() {
        SsmAccount account = SsmAccount.withPermanentCredential("id", "key", "ap-guangzhou")
                .withEndpoint("ssm.custom.endpoint.com");
        assertEquals("ssm.custom.endpoint.com", account.getEndpoint());
    }

    // ==================== 验证测试 ====================

    @Test
    public void testValidate_permanent_valid() throws SsmRotationException {
        SsmAccount account = SsmAccount.withPermanentCredential("id", "key", "ap-guangzhou");
        account.validate();
    }

    @Test(expected = SsmRotationException.class)
    public void testValidate_permanent_nullSecretId() throws SsmRotationException {
        SsmAccount account = SsmAccount.withPermanentCredential(null, "key", "ap-guangzhou");
        account.validate();
    }

    @Test(expected = SsmRotationException.class)
    public void testValidate_permanent_emptySecretKey() throws SsmRotationException {
        SsmAccount account = SsmAccount.withPermanentCredential("id", "", "ap-guangzhou");
        account.validate();
    }

    @Test(expected = SsmRotationException.class)
    public void testValidate_nullRegion() throws SsmRotationException {
        SsmAccount account = SsmAccount.withPermanentCredential("id", "key", null);
        account.validate();
    }

    @Test(expected = SsmRotationException.class)
    public void testValidate_temporary_nullToken() throws SsmRotationException {
        SsmAccount account = SsmAccount.withTemporaryCredential("id", "key", null, "ap-guangzhou");
        account.validate();
    }

    @Test(expected = SsmRotationException.class)
    public void testValidate_camRole_nullRoleName() throws SsmRotationException {
        SsmAccount account = SsmAccount.withCamRole(null, "ap-guangzhou");
        account.validate();
    }

    @Test(expected = SsmRotationException.class)
    public void testValidate_camRole_emptyRoleName() throws SsmRotationException {
        SsmAccount account = SsmAccount.withCamRole("  ", "ap-guangzhou");
        account.validate();
    }

    // ==================== toString 脱敏测试 ====================

    @Test
    public void testToString_permanent_masksSecretKey() {
        SsmAccount account = SsmAccount.withPermanentCredential("AKID1234567890", "secretkey123", "ap-guangzhou");
        String str = account.toString();
        assertTrue(str.contains("AKID****"));
        assertTrue(str.contains("secretKey='****'"));
        assertFalse(str.contains("secretkey123"));
    }

    @Test
    public void testToString_temporary_masksToken() {
        SsmAccount account = SsmAccount.withTemporaryCredential("AKID1234567890", "key", "mytoken", "ap-guangzhou");
        String str = account.toString();
        assertTrue(str.contains("token='****'"));
        assertFalse(str.contains("mytoken"));
    }

    @Test
    public void testToString_camRole_showsRoleName() {
        SsmAccount account = SsmAccount.withCamRole("my-role", "ap-guangzhou");
        String str = account.toString();
        assertTrue(str.contains("my-role"));
        assertTrue(str.contains("CAM_ROLE"));
    }

    // ==================== 兼容性测试 ====================

    @Test
    @SuppressWarnings("deprecation")
    public void testDeprecatedConstructor() {
        SsmAccount account = new SsmAccount("id", "key", "ap-guangzhou");
        assertEquals(CredentialType.PERMANENT, account.getCredentialType());
        assertEquals("id", account.getSecretId());
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testDeprecatedConstructorWithEndpoint() {
        SsmAccount account = new SsmAccount("id", "key", "ap-guangzhou", "custom.endpoint");
        assertEquals("custom.endpoint", account.getEndpoint());
    }
}
