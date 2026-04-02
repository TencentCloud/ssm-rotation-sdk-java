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

package com.tencentcloudapi.ssm.rotation.ssm;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

/**
 * 数据库账号信息类
 * 存储从 SSM 凭据中解析出的数据库用户名和密码
 * 
 * @author tencentcloud
 * @since 1.0.0
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class DbAccount {

    /**
     * 数据库用户名
     */
    @SerializedName("UserName")
    private String userName;

    /**
     * 数据库密码
     */
    @SerializedName("Password")
    private String password;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DbAccount that = (DbAccount) o;
        return Objects.equals(userName, that.userName)
                && Objects.equals(password, that.password);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userName, password);
    }

    /**
     * 自定义 toString 方法，对密码进行脱敏处理
     */
    @Override
    public String toString() {
        return "DbAccount{"
                + "userName='" + userName + '\''
                + ", password='****'"
                + '}';
    }
}
