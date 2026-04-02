# SSM Rotation SDK for Java

[![Maven Central](https://img.shields.io/maven-central/v/com.tencentcloudapi/ssm-rotation-sdk-java)](https://search.maven.org/artifact/com.tencentcloudapi/ssm-rotation-sdk-java)
[![Java](https://img.shields.io/badge/Java-8%2B-blue)](https://www.oracle.com/java/)
[![License](https://img.shields.io/github/license/TencentCloud/ssm-rotation-sdk-java)](LICENSE)
[![CI](https://github.com/TencentCloud/ssm-rotation-sdk-java/actions/workflows/ci.yml/badge.svg)](https://github.com/TencentCloud/ssm-rotation-sdk-java/actions/workflows/ci.yml)

腾讯云凭据管理服务（SSM）轮转 SDK，支持数据库凭据自动轮转。

## 功能特性

- 自动从 SSM 获取数据库凭据
- 定期监控凭据变化，自动更新连接池
- 线程安全的连接池管理（支持 HikariCP / Druid / DBCP2）
- 支持多种凭据认证方式
- 健康检查 API
- 凭据轮转时旧连接池平滑过渡，降低高并发下连接中断风险
- 指数退避：Watcher 连续失败后自动增大轮询间隔，避免频繁请求 SSM 服务
- Spring Boot Auto-Configuration 支持（单数据源 / 多数据源）

## 认证方式

| 方式 | 工厂方法 | 说明 | 推荐 |
|------|----------|------|------|
| **CAM_ROLE** | `SsmAccount.withCamRole()` | CVM 实例角色（元数据服务自动获取临时凭据） | ✅ 推荐 |
| **TEMPORARY** | `SsmAccount.withTemporaryCredential()` | 临时 AK/SK/Token（需自行管理刷新） | ⚠️ 可选 |
| **PERMANENT** | `SsmAccount.withPermanentCredential()` | 固定 AK/SK（存在泄露风险） | ❌ 不推荐 |

> 使用 CAM 角色前需为 CVM 绑定 CAM 角色：[CVM 绑定角色](https://cloud.tencent.com/document/product/213/47668)

## 前置条件

1. 已在腾讯云平台开通了 SSM 服务（[开通 SSM 服务](https://console.cloud.tencent.com/ssm/cloud)）
2. 已在腾讯云平台购买了至少一台云数据库实例（目前只支持 MySQL 实例），完成了数据库的初始化，并创建了至少一个 database（[MySQL 控制台](https://console.cloud.tencent.com/cdb)）
3. 已在 SSM 控制台创建了一个[数据库凭据](https://cloud.tencent.com/document/product/1140/57647)，并和指定的数据库做了关联（[创建数据库凭据](https://cloud.tencent.com/document/product/1140/57648)）
4. 已在腾讯云平台的 [访问管理（CAM）控制台](https://console.cloud.tencent.com/cam/overview) 创建了能够访问 SSM 凭据资源和 MySQL 实例资源的子账号

## 支持的 Java 版本

Java 8 及以上版本（CI 在 JDK 8 / 11 / 17 / 21 上持续验证）

## 数据库支持范围

- 当前版本**仅支持 MySQL（`com.mysql:mysql-connector-j`）**
- 暂不支持 PostgreSQL、SQL Server、Oracle、MariaDB 等其他数据库驱动

## 快速开始

### 安装

**Maven**

```xml
<dependency>
    <groupId>com.tencentcloudapi</groupId>
    <artifactId>ssm-rotation-sdk-java</artifactId>
    <version>1.0.0</version>
</dependency>
```

**运行时必选依赖**

> SDK 将 MySQL 驱动、连接池、JSON 处理和日志门面依赖声明为 `provided`，业务侧必须显式引入。

| 使用方式 | 必选依赖 |
|---|---|
| 所有模式通用 | `com.google.code.gson:gson:2.10.1`, `org.slf4j:slf4j-api:1.7.36`（及日志实现如 logback） |
| 仅 `DynamicSecretRotationDb` | 通用依赖 + `com.mysql:mysql-connector-j:8.4.0` |
| HikariCP 封装 | 通用依赖 + `com.mysql:mysql-connector-j:8.4.0`, `com.zaxxer:HikariCP:4.0.3` |
| Druid 封装 | 通用依赖 + `com.mysql:mysql-connector-j:8.4.0`, `com.alibaba:druid:1.2.21` |
| DBCP2 封装 | 通用依赖 + `com.mysql:mysql-connector-j:8.4.0`, `org.apache.commons:commons-dbcp2:2.11.0` |

### 使用示例

```java
import com.tencentcloudapi.ssm.rotation.*;
import com.tencentcloudapi.ssm.rotation.datasource.SsmRotationHikariDataSource;

// 1. SSM 账号配置（三选一）

// 方式一：CVM 角色绑定（推荐）
SsmAccount ssmAccount = SsmAccount.withCamRole("your-role-name", "ap-guangzhou");

// 方式二：临时凭据
// SsmAccount ssmAccount = SsmAccount.withTemporaryCredential("tmpSecretId", "tmpSecretKey", "token", "ap-guangzhou");

// 方式三：固定凭据（不推荐）
// SsmAccount ssmAccount = SsmAccount.withPermanentCredential("secretId", "secretKey", "ap-guangzhou");

// 2. 数据库配置
DbConfig dbConfig = DbConfig.builder()
        .secretName("your-secret-name")
        .ipAddress("127.0.0.1")
        .port(3306)
        .dbName("your_database")
        .paramStr("useSSL=false&characterEncoding=utf8")
        .build();

// 3. 轮转配置
DynamicSecretRotationConfig config = DynamicSecretRotationConfig.builder()
        .dbConfig(dbConfig)
        .ssmServiceConfig(ssmAccount)
        .watchChangeIntervalMs(10000)  // 凭据监控间隔（毫秒），建议 10000-60000
        .build();

// 4. 创建数据源（以 HikariCP 为例）
SsmRotationHikariDataSource dataSource = SsmRotationHikariDataSource.builder()
        .rotationConfig(config)
        .maximumPoolSize(20)
        .minimumIdle(5)
        .build();

// 5. 业务代码无改造
try (Connection conn = dataSource.getConnection()) {
    // 执行 SQL
}

// 6. 健康检查
boolean healthy = dataSource.isHealthy();

// 7. 应用退出时关闭
dataSource.close();
```

## Spring Boot Auto-Configuration

SDK 提供 Spring Boot Auto-Configuration，无需手写 `@Configuration`：

1. 引入 SDK 依赖（保持 MySQL 驱动与连接池依赖由业务侧显式引入）
2. 配置 `ssm.rotation.*`
3. 自动注册 `DataSource` Bean（默认名 `dataSource`）

### 单数据源（最简配置）

```yaml
ssm:
  rotation:
    enabled: true
    credential:
      type: permanent
      region: ap-guangzhou
      secret-id: ${SSM_SECRET_ID:}
      secret-key: ${SSM_SECRET_KEY:}
    secret-name: your-secret-name
    db:
      ip: 127.0.0.1
      name: mydb
```

默认值：

1. `mode=single`
2. `pool-type=druid`
3. `db.port=3306`
4. `watch-interval-ms=10000`

### 单数据源（显式配置）

```yaml
ssm:
  rotation:
    enabled: true
    mode: single
    credential:
      type: cam_role
      role-name: ssm-role
      region: ap-guangzhou
    datasource:
      bean-name: dataSource
      pool-type: hikari
      secret-name: your-secret-name
      db:
        ip: 10.x.x.x
        port: 3306
        name: order_db
      hikari:
        maximum-pool-size: 20
        minimum-idle: 5
```

### 多数据源

```yaml
ssm:
  rotation:
    enabled: true
    mode: multi
    credential:
      type: permanent
      region: ap-guangzhou
      secret-id: ${SSM_SECRET_ID:}
      secret-key: ${SSM_SECRET_KEY:}
    datasources:
      order:
        primary: true
        pool-type: druid
        secret-name: ssm-order
        db:
          ip: 10.x.x.x
          name: order_db
      audit:
        primary: false
        pool-type: hikari
        secret-name: ssm-audit
        db:
          ip: 10.x.x.y
          name: audit_db
```

多数据源规则：

1. 必须且仅允许一个 `primary=true`
2. 每个数据源都必须提供自己的 `secret-name` 和 `db` 参数
3. 主数据源 Bean：`dataSource`（可直接给 ORM/JdbcTemplate 使用）
4. 全量数据源 Map Bean：`ssmRotationDataSources`（key 为 `datasources` 下的键名）

## 非 Spring 项目快速接入

```java
// 单数据源
DataSource ds = SsmRotationQuickStart.single()
        .region("ap-guangzhou")
        .permanentCredential(System.getenv("SSM_SECRET_ID"), System.getenv("SSM_SECRET_KEY"))
        .secretName("your-secret-name")
        .db("127.0.0.1", 3306, "mydb")
        .build();

// 多数据源
Map<String, DataSource> dsMap = SsmRotationQuickStart.multi()
        .add("order", b -> b.region("ap-guangzhou")
                .permanentCredential("id", "key")
                .secretName("ssm-order")
                .db("10.x.x.x", 3306, "order_db"))
        .add("audit", b -> b.region("ap-guangzhou")
                .permanentCredential("id", "key")
                .poolType("hikari")
                .secretName("ssm-audit")
                .db("10.x.x.y", 3306, "audit_db"))
        .build();
```

## 配置参数

### DbConfig（数据库配置）

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|-----|------|-----|--------|------|
| secretName | String | ✅ | - | SSM 凭据名称 |
| ipAddress | String | ✅ | - | 数据库 IP |
| port | int | ✅ | - | 数据库端口 |
| dbName | String | ❌ | - | 数据库名称 |
| paramStr | String | ❌ | - | 额外 JDBC 连接参数（如 `useSSL=false&characterEncoding=utf8`） |

### SsmAccount（SSM 账号配置）

| 参数 | 类型 | 必填 | 说明 |
|-----|------|-----|------|
| region | String | ✅ | 地域，如 ap-guangzhou |
| roleName | String | 条件 | 角色名称（CAM_ROLE 时必填） |
| secretId | String | 条件 | AK（PERMANENT/TEMPORARY 时必填） |
| secretKey | String | 条件 | SK（PERMANENT/TEMPORARY 时必填） |
| token | String | 条件 | 临时 Token（TEMPORARY 时必填） |
| url | String | ❌ | 自定义 SSM 接入点 |

### DynamicSecretRotationConfig（轮转配置）

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|-----|------|-----|--------|------|
| dbConfig | DbConfig | ✅ | - | 数据库配置 |
| ssmServiceConfig | SsmAccount | ✅ | - | SSM 账号配置 |
| watchChangeIntervalMs | long | ❌ | 10000 | 凭据监控间隔（毫秒），最小 1000，建议 10000-60000 |
| watchScheduler | ScheduledExecutorService | ❌ | SDK 内部单线程 | 自定义 watcher 调度线程池 |
| credentialChangeExecutor | Executor | ❌ | SDK 内部单线程 | 凭据变更监听器回调线程池 |

### 连接池模式

| 模式 | 推荐场景 | 关键参数 |
|---|---|---|
| `DynamicSecretRotationDb` | 低并发、批处理 | 无池化，`getConnection()` 每次新建物理连接 |
| `SsmRotationHikariDataSource` | 默认推荐 | `maximumPoolSize`、`minimumIdle`、`maxLifetime` |
| `SsmRotationDruidDataSource` | 依赖 Druid 生态 | `maxActive`、`minIdle`、`validationQuery` |
| `SsmRotationDbcpDataSource` | Apache 体系 | `maxTotal`、`minIdle`、`validationQuery` |

## 健康检查 API

```java
// 简单健康检查
boolean healthy = dataSource.isHealthy();

// 详细健康检查（DynamicSecretRotationDb）
HealthCheckResult result = rotationDb.getHealthCheckResult();
// result.isHealthy()       - 是否健康
// result.isClosed()        - 是否已关闭
// result.getCurrentUser()  - 当前凭据用户名
// result.getWatchFailures()- 监控失败次数
// result.getLastError()    - 最后一次错误信息
```

## 异常码

| 错误码 | 常量 | 说明 |
|---|---|---|
| `CONFIG_ERROR` | `ERROR_CONFIG` | 配置错误 |
| `SSM_ERROR` | `ERROR_SSM` | SSM API 调用或凭据解析失败 |
| `DB_DRIVER_ERROR` | `ERROR_DB_DRIVER` | MySQL 驱动缺失 |
| `CAM_ROLE_ERROR` | `ERROR_CAM_ROLE` | CAM 角色凭据获取失败 |
| `METADATA_TIMEOUT` | `ERROR_METADATA_TIMEOUT` | 元数据服务超时 |
| `METADATA_UNREACHABLE` | `ERROR_METADATA_UNREACHABLE` | 元数据服务不可达 |
| `METADATA_ERROR` | `ERROR_METADATA` | 元数据服务通用错误 |

## 注意事项

- `region` 必填
- 每次访问数据库请通过 `dataSource.getConnection()` 获取连接，使用完后务必 `close()` 归还
- **请勿缓存** `getConnection()` 返回的连接对象，凭据轮转后旧连接会失效
- 连接池模式下 `close()` 是将连接**归还到连接池**，而非销毁底层 TCP 连接
- 临时凭据有过期时间，SDK 不会自动刷新 TEMPORARY 类型凭据
- CAM_ROLE 方式通过元数据服务自动获取和刷新凭据，仅限 CVM 环境
- `CredentialChangeListener` 回调里避免执行阻塞任务，建议使用自定义执行器并限制任务耗时
- 连接池模式采用"平滑换凭据"策略，在途连接不会被强行中断，旧连接会在归还/淘汰后替换
- Watcher 启动时会自动增加随机初始延时，降低多实例同时访问 SSM 的请求尖峰

## 项目结构

```
ssm-rotation-sdk-java/
├── src/main/java/com/tencentcloudapi/ssm/rotation/
│   ├── DynamicSecretRotationDb.java          # 连接管理器（核心类）
│   ├── DynamicSecretRotationConfig.java      # 轮转配置
│   ├── DbConfig.java                         # 数据库配置
│   ├── SsmAccount.java                       # SSM 账号配置
│   ├── SsmRotationException.java             # 统一异常
│   ├── SsmRotationQuickStart.java            # 快速接入工具类
│   ├── datasource/                           # 连接池数据源封装
│   │   ├── SsmRotationHikariDataSource.java
│   │   ├── SsmRotationDruidDataSource.java
│   │   └── SsmRotationDbcpDataSource.java
│   └── spring/                               # Spring Boot Auto-Configuration
│       ├── SsmRotationAutoConfiguration.java
│       └── SsmRotationProperties.java
├── src/test/                                 # 单元测试
├── examples/                                 # 使用示例
│   └── springboot-demo/
├── .github/workflows/                        # CI/CD
│   └── ci.yml
├── CHANGELOG.md                              # 变更日志
├── CONTRIBUTING.md                           # 贡献指南
├── SECURITY.md                               # 安全策略
└── LICENSE                                   # Apache License 2.0
```

## License

Apache License 2.0
