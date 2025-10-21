package cn.techoc.oriongateway.core.loggging;


import lombok.var;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
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
public class AccessLogGlobalFilter implements WebFilter {

    private final AccessLogProperties props;
    private final Logger log;

    public AccessLogGlobalFilter() {
        this(new AccessLogProperties(), LoggerFactory.getLogger(AccessLogGlobalFilter.class));
    }

    public AccessLogGlobalFilter(AccessLogProperties props) {
        this(props, LoggerFactory.getLogger(AccessLogGlobalFilter.class));
    }

    public AccessLogGlobalFilter(AccessLogProperties props, Logger log) {
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

        // 在请求处理前设置上游响应时间开始时间
        exchange.getAttributes().put("upstream_start_time", System.currentTimeMillis());

        return chain.filter(exchange)
                .doOnSuccess(aVoid -> {
                    // 请求成功处理后记录上游响应结束时间
                    exchange.getAttributes().put("upstream_end_time", System.currentTimeMillis());
                })
                .doOnError(throwable -> {
                    // 请求处理出错时也记录上游响应结束时间
                    exchange.getAttributes().put("upstream_end_time", System.currentTimeMillis());
                })
                .doFinally(signalType -> {
                    try {
                        logAccess(exchange, requestStartTime);
                    } catch (Exception e) {
                        log.error("Access log error", e);
                    }
                });
    }

    void logAccess(ServerWebExchange exchange, long requestStartTime) {

        // 提前取出请求信息
        var request = exchange.getRequest();
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        InetAddress address = remoteAddress != null ? remoteAddress.getAddress() : null;
        String remoteAddr = address != null ? address.getHostAddress() : "-";

        // 获取上游目标地址（在 RoutingFilter 阶段会被设置）
        URI routeUri = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR);
        String upstreamAddr = routeUri != null ? routeUri.getHost() + ":" + routeUri.getPort() : "-";

        // 计算总请求时间
        long requestEndTime = System.currentTimeMillis();
        double totalRequestTime = (requestEndTime - requestStartTime) / 1000.0;

        // 计算上游响应时间（从转发到上游开始到收到上游响应结束的时间）
        Long upstreamStartTime = exchange.getAttribute("upstream_start_time");
        Long upstreamEndTime = exchange.getAttribute("upstream_end_time");

        double upstreamResponseTime;
        if (upstreamStartTime != null && upstreamEndTime != null) {
            upstreamResponseTime = (upstreamEndTime - upstreamStartTime) / 1000.0;
        } else {
            // 如果无法获取上游时间，则使用总时间作为后备
            upstreamResponseTime = totalRequestTime;
        }

        var response = exchange.getResponse();

        java.util.EnumMap<AccessLogVariable, Object> vars = new java.util.EnumMap<>(AccessLogVariable.class);
        vars.put(AccessLogVariable.REMOTE_ADDR, remoteAddr);

        String remoteUser = "-";
        String authorization = request.getHeaders().getFirst("Authorization");
        if (authorization != null && authorization.startsWith("Basic ")) {
            try {
                String base64Credentials = authorization.substring("Basic ".length()).trim();
                String credentials = new String(java.util.Base64.getDecoder().decode(base64Credentials));
                String[] values = credentials.split(":", 2);
                if (values.length > 0) {
                    remoteUser = values[0];
                }
            } catch (Exception e) {
                // 忽略解析错误，使用默认值 "-"
            }
        }
        vars.put(AccessLogVariable.REMOTE_USER, remoteUser);
        vars.put(AccessLogVariable.TIME_LOCAL, AccessLogFormatter.now());
        vars.put(AccessLogVariable.REQUEST, request.getMethodValue() + " " + request.getURI().getRawPath());
        vars.put(AccessLogVariable.STATUS, getStatus(response));
        vars.put(AccessLogVariable.BODY_BYTES_SENT, response.getHeaders().getContentLength());
        vars.put(AccessLogVariable.HTTP_REFERER, request.getHeaders().getFirst("Referer"));
        vars.put(AccessLogVariable.HTTP_USER_AGENT, request.getHeaders().getFirst("User-Agent"));
        vars.put(AccessLogVariable.HTTP_X_FORWARDED_FOR, request.getHeaders().getFirst("X-Forwarded-For"));
        vars.put(AccessLogVariable.UPSTREAM_ADDR, upstreamAddr);   // 可选
        vars.put(AccessLogVariable.UPSTREAM_RESPONSE_TIME, String.format("%.3f", upstreamResponseTime));
        vars.put(AccessLogVariable.REQUEST_TIME, String.format("%.3f", totalRequestTime));

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

