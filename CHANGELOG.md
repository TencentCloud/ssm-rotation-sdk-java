# Changelog

本项目的所有重要变更将记录在此文件中。

格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/)，
本项目遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

## [1.0.0] - 2026-04-02

首次发布。

### 新增

- **凭据自动轮转**：自动从 SSM 获取数据库凭据，定期监控凭据变化并更新数据库连接
- **三种认证方式**：CAM_ROLE（推荐）、TEMPORARY、PERMANENT
- **连接池支持**：内置 HikariCP / Druid / DBCP2 三种连接池封装，线程安全管理
- **平滑换凭据**：凭据轮转时旧连接池平滑过渡，在途连接不会被强行中断
- **指数退避**：Watcher 连续失败后自动增大轮询间隔，避免频繁请求 SSM 服务
- **健康检查 API**：提供 `isHealthy()` 与 `getHealthCheckResult()` 接口
- **统一异常模型**：`SsmRotationException` 定义 7 个错误码常量，便于调用方分类处理
- **Spring Boot Auto-Configuration**：支持 `ssm.rotation.*` 配置驱动，单数据源最简接入与多数据源显式接入（Boot 2/3 兼容）
- **非 Spring 快速接入**：提供 `SsmRotationQuickStart` 工具类，支持不依赖 Spring 的单/多数据源快速构建
- **凭据变更回调**：支持 `CredentialChangeListener`，可注入自定义执行器
- **自定义调度器**：支持注入业务侧 `ScheduledExecutorService` 复用线程池
- **自定义 SSM 接入点**：支持配置自定义 endpoint
- **额外 JDBC 参数透传**：通过 `paramStr` 传递任意 JDBC 连接参数

### 测试

- **核心单测补齐**：新增 `DynamicSecretRotationDbTest`、`SsmRequesterTest`，覆盖重试刷新与 SSM 请求核心路径

### 构建与 CI

- **开源 CI 与发布准备**：新增 GitHub Actions CI；`pom.xml` 补充 Sonatype staging 与 GPG 签名（`release` profile）配置
- **JDK 兼容策略明确化**：构建约束调整为允许 JDK `8-21`，避免误用不在验证范围内的 JDK 导致编译链兼容问题
- **CI JDK 矩阵扩展**：从 `8/11` 扩展到 `8/11/17/21`，以"Java 8 运行兼容 + 多 LTS 构建验证"为开源基线

### 文档

- **必选依赖表**：README 新增 MySQL/Hikari/Druid/DBCP2 的业务侧必选 Maven 依赖与 scope 说明
- **线程池注入说明**：README 补充自定义 `watchScheduler` 与 `credentialChangeExecutor` 的配置示例
- **示例文档同步**：examples/README 移除 C3P0 场景并注明开源版支持范围
- **文档结构重写**：README 与 examples/README 重构为"最小接入 + 差异化参数 + 常见坑"结构，提升接入可读性
- **链接规范化**：README 本地绝对路径链接改为仓库相对路径
- **包级文档补齐**：新增各核心包 `package-info.java`