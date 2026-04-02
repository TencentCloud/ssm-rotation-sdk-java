package com.tencentcloudapi.ssm.rotation.ssm;

import com.tencentcloudapi.ssm.rotation.SsmRotationException;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.ssm.v20190923.SsmClient;
import com.tencentcloudapi.ssm.v20190923.models.GetSecretValueRequest;
import com.tencentcloudapi.ssm.v20190923.models.GetSecretValueResponse;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SsmRequester 单元测试
 */
public class SsmRequesterTest {

    @Test(expected = IllegalArgumentException.class)
    public void testConstructor_nullSsmAccount() {
        new SsmRequester(null);
    }

    @Test
    public void testGetCurrentAccount_withCachedClient() throws Exception {
        SsmAccount account = SsmAccount.withPermanentCredential("id", "key", "ap-guangzhou");
        SsmRequester requester = new SsmRequester(account);

        SsmClient cachedClient = mock(SsmClient.class);
        GetSecretValueResponse response = new GetSecretValueResponse();
        response.setSecretString("{\"UserName\":\"db_user\",\"Password\":\"db_pwd\"}");
        when(cachedClient.GetSecretValue(any(GetSecretValueRequest.class))).thenReturn(response);

        setCachedClient(requester, account, cachedClient);

        DbAccount dbAccount = requester.getCurrentAccount("secret-demo");
        assertEquals("db_user", dbAccount.getUserName());
        assertEquals("db_pwd", dbAccount.getPassword());

        verify(cachedClient).GetSecretValue(any(GetSecretValueRequest.class));
    }

    @Test
    public void testGetCurrentAccount_invalidSecretFormat() throws Exception {
        SsmAccount account = SsmAccount.withPermanentCredential("id", "key", "ap-guangzhou");
        SsmRequester requester = new SsmRequester(account);

        SsmClient cachedClient = mock(SsmClient.class);
        GetSecretValueResponse response = new GetSecretValueResponse();
        response.setSecretString("{\"UserName\":\"db_user\"}");
        when(cachedClient.GetSecretValue(any(GetSecretValueRequest.class))).thenReturn(response);

        setCachedClient(requester, account, cachedClient);

        try {
            requester.getCurrentAccount("secret-demo");
            fail("Expected SsmRotationException");
        } catch (SsmRotationException e) {
            assertEquals(SsmRotationException.ERROR_SSM, e.getErrorCode());
            assertNotNull(e.getMessage());
        }
    }

    @Test
    public void testGetCurrentAccount_whenSdkThrows() throws Exception {
        SsmAccount account = SsmAccount.withPermanentCredential("id", "key", "ap-guangzhou");
        SsmRequester requester = new SsmRequester(account);

        SsmClient cachedClient = mock(SsmClient.class);
        when(cachedClient.GetSecretValue(any(GetSecretValueRequest.class)))
                .thenThrow(new TencentCloudSDKException("mock sdk error"));

        setCachedClient(requester, account, cachedClient);

        try {
            requester.getCurrentAccount("secret-demo");
            fail("Expected SsmRotationException");
        } catch (SsmRotationException e) {
            assertEquals(SsmRotationException.ERROR_SSM, e.getErrorCode());
            assertNotNull(e.getCause());
        }
    }

    @Test
    public void testGetOrCreateSsmClient_returnsCachedWhenFingerprintMatches() throws Exception {
        SsmAccount account = SsmAccount.withPermanentCredential("id", "key", "ap-guangzhou");
        SsmRequester requester = new SsmRequester(account);
        SsmClient cachedClient = mock(SsmClient.class);
        setCachedClient(requester, account, cachedClient);

        Method method = SsmRequester.class.getDeclaredMethod("getOrCreateSsmClient");
        method.setAccessible(true);
        Object result = method.invoke(requester);

        assertSame(cachedClient, result);
    }

    @Test
    public void testBuildClientFingerprint_camRoleContainsRoleName() throws Exception {
        SsmAccount camRoleAccount = SsmAccount.withCamRole("role-demo", "ap-guangzhou")
                .withEndpoint("ssm.tencentcloudapi.com");
        String fingerprint = invokeBuildClientFingerprint(camRoleAccount);
        assertNotNull(fingerprint);
        assertTrue(fingerprint.contains("CAM_ROLE"));
        assertTrue(fingerprint.contains("role-demo"));
    }

    @Test
    public void testBuildClientFingerprint_permanentDifferentSecretKeySameLength_shouldDiffer() throws Exception {
        SsmAccount accountA = SsmAccount.withPermanentCredential("id", "abcd1234", "ap-guangzhou");
        SsmAccount accountB = SsmAccount.withPermanentCredential("id", "wxyz5678", "ap-guangzhou");

        String fingerprintA = invokeBuildClientFingerprint(accountA);
        String fingerprintB = invokeBuildClientFingerprint(accountB);

        assertNotEquals(fingerprintA, fingerprintB);
    }

    @Test
    public void testBuildClientFingerprint_temporaryDifferentToken_shouldDiffer() throws Exception {
        SsmAccount accountA = SsmAccount.withTemporaryCredential("id", "secret-key", "token-a", "ap-guangzhou");
        SsmAccount accountB = SsmAccount.withTemporaryCredential("id", "secret-key", "token-b", "ap-guangzhou");

        String fingerprintA = invokeBuildClientFingerprint(accountA);
        String fingerprintB = invokeBuildClientFingerprint(accountB);

        assertNotEquals(fingerprintA, fingerprintB);
    }

    private static void setCachedClient(SsmRequester requester, SsmAccount account, SsmClient cachedClient)
            throws NoSuchFieldException, IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        Field cachedClientField = SsmRequester.class.getDeclaredField("cachedClient");
        cachedClientField.setAccessible(true);
        cachedClientField.set(requester, cachedClient);

        Field fingerprintField = SsmRequester.class.getDeclaredField("cachedClientFingerprint");
        fingerprintField.setAccessible(true);
        fingerprintField.set(requester, invokeBuildClientFingerprint(account));
    }

    private static String invokeBuildClientFingerprint(SsmAccount account)
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method fingerprintMethod = SsmRequester.class.getDeclaredMethod("buildClientFingerprint", SsmAccount.class);
        fingerprintMethod.setAccessible(true);
        return (String) fingerprintMethod.invoke(null, account);
    }
}
