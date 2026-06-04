# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

Orion Gateway 是一个基于 Spring Cloud Gateway 的多模块 Java 网关项目，提供 URI 清洗、访问日志、链路追踪和安全过滤功能。项目采用
Maven 构建，Java 11+。

## 模块结构

```
├── orion-gateway-core         # 核心功能模块
│   ├── filter/                # WebFilter 实现（WAF、URI 清洗标记等）
│   ├── logging/               # 日志相关功能
│   │   ├── access/            # 访问日志 WebFilter 和 GlobalFilter
│   │   └── enhance/           # Log4j2 动态配置增强
│   ├── netty/handler/         # Netty ChannelHandler（UriSanitizingHandler）
│   └── trace/                 # 链路追踪 GlobalFilter
├── orion-gateway-starter      # Spring Boot 自动配置模块
│   └── *AutoConfiguration.java  # 自动配置类
└── orion-gateway-simple       # 示例应用（集成测试用）
```

## 常用命令

### 构建项目

```bash
mvn clean package
mvn clean package -DskipTests
```

### 运行测试
```bash
mvn test                          # 运行所有测试
mvn test -pl orion-gateway-core   # 运行 core 模块测试
mvn test -Dtest=ClassName         # 运行指定测试类
mvn test -Dtest=ClassName#method  # 运行指定测试方法
```

### 代码格式化

项目使用 Spotless 插件和 Palantir Java 格式规范：

```bash
mvn spotless:apply   # 自动格式化代码
mvn spotless:check   # 检查格式是否符合规范
```

## 架构关键点

### 1. Netty Pipeline 集成

UriSanitizingHandler 作为 Netty ChannelHandler 插入到 HTTP 解码器之后：

```java
// 在 GatewayNettyPipelineAutoConfiguration 中注册
channel.pipeline().

addAfter(
    "reactor.left.httpCodec",  // HttpServerCodec 的名称
            "orion.handler",
    uriSanitizingHandler
    );
```

### 2. 三层过滤器架构
```
Netty Handler 层 (UriSanitizingHandler)
    ↓
WebFilter 层 (AccessLogWebFilter, UriSanitizingMarkerWebFilter, TestWebFilter)
    ↓
GlobalFilter 层 (SecurityWafFilter, AccessLongGlobalFilter, LinkTracingGlobalFilter)
```

### 3. 配置层次

- **orion.gateway.uri-sanitizing.*** - URI 清洗配置
- **orion.gateway.enable-netty-uri-sanitizing** - 启用 Netty 层 URI 清洗
- **orion.gateway.enable-gateway-access-log** - 启用访问日志
- **orion.logging.*** - Log4j2 动态配置
- **orion.security.waf.*** - WAF 安全过滤器配置

## 关键实现细节

### URI 清洗桥接机制

Netty Handler → WebFilter 通过动态生成的 Header 传递状态：

```java
// Constants.java 中定义
URI_SANITIZED_HEADER ="X-Orion-Uri-Sanitized-"+
随机后缀  // 每次 JVM 启动时生成
        URI_ORIGINAL_HEADER = "X-Orion-Uri-Original-" + 随机后缀
```

**重要**：这些 Header 在 WebFilter 层被消费并移除，不会泄漏到下游服务。

### 测试策略

- 单元测试：`MainTest`, `UriSanitizingHandlerTest`
- 集成测试：`RealGatewayIntegrationTest`, `UriSanitizingHandlerGatewayDeepTest`
- 测试配置：使用 `src/test/resources/application-test.yaml`
- 测试框架：JUnit 5 + Mockito + Reactor Test

## 配置示例

```yaml
orion:
  gateway:
    enable-netty-uri-sanitizing: true
    enable-gateway-access-log: false
    uri-sanitizing:
      enabled: true
      path-patterns: /api/**,/service/**
      charset: UTF-8
  security:
    waf:
      enabled: true
      inspect-body: true
```

## 依赖说明

核心依赖：

- Spring Cloud Gateway 2021.0.9
- Spring Boot 2.7.18
- LMAX Disruptor（异步日志）
- JSqlParser 4.6（SQL 语义分析）
- Lombok 1.18.30

注意：项目排除了默认的 Spring Boot Logging，改用 Log4j2。

## 开发注意事项

1. **线程安全**：所有 Handler 和 Filter 必须支持并发访问
2. **Pipeline 顺序**：Netty Handler 必须在 `reactor.left.httpCodec` 之后
3. **Header 隔离**：内部使用的 Header（X-Orion-*）必须在返回响应前移除
4. **配置优先级**：属性配置 > 代码默认值
5. **测试数据**：不要在测试代码中硬编码敏感数据
