package cn.techoc.oriongateway.core.logging.access;

import cn.techoc.oriongateway.core.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Base64;

/**
 * 通用访问日志过滤器（Starter中自动生效）
 * <p>
 * 计算时间说明：
 * <ul>
 *   <li>总请求时间（REQUEST_TIME）：从网关接收到请求开始，到响应完全发送给客户端结束的总时间</li>
 *   <li>上游响应时间（UPSTREAM_RESPONSE_TIME）：从请求被转发到上游服务器开始，到上游服务器响应完成的时间</li>
 * </ul>
 * </p>
 */
@Component
public class AccessLogWebFilter implements WebFilter {

    // ==================== 常量定义 ====================

    private static final Logger DEFAULT_LOGGER = LoggerFactory.getLogger(AccessLogWebFilter.class);
    private static final String BASIC_AUTH_PREFIX = "Basic ";
    private static final String DEFAULT_VALUE = "-";
    private static final double UNAVAILABLE_TIME = -1.0;
    private static final String HEADER_DELIMITER = "; ";
    private static final String HEADER_KEY_VALUE_SEPARATOR = "=";
    private static final String TRUNCATED_SUFFIX = "...";

    // ==================== 字段声明 ====================

    private final AccessLogProperties properties;
    private final Logger logger;

    // ==================== 构造函数 ====================

    /**
     * 默认构造函数，使用默认配置和默认日志记录器
     */
    public AccessLogWebFilter() {
        this(new AccessLogProperties());
    }

    /**
     * 构造函数，使用指定配置和默认日志记录器
     *
     * @param properties 访问日志配置属性
     */
    public AccessLogWebFilter(AccessLogProperties properties) {
        this(properties, DEFAULT_LOGGER);
    }

    /**
     * 构造函数，使用指定配置和日志记录器
     *
     * @param properties 访问日志配置属性
     * @param logger     日志记录器（主要用于单元测试注入Mock Logger）
     */
    public AccessLogWebFilter(AccessLogProperties properties, Logger logger) {
        this.properties = properties;
        this.logger = logger;
    }

    // ==================== WebFilter 实现 ====================

    /**
     * 过滤器核心方法
     * <p>
     * 执行流程：
     * <ol>
     *   <li>检查日志是否启用，未启用则直接放行</li>
     *   <li>记录请求开始时间</li>
     *   <li>执行过滤器链</li>
     *   <li>在响应完成后记录访问日志</li>
     * </ol>
     * </p>
     *
     * @param exchange 服务交换器，包含请求和响应信息
     * @param chain    过滤器链
     * @return 异步处理结果
     */
    @Override
    @NonNull
    public Mono<Void> filter(@NonNull ServerWebExchange exchange, @NonNull WebFilterChain chain) {
        if (!properties.isEnabled()) {
            return chain.filter(exchange);
        }

        long requestStartTime = System.currentTimeMillis();
        return chain.filter(exchange).doFinally(signalType -> logAccess(exchange, requestStartTime));
    }

    // ==================== 日志记录核心逻辑 ====================

    /**
     * 记录访问日志
     * <p>
     * 该方法会捕获所有异常，确保日志记录失败不会影响主业务流程
     * </p>
     *
     * @param exchange         服务交换器
     * @param requestStartTime 请求开始时间（毫秒）
     */
    public void logAccess(ServerWebExchange exchange, long requestStartTime) {
        try {
            AccessLogContext context = buildLogContext(exchange, requestStartTime);
            java.util.EnumMap<AccessLogVariable, Object> variables = buildLogVariables(context);
            String logLine = AccessLogFormatter.format(properties.getPattern(), variables);
            logger.info(logLine);
        } catch (Exception e) {
            logger.error("Access log error", e);
        }
    }

    /**
     * 构建访问日志上下文
     * <p>
     * 收集并计算日志所需的所有信息，封装到上下文对象中
     * </p>
     *
     * @param exchange         服务交换器
     * @param requestStartTime 请求开始时间
     * @return 日志上下文对象
     */
    private AccessLogContext buildLogContext(ServerWebExchange exchange, long requestStartTime) {
        AccessLogContext context = new AccessLogContext();
        ServerHttpRequest request = exchange.getRequest();

        context.exchange = exchange;
        context.request = request;
        context.response = exchange.getResponse();
        context.requestStartTime = requestStartTime;
        context.requestEndTime = System.currentTimeMillis();

        // 提取客户端地址
        context.remoteAddress = extractRemoteAddress(request.getRemoteAddress());

        // 提取上游服务地址
        context.upstreamAddress = extractUpstreamAddress(exchange);

        // 计算时间指标
        context.totalRequestTime = calculateDuration(requestStartTime, context.requestEndTime);
        context.upstreamResponseTime = calculateUpstreamResponseTime(exchange);

        // 提取远程用户（从 Basic Auth）
        context.remoteUser = extractRemoteUser(request);

        return context;
    }

    /**
     * 构建日志变量映射表
     * <p>
     * 根据配置决定哪些变量需要被记录，未启用的变量值为 null
     * </p>
     *
     * @param context 日志上下文
     * @return 日志变量映射表
     */
    private java.util.EnumMap<AccessLogVariable, Object> buildLogVariables(AccessLogContext context) {
        java.util.EnumMap<AccessLogVariable, Object> variables = new java.util.EnumMap<>(AccessLogVariable.class);

        // 基础变量（始终记录）
        variables.put(AccessLogVariable.REMOTE_ADDR, context.remoteAddress);
        variables.put(AccessLogVariable.REMOTE_USER, context.remoteUser);
        variables.put(AccessLogVariable.TIME_LOCAL, AccessLogFormatter.now(properties.getZoneId()));
        variables.put(AccessLogVariable.REQUEST, buildRequestLine(context.request));
        variables.put(AccessLogVariable.STATUS, getStatusCode(context.response));
        variables.put(
                AccessLogVariable.BODY_BYTES_SENT, context.response.getHeaders().getContentLength());

        // 条件性记录的变量
        addOptionalVariables(variables, context);

        return variables;
    }

    /**
     * 添加可选的日志变量（根据配置决定是否记录）
     *
     * @param variables 变量映射表
     * @param context   日志上下文
     */
    private void addOptionalVariables(
            java.util.EnumMap<AccessLogVariable, Object> variables, AccessLogContext context) {
        // HTTP 请求头
        variables.put(
                AccessLogVariable.HTTP_REFERER,
                properties.isIncludeReferer() ? context.request.getHeaders().getFirst("Referer") : null);
        variables.put(
                AccessLogVariable.HTTP_USER_AGENT,
                properties.isIncludeUserAgent() ? context.request.getHeaders().getFirst("User-Agent") : null);
        variables.put(
                AccessLogVariable.HTTP_X_FORWARDED_FOR,
                properties.isIncludeXForwardedFor()
                        ? context.request.getHeaders().getFirst("X-Forwarded-For")
                        : null);

        // 上游地址
        variables.put(
                AccessLogVariable.UPSTREAM_ADDR, properties.isIncludeUpstreamAddr() ? context.upstreamAddress : null);

        // 时间指标
        if (properties.isIncludeTimes()) {
            variables.put(AccessLogVariable.UPSTREAM_RESPONSE_TIME, formatTime(context.upstreamResponseTime));
            variables.put(AccessLogVariable.REQUEST_TIME, formatTime(context.totalRequestTime));
        } else {
            variables.put(AccessLogVariable.UPSTREAM_RESPONSE_TIME, null);
            variables.put(AccessLogVariable.REQUEST_TIME, null);
        }

        // 完整的请求头/响应头
        variables.put(
                AccessLogVariable.REQ_HEADERS,
                properties.isIncludeRequestHeaders() ? formatHeaders(context.request.getHeaders()) : null);
        variables.put(
                AccessLogVariable.RESP_HEADERS,
                properties.isIncludeResponseHeaders() ? formatHeaders(context.response.getHeaders()) : null);
    }

    // ==================== 提取器方法 ====================

    /**
     * 提取客户端 IP 地址
     *
     * @param remoteAddress 远程地址套接字
     * @return IP 地址字符串，无法获取时返回 "-"
     */
    private String extractRemoteAddress(InetSocketAddress remoteAddress) {
        if (remoteAddress == null) {
            return DEFAULT_VALUE;
        }
        InetAddress address = remoteAddress.getAddress();
        return address != null ? address.getHostAddress() : DEFAULT_VALUE;
    }

    /**
     * 提取上游服务地址
     * <p>
     * 尝试将主机名解析为 IP 地址，失败则使用原始主机名
     * </p>
     *
     * @param exchange 服务交换器
     * @return 上游地址，格式为 "host:port" 或 "-"
     */
    private String extractUpstreamAddress(ServerWebExchange exchange) {
        URI routeUri = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR);

        if (routeUri == null) {
            return DEFAULT_VALUE;
        }

        String hostAddress = resolveHostAddress(routeUri.getHost());
        return hostAddress + ":" + routeUri.getPort();
    }

    /**
     * 解析主机名为 IP 地址
     *
     * @param hostname 主机名
     * @return IP 地址或原始主机名（解析失败时）
     */
    private String resolveHostAddress(String hostname) {
        try {
            return InetAddress.getByName(hostname).getHostAddress();
        } catch (Exception e) {
            // 解析失败时使用原始主机名
            return hostname;
        }
    }

    /**
     * 从 Basic Auth 头部提取用户名
     *
     * @param request HTTP 请求
     * @return 用户名，无法获取时返回 "-"
     */
    private String extractRemoteUser(ServerHttpRequest request) {
        if (!properties.isResolveRemoteUserFromBasicAuth()) {
            return DEFAULT_VALUE;
        }

        String authorization = request.getHeaders().getFirst("Authorization");
        if (authorization == null || !authorization.startsWith(BASIC_AUTH_PREFIX)) {
            return DEFAULT_VALUE;
        }

        try {
            String base64Credentials =
                    authorization.substring(BASIC_AUTH_PREFIX.length()).trim();
            String credentials = new String(Base64.getDecoder().decode(base64Credentials));
            String[] parts = credentials.split(":", 2);
            return parts.length > 0 ? parts[0] : DEFAULT_VALUE;
        } catch (Exception e) {
            // 解码或解析失败时使用默认值
            return DEFAULT_VALUE;
        }
    }

    /**
     * 从请求构建请求行（方法 + 路径）
     *
     * @param request HTTP 请求
     * @return 请求行字符串，如 "GET /api/users"
     */
    private String buildRequestLine(ServerHttpRequest request) {
        return request.getMethodValue() + " " + request.getURI().getRawPath();
    }

    // ==================== 时间计算相关方法 ====================

    /**
     * 计算持续时间（秒）
     *
     * @param startTimeMs 开始时间（毫秒）
     * @param endTimeMs   结束时间（毫秒）
     * @return 持续时间（秒）
     */
    private double calculateDuration(long startTimeMs, long endTimeMs) {
        return (endTimeMs - startTimeMs) / 1000.0;
    }

    /**
     * 计算上游响应时间
     * <p>
     * 从 Exchange 属性中获取上游服务开始和结束时间
     * </p>
     *
     * @param exchange 服务交换器
     * @return 上游响应时间（秒），无法获取时返回 -1.0
     */
    private double calculateUpstreamResponseTime(ServerWebExchange exchange) {
        Long startTime = exchange.getAttribute(Constants.UPSTREAM_START_TIME_ATTR);
        Long endTime = exchange.getAttribute(Constants.UPSTREAM_END_TIME_ATTR);

        if (startTime != null && endTime != null) {
            return calculateDuration(startTime, endTime);
        }
        return UNAVAILABLE_TIME;
    }

    /**
     * 格式化时间（秒，保留 3 位小数）
     *
     * @param seconds 秒数
     * @return 格式化后的时间字符串
     */
    private String formatTime(double seconds) {
        return String.format("%.3f", seconds);
    }

    // ==================== 其他辅助方法 ====================

    /**
     * 格式化 HTTP 头部为字符串
     * <p>
     * 格式：key1=value1,value2; key2=value3
     * 超过最大长度时会被截断
     * </p>
     *
     * @param headers HTTP 头部 Map
     * @return 格式化后的头部字符串
     */
    private String formatHeaders(java.util.Map<String, java.util.List<String>> headers) {
        String formatted = headers.entrySet().stream()
                .map(e -> e.getKey() + HEADER_KEY_VALUE_SEPARATOR + String.join(",", e.getValue()))
                .collect(java.util.stream.Collectors.joining(HEADER_DELIMITER));

        int maxLength = properties.getHeadersMaxLength();
        return formatted.length() > maxLength ? formatted.substring(0, maxLength) + TRUNCATED_SUFFIX : formatted;
    }

    /**
     * 获取 HTTP 响应状态码
     *
     * @param response HTTP 响应
     * @return 状态码，响应为空或无状态码时返回 0
     */
    private int getStatusCode(ServerHttpResponse response) {
        if (response == null || response.getStatusCode() == null) {
            return 0;
        }
        return response.getStatusCode().value();
    }

    // ==================== 内部类 ====================

    /**
     * 访问日志上下文
     * <p>
     * 封装日志记录所需的所有信息，避免方法参数过多
     * </p>
     */
    private static class AccessLogContext {
        /**
         * 服务交换器
         */
        ServerWebExchange exchange;
        /**
         * HTTP 请求
         */
        ServerHttpRequest request;
        /**
         * HTTP 响应
         */
        ServerHttpResponse response;
        /**
         * 请求开始时间（毫秒）
         */
        long requestStartTime;
        /**
         * 请求结束时间（毫秒）
         */
        long requestEndTime;
        /**
         * 客户端 IP 地址
         */
        String remoteAddress;
        /**
         * 上游服务地址
         */
        String upstreamAddress;
        /**
         * 总请求时间（秒）
         */
        double totalRequestTime;
        /**
         * 上游响应时间（秒）
         */
        double upstreamResponseTime;
        /**
         * 远程用户名（从 Basic Auth 提取）
         */
        String remoteUser;
    }
}
