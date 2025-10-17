package cn.techoc.oriongateway.core.loggging;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * 通用访问日志过滤器（Starter中自动生效）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccessLogGlobalFilter implements WebFilter {

    private final AccessLogProperties props;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!props.isEnabled()) {
            return chain.filter(exchange);
        }

        long startTime = System.currentTimeMillis();


        return chain.filter(exchange)
                .doFinally(signalType -> {
                    try {
                        logAccess(exchange, startTime);
                    } catch (Exception e) {
                        log.error("Access log error", e);
                    }
                });
    }

    void logAccess(ServerWebExchange exchange, long startTime) {

        // 提前取出请求信息
        var request = exchange.getRequest();
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        String remoteAddr = remoteAddress != null ? remoteAddress.getAddress().getHostAddress() : "-";

        // 获取上游目标地址（在 RoutingFilter 阶段会被设置）
        URI routeUri = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR);
        String upstreamAddr = routeUri != null ? routeUri.getHost() + ":" + routeUri.getPort() : "-";

        long endTime = System.currentTimeMillis();
        double requestTime = (endTime - startTime) / 1000.0;

        var response = exchange.getResponse();

        Map<String, Object> vars = new HashMap<>();
        vars.put("remote_addr", remoteAddr);
        vars.put("remote_user", "-");
        vars.put("time_local", AccessLogFormatter.now());
        vars.put("request", request.getMethodValue() + " " + request.getURI().getRawPath());
        vars.put("status", getStatus(response));
        vars.put("body_bytes_sent", response.getHeaders().getContentLength());
        vars.put("http_referer", request.getHeaders().getFirst("Referer"));
        vars.put("http_user_agent", request.getHeaders().getFirst("User-Agent"));
        vars.put("http_x_forwarded_for", request.getHeaders().getFirst("X-Forwarded-For"));
        vars.put("upstream_addr", upstreamAddr);   // 可选
        vars.put("upstream_response_time", String.format("%.3f", requestTime));
        vars.put("request_time", String.format("%.3f", requestTime));

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

