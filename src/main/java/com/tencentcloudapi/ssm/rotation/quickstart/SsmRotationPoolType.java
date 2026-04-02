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

import java.util.Locale;

/**
 * SDK 支持的连接池类型
 */
public enum SsmRotationPoolType {
    DRUID,
    HIKARI,
    DBCP;

    /**
     * 大小写不敏感地解析连接池类型
     *
     * @param value 字符串值，例如 druid/hikari/dbcp
     * @return 对应的连接池类型
     */
    public static SsmRotationPoolType from(String value) {
        if (value == null) {
            throw new IllegalArgumentException("poolType cannot be null");
        }
        return SsmRotationPoolType.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
