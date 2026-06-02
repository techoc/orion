# UriSanitizingHandler 设计文档

## 1. 概述

`UriSanitizingHandler` 是 Orion Gateway 中的一个 Netty 入站处理器，用于在 HTTP 请求解码阶段预处理 URI，将 URI 中的非法字符转换为
URL 编码形式，确保后续 `URI.create()` 等操作不会抛出 `IllegalArgumentException`。

**文件位置
**：[UriSanitizingHandler.java](file:///D:/dev/projects/IdeaProjects/Orion/orion-gateway-core/src/main/java/cn/techoc/oriongateway/core/netty/handler/UriSanitizingHandler.java)

## 2. 设计背景与问题分析

### 2.1 问题场景

在现代 Web 应用中，客户端请求的 URI 可能包含各种特殊字符，例如：

- 竖线 `|`
- 大括号 `{`、`}`
- 反斜杠 `\`
- 脱字符 `^`
- 方括号 `[`、`]`
- 反引号 `` ` ``

这些字符在某些场景下（如 REST API 路径参数、查询参数值等）可能被客户端使用，但在标准 URI 规范中需要进行编码。如果不预处理，直接使用
Java 的 `URI.create()` 方法解析会抛出异常。

### 2.2 技术挑战

- **Netty 处理时机**：需要在 Netty 的 HTTP 解码器之后、请求进入 Spring Cloud Gateway 处理链之前进行拦截
- **性能影响**：处理逻辑必须高效，不能成为网关的性能瓶颈
- **兼容性**：需要确保编码后的 URI 能够被下游服务正确解析

## 3. 类架构设计

### 3.1 类继承关系

```
ChannelInboundHandlerAdapter (Netty)
    └── UriSanitizingHandler (自定义实现)
```

### 3.2 核心成员

| 成员                      | 类型           | 说明               |
|-------------------------|--------------|------------------|
| `ILLEGAL_CHAR_MAPPINGS` | `String[][]` | 非法字符到 URL 编码的映射表 |

## 4. 核心实现分析

### 4.1 非法字符映射表

```java
private static final String[][] ILLEGAL_CHAR_MAPPINGS = {
    {"|", "%7C"},
    {"{", "%7B"},
    {"}", "%7D"},
    {"\\", "%5C"},
    {"^", "%5E"},
    {"[", "%5B"},
    {"]", "%5D"},
    {"`", "%60"},
};
```

**设计要点**：

- 采用二维数组存储映射关系，初始化时即确定
- 字符映射遵循 RFC 3986 URI 编码规范
- 数组顺序不影响最终结果，因为每次替换都是全量检查

### 4.2 channelRead 方法

```java
@Override
public void channelRead(ChannelHandlerContext ctx, Object msg) {
    if (msg instanceof HttpRequest) {
        HttpRequest request = (HttpRequest) msg;
        String uri = request.uri();
        String sanitized = sanitizeUri(uri);
        if (!uri.equals(sanitized)) {
            request.setUri(sanitized); // 替换 URI
        }
    }
    ctx.fireChannelRead(msg); // 传递给下一个 Handler
}
```

**处理流程**：

1. 检查消息是否为 `HttpRequest` 类型
2. 获取原始 URI
3. 调用 `sanitizeUri()` 进行清洗
4. 如果有变化，更新请求的 URI
5. 始终将消息传递给下一个处理器（保证处理链完整）

### 4.3 sanitizeUri 方法

```java
private String sanitizeUri(String uri) {
    String result = uri;
    for (String[] mapping : ILLEGAL_CHAR_MAPPINGS) {
        if (result.contains(mapping[0])) {
            result = result.replace(mapping[0], mapping[1]);
        }
    }
    return result;
}
```

**优化点**：

- 使用 `contains()` 先判断字符是否存在，避免不必要的 `replace()` 操作
- 依次替换所有非法字符

## 5. 集成配置

配置类：[GatewayNettyPipelineAutoConfiguration.java](file:///D:/dev/projects/IdeaProjects/Orion/orion-gateway-starter/src/main/java/cn/techoc/oriongatewaystarter/GatewayNettyPipelineAutoConfiguration.java)

### 5.1 配置方式

```java
@Configuration
public class GatewayNettyPipelineAutoConfiguration {
    @Bean
    public NettyServerCustomizer uriSanitizingCustomizer() {
        return httpServer -> httpServer
                // 先配置解码器放行
                .httpRequestDecoder(spec -> spec.validateHeaders(false))
                // 在 Pipeline 中注入 URI 预处理器
                .doOnChannelInit((observer, channel, remoteAddress) -> {
                    channel.pipeline()
                            .addAfter(
                                    "reactor.left.httpCodec", // HttpServerCodec 的名称
                                    "uriSanitizer", // 自定义 Handler 名称
                                    new UriSanitizingHandler());
                });
    }
}
```

### 5.2 Pipeline 位置

```
Inbound Processing Flow:
    ┌─────────────────┐
    │  Socket Read    │
    └────────┬────────┘
             │
    ┌────────▼────────┐
    │ HttpServerCodec │ ← Netty HTTP 解码器
    └────────┬────────┘
             │
    ┌────────▼────────┐
    │UriSanitizingHandler│ ← 在此处插入
    └────────┬────────┘
             │
    ┌────────▼────────┐
    │ 后续 Handler    │ ← Spring Cloud Gateway 处理链
    └─────────────────┘
```

**关键配置**：

- `validateHeaders(false)`：禁用 Netty 的严格头验证，允许包含非法字符的请求通过
- `addAfter("reactor.left.httpCodec", ...)`：在 HTTP 解码器之后插入

## 6. 使用场景

### 6.1 适用场景

- 客户端传递包含特殊字符的路径参数
- 网关需要兼容遗留系统的非标准 URI
- 需要避免 URI 解析异常导致请求失败

### 6.2 示例

**原始请求**：

```
GET /api/items/{id}|detail HTTP/1.1
Host: example.com
```

**处理后**：

```
GET /api/items/%7Bid%7D%7Cdetail HTTP/1.1
Host: example.com
```

## 7. 扩展性考虑

### 7.1 可扩展方向

1. **配置化映射**：将 `ILLEGAL_CHAR_MAPPINGS` 改为可配置的属性，支持外部化配置
2. **正则表达式匹配**：支持更复杂的模式匹配和替换
3. **白名单/黑名单机制**：允许配置需要保留或必须编码的字符
4. **性能优化**：对于大量请求，可以考虑使用预编译的模式或 StringBuilder 优化

### 7.2 注意事项

- 此 Handler 是 `@Sharable` 安全的吗？是的，因为它没有可变状态
- 线程安全：完全线程安全，可被多个 Channel 共享
- 顺序依赖：必须在 HTTP 解码器之后、业务处理之前插入

## 8. 测试建议

### 8.1 单元测试覆盖

- 测试所有非法字符的替换
- 测试无非法字符的情况（不修改）
- 测试混合非法字符的情况
- 测试 URI 各部分（路径、查询参数、片段）的处理

### 8.2 集成测试

- 验证在 Netty Pipeline 中的正确位置
- 验证与 Spring Cloud Gateway 的集成
- 性能基准测试

## 9. 相关文件

-
实现类：[UriSanitizingHandler.java](file:///D:/dev/projects/IdeaProjects/Orion/orion-gateway-core/src/main/java/cn/techoc/oriongateway/core/netty/handler/UriSanitizingHandler.java)
-
配置类：[GatewayNettyPipelineAutoConfiguration.java](file:///D:/dev/projects/IdeaProjects/Orion/orion-gateway-starter/src/main/java/cn/techoc/oriongatewaystarter/GatewayNettyPipelineAutoConfiguration.java)
