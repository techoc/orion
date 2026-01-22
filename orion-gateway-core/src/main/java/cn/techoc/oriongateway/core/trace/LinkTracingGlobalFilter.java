package cn.techoc.oriongateway.core.trace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 全链路追踪过滤器，用于记录请求和响应的详细信息
 * <p>
 * 该过滤器实现了四个关键阶段的日志记录：
 * 1. Request_prefix - 上游请求进入网关时
 * 2. Request_suffix - 上游请求经过网关处理后要离开网关进入下游时
 * 3. Response_prefix - 下游响应进入网关时
 * 4. Response_suffix - 下游响应经过网关处理后离开网关时
 * <p>
 * 每个阶段都可以记录请求/响应的行信息和头部信息，并且支持记录请求体和响应体。
 * 通过 RaceRequestDecorator 和 RaceResponseDecorator 装饰器类，确保请求体和响应体可以被多次读取。
 */
public class LinkTracingGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(LinkTracingGlobalFilter.class);

    // 链路追踪配置属性
    private final LinkTracingProperties properties;

    /**
     * 构造函数
     *
     * @param properties 链路追踪配置属性
     */
    public LinkTracingGlobalFilter(LinkTracingProperties properties) {
        this.properties = properties;
    }

    /**
     * 过滤器核心方法，处理请求和响应的链路追踪
     * <p>
     * 执行流程：
     * 1. 检查是否启用链路追踪
     * 2. 记录上游请求进入网关时（Request_prefix）
     * 3. 包装请求对象以支持请求体多次读取
     * 4. 包装响应对象以支持响应体多次读取
     * 5. 继续执行过滤器链
     * 6. 在上游调用完成后记录上游请求离开网关进入下游时（Request_suffix）
     * 7. 记录下游响应进入网关时（Response_prefix）
     * 8. 记录下游响应离开网关时（Response_suffix）
     *
     * @param exchange ServerWebExchange 对象，包含请求和响应信息
     * @param chain    GatewayFilterChain 对象，用于继续执行过滤器链
     * @return Mono<Void> 异步处理结果
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 如果链路追踪未启用，直接继续过滤器链
        if (!properties.isEnabled()) {
            return chain.filter(exchange);
        }

        // 获取请求相关信息
        String requestId = exchange.getRequest().getId();
        HttpMethod method = exchange.getRequest().getMethod();
        URI uri = exchange.getRequest().getURI();

        // 存储请求上下文，用于后续日志记录
        LinkTracingContextHolder.setExchange(requestId, exchange);

        // Phase 1: Request_prefix - 上游请求进入网关时
        // 记录客户端发送到网关的请求信息
        logRequestPrefixPhase(requestId, method, uri, exchange);

        // 包装请求和响应对象
        ServerHttpRequest wrappedRequest = wrapRequest(requestId, exchange);
        ServerHttpResponse wrappedResponse = wrapResponse(requestId, exchange);

        // 继续执行过滤器链
        return chain.filter(exchange.mutate().request(wrappedRequest).response(wrappedResponse).build())
                .doOnEach(signal -> {
                    // 当上游调用完成时（成功或错误），记录后续阶段的日志
                    if (signal.isOnComplete() || signal.isOnError()) {
                        logPostProcessingPhases(requestId, method, uri, wrappedRequest, wrappedResponse);
                        // 清理上下文
                        LinkTracingContextHolder.removeExchange(requestId);
                        LinkTracingContextHolder.removeRequestBody(requestId);
                    }
                })
                .doOnError(throwable -> {
                    logError(requestId, throwable);
                    // 清理上下文
                    LinkTracingContextHolder.removeExchange(requestId);
                    LinkTracingContextHolder.removeRequestBody(requestId);
                });
    }

    /**
     * 记录上游请求进入网关时（Request_prefix）
     *
     * @param requestId 请求ID
     * @param method    HTTP方法
     * @param uri       请求URI
     * @param exchange  ServerWebExchange对象
     */
    private void logRequestPrefixPhase(String requestId, HttpMethod method, URI uri, ServerWebExchange exchange) {
        if (properties.isTraceRequestPrefix()) {
            // 启用请求体追踪时，交由请求体回调统一输出（包含方法/URI/参数/头/体）
            if (properties.isTraceRequestBody()) {
                return;
            }
            logPhase(requestId, "Request_prefix", method, uri, exchange.getRequest().getHeaders(), null);
        }
    }

    /**
     * 包装请求对象以支持请求体多次读取
     *
     * @param requestId 请求ID
     * @param exchange  ServerWebExchange对象
     * @return 包装后的ServerHttpRequest对象
     */
    private ServerHttpRequest wrapRequest(String requestId, ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        if (properties.isTraceRequestBody()) {
            // 确定在哪个阶段记录请求体日志
            final String bodyPhase = properties.isTraceRequestPrefix() ? "Request_prefix" : "Request_suffix";
            // 确定是否需要记录请求体日志
            final boolean shouldLogBody = properties.isTraceRequestPrefix() || properties.isTraceRequestSuffix();
            request = new LinkTracingRequestDecorator(
                    requestId,
                    request,
                    body -> logRequestBody(requestId, body, bodyPhase),
                    properties.getMaxBodySize(),
                    shouldLogBody,
                    exchange.getResponse().bufferFactory()
            );
        }
        return request;
    }

    /**
     * 包装响应对象以支持响应体多次读取
     *
     * @param requestId 请求ID
     * @param exchange  ServerWebExchange对象
     * @return 包装后的ServerHttpResponse对象
     */
    private ServerHttpResponse wrapResponse(String requestId, ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        ServerHttpRequest request = exchange.getRequest();
        HttpMethod method = request.getMethod();
        URI uri = request.getURI();

        if (properties.isTraceResponseBody()) {
            response = new LinkTracingResponseDecorator(
                    requestId,
                    response,
                    () -> logRequestSuffixPhase(requestId, method, uri, request), // Request_suffix 回调
                    body -> logResponseBody(requestId, body, "Response_prefix"), // 下游响应进入网关时日志回调
                    body -> logResponseBody(requestId, body, "Response_suffix"), // 下游响应离开网关时日志回调
                    properties.getMaxBodySize(), // 最大响应体大小限制
                    properties.isTraceResponsePrefix(),
                    properties.isTraceResponseSuffix()
            );
        } else {
            // 即使不追踪响应体，也需要确保 Request_suffix 被记录
            response = new LinkTracingResponseDecorator(
                    requestId,
                    response,
                    () -> logRequestSuffixPhase(requestId, method, uri, request), // Request_suffix 回调
                    null, // 不追踪响应体时不需要 Response_prefix 回调
                    null, // 不追踪响应体时不需要 Response_suffix 回调
                    0, // 不追踪响应体时不需要限制大小
                    false, // 不追踪 Response_prefix
                    false  // 不追踪 Response_suffix
            );
        }
        return response;
    }

    /**
     * 记录后处理阶段的日志
     *
     * @param requestId 请求ID
     * @param method    HTTP方法
     * @param uri       请求URI
     * @param request   包装后的请求对象
     * @param response  包装后的响应对象
     */
    private void logPostProcessingPhases(String requestId, HttpMethod method, URI uri,
                                         ServerHttpRequest request, ServerHttpResponse response) {
        // Phase 3: Response_prefix - 下游响应进入网关时
        logResponsePrefixPhase(requestId, method, uri, response);

        // Phase 4: Response_suffix - 下游响应经过网关处理后离开网关时
        logResponseSuffixPhase(requestId, method, uri, response);
    }

    /**
     * 记录上游请求经过网关处理后要离开网关进入下游时（Request_suffix）
     *
     * @param requestId 请求ID
     * @param method    HTTP方法
     * @param uri       请求URI
     * @param request   包装后的请求对象
     */
    private void logRequestSuffixPhase(String requestId, HttpMethod method, URI uri, ServerHttpRequest request) {
        if (properties.isTraceRequestSuffix()) {
            // 启用请求体追踪时，仅当也启用了前缀阶段时在后缀补打一遍体，避免后缀-only重复
            if (properties.isTraceRequestBody()) {
                if (properties.isTraceRequestPrefix()) {
                    String cachedBody = LinkTracingContextHolder.getRequestBody(requestId);
                    if (cachedBody != null) {
                        logRequestBody(requestId, cachedBody, "Request_suffix");
                        return;
                    }
                    // 无缓存体则回退到行与头部
                    logPhase(requestId, "Request_suffix", method, uri, request.getHeaders(), null);
                    return;
                }
                // 仅启用后缀体追踪时，体已由装饰器输出，这里避免重复输出
                return;
            }
            // 未启用体追踪时，常规输出行与头部
            logPhase(requestId, "Request_suffix", method, uri, request.getHeaders(), null);
        }
    }

    /**
     * 记录下游响应进入网关时（Response_prefix）
     *
     * @param requestId 请求ID
     * @param method    HTTP方法
     * @param uri       请求URI
     * @param response  包装后的响应对象
     */
    private void logResponsePrefixPhase(String requestId, HttpMethod method, URI uri, ServerHttpResponse response) {
        if (properties.isTraceResponsePrefix()) {
            // 启用响应体追踪时，由体日志统一输出，避免重复两行
            if (properties.isTraceResponseBody()) {
                return;
            }
            logPhase(requestId, "Response_prefix", method, uri, null, response);
        }
    }

    /**
     * 记录下游响应经过网关处理后离开网关时（Response_suffix）
     *
     * @param requestId 请求ID
     * @param method    HTTP方法
     * @param uri       请求URI
     * @param response  包装后的响应对象
     */
    private void logResponseSuffixPhase(String requestId, HttpMethod method, URI uri, ServerHttpResponse response) {
        if (properties.isTraceResponseSuffix()) {
            // 启用响应体追踪时，由体日志统一输出，避免重复两行
            if (properties.isTraceResponseBody()) {
                return;
            }
            logPhase(requestId, "Response_suffix", method, uri, null, response);
        }
    }

    /**
     * 记录指定阶段的日志信息
     *
     * @param requestId      请求ID，用于关联同一请求的所有日志
     * @param phase          阶段名称（Request Entry、Request Exit、Response Entry、Response Exit）
     * @param method         HTTP方法（GET、POST等）
     * @param uri            请求URI
     * @param requestHeaders 请求头部信息（用于请求阶段）
     * @param response       响应对象（用于响应阶段）
     */
    private void logPhase(String requestId, String phase, HttpMethod method, URI uri,
                          HttpHeaders requestHeaders, ServerHttpResponse response) {
        switch (phase) {
            case "Request_prefix":
            case "Request_suffix":
                // 记录请求阶段的日志（包含请求行和请求头部）
                logRequestPhase(requestId, phase, method, uri, requestHeaders);
                break;
            case "Response_prefix":
            case "Response_suffix":
                // 记录响应阶段的日志（包含状态行和响应头部）
                logResponsePhase(requestId, phase, method, uri, response);
                break;
            default:
                // 不应该到达这里
                log.warn("[{}] Unknown phase: {}", requestId, phase);
                break;
        }
    }

    /**
     * 记录请求阶段的日志
     *
     * @param requestId 请求ID
     * @param phase     阶段名称
     * @param method    HTTP方法
     * @param uri       请求URI
     * @param headers   请求头部信息
     */
    private void logRequestPhase(String requestId, String phase, HttpMethod method, URI uri, HttpHeaders headers) {
        StringBuilder logMessage = new StringBuilder();
        logMessage.append(String.format("[%s] --- %s --- %s %s", requestId, phase, method, uri));

        // 添加请求参数
        if (properties.isTraceRequestParams() && uri.getQuery() != null && !uri.getQuery().isEmpty()) {
            logMessage.append(String.format(" | Params: %s", uri.getQuery()));
        }

        // 添加请求头
        if (headers != null && !headers.isEmpty()) {
            Set<String> excludeHeaders = properties.getExcludeHeaders();
            String headerString = headers.entrySet().stream()
                    .filter(entry -> !excludeHeaders.contains(entry.getKey().toLowerCase()))
                    .map(entry -> entry.getKey() + "=" + String.join(",", entry.getValue()))
                    .collect(Collectors.joining("; "));
            logMessage.append(String.format(" | Headers: {%s}", headerString));
        }

        if (properties.isGroupLogsByPhase()) {
            logPhaseInfo(requestId, phase, () -> log.info(logMessage.toString()));
        } else {
            log.info(logMessage.toString());
        }
    }

    /**
     * 记录响应阶段的日志
     *
     * @param requestId 请求ID
     * @param phase     阶段名称
     * @param method    HTTP方法
     * @param uri       请求URI
     * @param response  响应对象
     */
    private void logResponsePhase(String requestId, String phase, HttpMethod method, URI uri, ServerHttpResponse response) {
        StringBuilder logMessage = new StringBuilder();
        logMessage.append(String.format("[%s] --- %s --- %s %s | Status: %s",
                requestId, phase, method, uri, response.getStatusCode()));

        // 添加请求参数（用于响应阶段也展示请求的查询参数）
        if (uri.getQuery() != null && !uri.getQuery().isEmpty()) {
            if (properties.isTraceRequestParams()) {
                logMessage.append(String.format(" | Params: %s", uri.getQuery()));
            } else {
                // 即使未显式启用参数追踪，为保持响应日志的完整性也可输出
                logMessage.append(String.format(" | Params: %s", uri.getQuery()));
            }
        }

        // 添加响应头
        response.getHeaders();
        if (!response.getHeaders().isEmpty()) {
            Set<String> excludeHeaders = properties.getExcludeHeaders();
            String headerString = response.getHeaders().entrySet().stream()
                    .filter(entry -> !excludeHeaders.contains(entry.getKey().toLowerCase()))
                    .map(entry -> entry.getKey() + "=" + String.join(",", entry.getValue()))
                    .collect(Collectors.joining("; "));
            logMessage.append(String.format(" | Headers: {%s}", headerString));
        }

        if (properties.isGroupLogsByPhase()) {
            logPhaseInfo(requestId, phase, () -> log.info(logMessage.toString()));
        } else {
            log.info(logMessage.toString());
        }
    }

    // 这些方法已被整合到 logRequestPhase 和 logResponsePhase 中，不再需要单独的方法

    /**
     * 记录请求体日志
     *
     * @param requestId 请求ID
     * @param body      请求体内容
     * @param phase     当前阶段名称
     */
    private void logRequestBody(String requestId, String body, String phase) {
        // 如果未启用请求体追踪，直接返回
        if (!properties.isTraceRequestBody()) return;

        // 获取请求信息
        ServerWebExchange exchange = LinkTracingContextHolder.getExchange(requestId);
        if (exchange == null) {
            // 如果找不到对应的请求信息，使用原来的日志格式
            if (body == null || body.trim().isEmpty()) {
                log.info("[{}] {} Body: [Empty Body]", requestId, phase);
            } else {
                log.info("[{}] {} Body: {}", requestId, phase, body);
            }
            return;
        }

        // 获取请求信息
        HttpMethod method = exchange.getRequest().getMethod();
        URI uri = exchange.getRequest().getURI();
        HttpHeaders headers = exchange.getRequest().getHeaders();

        // 构建完整日志
        StringBuilder logMessage = new StringBuilder();
        logMessage.append(String.format("[%s] --- %s --- %s %s", requestId, phase, method, uri));

        // 添加请求参数
        if (properties.isTraceRequestParams() && uri.getQuery() != null && !uri.getQuery().isEmpty()) {
            logMessage.append(String.format(" | Params: %s", uri.getQuery()));
        } else if (uri.getQuery() != null && !uri.getQuery().isEmpty()) {
            // 即使没有显式启用参数追踪，也输出参数信息以保持日志完整性
            logMessage.append(String.format(" | Params: %s", uri.getQuery()));
        }

        // 添加请求头
        if (properties.isTraceRequestHeaders() && !headers.isEmpty()) {
            Set<String> excludeHeaders = properties.getExcludeHeaders();
            String headerString = headers.entrySet().stream()
                    .filter(entry -> !excludeHeaders.contains(entry.getKey().toLowerCase()))
                    .map(entry -> entry.getKey() + "=" + String.join(",", entry.getValue()))
                    .collect(Collectors.joining("; "));
            logMessage.append(String.format(" | Headers: {%s}", headerString));
        } else if (!headers.isEmpty()) {
            // 即使没有显式启用头部追踪，也输出头部信息以保持日志完整性
            Set<String> excludeHeaders = properties.getExcludeHeaders();
            String headerString = headers.entrySet().stream()
                    .filter(entry -> !excludeHeaders.contains(entry.getKey().toLowerCase()))
                    .map(entry -> entry.getKey() + "=" + String.join(",", entry.getValue()))
                    .collect(Collectors.joining("; "));
            logMessage.append(String.format(" | Headers: {%s}", headerString));
        }

        // 添加请求体
        if (body != null && !body.trim().isEmpty()) {
            // 将所有换行符替换为空格，确保输出在一行中
            body = body.replaceAll("\\r?\\n", "");
            logMessage.append(String.format(" | Body: %s", body));
        } else {
            logMessage.append(" | Body: [Empty Body]");
        }

        // 缓存请求体用于后续阶段（例如 Request_suffix）统一输出
        if (body != null) {
            LinkTracingContextHolder.setRequestBody(requestId, body);
        }

        log.info(logMessage.toString());
    }

    /**
     * 记录响应体日志
     *
     * @param requestId 请求ID
     * @param body      响应体内容
     * @param phase     当前阶段名称
     */
    private void logResponseBody(String requestId, String body, String phase) {
        // 如果未启用响应体追踪，直接返回
        if (!properties.isTraceResponseBody()) return;

        // 获取请求信息
        ServerWebExchange exchange = LinkTracingContextHolder.getExchange(requestId);
        if (exchange == null) {
            // 如果找不到对应的请求信息，使用原来的日志格式
            if (body == null || body.trim().isEmpty()) {
                body = body.replaceAll("\\r?\\n", "");
                log.info("[{}] {} Body: [Empty Body]", requestId, phase);
            } else {
                log.info("[{}] {} Body: {}", requestId, phase, body);
            }
            return;
        }

        // 获取请求和响应信息
        HttpMethod method = exchange.getRequest().getMethod();
        URI uri = exchange.getRequest().getURI();
        HttpHeaders responseHeaders = exchange.getResponse().getHeaders();

        // 构建完整日志
        StringBuilder logMessage = new StringBuilder();
        logMessage.append(String.format("[%s] --- %s --- %s %s", requestId, phase, method, uri));

        // 响应阶段也添加请求参数信息
        if (uri.getQuery() != null && !uri.getQuery().isEmpty()) {
            if (properties.isTraceRequestParams()) {
                logMessage.append(String.format(" | Params: %s", uri.getQuery()));
            } else {
                logMessage.append(String.format(" | Params: %s", uri.getQuery()));
            }
        }

        // 添加响应头
        if (properties.isTraceResponseHeaders() && !responseHeaders.isEmpty()) {
            Set<String> excludeHeaders = properties.getExcludeHeaders();
            String headerString = responseHeaders.entrySet().stream()
                    .filter(entry -> !excludeHeaders.contains(entry.getKey().toLowerCase()))
                    .map(entry -> entry.getKey() + "=" + String.join(",", entry.getValue()))
                    .collect(Collectors.joining("; "));
            logMessage.append(String.format(" | Headers: {%s}", headerString));
        } else if (!responseHeaders.isEmpty()) {
            // 即使没有显式启用头部追踪，也输出头部信息以保持日志完整性
            Set<String> excludeHeaders = properties.getExcludeHeaders();
            String headerString = responseHeaders.entrySet().stream()
                    .filter(entry -> !excludeHeaders.contains(entry.getKey().toLowerCase()))
                    .map(entry -> entry.getKey() + "=" + String.join(",", entry.getValue()))
                    .collect(Collectors.joining("; "));
            logMessage.append(String.format(" | Headers: {%s}", headerString));
        }

        // 添加响应体
        if (body != null && !body.trim().isEmpty()) {
            logMessage.append(String.format(" | Body: %s", body));
        } else {
            logMessage.append(" | Body: [Empty Body]");
        }

        log.info(logMessage.toString());
    }

    /**
     * 记录阶段信息，如果启用了分组日志，则在阶段开始和结束时添加标记
     *
     * @param requestId     请求ID
     * @param phase         阶段名称
     * @param loggingAction 实际的日志记录操作
     */
    private void logPhaseInfo(String requestId, String phase, Runnable loggingAction) {
        if (properties.isGroupLogsByPhase()) {
            log.info("[{}] --- BEGIN {} ---", requestId, phase);
            loggingAction.run();
            log.info("[{}] --- END {} ---", requestId, phase);
        } else {
            loggingAction.run();
        }
    }

    /**
     * 记录错误日志
     *
     * @param requestId 请求ID
     * @param throwable 异常对象
     */
    private void logError(String requestId, Throwable throwable) {
        log.error("[{}] Error during request processing: {}", requestId, throwable.getMessage(), throwable);
    }

    /**
     * 返回过滤器的执行顺序
     *
     * @return 过滤器顺序，HIGHEST_PRECEDENCE 表示最高优先级
     */
    @Override
    public int getOrder() {
        // 尽早运行此过滤器，确保在其他可能消费请求体的过滤器之前包装请求/响应
        // 但在路径路由之后执行
        return Ordered.HIGHEST_PRECEDENCE;
    }
}