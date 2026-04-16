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

package com.tencentcloudapi.ssm.rotation.db;

import com.tencentcloudapi.ssm.rotation.SsmRotationException;
import com.tencentcloudapi.ssm.rotation.config.DbConfig;
import com.tencentcloudapi.ssm.rotation.config.DynamicSecretRotationConfig;
import com.tencentcloudapi.ssm.rotation.ssm.CredentialType;
import com.tencentcloudapi.ssm.rotation.ssm.DbAccount;
import com.tencentcloudapi.ssm.rotation.ssm.SsmRequester;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 动态凭据轮转数据库连接工厂
 * 
 * <p>核心功能：</p>
 * <ul>
 *   <li>后台自动监控 SSM 凭据变化</li>
 *   <li>使用最新凭据创建数据库连接</li>
 * </ul>
 *
 * <p><b>注意：</b>本类不维护连接池，每次 {@code getConnection()} 创建新物理连接。
 * 高并发场景建议配合 HikariCP 等连接池使用。</p>
 * 
 * @author tencentcloud
 * @since 1.0.0
 */
@Slf4j
public class DynamicSecretRotationDb implements AutoCloseable {

    private static final String MYSQL_DRIVER = "com.mysql.cj.jdbc.Driver";
    private static final String JDBC_URL_PREFIX = "jdbc:mysql://";
    private static final int MAX_WATCH_FAILURES = 5;
    
    /**
     * Watcher 连续失败时的最大退避间隔（毫秒），默认 5 分钟
     */
    private static final long MAX_BACKOFF_INTERVAL_MS = 300_000;

    /**
     * MySQL 认证相关的错误码
     * 1045: Access denied for user
     * 1044: Access denied for user to database
     * 1698: Access denied (auth_socket plugin)
     */
    private static final int[] AUTH_ERROR_CODES = {1045, 1044, 1698};

    private final DynamicSecretRotationConfig config;
    private final SsmRequester ssmRequester;
    private final ScheduledExecutorService configuredWatchScheduler;
    private final ExecutorService credentialChangeExecutor;
    private final boolean ownsCredentialChangeExecutor;
    private final long baseWatchIntervalMs;
    private final Object watchRescheduleLock = new Object();
    private volatile DbAccount currentAccount;
    private ScheduledExecutorService scheduler;
    private volatile boolean ownsWatchScheduler = false;
    private volatile ScheduledFuture<?> watchFuture;
    private volatile long currentWatchIntervalMs;
    private volatile boolean closed = false;

    /**
     * 凭据更新锁，用于保护 currentAccount 的原子更新
     * 同时用于合并多线程的凭据刷新请求（避免惊群效应）
     */
    private final Object credentialLock = new Object();

    // 健康状态
    private final AtomicInteger watchFailures = new AtomicInteger(0);
    private volatile String lastError = null;

    /**
     * 凭据变更监听器列表，使用 CopyOnWriteArrayList 保证线程安全
     */
    private final List<CredentialChangeListener> listeners = new CopyOnWriteArrayList<>();

    /**
     * 构造函数
     *
     * @param config 动态凭据轮转配置
     * @throws SsmRotationException 当初始化失败时抛出
     */
    public DynamicSecretRotationDb(DynamicSecretRotationConfig config) throws SsmRotationException {
        if (config == null) {
            throw new SsmRotationException(SsmRotationException.ERROR_CONFIG, "config cannot be null");
        }
        config.validate();
        this.config = config;
        this.configuredWatchScheduler = config.getWatchScheduler();
        this.baseWatchIntervalMs = config.getWatchChangeIntervalMs();
        this.currentWatchIntervalMs = this.baseWatchIntervalMs;
        
        // 创建实例级别的 SsmRequester，支持多实例场景
        this.ssmRequester = new SsmRequester(config.getSsmServiceConfig());
        
        // 临时凭据类型警告
        if (config.getSsmServiceConfig().getCredentialType() == CredentialType.TEMPORARY) {
            log.warn("Using TEMPORARY credential type. The SDK will NOT auto-refresh temporary credentials. "
                    + "Please ensure your application handles credential renewal before expiration.");
        }
        
        // 加载 MySQL 驱动
        try {
            Class.forName(MYSQL_DRIVER);
        } catch (ClassNotFoundException e) {
            throw new SsmRotationException(SsmRotationException.ERROR_DB_DRIVER, "MySQL driver not found", e);
        }

        // 初始化凭据监听回调执行器（默认单线程异步执行，避免阻塞 watcher）
        ExecutorService configuredExecutor = config.getCredentialChangeExecutor();
        if (configuredExecutor != null) {
            this.credentialChangeExecutor = configuredExecutor;
            this.ownsCredentialChangeExecutor = false;
        } else {
            String secretName = config.getDbConfig().getSecretName();
            this.credentialChangeExecutor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "SSM-Listener-" + secretName);
                t.setDaemon(true);
                return t;
            });
            this.ownsCredentialChangeExecutor = true;
        }

        // 初始获取凭据
        try {
            this.currentAccount = fetchAccount();
        } catch (SsmRotationException e) {
            // 构造函数失败，清理已创建的线程池资源
            closeCredentialChangeExecutorQuietly();
            throw e;
        }
        log.info("Initialized with account: {}", currentAccount.getUserName());

        // 启动凭据监控
        try {
            startWatcher();
        } catch (SsmRotationException e) {
            // 构造函数失败，清理已创建的线程池资源
            closeCredentialChangeExecutorQuietly();
            throw e;
        }
    }

    /**
     * 获取数据库连接
     *
     * <p><b>注意：</b>每次调用创建新物理连接，使用后务必调用 {@code close()} 释放。</p>
     * 
     * <p><b>自动刷新机制：</b>当连接因认证失败时（如凭据已轮转），SDK 会自动从 SSM 获取最新凭据并重试连接。
     * 多线程并发遇到认证失败时，只会发起一次 SSM API 刷新请求（避免惊群效应）。</p>
     *
     * @return 当前有效的数据库连接
     * @throws SQLException 如果连接创建失败
     */
    public Connection getConnection() throws SQLException {
        if (closed) {
            throw new SQLException("DynamicSecretRotationDb is closed");
        }
        
        // currentAccount 是 volatile 字段，直接读取即可保证可见性
        DbAccount account = this.currentAccount;
        
        try {
            return createConnection(account);
        } catch (SQLException e) {
            // 如果是认证错误，尝试刷新凭据后重试
            if (isAuthenticationError(e)) {
                log.warn("Connection failed due to authentication error (errorCode={}), refreshing credential...", 
                        e.getErrorCode());
                return retryWithRefreshedCredential(e, account);
            }
            throw e;
        }
    }
    
    /**
     * 刷新凭据后重试连接
     * 
     * <p>使用 synchronized + double-check 机制合并多线程的刷新请求：
     * 通过比较失败快照与当前凭据，判断是否已被其他线程刷新，避免重复尝试旧凭据连接。</p>
     */
    private Connection retryWithRefreshedCredential(SQLException originalException,
                                                    DbAccount failedAccountSnapshot) throws SQLException {
        DbAccount accountForRetry;
        DbAccount oldAccountForNotify = null;
        DbAccount newAccountForNotify = null;

        synchronized (credentialLock) {
            // Double-check: 检查凭据是否已被其他线程刷新（避免额外旧凭据连接尝试）
            DbAccount current = this.currentAccount;
            if (isCredentialChanged(failedAccountSnapshot, current)) {
                log.debug("Credential already refreshed by another thread, reusing current credential");
                accountForRetry = current;
            } else {
                try {
                    log.info("Refreshing credential from SSM...");
                    DbAccount newAccount = fetchAccount();

                    if (isCredentialChanged(current, newAccount)) {
                        log.info("Credential refreshed: {} -> {}, retrying connection...",
                                current.getUserName(), newAccount.getUserName());
                        oldAccountForNotify = this.currentAccount;
                        this.currentAccount = newAccount;
                        newAccountForNotify = newAccount;
                        accountForRetry = newAccount;
                    } else {
                        // 凭据没变化，说明不是凭据轮转问题，抛出原始异常
                        log.warn("Credential unchanged after refresh, authentication error may have other causes");
                        throw originalException;
                    }
                } catch (SsmRotationException e) {
                    log.error("Failed to refresh credential from SSM: {}", e.getMessage());
                    SQLException sqlEx = new SQLException(
                            "Authentication failed and credential refresh also failed: " + e.getMessage(),
                            originalException);
                    sqlEx.addSuppressed(e);
                    throw sqlEx;
                }
            }
        }

        if (oldAccountForNotify != null) {
            notifyCredentialChanged(oldAccountForNotify, newAccountForNotify);
        }

        // 在锁外执行数据库连接，避免网络 I/O 长时间持有 credentialLock
        return createConnection(accountForRetry);
    }
    
    /**
     * 使用指定凭据创建数据库连接
     */
    private Connection createConnection(DbAccount account) throws SQLException {
        DbConfig dbConfig = config.getDbConfig();

        Properties props = new Properties();
        props.setProperty("user", account.getUserName());
        props.setProperty("password", account.getPassword());
        props.setProperty("connectTimeout", String.valueOf(dbConfig.getConnectTimeoutSeconds() * 1000));
        props.setProperty("socketTimeout", String.valueOf(dbConfig.getSocketTimeoutSeconds() * 1000));

        return DriverManager.getConnection(buildJdbcUrl(), props);
    }
    
    /**
     * 判断是否是认证相关的错误
     * 
     * <p>仅通过 MySQL 错误码和精确的错误消息匹配来判断，避免误判。</p>
     * 
     * @param e SQL 异常
     * @return 如果是认证错误返回 true
     */
    private boolean isAuthenticationError(SQLException e) {
        int errorCode = e.getErrorCode();
        for (int authCode : AUTH_ERROR_CODES) {
            if (errorCode == authCode) {
                return true;
            }
        }
        
        // 仅在 errorCode 为 0 时检查消息（某些驱动可能不设置 errorCode）
        // 移除了过于宽泛的 "password" 匹配，避免误将密码过期等问题当做凭据轮转处理
        if (errorCode == 0) {
            String message = e.getMessage();
            if (message != null) {
                String lowerMsg = message.toLowerCase();
                return lowerMsg.contains("access denied")
                       || lowerMsg.contains("authentication failed");
            }
        }
        return false;
    }

    /**
     * 关闭连接工厂
     */
    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;

        if (watchFuture != null) {
            watchFuture.cancel(false);
        }

        if (scheduler != null && !scheduler.isShutdown()) {
            if (ownsWatchScheduler) {
                scheduler.shutdown();
                try {
                    if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                        scheduler.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    scheduler.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            } else {
                log.debug("Skip shutting down externally managed watchScheduler");
            }
        }

        if (ownsCredentialChangeExecutor && credentialChangeExecutor != null
                && !credentialChangeExecutor.isShutdown()) {
            credentialChangeExecutor.shutdown();
            try {
                if (!credentialChangeExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    credentialChangeExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                credentialChangeExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        log.info("DynamicSecretRotationDb closed");
    }

    // ==================== 健康检查 ====================

    /**
     * 检查服务是否健康
     * 
     * @return 健康返回 true
     */
    public boolean isHealthy() {
        return !closed && watchFailures.get() < MAX_WATCH_FAILURES;
    }

    /**
     * 获取健康检查详情
     *
     * @return 健康检查结果
     */
    public HealthCheckResult getHealthCheckResult() {
        return new HealthCheckResult(isHealthy(), closed, currentAccount != null ? currentAccount.getUserName() : null,
                watchFailures.get(), lastError);
    }

    /**
     * 获取当前用户名（用于监控轮转）
     *
     * @return 当前凭据用户名
     */
    public String getCurrentUser() {
        return currentAccount != null ? currentAccount.getUserName() : null;
    }

    /**
     * 获取当前数据库凭据（用户名和密码）
     * 
     * <p>可用于连接池等外部组件获取初始凭据。返回的是当前凭据的快照副本，
     * 后续凭据轮转不会影响已返回的对象。</p>
     *
     * @return 当前凭据的副本，包含用户名和密码；如果尚未初始化则返回 null
     * @since 1.0.0
     */
    public DbAccount getCurrentAccount() {
        DbAccount account = this.currentAccount;
        if (account == null) {
            return null;
        }
        // 返回副本，避免外部修改影响内部状态
        return new DbAccount(account.getUserName(), account.getPassword());
    }

    // ==================== 凭据变更监听器 ====================

    /**
     * 注册凭据变更监听器
     * 
     * <p>当 SSM 凭据发生轮转时，所有已注册的监听器会通过 credentialChangeExecutor 异步回调。
     * 默认使用单线程执行器；如注入自定义执行器，由业务方负责其并发与生命周期管理。</p>
     *
     * @param listener 凭据变更监听器，不能为 null
     * @throws IllegalArgumentException 如果 listener 为 null
     * @since 1.0.0
     */
    public void addCredentialChangeListener(CredentialChangeListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener cannot be null");
        }
        listeners.add(listener);
        log.debug("CredentialChangeListener added, total listeners: {}", listeners.size());
    }

    /**
     * 移除凭据变更监听器
     *
     * @param listener 要移除的监听器
     * @return 如果成功移除返回 true
     * @since 1.0.0
     */
    public boolean removeCredentialChangeListener(CredentialChangeListener listener) {
        boolean removed = listeners.remove(listener);
        if (removed) {
            log.debug("CredentialChangeListener removed, total listeners: {}", listeners.size());
        }
        return removed;
    }

    /**
     * 健康检查结果
     */
    @Data
    @AllArgsConstructor
    public static class HealthCheckResult {
        private boolean healthy;
        private boolean closed;
        private String currentUser;
        private int watchFailures;
        private String lastError;
    }

    // ==================== 公共工具方法 ====================

    /**
     * 构建 JDBC URL
     * 
     * <p>根据 {@link DbConfig} 中的配置参数构建完整的 MySQL JDBC URL。
     * 可用于连接池等外部组件配置数据源。</p>
     * 
     * @return JDBC URL，格式为 jdbc:mysql://host:port[/dbName][?params]
     * @since 1.0.0
     */
    public String buildJdbcUrl() {
        DbConfig db = config.getDbConfig();
        StringBuilder url = new StringBuilder(JDBC_URL_PREFIX)
                .append(db.getIpAddress()).append(":").append(db.getPort());
        if (db.getDbName() != null && !db.getDbName().isEmpty()) {
            url.append("/").append(db.getDbName());
        }
        if (db.getParamStr() != null && !db.getParamStr().isEmpty()) {
            url.append("?").append(db.getParamStr());
        }
        return url.toString();
    }

    // ==================== 私有方法 ====================

    private DbAccount fetchAccount() throws SsmRotationException {
        return ssmRequester.getCurrentAccount(
                config.getDbConfig().getSecretName()
        );
    }

    private void startWatcher() throws SsmRotationException {
        String secretName = config.getDbConfig().getSecretName();
        if (configuredWatchScheduler != null) {
            scheduler = configuredWatchScheduler;
            ownsWatchScheduler = false;
        } else {
            scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "SSM-Watcher-" + secretName);
                t.setDaemon(true);
                return t;
            });
            ownsWatchScheduler = true;
        }

        long interval = baseWatchIntervalMs;
        // 添加随机初始延时（0 到 interval 之间），避免多实例同时启动时造成 SSM API 请求风暴
        long initialDelay = ThreadLocalRandom.current().nextLong(interval);
        scheduleWatchOrThrow(initialDelay, interval);
        log.info("Watcher started for secret [{}], initialDelay: {} ms, interval: {} ms, customScheduler={}",
                secretName, initialDelay, interval, !ownsWatchScheduler);
    }

    private void watch() {
        if (closed) {
            return;
        }

        try {
            DbAccount newAccount = fetchAccount();
            DbAccount oldAccountForNotify = null;
            DbAccount newAccountForNotify = null;
            
            // 成功，重置失败计数
            int previousFailures = watchFailures.getAndSet(0);
            lastError = null;

            // 使用同步块保护凭据更新
            synchronized (credentialLock) {
                // 检测凭据是否变化（用户名或密码任一变化即更新）
                if (isCredentialChanged(currentAccount, newAccount)) {
                    log.info("Credential rotated: {} -> {}", currentAccount.getUserName(), newAccount.getUserName());
                    oldAccountForNotify = this.currentAccount;
                    this.currentAccount = newAccount;
                    newAccountForNotify = newAccount;
                }
            }

            if (oldAccountForNotify != null) {
                notifyCredentialChanged(oldAccountForNotify, newAccountForNotify);
            }

            // 如果之前有过退避，恢复正常调度间隔
            if (previousFailures > 0) {
                if (currentWatchIntervalMs > baseWatchIntervalMs) {
                    boolean resumed = rescheduleWatch(baseWatchIntervalMs, baseWatchIntervalMs, "watch-recovered");
                    if (resumed) {
                        log.info("Watch recovered after {} failures, resuming normal interval: {} ms",
                                previousFailures, baseWatchIntervalMs);
                    }
                } else {
                    log.info("Watch recovered after {} failures", previousFailures);
                }
            }
        } catch (Exception e) {
            int failures = watchFailures.incrementAndGet();
            lastError = e.getMessage();
            
            if (failures >= MAX_WATCH_FAILURES) {
                log.error("Watch failed {} times consecutively: {}. Consider checking SSM service availability.", 
                        failures, e.getMessage());
                
                // 应用指数退避：取消当前定时任务，以更长的间隔重新调度
                long backoffInterval = Math.min(
                        baseWatchIntervalMs * (1L << Math.min(failures - MAX_WATCH_FAILURES, 5)),
                        MAX_BACKOFF_INTERVAL_MS);
                if (backoffInterval > currentWatchIntervalMs) {
                    log.warn("Applying exponential backoff, next retry interval: {} ms", backoffInterval);
                    boolean backoffApplied = rescheduleWatch(backoffInterval, backoffInterval, "watch-failure-backoff");
                    if (!backoffApplied) {
                        log.error("Failed to apply watch backoff schedule, keep current watcher schedule");
                    }
                }
            } else {
                log.warn("Watch failed ({}/{}): {}", failures, MAX_WATCH_FAILURES, e.getMessage());
            }
        }
    }

    /**
     * 启动 Watcher 任务
     */
    private void scheduleWatchOrThrow(long initialDelayMs, long intervalMs) throws SsmRotationException {
        try {
            watchFuture = scheduler.scheduleWithFixedDelay(this::watch, initialDelayMs, intervalMs, TimeUnit.MILLISECONDS);
            currentWatchIntervalMs = intervalMs;
        } catch (RejectedExecutionException e) {
            throw new SsmRotationException(SsmRotationException.ERROR_CONFIG,
                    "Failed to start watcher: scheduler rejected task", e);
        }
    }

    /**
     * 重调度 Watcher（用于指数退避或恢复默认间隔）
     */
    private boolean rescheduleWatch(long initialDelayMs, long intervalMs, String reason) {
        synchronized (watchRescheduleLock) {
            if (closed || scheduler == null || scheduler.isShutdown()) {
                lastError = "Watcher reschedule skipped: scheduler is closed";
                log.error("Watcher reschedule skipped, reason={}, scheduler unavailable", reason);
                return false;
            }

            ScheduledFuture<?> previousFuture = watchFuture;
            final ScheduledFuture<?> newFuture;
            try {
                newFuture = scheduler.scheduleWithFixedDelay(this::watch, initialDelayMs, intervalMs,
                        TimeUnit.MILLISECONDS);
            } catch (RejectedExecutionException e) {
                lastError = "Watcher reschedule rejected: " + e.getMessage();
                log.error("Watcher reschedule rejected, reason={}, initialDelay={}ms, interval={}ms",
                        reason, initialDelayMs, intervalMs, e);
                return false;
            }

            watchFuture = newFuture;
            currentWatchIntervalMs = intervalMs;
            if (previousFuture != null) {
                previousFuture.cancel(false);
            }
            log.info("Watcher rescheduled, reason={}, initialDelay={}ms, interval={}ms",
                    reason, initialDelayMs, intervalMs);
            return true;
        }
    }

    /**
     * 通知所有监听器凭据已变更
     * 
     * @param oldAccount 旧凭据
     * @param newAccount 新凭据
     */
    private void notifyCredentialChanged(DbAccount oldAccount, DbAccount newAccount) {
        for (CredentialChangeListener listener : listeners) {
            Runnable callback = () -> {
                try {
                    listener.onCredentialChanged(oldAccount, newAccount);
                } catch (Exception e) {
                    log.warn("CredentialChangeListener callback failed: {}", e.getMessage(), e);
                }
            };

            try {
                credentialChangeExecutor.execute(callback);
            } catch (RejectedExecutionException e) {
                if (!closed) {
                    log.warn("CredentialChangeExecutor rejected callback, fallback to current thread: {}", e.getMessage());
                    callback.run();
                } else {
                    log.info("CredentialChangeExecutor rejected callback during shutdown, skipping listener notification");
                }
            }
        }
    }

    /**
     * 检测凭据是否变化（用户名或密码任一变化）
     */
    private boolean isCredentialChanged(DbAccount oldAccount, DbAccount newAccount) {
        if (oldAccount == null || newAccount == null) {
            return true;
        }
        // 用户名变化
        if (!newAccount.getUserName().equals(oldAccount.getUserName())) {
            return true;
        }
        // 密码变化（同一用户密码轮转场景）
        if (!newAccount.getPassword().equals(oldAccount.getPassword())) {
            log.info("Password rotated for user: {}", newAccount.getUserName());
            return true;
        }
        return false;
    }

    /**
     * 静默关闭 credentialChangeExecutor（用于构造函数失败时的资源清理）
     */
    private void closeCredentialChangeExecutorQuietly() {
        if (ownsCredentialChangeExecutor && credentialChangeExecutor != null
                && !credentialChangeExecutor.isShutdown()) {
            try {
                credentialChangeExecutor.shutdownNow();
            } catch (Exception ignored) {
            }
        }
    }
}
