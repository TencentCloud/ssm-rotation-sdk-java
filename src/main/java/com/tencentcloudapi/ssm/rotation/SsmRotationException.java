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

package com.tencentcloudapi.ssm.rotation;

import lombok.Getter;

/**
 * SSM 轮转 SDK 统一异常类
 * 
 * @author tencentcloud
 * @since 1.0.0
 */
@Getter
public class SsmRotationException extends Exception {

    private static final long serialVersionUID = 1L;

    // ==================== 错误码常量 ====================

    /** 配置相关错误 */
    public static final String ERROR_CONFIG = "CONFIG_ERROR";

    /** SSM 服务调用错误 */
    public static final String ERROR_SSM = "SSM_ERROR";

    /** 数据库驱动错误 */
    public static final String ERROR_DB_DRIVER = "DB_DRIVER_ERROR";

    /** CAM 角色凭据错误 */
    public static final String ERROR_CAM_ROLE = "CAM_ROLE_ERROR";

    /** 元数据服务超时 */
    public static final String ERROR_METADATA_TIMEOUT = "METADATA_TIMEOUT";

    /** 元数据服务不可达 */
    public static final String ERROR_METADATA_UNREACHABLE = "METADATA_UNREACHABLE";

    /** 元数据服务通用错误 */
    public static final String ERROR_METADATA = "METADATA_ERROR";

    /**
     * 错误码
     */
    private final String errorCode;

    public SsmRotationException(String message) {
        super(message);
        this.errorCode = null;
    }

    public SsmRotationException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = null;
    }

    public SsmRotationException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public SsmRotationException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    @Override
    public String toString() {
        if (errorCode != null) {
            return "SsmRotationException{errorCode='" + errorCode + "', message='" + getMessage() + "'}";
        }
        return super.toString();
    }
}
