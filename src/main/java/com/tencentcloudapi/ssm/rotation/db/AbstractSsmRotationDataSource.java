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

import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;

/**
 * SSM 轮转 DataSource 的公共基类
 *
 * <p>封装公共行为：DataSource 方法委托、健康检查、监听器解绑和资源关闭。</p>
 *
 * @param <T> 底层连接池类型
 * @since 1.0.0
 */
public abstract class AbstractSsmRotationDataSource<T extends DataSource> implements DataSource, AutoCloseable {

    private final org.slf4j.Logger log = LoggerFactory.getLogger(getClass());

    private final DynamicSecretRotationDb rotationDb;
    private final T delegateDataSource;
    private final CredentialChangeListener credentialChangeListener;
    private final String delegateName;

    protected AbstractSsmRotationDataSource(DynamicSecretRotationDb rotationDb,
                                            T delegateDataSource,
                                            CredentialChangeListener credentialChangeListener,
                                            String delegateName) {
        this.rotationDb = rotationDb;
        this.delegateDataSource = delegateDataSource;
        this.credentialChangeListener = credentialChangeListener;
        this.delegateName = delegateName;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return delegateDataSource.getConnection();
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return delegateDataSource.getConnection(username, password);
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return delegateDataSource.getLogWriter();
    }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {
        delegateDataSource.setLogWriter(out);
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
        delegateDataSource.setLoginTimeout(seconds);
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return delegateDataSource.getLoginTimeout();
    }

    @Override
    public java.util.logging.Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return delegateDataSource.getParentLogger();
    }

    @Override
    public <K> K unwrap(Class<K> iface) throws SQLException {
        if (iface.isInstance(this)) {
            return iface.cast(this);
        }
        return delegateDataSource.unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return iface.isInstance(this) || delegateDataSource.isWrapperFor(iface);
    }

    public boolean isHealthy() {
        return rotationDb.isHealthy() && isDelegateHealthy(delegateDataSource);
    }

    public DynamicSecretRotationDb.HealthCheckResult getHealthCheckResult() {
        return rotationDb.getHealthCheckResult();
    }

    public DynamicSecretRotationDb getRotationDb() {
        return rotationDb;
    }

    protected T getDelegateDataSource() {
        return delegateDataSource;
    }

    protected boolean isDelegateHealthy(T delegateDataSource) {
        return true;
    }

    @Override
    public void close() {
        log.info("Closing {}...", getClass().getSimpleName());

        if (credentialChangeListener != null) {
            rotationDb.removeCredentialChangeListener(credentialChangeListener);
        }

        try {
            closeDelegate(delegateDataSource);
            log.info("{} closed", delegateName);
        } catch (Exception e) {
            log.warn("Error closing {}: {}", delegateName, e.getMessage(), e);
        }

        try {
            rotationDb.close();
            log.info("DynamicSecretRotationDb closed");
        } catch (Exception e) {
            log.warn("Error closing DynamicSecretRotationDb: {}", e.getMessage(), e);
        }

        log.info("{} closed", getClass().getSimpleName());
    }

    protected void closeDelegate(T delegateDataSource) throws Exception {
        if (delegateDataSource instanceof AutoCloseable) {
            ((AutoCloseable) delegateDataSource).close();
        }
    }
}
