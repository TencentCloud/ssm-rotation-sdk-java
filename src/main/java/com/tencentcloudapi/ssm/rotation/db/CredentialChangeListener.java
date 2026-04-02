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

import com.tencentcloudapi.ssm.rotation.ssm.DbAccount;

/**
 * 凭据变更监听器
 * 
 * <p>当 SSM 凭据发生轮转时触发回调，可用于连接池等外部组件的凭据联动更新。</p>
 * 
 * <p>使用示例：</p>
 * <pre>{@code
 * rotationDb.addCredentialChangeListener((oldAccount, newAccount) -> {
 *     // 更新连接池的用户名密码
 *     dataSource.setUsername(newAccount.getUserName());
 *     dataSource.setPassword(newAccount.getPassword());
 * });
 * }</pre>
 * 
 * @author tencentcloud
 * @since 1.0.0
 */
@FunctionalInterface
public interface CredentialChangeListener {

    /**
     * 凭据变更回调
     * 
     * <p><b>注意：</b>回调由 {@code DynamicSecretRotationDb} 的 credentialChangeExecutor 执行。
     * 默认是单线程异步执行器，也可由业务注入自定义执行器。回调实现应保证线程安全。</p>
     * 
     * @param oldAccount 旧凭据（轮转前）
     * @param newAccount 新凭据（轮转后）
     */
    void onCredentialChanged(DbAccount oldAccount, DbAccount newAccount);
}
