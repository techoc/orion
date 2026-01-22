package cn.techoc.oriongateway.core.logging.access;

import cn.techoc.oriongateway.core.Constants;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

public class AccessLongGlobalFilter implements GlobalFilter, Ordered {
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 在路由前记录开始时间
        exchange.getAttributes().put(Constants.UPSTREAM_START_TIME_ATTR, System.currentTimeMillis());

        return chain.filter(exchange)
                .doOnTerminate(() -> {
                    // 在响应完成后记录结束时间
                    exchange.getAttributes().put(Constants.UPSTREAM_END_TIME_ATTR, System.currentTimeMillis());
                });
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
