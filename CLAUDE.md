# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

Orion Gateway 是一个基于 Netty 的 Spring Cloud Gateway 实现，提供安全过滤、URI 清洗、访问日志和动态配置等功能。采用 Maven
多模块架构。

## 模块结构

- **orion-gateway-core**: 核心功能实现，包含所有处理器、过滤器和配置逻辑
- **orion-gateway-starter**: Spring Boot Starter，提供自动配置和依赖管理
- **orion-gateway-simple**: 简单示例应用

## 构建与测试

```bash
# 编译全部模块
./mvnw clean compile

# 打包
./mvnw clean package

# 运行全部测试
./mvnw test

# 运行单个模块的测试
./mvnw -pl orion-gateway-core test

# 运行单个测试类
./mvnw -pl orion-gateway-core test -Dtest=UriSanitizingHandlerTest

# 检查代码格式（Spotless）
./mvnw spotless:check

# 格式化代码
./mvnw spotless:apply
```

## 核心架构

### 请求处理流程

```
客户端请求
    ↓
HttpServerCodec (Netty HTTP 解码)
    ↓
UriSanitizingHandler (URI 非法字符清洗) ← 在此插入
    ↓
Spring Cloud Gateway 处理链
    ↓
SecurityWafFilter (WAF 安全检查)
    ↓
AccessLogFilter (访问日志)
    ↓
路由转发
```

### 主要组件

**Netty 处理器**

- `UriSanitizingHandler`: 在 Netty Pipeline 中清洗 URI 中的非法字符（`|`, `{`, `}`, `[`, `]`, `\`, `^`, `` ` ``），防止
  `URI.create()` 抛出异常
- 插入位置：`HttpServerCodec` 之后，Spring Cloud Gateway 处理链之前
- 通过 `GatewayNettyPipelineAutoConfiguration` 自动注册

**Web/Global 过滤器**

- `SecurityWafFilter`: Web 应用防火墙，支持 IP 黑白名单、速率限制、SQL 注入检测
- `AccessLogWebFilter` / `AccessLogGlobalFilter`: 访问日志记录
- `TestWebFilter`: 测试用过滤器
- `UriSanitizingMarkerWebFilter`: 标记已清洗的 URI

**日志系统**

- Log4j2 动态配置：运行时修改日志级别、添加 Appender
- 支持 Console、File、RandomAccess、Async Appender
- 异步日志优化（LMAX Disruptor）

**自动配置**

- `OrionGatewayAutoConfiguration`: 主配置，导入访问日志和链路追踪配置
- `GatewayNettyPipelineAutoConfiguration`: Netty Pipeline 自定义配置
- `GateWayAccessLogAutoConfiguration`: 访问日志配置
- `TraceLogAutoConfiguration`: 链路追踪日志配置
- `Log4j2DynamicAutoConfiguration`: 日志动态配置

## 技术栈

- Java 11
- Spring Boot 2.7.18
- Spring Cloud 2021.0.9
- Netty (通过 Reactor Netty)
- Log4j2 + LMAX Disruptor（异步日志）
- Lombok
- JSqlParser 4.6（SQL 语义分析）
- Jackson YAML

## 代码规范

- 使用 Palantir Java 格式（通过 Spotless 强制执行）
- 提交前运行 `./mvnw spotless:apply` 自动格式化
- Maven POM 使用 sortPom 排序

## 测试策略

### 单元测试

- JUnit 5 + Mockito
- 针对处理器和过滤器的独立测试

### 集成测试

- `UriSanitizingHandlerNettyPipelineTest`: 真实 Netty Pipeline 测试
- `RealGatewayIntegrationTest`: 完整网关集成测试
- `GatewayTestServer` / `GatewayTestClient`: 手动测试工具

### 测试场景

- 基本功能正确性
- 边界场景（空 URI、超长 URI、特殊字符组合）
- 并发性能
- 安全性验证（SQL 注入、XSS 等）

## 配置示例

### application.yml (网关应用)

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: example
          uri: http://example.com
          predicates:
            - Path=/api/**
```

### 自定义安全过滤器配置

```yaml
orion:
  security:
    waf:
      enabled: true
      inspect-body: true
      deny-ips:
        - 192.168.1.100
```

## 常见开发任务

**添加新的过滤器**

1. 在 `orion-gateway-core` 中创建过滤器类，实现 `GlobalFilter` 或 `WebFilter`
2. 在 starter 模块中添加自动配置
3. 编写单元测试和集成测试

**修改 URI 清洗规则**

1. 编辑 `UriSanitizingHandler.java` 中的 `ILLEGAL_CHAR_MAPPINGS`
2. 更新相应的单元测试
3. 验证 Netty Pipeline 集成

**调整日志配置**

1. 编辑 `Log4j2DynamicConfig.java` 支持的配置项
2. 通过 `Log4j2DynamicProperties` 进行外部化配置
3. 运行时可通过 API 动态修改

## 已知问题与限制

- Netty 严格验证需要关闭：`validateHeaders(false)`
- URI 清洗必须在 HTTP 解码器之后执行
- 速率限制使用内存实现，不支持集群模式

## 参考文档

- [UriSanitizingHandler 设计文档](./doc/UriSanitizingHandler-设计文档.md)
- [网关测试指南](./GATEWAY_TEST_GUIDE.md)
- [Spring Cloud Gateway 官方文档](https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway.html)
