# AGENTS.md

## 项目

Orion Gateway — 基于 Spring Cloud Gateway 的 Java 网关，Maven 多模块，Java 11。

## 必须执行的命令

```bash
# 构建（跳过测试快速验证）
./mvnw clean package -DskipTests

# 格式化 — 提交前必须运行
./mvnw spotless:apply
./mvnw spotless:check   # 验证格式

# 测试
./mvnw test
./mvnw test -pl orion-gateway-core
./mvnw test -Dtest=ClassName
./mvnw test -Dtest=ClassName#method
```

**注意**：使用 `./mvnw`（Maven Wrapper 3.9.11），不要依赖系统安装的 mvn。

## 模块边界

| 模块                    | 作用                                        | 入口                                     |
|-------------------------|---------------------------------------------|------------------------------------------|
| `orion-gateway-core`    | 核心功能（Filter、Handler、日志、链路追踪） | 按功能分散在各包                         |
| `orion-gateway-starter` | Spring Boot 自动配置                        | 5 个 `*AutoConfiguration.java`（见下方） |
| `orion-gateway-simple`  | 示例应用 / 集成测试                         | `SimpleGatewayApplication`               |

### Starter 自动配置类

修改或新增功能时，需在对应 AutoConfiguration 中注册：

- `GatewayNettyPipelineAutoConfiguration` — Netty Pipeline 注册
- `GateWayAccessLogAutoConfiguration` — 访问日志
- `Log4j2DynamicAutoConfiguration` — Log4j2 动态配置
- `TraceLogAutoConfiguration` — 链路追踪
- `OrionGatewayAutoConfiguration` — 主配置入口

## 三层过滤器架构

修改任何一层时，注意跨层数据流：

```
Netty Handler 层 (UriSanitizingHandler)
    ↓ 动态 Header (X-Orion-Uri-Sanitized-*)
WebFilter 层 (AccessLogWebFilter, UriSanitizingMarkerWebFilter)
    ↓
GlobalFilter 层 (SecurityWafFilter, AccessLongGlobalFilter, LinkTracingGlobalFilter)
```

**Header 隔离**：内部 Header 通过随机后缀生成（`Constants.java`），在 WebFilter 层消费并移除，不允许泄漏到下游。

## 代码风格

**Spotless + Palantir Java Format** 是强制规范。提交前必须运行 `./mvnw spotless:apply`。格式错误会阻塞 CI。

**Spotless ratchetFrom**：pom.xml 配置为 `origin/master`，格式检查只针对相对 master 的变更文件。新增文件必须格式化，未修改的文件不会被检查。

## 配置前缀速查

| 前缀                                        | 用途                   |
|---------------------------------------------|------------------------|
| `orion.gateway.uri-sanitizing.*`            | URI 清洗配置           |
| `orion.gateway.enable-netty-uri-sanitizing` | 启用 Netty 层 URI 清洗 |
| `orion.gateway.enable-gateway-access-log`   | 启用访问日志           |
| `orion.logging.*`                           | Log4j2 动态配置        |
| `orion.security.waf.*`                      | WAF 安全过滤器配置     |

## 关键约束

- Java 11（`.java-version`），不要用更高特性（如 var、switch 表达式、record）
- Spring Boot 2.7.18 / Spring Cloud 2021.0.9（不要升级到 3.x，需要 Java 17+）
- 日志用 Log4j2（已排除默认 Spring Boot Logging）
- Netty Handler 必须注册在 `reactor.left.httpCodec` 之后
- 所有 Handler/Filter 必须线程安全
- 测试中不要硬编码敏感数据

## 本地启动

`orion-gateway-simple` 已配置示例路由，可直接启动：

```bash
./mvnw spring-boot:run -pl orion-gateway-simple
```

默认配置：URI 清洗启用，访问日志关闭，日志级别 DEBUG。

## 集成测试

- 集成测试位于 `orion-gateway-core/src/test/java/.../integration/`
- 配置：`src/test/resources/application-test.yaml`（URI 清洗启用，路径模式 `/api/**`, `/service/**`）
- 框架：JUnit 5 + Mockito + Reactor Test
- 端到端验证：`RealGatewayIntegrationTest`, `UriSanitizingHandlerGatewayDeepTest`

## 日志与诊断

- 异步日志用 LMAX Disruptor
- Log4j2 动态配置增强模块：`orion-gateway-core/logging/enhance/`
- 日志目录：`logs/`（已在 .gitignore）

## 禁止事项

- 不要升级 Spring Boot 到 3.x（需要 Java 17+）
- 不要使用 Java 11+ 新特性（var、switch 表达式、record 等）
- 不要在 WebFilter 层之外消费内部 Header（X-Orion-*）
- 不要修改 `reactor.left.httpCodec` 之后的 Pipeline 顺序
- 不要在测试中硬编码密码、token 等敏感数据
