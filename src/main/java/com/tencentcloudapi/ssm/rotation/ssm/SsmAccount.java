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

import com.tencentcloudapi.ssm.rotation.SsmRotationException;
import lombok.Getter;

/**
 * SSM 服务账号配置类
 * 用于存储访问凭据管理系统（SSM）所需的认证信息
 * 
 * <h2>SDK 支持的三种核心认证方式</h2>
 * 
 * <pre>
 * ┌──────────────────────────────────────────────────────────────────────────┐
 * │                    SDK 核心认证方式（三种）                               │
 * ├──────────────────────────────────────────────────────────────────────────┤
 * │                                                                          │
 * │  1. 角色绑定                                                             │
 * │     - 只有 CVM 支持真正的角色绑定                                         │
 * │     - 通过元数据服务自动获取临时凭据                                       │
 * │     - SDK 自动刷新                                                       │
 * │     - 使用方法: SsmAccount.withCamRole(roleName, region)                 │
 * │                                                                          │
 * │  2. 临时凭据                                                             │
 * │     - 使用临时 AK/SK/Token                                               │
 * │     - 使用方法: SsmAccount.withTemporaryCredential(id, key, token, region)│
 * │                                                                          │
 * │  3. 固定凭据（不推荐）                                                    │
 * │     - 使用固定 AK/SK                                                     │
 * │     - 使用方法: SsmAccount.withPermanentCredential(id, key, region)      │
 * │                                                                          │
 * └──────────────────────────────────────────────────────────────────────────┘
 * </pre>
 * 
 * <h2>各环境推荐认证方式</h2>
 * 
 * <table border="1">
 *   <tr><th>环境</th><th>推荐方式</th><th>工厂方法</th><th>说明</th></tr>
 *   <tr><td>CVM</td><td>角色绑定</td><td>withCamRole()</td><td>唯一支持角色绑定的资源</td></tr>
 *   <tr><td>其他</td><td>临时凭据</td><td>withTemporaryCredential()</td><td>用户自行获取临时凭据</td></tr>
 * </table>
 * 
 * <p>SDK 不做环境判断（CAM/TKE/SCF 等），由使用者自行选择并初始化。</p>
 * 
 * @author tencentcloud
 * @since 1.0.0
 */
@Getter
public class SsmAccount {

    /**
     * 凭据类型
     * 默认为固定 AK/SK 方式，保持向后兼容
     */
    private CredentialType credentialType = CredentialType.PERMANENT;

    /**
     * 腾讯云 SecretId
     * 用于标识 API 调用者身份
     * 适用于 PERMANENT 和 TEMPORARY 类型
     */
    private String secretId;

    /**
     * 腾讯云 SecretKey
     * 用于验证 API 调用者身份的密钥
     * 适用于 PERMANENT 和 TEMPORARY 类型
     */
    private String secretKey;

    /**
     * 临时凭据 Token
     * 使用临时凭据时必须提供
     * 仅适用于 TEMPORARY 类型
     */
    private String token;

    /**
     * CAM 角色名称
     * 使用 CAM 角色方式时必须提供
     * 仅适用于 CAM_ROLE 类型
     */
    private String roleName;

    /**
     * 地域信息
     * 凭据所在的地域，如：ap-guangzhou, ap-beijing 等
     */
    private String region;

    /**
     * SSM 服务的自定义接入点 URL（可选）
     * 如果不设置，将使用默认的 SSM 服务地址
     */
    private String endpoint;

    /**
     * 默认构造函数
     */
    public SsmAccount() {
    }

    /**
     * 固定 AK/SK 方式构造函数（保持向后兼容）
     *
     * @param secretId  腾讯云 SecretId
     * @param secretKey 腾讯云 SecretKey
     * @param region    地域信息
     * @deprecated 请使用工厂方法 {@link #withPermanentCredential(String, String, String)} 替代
     */
    @Deprecated
    public SsmAccount(String secretId, String secretKey, String region) {
        this.credentialType = CredentialType.PERMANENT;
        this.secretId = secretId;
        this.secretKey = secretKey;
        this.region = region;
    }

    /**
     * 全参数构造函数（保持向后兼容）
     *
     * @param secretId  腾讯云 SecretId
     * @param secretKey 腾讯云 SecretKey
     * @param region    地域信息
     * @param endpoint  自定义接入点
     * @deprecated 请使用工厂方法 {@link #withPermanentCredential(String, String, String)} 配合 {@link #withEndpoint(String)} 替代
     */
    @Deprecated
    public SsmAccount(String secretId, String secretKey, String region, String endpoint) {
        this.credentialType = CredentialType.PERMANENT;
        this.secretId = secretId;
        this.secretKey = secretKey;
        this.region = region;
        this.endpoint = endpoint;
    }

    // ==================== 核心认证方式：角色绑定（仅 CVM 支持）====================

    /**
     * 创建角色绑定方式的凭据配置（推荐用于 CVM）
     * 
     * <h3>这是唯一真正通过"角色绑定"获取凭据的方式，只有 CVM 支持</h3>
     * 
     * SDK 会通过计算实例元数据服务 (http://metadata.tencentyun.com) 自动获取临时凭据
     * 并在过期前自动刷新
     * 
     * <h3>适用环境</h3>
     * <ul>
     *   <li>CVM 实例绑定 CAM 角色</li>
     * </ul>
     * 
     * <h3>配置步骤</h3>
     * <ol>
     *   <li>在 CAM 控制台创建角色，添加 SSM 访问权限</li>
     *   <li>在 CVM 控制台为实例绑定该角色</li>
     *   <li>使用本方法创建 SsmAccount</li>
     * </ol>
     *
     * @param roleName 计算实例绑定的 CAM 角色名称
     * @param region   地域信息
     * @return SsmAccount 实例
     */
    public static SsmAccount withCamRole(String roleName, String region) {
        SsmAccount account = new SsmAccount();
        account.credentialType = CredentialType.CAM_ROLE;
        account.roleName = roleName;
        account.region = region;
        return account;
    }

    // ==================== 核心认证方式：临时凭据 ====================

    /**
     * 创建临时凭据方式的配置
     * 
     * <h3>通用临时凭据方式</h3>
     * 
     * 用户自行获取临时凭据后，通过此方法传入 SDK
     * 
     * <h3>临时凭据获取途径</h3>
     * <ul>
     *   <li>调用 STS API (AssumeRole)</li>
     *   <li>从外部凭据管理系统（如 Vault）获取</li>
     *   <li>其他方式获取的临时凭据</li>
     * </ul>
     * 
     * <h3>注意事项</h3>
     * <ul>
     *   <li>临时凭据有过期时间（通常 2 小时）</li>
     *   <li>SDK 不会自动刷新此方式的临时凭据</li>
     *   <li>需要用户自行管理凭据刷新</li>
     * </ul>
     *
     * @param secretId  临时 SecretId
     * @param secretKey 临时 SecretKey
     * @param token     临时 Token
     * @param region    地域信息
     * @return SsmAccount 实例
     */
    public static SsmAccount withTemporaryCredential(String secretId, String secretKey, String token, String region) {
        SsmAccount account = new SsmAccount();
        account.credentialType = CredentialType.TEMPORARY;
        account.secretId = secretId;
        account.secretKey = secretKey;
        account.token = token;
        account.region = region;
        return account;
    }

    // ==================== 核心认证方式：固定凭据（不推荐）====================

    /**
     * 创建固定 AK/SK 方式的凭据配置（不推荐）
     * 
     * <h3>⚠️ 安全警告：此方式存在密钥泄露风险，不推荐在生产环境使用</h3>
     * 
     * <h3>仅建议在以下场景使用</h3>
     * <ul>
     *   <li>本地开发调试</li>
     *   <li>快速功能验证</li>
     * </ul>
     * 
     * <h3>生产环境请使用</h3>
     * <ul>
     *   <li>CVM: withCamRole() 角色绑定方式</li>
     *   <li>其他: withTemporaryCredential() 临时凭据方式</li>
     * </ul>
     *
     * @param secretId  腾讯云 SecretId
     * @param secretKey 腾讯云 SecretKey
     * @param region    地域信息
     * @return SsmAccount 实例
     */
    public static SsmAccount withPermanentCredential(String secretId, String secretKey, String region) {
        SsmAccount account = new SsmAccount();
        account.credentialType = CredentialType.PERMANENT;
        account.secretId = secretId;
        account.secretKey = secretKey;
        account.region = region;
        return account;
    }



    /**
     * 设置自定义接入点
     *
     * @param endpoint 自定义接入点 URL
     * @return 当前实例（支持链式调用）
     */
    public SsmAccount withEndpoint(String endpoint) {
        this.endpoint = endpoint;
        return this;
    }

    /**
     * 验证配置参数的有效性
     *
     * @throws SsmRotationException 当配置参数无效时抛出
     */
    public void validate() throws SsmRotationException {
        if (region == null || region.trim().isEmpty()) {
            throw new SsmRotationException(SsmRotationException.ERROR_CONFIG, "region cannot be null or empty");
        }
        
        switch (credentialType) {
            case PERMANENT:
                if (secretId == null || secretId.trim().isEmpty()) {
                    throw new SsmRotationException(SsmRotationException.ERROR_CONFIG,
                        "secretId cannot be null or empty for PERMANENT credential type");
                }
                if (secretKey == null || secretKey.trim().isEmpty()) {
                    throw new SsmRotationException(SsmRotationException.ERROR_CONFIG,
                        "secretKey cannot be null or empty for PERMANENT credential type");
                }
                break;
            case TEMPORARY:
                if (secretId == null || secretId.trim().isEmpty()) {
                    throw new SsmRotationException(SsmRotationException.ERROR_CONFIG,
                        "secretId cannot be null or empty for TEMPORARY credential type");
                }
                if (secretKey == null || secretKey.trim().isEmpty()) {
                    throw new SsmRotationException(SsmRotationException.ERROR_CONFIG,
                        "secretKey cannot be null or empty for TEMPORARY credential type");
                }
                if (token == null || token.trim().isEmpty()) {
                    throw new SsmRotationException(SsmRotationException.ERROR_CONFIG,
                        "token cannot be null or empty for TEMPORARY credential type");
                }
                break;
            case CAM_ROLE:
                if (roleName == null || roleName.trim().isEmpty()) {
                    throw new SsmRotationException(SsmRotationException.ERROR_CONFIG,
                        "roleName cannot be null or empty for CAM_ROLE credential type");
                }
                break;
            default:
                throw new SsmRotationException(SsmRotationException.ERROR_CONFIG,
                    "Unknown credential type: " + credentialType);
        }
    }

    /**
     * 自定义 toString 方法，对敏感信息进行脱敏处理
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("SsmAccount{");
        sb.append("credentialType=").append(credentialType);
        
        if (credentialType == CredentialType.CAM_ROLE) {
            sb.append(", roleName='").append(roleName).append("'");
        } else {
            sb.append(", secretId='");
            if (secretId == null) {
                sb.append("null");
            } else if (secretId.length() < 8) {
                sb.append("****");
            } else {
                sb.append(secretId.substring(0, 4)).append("****");
            }
            sb.append("'");
            sb.append(", secretKey='****'");
            if (credentialType == CredentialType.TEMPORARY) {
                sb.append(", token='****'");
            }
        }
        
        sb.append(", region='").append(region).append("'");
        if (endpoint != null) {
            sb.append(", endpoint='").append(endpoint).append("'");
        }
        sb.append('}');
        return sb.toString();
    }
}
