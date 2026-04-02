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

package com.tencentcloudapi.ssm.rotation.quickstart;

import com.tencentcloudapi.ssm.rotation.ssm.CredentialType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * QuickStart / Auto-Configuration 共享的数据源配置模型
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SsmRotationDataSourceOptions {

    @Builder.Default
    private SsmRotationPoolType poolType = SsmRotationPoolType.DRUID;

    @Builder.Default
    private long watchIntervalMs = 10000L;

    @Builder.Default
    private CredentialType credentialType = CredentialType.PERMANENT;

    private String region;
    private String secretId;
    private String secretKey;
    private String token;
    private String roleName;
    private String endpoint;

    private String secretName;
    private String ipAddress;

    @Builder.Default
    private int port = 3306;

    private String dbName;
    private String paramStr;

    @Builder.Default
    private int connectTimeoutSeconds = 5;

    @Builder.Default
    private int socketTimeoutSeconds = 5;

    @Builder.Default
    private DruidOptions druid = DruidOptions.builder().build();

    @Builder.Default
    private HikariOptions hikari = HikariOptions.builder().build();

    @Builder.Default
    private DbcpOptions dbcp = DbcpOptions.builder().build();

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DruidOptions {
        @Builder.Default
        private int maxActive = 20;
        @Builder.Default
        private int minIdle = 5;
        @Builder.Default
        private int initialSize = 5;
        @Builder.Default
        private long maxWait = 60000L;
        @Builder.Default
        private long timeBetweenEvictionRunsMillis = 60000L;
        @Builder.Default
        private long minEvictableIdleTimeMillis = 300000L;
        @Builder.Default
        private String validationQuery = "SELECT 1";
        @Builder.Default
        private boolean testWhileIdle = true;
        @Builder.Default
        private boolean testOnBorrow = false;
        @Builder.Default
        private boolean testOnReturn = false;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HikariOptions {
        @Builder.Default
        private int maximumPoolSize = 20;
        @Builder.Default
        private int minimumIdle = 5;
        @Builder.Default
        private long connectionTimeout = 30000L;
        @Builder.Default
        private long idleTimeout = 600000L;
        @Builder.Default
        private long maxLifetime = 1800000L;
        @Builder.Default
        private String connectionTestQuery = "SELECT 1";
        @Builder.Default
        private String poolName = "SSM-Rotation-HikariPool";
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DbcpOptions {
        @Builder.Default
        private int maxTotal = 20;
        @Builder.Default
        private int minIdle = 5;
        @Builder.Default
        private int maxIdle = 10;
        @Builder.Default
        private int initialSize = 5;
        @Builder.Default
        private long maxWaitMillis = 60000L;
        @Builder.Default
        private long timeBetweenEvictionRunsMillis = 60000L;
        @Builder.Default
        private long minEvictableIdleTimeMillis = 300000L;
        @Builder.Default
        private String validationQuery = "SELECT 1";
        @Builder.Default
        private boolean testWhileIdle = true;
        @Builder.Default
        private boolean testOnBorrow = false;
        @Builder.Default
        private boolean testOnReturn = false;
    }
}
