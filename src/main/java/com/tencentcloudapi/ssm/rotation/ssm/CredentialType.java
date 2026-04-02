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

/**
 * 凭据类型枚举
 * 定义 SDK 支持的凭据获取方式
 * 
 * <h2>SDK 支持的三种核心认证方式</h2>
 * 
 * <pre>
 * ┌──────────────────────────────────────────────────────────────────────────┐
 * │                    SDK 核心认证方式（三种）                               │
 * ├──────────────────────────────────────────────────────────────────────────┤
 * │                                                                          │
 * │  1. 角色绑定（CAM_ROLE）                                                  │
 * │     - 只有 CVM 支持真正的角色绑定                                         │
 * │     - 通过元数据服务自动获取临时凭据                                       │
 * │     - SDK 自动刷新                                                       │
 * │                                                                          │
 * │  2. 临时凭据（TEMPORARY）                                                 │
 * │     - 使用临时 AK/SK/Token                                               │
 * │     - 可通过 STS API 或外部凭据管理系统获取                              │
 * │                                                                          │
 * │  3. 固定凭据（PERMANENT）                                                 │
 * │     - 使用固定 AK/SK                                                     │
 * │     - 不推荐在生产环境使用                                               │
 * │                                                                          │
 * └──────────────────────────────────────────────────────────────────────────┘
 * </pre>
 * 
 * <h2>各环境推荐方式</h2>
 * 
 * <table border="1">
 *   <tr><th>环境</th><th>推荐方式</th><th>临时凭据获取途径</th></tr>
 *   <tr><td>CVM</td><td>角色绑定 (CAM_ROLE)</td><td>元数据服务自动获取</td></tr>
 *   <tr><td>其他</td><td>临时凭据 (TEMPORARY)</td><td>STS API 或外部凭据管理系统</td></tr>
 * </table>
 * 
 * <p>SDK 不做环境判断（CAM/TKE/SCF 等），由使用者自行选择认证方式。</p>
 *
 * @author tencentcloud
 * @since 1.0.0
 */
public enum CredentialType {

    // ==================== 核心认证方式 ====================

    /**
     * 角色绑定方式（推荐用于 CVM）
     * 
     * <b>这是唯一真正通过"角色绑定"获取凭据的方式</b>
     * 
     * 通过计算实例元数据服务 (http://metadata.tencentyun.com) 获取临时凭据
     * 适用于 CVM 实例角色绑定
     * SDK 会自动刷新过期凭据，安全性高
     * 
     * 原理：
     * 1. CVM 实例绑定 CAM 角色
     * 2. 应用通过 HTTP 调用元数据服务获取临时 AK/SK/Token
     * 3. SDK 自动管理凭据刷新
     * 
     * 注意：
     * - 此方式依赖元数据服务网络可达
     * - 只有 CVM 资源支持此方式
     */
    CAM_ROLE,

    /**
     * 临时凭据方式
     * 
     * 使用临时 SecretId、SecretKey 和 Token
     * 凭据有过期时间，需要用户自行管理刷新
     * 
     * 适用于所有环境，用户可以通过以下途径获取临时凭据：
     * - 调用 STS API (AssumeRole)
     * - 从外部凭据管理系统获取
     * - 其他方式获取的临时凭据
     */
    TEMPORARY,

    /**
     * 固定 AK/SK 方式
     * 
     * 使用长期有效的 SecretId 和 SecretKey
     * <b>安全性较低，不推荐在生产环境使用</b>
     * 
     * 仅建议在以下场景使用：
     * - 本地开发调试
     * - 快速功能验证
     */
    PERMANENT
}
