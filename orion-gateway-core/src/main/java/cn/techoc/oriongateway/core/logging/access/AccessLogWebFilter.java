package cn.techoc.oriongateway.core.logging.access;

import cn.techoc.oriongateway.core.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;

/**
 * 通用访问日志过滤器（Starter中自动生效）
 * <p>
 * 计算时间说明：
 * - 总请求时间（REQUEST_TIME）：从网关接收到请求开始，到响应完全发送给客户端结束的总时间
 * - 上游响应时间（UPSTREAM_RESPONSE_TIME）：从请求被转发到上游服务器开始，到上游服务器响应完成的时间
 */
@Component
public class AccessLogWebFilter implements WebFilter {

    private final AccessLogProperties props;
    private final Logger log;

    public AccessLogWebFilter() {
        this(new AccessLogProperties(), LoggerFactory.getLogger(AccessLogWebFilter.class));
    }

    public AccessLogWebFilter(AccessLogProperties props) {
        this(props, LoggerFactory.getLogger(AccessLogWebFilter.class));
    }

    public AccessLogWebFilter(AccessLogProperties props, Logger log) {
        this.props = props;
        this.log = log;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!props.isEnabled()) {
            return chain.filter(exchange);
        }

        // 记录请求开始时间（总时间）
        long requestStartTime = System.currentTimeMillis();
        return chain.filter(exchange).doFinally(signalType -> {
            try {
                logAccess(exchange, requestStartTime);
            } catch (Exception e) {
                log.error("Access log error", e);
            }
        });
    }

    public void logAccess(ServerWebExchange exchange, long requestStartTime) {

        // 提前取出请求信息
        ServerHttpRequest request = exchange.getRequest();
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        InetAddress address = remoteAddress != null ? remoteAddress.getAddress() : null;
        String remoteAddr = address != null ? address.getHostAddress() : "-";

        // 获取上游目标地址（在 RoutingFilter 阶段会被设置）
        URI routeUri = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR);

        String upstreamAddr;

        // 如果routeUri不为空，尝试获取其IP地址
        if (routeUri != null) {
            try {
                InetAddress inetAddress = InetAddress.getByName(routeUri.getHost());
                upstreamAddr = inetAddress.getHostAddress() + ":" + routeUri.getPort();
            } catch (Exception e) {
                // 如果无法解析IP地址，则保持原有的主机名和端口
                upstreamAddr = routeUri.getHost() + ":" + routeUri.getPort();
            }
        } else {
            upstreamAddr = "-";
        }

        // 计算总请求时间
        long requestEndTime = System.currentTimeMillis();
        double totalRequestTime = (requestEndTime - requestStartTime) / 1000.0;

        // 计算上游响应时间（从转发到上游开始到收到上游响应结束的时间）
        Long upstreamStartTime = exchange.getAttribute(Constants.UPSTREAM_START_TIME_ATTR);
        Long upstreamEndTime = exchange.getAttribute(Constants.UPSTREAM_END_TIME_ATTR);

        double upstreamResponseTime;
        if (upstreamStartTime != null && upstreamEndTime != null) {
            upstreamResponseTime = (upstreamEndTime - upstreamStartTime) / 1000.0;
        } else {
            // 如果无法获取上游时间，则使用 -1
            upstreamResponseTime = -1.0;
        }

        ServerHttpResponse response = exchange.getResponse();

        java.util.EnumMap<AccessLogVariable, Object> vars = new java.util.EnumMap<>(AccessLogVariable.class);
        vars.put(AccessLogVariable.REMOTE_ADDR, remoteAddr);

        String remoteUser = "-";
        if (props.isResolveRemoteUserFromBasicAuth()) {
            String authorization = request.getHeaders().getFirst("Authorization");
            if (authorization != null && authorization.startsWith("Basic ")) {
                try {
                    String base64Credentials =
                            authorization.substring("Basic ".length()).trim();
                    String credentials =
                            new String(java.util.Base64.getDecoder().decode(base64Credentials));
                    String[] values = credentials.split(":", 2);
                    if (values.length > 0) {
                        remoteUser = values[0];
                    }
                } catch (Exception e) {
                    // 忽略解析错误，使用默认值 "-"
                }
            }
        }
        vars.put(AccessLogVariable.REMOTE_USER, remoteUser);
        vars.put(AccessLogVariable.TIME_LOCAL, AccessLogFormatter.now(props.getZoneId()));
        vars.put(
                AccessLogVariable.REQUEST,
                request.getMethodValue() + " " + request.getURI().getRawPath());
        vars.put(AccessLogVariable.STATUS, getStatus(response));
        vars.put(AccessLogVariable.BODY_BYTES_SENT, response.getHeaders().getContentLength());
        // 按开关控制标准头写入（关闭则置为 null，格式化为 "-")
        vars.put(
                AccessLogVariable.HTTP_REFERER,
                props.isIncludeReferer() ? request.getHeaders().getFirst("Referer") : null);
        vars.put(
                AccessLogVariable.HTTP_USER_AGENT,
                props.isIncludeUserAgent() ? request.getHeaders().getFirst("User-Agent") : null);
        vars.put(
                AccessLogVariable.HTTP_X_FORWARDED_FOR,
                props.isIncludeXForwardedFor() ? request.getHeaders().getFirst("X-Forwarded-For") : null);
        vars.put(AccessLogVariable.UPSTREAM_ADDR, props.isIncludeUpstreamAddr() ? upstreamAddr : null);
        if (props.isIncludeTimes()) {
            vars.put(AccessLogVariable.UPSTREAM_RESPONSE_TIME, String.format("%.3f", upstreamResponseTime));
            vars.put(AccessLogVariable.REQUEST_TIME, String.format("%.3f", totalRequestTime));
        } else {
            vars.put(AccessLogVariable.UPSTREAM_RESPONSE_TIME, null);
            vars.put(AccessLogVariable.REQUEST_TIME, null);
        }

        // 聚合请求头/响应头（仅在 pattern 使用且开关打开时有效）
        if (props.isIncludeRequestHeaders()) {
            String reqHeaders = request.getHeaders().entrySet().stream()
                    .map(e -> e.getKey() + "=" + String.join(",", e.getValue()))
                    .collect(java.util.stream.Collectors.joining("; "));
            if (reqHeaders.length() > props.getHeadersMaxLength()) {
                reqHeaders = reqHeaders.substring(0, props.getHeadersMaxLength()) + "...";
            }
            vars.put(AccessLogVariable.REQ_HEADERS, reqHeaders);
        } else {
            vars.put(AccessLogVariable.REQ_HEADERS, null);
        }
        if (props.isIncludeResponseHeaders()) {
            String respHeaders = response.getHeaders().entrySet().stream()
                    .map(e -> e.getKey() + "=" + String.join(",", e.getValue()))
                    .collect(java.util.stream.Collectors.joining("; "));
            if (respHeaders.length() > props.getHeadersMaxLength()) {
                respHeaders = respHeaders.substring(0, props.getHeadersMaxLength()) + "...";
            }
            vars.put(AccessLogVariable.RESP_HEADERS, respHeaders);
        } else {
            vars.put(AccessLogVariable.RESP_HEADERS, null);
        }

        String logLine = AccessLogFormatter.format(props.getPattern(), vars);
        log.info(logLine);
    }

    private int getStatus(ServerHttpResponse response) {
        if (response == null || response.getStatusCode() == null) {
            return 0;
        }
        return response.getStatusCode().value();
    }
}
