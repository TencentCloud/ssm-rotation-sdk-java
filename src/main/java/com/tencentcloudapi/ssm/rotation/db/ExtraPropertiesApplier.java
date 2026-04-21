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

import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * 扩展属性反射工具类
 *
 * <p>通过反射将扩展属性（extraProperties）应用到目标对象上，
 * 支持 Druid、HikariCP、DBCP 等连接池的原生参数设置。</p>
 *
 * <p>遍历 extraProperties，对每个 key 查找目标对象的 setter 方法并调用。
 * 支持常见类型的自动转换（String、int/Integer、long/Long、boolean/Boolean 等）。</p>
 *
 * @author tencentcloud
 * @since 1.0.0
 */
@Slf4j
public final class ExtraPropertiesApplier {

    private ExtraPropertiesApplier() {
        // 工具类，禁止实例化
    }

    /**
     * 通过反射将扩展属性应用到目标对象上
     *
     * @param target          目标对象（如 DruidDataSource、HikariConfig、BasicDataSource）
     * @param extraProperties 扩展属性 Map，key 为属性名（对应 setter 方法去掉 "set" 前缀后首字母小写），value 为属性值
     */
    public static void apply(Object target, Map<String, Object> extraProperties) {
        if (extraProperties == null || extraProperties.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : extraProperties.entrySet()) {
            String propertyName = entry.getKey();
            Object value = entry.getValue();
            if (propertyName == null || propertyName.isEmpty() || value == null) {
                continue;
            }
            String setterName = "set" + Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
            boolean applied = false;
            for (Method method : target.getClass().getMethods()) {
                if (method.getName().equals(setterName) && method.getParameterCount() == 1) {
                    try {
                        Class<?> paramType = method.getParameterTypes()[0];
                        Object convertedValue = convertValue(value, paramType);
                        method.invoke(target, convertedValue);
                        log.debug("Applied extra property: {}={}", propertyName, value);
                        applied = true;
                        break;
                    } catch (Exception e) {
                        log.warn("Failed to apply extra property '{}': {}", propertyName, e.getMessage());
                    }
                }
            }
            if (!applied) {
                log.warn("No matching setter found for extra property '{}', skipped", propertyName);
            }
        }
    }

    /**
     * 将值转换为目标类型
     *
     * @param value      原始值
     * @param targetType 目标类型
     * @return 转换后的值
     */
    private static Object convertValue(Object value, Class<?> targetType) {
        if (targetType.isInstance(value)) {
            return value;
        }
        String strValue = String.valueOf(value);
        if (targetType == int.class || targetType == Integer.class) {
            return Integer.parseInt(strValue);
        } else if (targetType == long.class || targetType == Long.class) {
            return Long.parseLong(strValue);
        } else if (targetType == boolean.class || targetType == Boolean.class) {
            return Boolean.parseBoolean(strValue);
        } else if (targetType == double.class || targetType == Double.class) {
            return Double.parseDouble(strValue);
        } else if (targetType == float.class || targetType == Float.class) {
            return Float.parseFloat(strValue);
        } else if (targetType == String.class) {
            return strValue;
        }
        return value;
    }
}
