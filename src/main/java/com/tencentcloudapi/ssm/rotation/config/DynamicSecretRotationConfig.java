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
import com.tencentcloudapi.ssm.rotation.ssm.SsmAccount;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

/**
 * 动态凭据轮转配置类
 * 
 * @author tencentcloud
 * @since 1.0.0
 */
@Slf4j
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class DynamicSecretRotationConfig {

    /**
     * 数据库配置
     */
    private DbConfig dbConfig;

    /**
     * SSM 服务配置
     */
    private SsmAccount ssmServiceConfig;

    /**
     * 监控凭据变化的间隔时间（毫秒），默认 10 秒
     * 建议范围：1 秒 - 60 秒（超过 60 秒会告警提示）
     */
    @Builder.Default
    private long watchChangeIntervalMs = 10000;

    /**
     * 自定义 Watch 调度器（可选）。
     *
     * <p>默认情况下 SDK 会创建单线程调度器。如果业务已有统一线程池，
     * 可注入该字段复用线程资源。注入后由业务方负责生命周期管理。</p>
     *
     * @since 1.0.0
     */
    private ScheduledExecutorService watchScheduler;

    /**
     * 凭据变更回调执行器（可选）。
     *
     * <p>默认情况下 SDK 会创建单线程执行器异步执行监听器回调，
     * 避免阻塞 Watch 线程。注入后由业务方负责生命周期管理。</p>
     *
     * @since 1.0.0
     */
    private ExecutorService credentialChangeExecutor;

    /**
     * 验证配置参数的有效性
     *
     * @throws SsmRotationException 当配置参数无效时抛出
     */
    public void validate() throws SsmRotationException {
        if (dbConfig == null) {
            throw new SsmRotationException(SsmRotationException.ERROR_CONFIG, "dbConfig cannot be null");
        }
        dbConfig.validate();
        
        if (ssmServiceConfig == null) {
            throw new SsmRotationException(SsmRotationException.ERROR_CONFIG, "ssmServiceConfig cannot be null");
        }
        ssmServiceConfig.validate();
        
        if (watchChangeIntervalMs <= 0) {
            throw new SsmRotationException(SsmRotationException.ERROR_CONFIG,
                "watchChangeIntervalMs must be positive, got: "
                    + watchChangeIntervalMs);
        }
        if (watchChangeIntervalMs < 1000) {
            throw new SsmRotationException(SsmRotationException.ERROR_CONFIG,
                "watchChangeIntervalMs should be at least 1000ms"
                    + " to avoid excessive API calls");
        }
        if (watchChangeIntervalMs > 60000) {
            log.warn("watchChangeIntervalMs={}ms is larger than recommended upper bound 60000ms. "
                    + "Credential rotation detection may be delayed.", watchChangeIntervalMs);
        }

        if (watchScheduler != null && watchScheduler.isShutdown()) {
            throw new SsmRotationException(SsmRotationException.ERROR_CONFIG,
                "watchScheduler is already shutdown");
        }

        if (credentialChangeExecutor != null && credentialChangeExecutor.isShutdown()) {
            throw new SsmRotationException(SsmRotationException.ERROR_CONFIG,
                "credentialChangeExecutor is already shutdown");
        }
    }
}
