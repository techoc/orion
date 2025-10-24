package cn.techoc.oriongateway.core.loggging;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@Order(-1)
public class UpstreamTimingGatewayFilter implements GlobalFilter, Ordered {
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 在路由前记录开始时间
        exchange.getAttributes().put(AccessLogConstants.UPSTREAM_START_TIME_ATTR, System.currentTimeMillis());

        return chain.filter(exchange)
                .doOnTerminate(() -> {
                    // 在响应完成后记录结束时间
                    exchange.getAttributes().put(AccessLogConstants.UPSTREAM_END_TIME_ATTR, System.currentTimeMillis());
                });
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
