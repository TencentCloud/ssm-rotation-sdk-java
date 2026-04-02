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

package com.tencentcloudapi.ssm.rotation.ssm;

import com.google.gson.Gson;
import com.tencentcloudapi.ssm.rotation.SsmRotationException;
import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;
import com.tencentcloudapi.common.provider.CvmRoleCredential;
import com.tencentcloudapi.ssm.v20190923.SsmClient;
import com.tencentcloudapi.ssm.v20190923.models.GetSecretValueRequest;
import com.tencentcloudapi.ssm.v20190923.models.GetSecretValueResponse;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * SSM 凭据请求器
 * 
 * 支持三种凭据获取方式（适用于 CAM/TKE/SCF 等资源环境，SDK 不做环境判断）：
 * 1. 固定 AK/SK - 使用 Credential
 * 2. 临时凭据 - 使用带 Token 的 Credential
 * 3. CAM 角色 - 使用 CvmRoleCredential 通过元数据服务获取临时凭据，适用于 CVM 角色绑定
 * 
 * @author tencentcloud
 * @since 1.0.0
 */
@Slf4j
public class SsmRequester {

    private static final String SSM_CURRENT_VERSION = "SSM_Current";
    private static final Gson gson = new Gson();

    /**
     * 元数据服务连接超时时间（毫秒）
     * 用于 CAM 角色方式获取凭据时的超时控制
     */
    private static final int METADATA_CONNECT_TIMEOUT_MS = 2000;

    /**
     * 元数据服务读取超时时间（毫秒）
     */
    private static final int METADATA_READ_TIMEOUT_MS = 3000;

    /**
     * 当前实例绑定的 SSM 账号配置
     */
    private final SsmAccount ssmAccount;

    /**
     * 缓存的 SsmClient 实例，避免高并发下重复创建
     * 使用 volatile 保证多线程可见性
     */
    private volatile SsmClient cachedClient;

    /**
     * 缓存的 SsmClient 对应的配置指纹，用于判断是否需要重建
     */
    private volatile String cachedClientFingerprint;

    /**
     * SsmClient 创建锁
     */
    private final Object clientLock = new Object();

    /**
     * 构造函数
     *
     * @param ssmAccount SSM 账号配置（不可为 null）
     */
    public SsmRequester(SsmAccount ssmAccount) {
        if (ssmAccount == null) {
            throw new IllegalArgumentException("ssmAccount cannot be null");
        }
        this.ssmAccount = ssmAccount;
    }

    /**
     * 获取当前数据库账号信息
     *
     * @param secretName 凭据名称
     * @return 数据库账号
     * @throws SsmRotationException 获取失败时抛出
     */
    public DbAccount getCurrentAccount(String secretName) throws SsmRotationException {
        String secretValue = getSecretValue(secretName);
        if (secretValue == null || secretValue.isEmpty()) {
            throw new SsmRotationException(SsmRotationException.ERROR_SSM, "Secret value is empty");
        }

        try {
            DbAccount account = gson.fromJson(secretValue, DbAccount.class);
            if (account == null || account.getUserName() == null || account.getPassword() == null) {
                throw new SsmRotationException(SsmRotationException.ERROR_SSM, "Invalid secret format: missing userName or password");
            }
            return account;
        } catch (SsmRotationException e) {
            throw e;
        } catch (Exception e) {
            throw new SsmRotationException(SsmRotationException.ERROR_SSM, "Invalid secret JSON format: " + e.getMessage(), e);
        }
    }

    private String getSecretValue(String secretName) throws SsmRotationException {
        log.debug("Getting value for secretName={}", secretName);
        long startTime = System.currentTimeMillis();

        try {
            SsmClient client = getOrCreateSsmClient();
            GetSecretValueRequest request = new GetSecretValueRequest();
            request.setSecretName(secretName);
            request.setVersionId(SSM_CURRENT_VERSION);

            GetSecretValueResponse response = client.GetSecretValue(request);
            log.debug("GetSecretValue cost: {} ms", System.currentTimeMillis() - startTime);
            return response.getSecretString();
        } catch (TencentCloudSDKException e) {
            throw new SsmRotationException(SsmRotationException.ERROR_SSM, "SSM API error: " + e.getMessage(), e);
        }
    }

    /**
     * 获取或创建 SsmClient（带缓存，Double-Check Locking 模式）
     * 
     * <p>只有当凭据配置发生变化时才重新创建 SsmClient，避免高并发下重复创建</p>
     */
    private SsmClient getOrCreateSsmClient() throws SsmRotationException {
        String fingerprint = buildClientFingerprint(ssmAccount);
        
        // 快速路径：指纹匹配且 client 存在，直接复用
        if (fingerprint.equals(cachedClientFingerprint) && cachedClient != null) {
            return cachedClient;
        }
        
        // 慢路径：需要创建新 client
        synchronized (clientLock) {
            // Double-check：其他线程可能已经完成创建
            if (fingerprint.equals(cachedClientFingerprint) && cachedClient != null) {
                return cachedClient;
            }
            
            log.info("Creating new SsmClient (fingerprint changed or first creation)");
            SsmClient newClient = createSsmClient(ssmAccount);
            cachedClient = newClient;
            cachedClientFingerprint = fingerprint;
            return newClient;
        }
    }

    /**
     * 构建 SsmClient 配置指纹
     * 用于判断是否需要重新创建 SsmClient
     */
    private static String buildClientFingerprint(SsmAccount ssmAccount) {
        StringBuilder sb = new StringBuilder();
        sb.append(ssmAccount.getCredentialType()).append("|");
        sb.append(ssmAccount.getRegion()).append("|");
        sb.append(ssmAccount.getEndpoint() != null ? ssmAccount.getEndpoint() : "").append("|");
        
        // 对于 PERMANENT 和 TEMPORARY 类型，AK/SK 变化需要重建
        if (ssmAccount.getCredentialType() != CredentialType.CAM_ROLE) {
            sb.append(ssmAccount.getSecretId()).append("|");
            // 不放密钥明文，使用 SHA-256 摘要前 8 位
            sb.append(shortSha256(ssmAccount.getSecretKey()));
            if (ssmAccount.getCredentialType() == CredentialType.TEMPORARY) {
                // 临时凭据还要纳入 token，确保 token 变化时能够重建 client
                sb.append("|").append(shortSha256(ssmAccount.getToken()));
            }
        } else {
            sb.append(ssmAccount.getRoleName());
        }
        
        return sb.toString();
    }

    private static String shortSha256(String plaintext) {
        if (plaintext == null) {
            return "null";
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(plaintext.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(8);
            for (int i = 0; i < 4; i++) {
                int value = digest[i] & 0xFF;
                if (value < 16) {
                    hex.append('0');
                }
                hex.append(Integer.toHexString(value));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }

    /**
     * 根据凭据类型创建 SSM 客户端
     */
    private static SsmClient createSsmClient(SsmAccount ssmAccount) throws SsmRotationException {
        try {
            ensureRegion(ssmAccount);

            HttpProfile httpProfile = new HttpProfile();
            httpProfile.setReqMethod("POST");

            if (ssmAccount.getEndpoint() != null
                    && !ssmAccount.getEndpoint().isEmpty()) {
                httpProfile.setEndpoint(ssmAccount.getEndpoint());
            }

            ClientProfile clientProfile = new ClientProfile();
            clientProfile.setHttpProfile(httpProfile);
            Credential credential = createCredential(ssmAccount);
            return new SsmClient(credential,
                    ssmAccount.getRegion(), clientProfile);
        } catch (SsmRotationException e) {
            throw e;
        } catch (Exception e) {
            throw new SsmRotationException(SsmRotationException.ERROR_SSM, "Failed to create SSM client: " + e.getMessage(), e);
        }
    }

    /**
     * 根据凭据类型创建对应的 Credential 对象
     * 
     * @param ssmAccount SSM 账号配置
     * @return Credential 对象
     * @throws SsmRotationException 凭据创建失败时抛出
     */
    private static Credential createCredential(SsmAccount ssmAccount) throws SsmRotationException {
        CredentialType credentialType = ssmAccount.getCredentialType();
        if (credentialType == null) {
            credentialType = CredentialType.PERMANENT;
        }

        log.debug("Creating credential with type: {}", credentialType);

        switch (credentialType) {
            case PERMANENT:
                // 固定 AK/SK 方式
                validatePermanentCredential(ssmAccount);
                return new Credential(ssmAccount.getSecretId(), ssmAccount.getSecretKey());

            case TEMPORARY:
                // 临时凭据方式（带 Token）
                validateTemporaryCredential(ssmAccount);
                return new Credential(ssmAccount.getSecretId(), ssmAccount.getSecretKey(), ssmAccount.getToken());

            case CAM_ROLE:
                // CAM 角色方式
                // 通过计算实例元数据服务获取临时凭据，适用于 CVM 角色绑定
                validateCamRoleCredential(ssmAccount);
                return createCamRoleCredential(ssmAccount);

            default:
                throw new SsmRotationException(SsmRotationException.ERROR_SSM, "Unsupported credential type: " + credentialType);
        }
    }

    /**
     * 验证固定 AK/SK 凭据参数
     */
    private static void validatePermanentCredential(SsmAccount ssmAccount) throws SsmRotationException {
        if (ssmAccount.getSecretId() == null || ssmAccount.getSecretId().isEmpty()) {
            throw new SsmRotationException(SsmRotationException.ERROR_SSM, "SecretId is required for PERMANENT credential type");
        }
        if (ssmAccount.getSecretKey() == null || ssmAccount.getSecretKey().isEmpty()) {
            throw new SsmRotationException(SsmRotationException.ERROR_SSM, "SecretKey is required for PERMANENT credential type");
        }
    }

    /**
     * 验证临时凭据参数
     */
    private static void validateTemporaryCredential(SsmAccount ssmAccount) throws SsmRotationException {
        validatePermanentCredential(ssmAccount);
        if (ssmAccount.getToken() == null || ssmAccount.getToken().isEmpty()) {
            throw new SsmRotationException(SsmRotationException.ERROR_SSM, "Token is required for TEMPORARY credential type");
        }
    }

    /**
     * 验证 CAM 角色凭据参数
     */
    private static void validateCamRoleCredential(SsmAccount ssmAccount) throws SsmRotationException {
        if (ssmAccount.getRoleName() == null || ssmAccount.getRoleName().isEmpty()) {
            throw new SsmRotationException(SsmRotationException.ERROR_SSM, "RoleName is required for CAM_ROLE credential type");
        }
    }

    /**
     * 确保 region 可用
     */
    private static void ensureRegion(SsmAccount ssmAccount) throws SsmRotationException {
        String region = ssmAccount.getRegion();
        if (region != null && !region.isEmpty()) {
            return;
        }

        throw new SsmRotationException(SsmRotationException.ERROR_SSM,
                "Region is required to create SSM client. Please set region in SsmAccount.");
    }

    /**
     * 创建 CAM 角色凭据
     * 
     * @param ssmAccount SSM 账号配置
     * @return Credential 对象
     * @throws SsmRotationException 凭据创建失败时抛出
     */
    private static Credential createCamRoleCredential(SsmAccount ssmAccount) throws SsmRotationException {
        String roleName = ssmAccount.getRoleName();
        log.info("Using CAM role credential with roleName: {}", roleName);

        // 使用元数据服务方式
        log.info("Using CvmRoleCredential via metadata service");
        log.info("Note: If this hangs, the metadata service "
                + "(http://metadata.tencentyun.com) may be unreachable.");
        log.info("Please verify: 1) NetworkPolicy allows access "
                + "2) Node has CAM role bound "
                + "3) Run in Tencent Cloud environment");
        
        try {
            // 先测试元数据服务是否可达
            testMetadataServiceReachable();
            
            CvmRoleCredential credential = new CvmRoleCredential(roleName);
            // 主动调用一次获取凭据，以便快速失败而不是在后续请求时才发现问题
            log.info("Fetching initial credential from metadata service...");
            credential.getSecretId();
            log.info("Successfully obtained credential from metadata service");
            return credential;
        } catch (Exception e) {
            String errorMsg = String.format(
                    "Failed to get credential from metadata service. "
                    + "RoleName: %s, Error: %s. "
                    + "Possible causes: "
                    + "1) Metadata service unreachable "
                    + "(not in Tencent Cloud or NetworkPolicy blocked) "
                    + "2) Node does not have CAM role bound",
                    roleName, e.getMessage());
            throw new SsmRotationException(SsmRotationException.ERROR_CAM_ROLE, errorMsg, e);
        }
    }

    /**
     * 测试元数据服务是否可达
     * 
     * @throws SsmRotationException 如果元数据服务不可达
     */
    private static void testMetadataServiceReachable() throws SsmRotationException {
        String metadataUrl = "http://metadata.tencentyun.com/latest/meta-data/";
        log.info("Testing metadata service reachability: {}", metadataUrl);
        
        java.net.HttpURLConnection conn = null;
        try {
            java.net.URL url = new java.net.URL(metadataUrl);
            conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(METADATA_CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(METADATA_READ_TIMEOUT_MS);
            conn.setRequestMethod("GET");
            
            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                log.info("Metadata service is reachable");
            } else {
                log.warn("Metadata service returned unexpected status: {}", responseCode);
            }
        } catch (java.net.SocketTimeoutException e) {
            throw new SsmRotationException(SsmRotationException.ERROR_METADATA_TIMEOUT,
                    String.format("Metadata service connection "
                            + "timeout after %d ms. "
                            + "This usually means: "
                            + "1) Not running in Tencent Cloud "
                            + "environment "
                            + "2) NetworkPolicy blocks access "
                            + "to metadata service",
                            METADATA_CONNECT_TIMEOUT_MS), e);
        } catch (java.net.ConnectException e) {
            throw new SsmRotationException(SsmRotationException.ERROR_METADATA_UNREACHABLE,
                    "Cannot connect to metadata service: "
                    + e.getMessage()
                    + ". Please ensure running in "
                    + "Tencent Cloud environment.", e);
        } catch (Exception e) {
            throw new SsmRotationException(SsmRotationException.ERROR_METADATA,
                    "Failed to access metadata service: "
                    + e.getMessage(), e);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
}
