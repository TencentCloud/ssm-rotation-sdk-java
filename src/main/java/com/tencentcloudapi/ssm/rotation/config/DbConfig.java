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

package com.tencentcloudapi.ssm.rotation.config;

import com.tencentcloudapi.ssm.rotation.SsmRotationException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.regex.Pattern;

/**
 * 数据库连接配置类
 * 
 * <p>存储数据库连接相关的配置参数，用于连接工厂创建数据库连接。</p>
 * 
 * <p><b>注意：</b>本 SDK 作为"连接工厂"使用，每次 {@code getConnection()} 都会创建新的物理连接。
 * 如需连接池功能，请配合 HikariCP 等成熟连接池使用。</p>
 * 
 * @author tencentcloud
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DbConfig {

    /**
     * 超时时间上限值（秒）：3600秒（1小时）
     * 避免读写操作长期阻塞
     */
    public static final int MAX_TIMEOUT_SECONDS = 3600;

    /**
     * 数据库名称合法字符正则：只允许字母、数字、下划线、中划线
     */
    private static final Pattern DB_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_\\-]+$");

    /**
     * JDBC 参数中禁止使用的危险参数（防止 JDBC 注入攻击）
     */
    private static final String[] DANGEROUS_JDBC_PARAMS = {
            "autoDeserialize",
            "allowLoadLocalInfile",
            "allowLoadLocalInfileInPath",
            "allowUrlInLocalInfile"
    };

    /**
     * 连接超时时间（秒），默认 5 秒，最大 3600 秒
     * 对应 JDBC connectTimeout 参数
     */
    @Builder.Default
    private int connectTimeoutSeconds = 5;

    /**
     * Socket 超时时间（秒），默认 5 秒，最大 3600 秒
     * 对应 JDBC socketTimeout 参数，用于读写操作超时
     */
    @Builder.Default
    private int socketTimeoutSeconds = 5;

    /**
     * SSM 凭据名称（必填）
     */
    private String secretName;

    /**
     * 数据库 IP 地址（必填）
     */
    private String ipAddress;

    /**
     * 数据库端口（必填）
     */
    private int port;

    /**
     * 数据库名称（可选）
     */
    private String dbName;

    /**
     * 额外的 JDBC 连接参数（可选）
     * 例如：charset=utf8&useSSL=false
     */
    private String paramStr;

    /**
     * 验证配置参数的有效性
     *
     * @throws SsmRotationException 当配置参数无效时抛出
     */
    public void validate() throws SsmRotationException {
        if (secretName == null || secretName.trim().isEmpty()) {
            throw new SsmRotationException(SsmRotationException.ERROR_CONFIG, "secretName cannot be null or empty");
        }
        if (ipAddress == null || ipAddress.trim().isEmpty()) {
            throw new SsmRotationException(SsmRotationException.ERROR_CONFIG, "ipAddress cannot be null or empty");
        }
        if (port <= 0 || port > 65535) {
            throw new SsmRotationException(SsmRotationException.ERROR_CONFIG,
                "Invalid port: " + port + ", must be between 1 and 65535");
        }
        if (connectTimeoutSeconds <= 0
                || connectTimeoutSeconds > MAX_TIMEOUT_SECONDS) {
            throw new SsmRotationException(SsmRotationException.ERROR_CONFIG,
                "connectTimeoutSeconds must be between 1 and "
                    + MAX_TIMEOUT_SECONDS
                    + ", got: " + connectTimeoutSeconds);
        }
        if (socketTimeoutSeconds <= 0 || socketTimeoutSeconds > MAX_TIMEOUT_SECONDS) {
            throw new SsmRotationException(SsmRotationException.ERROR_CONFIG, 
                "socketTimeoutSeconds must be between 1 and " + MAX_TIMEOUT_SECONDS + ", got: " + socketTimeoutSeconds);
        }
        
        // 校验 dbName：只允许合法字符，防止 JDBC URL 注入
        if (dbName != null && !dbName.isEmpty()) {
            if (!DB_NAME_PATTERN.matcher(dbName).matches()) {
                throw new SsmRotationException(SsmRotationException.ERROR_CONFIG,
                    "Invalid dbName: '" + dbName + "'. Only letters, digits, underscores and hyphens are allowed");
            }
        }
        
        // 校验 paramStr：禁止包含危险的 JDBC 参数
        if (paramStr != null && !paramStr.isEmpty()) {
            String lowerParam = paramStr.toLowerCase();
            for (String dangerous : DANGEROUS_JDBC_PARAMS) {
                if (lowerParam.contains(dangerous.toLowerCase())) {
                    throw new SsmRotationException(SsmRotationException.ERROR_CONFIG,
                        "Dangerous JDBC parameter detected in paramStr: '" + dangerous 
                        + "'. This parameter is not allowed for security reasons");
                }
            }
        }
    }
}