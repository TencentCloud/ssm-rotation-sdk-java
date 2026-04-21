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
package com.tencentcloudapi.ssm.rotation.demo.unified;

import com.tencentcloudapi.ssm.rotation.SsmRotationException;
import com.tencentcloudapi.ssm.rotation.config.DbConfig;
import com.tencentcloudapi.ssm.rotation.config.DynamicSecretRotationConfig;
import com.tencentcloudapi.ssm.rotation.db.DynamicSecretRotationDb;
import com.tencentcloudapi.ssm.rotation.db.dbcp.SsmRotationDbcpDataSource;
import com.tencentcloudapi.ssm.rotation.db.druid.SsmRotationDruidDataSource;
import com.tencentcloudapi.ssm.rotation.db.hikari.SsmRotationHikariDataSource;
import com.tencentcloudapi.ssm.rotation.ssm.SsmAccount;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.PreDestroy;
import javax.sql.DataSource;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 统一 Demo：认证方式 + 连接模式可配置
 *
 * <p>认证模式：permanent / temporary / camrole</p>
 * <p>连接模式：jdbc / hikari / druid / dbcp</p>
 */
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
@RestController
public class UnifiedDemoApplication {

    @Value("${demo.mode.auth:permanent}")
    private String authMode;

    @Value("${demo.mode.pool:jdbc}")
    private String poolMode;

    @Value("${ssm.secret-id:your-secret-id}")
    private String secretId;

    @Value("${ssm.secret-key:your-secret-key}")
    private String secretKey;

    @Value("${ssm.tmp-secret-id:your-tmp-secret-id}")
    private String tmpSecretId;

    @Value("${ssm.tmp-secret-key:your-tmp-secret-key}")
    private String tmpSecretKey;

    @Value("${ssm.tmp-token:your-tmp-token}")
    private String tmpToken;

    @Value("${ssm.cam-role-name:your-cam-role-name}")
    private String camRoleName;

    @Value("${ssm.region:ap-guangzhou}")
    private String region;

    @Value("${ssm.secret-name:your-secret-name}")
    private String secretName;

    @Value("${ssm.db.ip:127.0.0.1}")
    private String dbIp;

    @Value("${ssm.db.port:3306}")
    private int dbPort;

    @Value("${ssm.db.name:test}")
    private String dbName;

    @Value("${ssm.watch-interval-ms:10000}")
    private long watchIntervalMs;

    @Value("${pool.max-size:20}")
    private int poolMaxSize;

    @Value("${pool.min-idle:5}")
    private int poolMinIdle;

    // ==================== Druid 连接池参数（仅 pool=druid 时生效） ====================

    @Value("${druid.initial-size:5}")
    private int druidInitialSize;

    @Value("${druid.max-wait:60000}")
    private long druidMaxWait;

    @Value("${druid.time-between-eviction-runs-millis:60000}")
    private long druidTimeBetweenEvictionRunsMillis;

    @Value("${druid.min-evictable-idle-time-millis:300000}")
    private long druidMinEvictableIdleTimeMillis;

    @Value("${druid.validation-query:SELECT 1}")
    private String druidValidationQuery;

    @Value("${druid.test-while-idle:true}")
    private boolean druidTestWhileIdle;

    @Value("${druid.test-on-borrow:false}")
    private boolean druidTestOnBorrow;

    @Value("${druid.test-on-return:false}")
    private boolean druidTestOnReturn;

    @Value("${druid.keep-alive:true}")
    private boolean druidKeepAlive;

    @Value("${druid.keep-alive-between-time-millis:120000}")
    private long druidKeepAliveBetweenTimeMillis;

    private DemoRuntime runtime;

    public static void main(String[] args) {
        System.out.println("============================================================");
        System.out.println("  SSM Rotation SDK Unified Demo");
        System.out.println("============================================================");
        System.out.println("  auth modes: permanent | temporary | camrole");
        System.out.println("  pool modes: jdbc | hikari | druid | dbcp");
        System.out.println();
        SpringApplication.run(UnifiedDemoApplication.class, args);
    }

    @Bean
    public DemoRuntime demoRuntime() throws SsmRotationException {
        this.runtime = createDemoRuntime();
        return this.runtime;
    }

    private DemoRuntime createDemoRuntime() throws SsmRotationException {
        String normalizedAuthMode = normalizeMode(authMode);
        String normalizedPoolMode = normalizeMode(poolMode);

        DbConfig dbConfig = DbConfig.builder()
                .secretName(secretName)
                .ipAddress(dbIp)
                .port(dbPort)
                .dbName(dbName)
                .paramStr("useSSL=false&characterEncoding=utf8")
                .build();

        SsmAccount ssmAccount = buildSsmAccount(normalizedAuthMode);
        DynamicSecretRotationConfig config = DynamicSecretRotationConfig.builder()
                .dbConfig(dbConfig)
                .ssmServiceConfig(ssmAccount)
                .watchChangeIntervalMs(watchIntervalMs)
                .build();

        if ("jdbc".equals(normalizedPoolMode)) {
            DynamicSecretRotationDb rotationDb = new DynamicSecretRotationDb(config);
            System.out.println("✓ Unified demo started with auth=" + normalizedAuthMode + ", pool=jdbc");
            return new DemoRuntime(normalizedAuthMode, normalizedPoolMode, rotationDb, null, rotationDb);
        }

        if ("hikari".equals(normalizedPoolMode)) {
            SsmRotationHikariDataSource ds = SsmRotationHikariDataSource.builder()
                    .rotationConfig(config)
                    .maximumPoolSize(poolMaxSize)
                    .minimumIdle(poolMinIdle)
                    .build();
            System.out.println("✓ Unified demo started with auth=" + normalizedAuthMode + ", pool=hikari");
            return new DemoRuntime(normalizedAuthMode, normalizedPoolMode, ds.getRotationDb(), ds, ds);
        }

        if ("druid".equals(normalizedPoolMode)) {
            // 所有 Druid 连接池参数均从 application.yml 中读取，用户只需修改配置文件即可调整连接池行为
            SsmRotationDruidDataSource ds = SsmRotationDruidDataSource.builder()
                    .rotationConfig(config)
                    .maxActive(poolMaxSize)
                    .minIdle(poolMinIdle)
                    .initialSize(druidInitialSize)
                    .maxWait(druidMaxWait)
                    .timeBetweenEvictionRunsMillis(druidTimeBetweenEvictionRunsMillis)
                    .minEvictableIdleTimeMillis(druidMinEvictableIdleTimeMillis)
                    .validationQuery(druidValidationQuery)
                    .testWhileIdle(druidTestWhileIdle)
                    .testOnBorrow(druidTestOnBorrow)
                    .testOnReturn(druidTestOnReturn)
                    .keepAlive(druidKeepAlive)
                    .keepAliveBetweenTimeMillis(druidKeepAliveBetweenTimeMillis)
                    .build();
            System.out.println("✓ Unified demo started with auth=" + normalizedAuthMode + ", pool=druid");
            return new DemoRuntime(normalizedAuthMode, normalizedPoolMode, ds.getRotationDb(), ds, ds);
        }

        if ("dbcp".equals(normalizedPoolMode)) {
            SsmRotationDbcpDataSource ds = SsmRotationDbcpDataSource.builder()
                    .rotationConfig(config)
                    .maxTotal(poolMaxSize)
                    .minIdle(poolMinIdle)
                    .build();
            System.out.println("✓ Unified demo started with auth=" + normalizedAuthMode + ", pool=dbcp");
            return new DemoRuntime(normalizedAuthMode, normalizedPoolMode, ds.getRotationDb(), ds, ds);
        }

        throw new SsmRotationException(SsmRotationException.ERROR_CONFIG,
                "Unsupported demo.mode.pool: " + poolMode + ", expected one of [jdbc, hikari, druid, dbcp]");
    }

    private SsmAccount buildSsmAccount(String normalizedAuthMode) throws SsmRotationException {
        if ("camrole".equals(normalizedAuthMode)) {
            checkCvmEnvironment();
            return SsmAccount.withCamRole(camRoleName, region);
        }
        if ("temporary".equals(normalizedAuthMode)) {
            return SsmAccount.withTemporaryCredential(tmpSecretId, tmpSecretKey, tmpToken, region);
        }
        if ("permanent".equals(normalizedAuthMode)) {
            return SsmAccount.withPermanentCredential(secretId, secretKey, region);
        }
        throw new SsmRotationException(SsmRotationException.ERROR_CONFIG,
                "Unsupported demo.mode.auth: " + authMode + ", expected one of [permanent, temporary, camrole]");
    }

    private void checkCvmEnvironment() {
        try {
            URL url = new URL("http://metadata.tencentyun.com/latest/meta-data/");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                System.out.println("✓ metadata.tencentyun.com reachable");
            } else {
                System.out.println("⚠ metadata service status: " + responseCode);
            }
            conn.disconnect();
        } catch (Exception e) {
            System.out.println("⚠ metadata service unavailable: " + e.getMessage());
        }
    }

    private String normalizeMode(String mode) {
        if (mode == null) {
            return "";
        }
        return mode.trim().toLowerCase(Locale.ROOT);
    }

    @PreDestroy
    public void cleanup() {
        if (runtime == null || runtime.managedResource == null) {
            return;
        }
        try {
            runtime.managedResource.close();
        } catch (Exception e) {
            System.out.println("⚠ cleanup failed: " + e.getMessage());
        }
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "UP");
        result.put("authMode", runtime.authMode);
        result.put("poolMode", runtime.poolMode);
        result.put("healthy", runtime.rotationDb.isHealthy());
        result.put("healthCheck", runtime.rotationDb.getHealthCheckResult());
        fillPoolStats(result);
        result.put("timestamp", System.currentTimeMillis());
        return result;
    }

    @GetMapping("/db/test")
    public Map<String, Object> testDb() {
        Map<String, Object> result = new HashMap<>();
        result.put("authMode", runtime.authMode);
        result.put("poolMode", runtime.poolMode);

        try (Connection conn = acquireConnection()) {
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT 1 as test, NOW() as time")) {
                if (rs.next()) {
                    result.put("status", "SUCCESS");
                    result.put("queryResult", rs.getInt("test"));
                    result.put("serverTime", rs.getString("time"));
                }
            }
        } catch (Exception e) {
            result.put("status", "ERROR");
            result.put("message", e.getMessage());
        }

        result.put("currentUser", runtime.rotationDb.getCurrentUser());
        fillPoolStats(result);
        result.put("timestamp", System.currentTimeMillis());
        return result;
    }

    private Connection acquireConnection() throws Exception {
        if (runtime.dataSource != null) {
            return runtime.dataSource.getConnection();
        }
        return runtime.rotationDb.getConnection();
    }

    private void fillPoolStats(Map<String, Object> result) {
        if (runtime.managedResource instanceof SsmRotationHikariDataSource) {
            SsmRotationHikariDataSource hikari = (SsmRotationHikariDataSource) runtime.managedResource;
            if (hikari.getHikariDataSource().getHikariPoolMXBean() != null) {
                result.put("totalConnections", hikari.getHikariDataSource().getHikariPoolMXBean().getTotalConnections());
                result.put("activeConnections", hikari.getHikariDataSource().getHikariPoolMXBean().getActiveConnections());
                result.put("idleConnections", hikari.getHikariDataSource().getHikariPoolMXBean().getIdleConnections());
            }
            return;
        }
        if (runtime.managedResource instanceof SsmRotationDruidDataSource) {
            SsmRotationDruidDataSource druid = (SsmRotationDruidDataSource) runtime.managedResource;
            result.put("activeCount", druid.getDruidDataSource().getActiveCount());
            result.put("poolingCount", druid.getDruidDataSource().getPoolingCount());
            return;
        }
        if (runtime.managedResource instanceof SsmRotationDbcpDataSource) {
            SsmRotationDbcpDataSource dbcp = (SsmRotationDbcpDataSource) runtime.managedResource;
            result.put("numActive", dbcp.getBasicDataSource().getNumActive());
            result.put("numIdle", dbcp.getBasicDataSource().getNumIdle());
            return;
        }
        result.put("connectionMode", "DIRECT_JDBC");
    }

    private static final class DemoRuntime {
        private final String authMode;
        private final String poolMode;
        private final DynamicSecretRotationDb rotationDb;
        private final DataSource dataSource;
        private final AutoCloseable managedResource;

        private DemoRuntime(String authMode,
                            String poolMode,
                            DynamicSecretRotationDb rotationDb,
                            DataSource dataSource,
                            AutoCloseable managedResource) {
            this.authMode = authMode;
            this.poolMode = poolMode;
            this.rotationDb = rotationDb;
            this.dataSource = dataSource;
            this.managedResource = managedResource;
        }
    }
}
