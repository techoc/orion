# 网关 URI 清理功能测试文档

## 概述

本文档说明如何测试 URI 清理功能在网关中的运行。

## 测试文件

### 1. 单元测试

- `orion-gateway-core/src/test/java/cn/techoc/oriongateway/core/netty/handler/UriSanitizingHandlerTest.java`
- 已有的完整单元测试，覆盖基本功能

### 2. 网关层深度测试

- `orion-gateway-core/src/test/java/cn/techoc/oriongateway/core/integration/UriSanitizingHandlerGatewayDeepTest.java`
- 覆盖网关场景、边界场景、HTTP 方法、安全性测试

### 3. 真实 Netty 集成测试

- `orion-gateway-core/src/test/java/cn/techoc/oriongateway/core/integration/UriSanitizingHandlerNettyPipelineTest.java`
- 在真实 Netty Pipeline 中测试功能

### 4. 完整网关集成测试

- `orion-gateway-core/src/test/java/cn/techoc/oriongateway/core/integration/RealGatewayIntegrationTest.java`
- 启动真实 Netty 服务器并测试功能

### 5. 手动测试工具

- `orion-gateway-core/src/test/java/cn/techoc/oriongateway/core/test/GatewayTestServer.java`
- 独立的测试服务器，可手动测试
- `orion-gateway-core/src/test/java/cn/techoc/oriongateway/core/test/GatewayTestClient.java`
- 自动化测试客户端

## 功能说明

UriSanitizingHandler 用于清理 URI 中的非法字符，防止 URI 编码问题：

- 将 `|` 编码为 `%7C`
- 将 `{` 编码为 `%7B`
- 将 `}` 编码为 `%7D`
- 将 `[` 编码为 `%5B`
- 将 `]` 编码为 `%5D`
- 将 `\` 编码为 `%5C`
- 将 `^` 编码为 `%5E`
- 将 `` ` `` 编码为 `%60`

## 测试方法

### 方法一：使用 IDE 运行测试

1. 在 IDE 中打开项目
2. 运行任意测试类中的测试方法
3. 推荐使用 `UriSanitizingHandlerNettyPipelineTest` 或 `UriSanitizingHandlerTest`

### 方法二：使用 Maven 命令

```bash
# 编译项目
cd orion-gateway-core
mvn clean compile test-compile

# 运行单元测试（需要配置 Surefire）
mvn -o test
```

### 方法三：手动运行测试服务器

```bash
# 编译项目后，在 IDE 中运行
# 主类: cn.techoc.oriongateway.core.test.GatewayTestServer

# 或者使用命令行（需要配置 classpath）
java -cp <classpath> cn.techoc.oriongateway.core.test.GatewayTestServer
```

## 测试场景

### 1. 基本 URI 清理

| 原始 URI                  | 期望结果                     | 说明       |
|-------------------------|--------------------------|----------|
| `/test\|path`           | `/test%7Cpath`           | 单个 \| 字符 |
| `/api/user{id}`         | `/api/user%7Bid%7D`      | 路径参数     |
| `/search?q=test\|value` | `/search?q=test%7Cvalue` | 查询参数     |

### 2. 复杂场景

| 原始 URI                                  | 期望结果                                              |
|-----------------------------------------|---------------------------------------------------|
| `/test\|path{with}\|multiple[brackets]` | `/test%7Cpath%7Bwith%7D%7Cmultiple%5Bbrackets%5D` |
| `/path\|\|with\|\|multiple\|\|pipes`    | `/path%7C%7Cwith%7C%7Cmultiple%7C%7Cpipes`        |

### 3. 边界场景

- 正常 URI 保持不变
- 超长 URI
- 仅包含非法字符的 URI
- 空 URI

### 4. HTTP 方法

- GET, POST, PUT, DELETE, PATCH 等

## 项目集成

网关配置在：
`orion-gateway-starter/src/main/java/cn/techoc/oriongatewaystarter/GatewayNettyPipelineAutoConfiguration.java`

它通过 `NettyServerCustomizer` 将 UriSanitizingHandler 注册到 Spring Cloud Gateway 中。

## 集成测试检查清单

- [x] 单元测试通过
- [x] Netty Pipeline 测试通过
- [x] 网关场景测试覆盖
- [x] 边界场景测试覆盖
- [x] 安全性测试覆盖

## 故障排除

### 1. Maven 仓库问题

如果遇到 Maven 仓库问题：

- 使用离线模式：`mvn -o`
- 清理本地仓库缓存
- 检查网络连接

### 2. 端口占用

如果 8080/18080 端口被占用：

- 修改测试代码中的端口号
- 或者关闭占用端口的程序

## 测试总结

所有测试覆盖了以下方面：

1. 基本功能正确性
2. 边界场景处理
3. 真实网关集成
4. 性能和并发（可选）
5. 安全性考虑

功能已实现并测试完成。
