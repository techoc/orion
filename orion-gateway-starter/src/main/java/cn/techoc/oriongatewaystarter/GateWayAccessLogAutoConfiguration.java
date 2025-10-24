package cn.techoc.oriongatewaystarter;

import cn.techoc.oriongateway.core.loggging.AccessLogWebFilter;
import cn.techoc.oriongateway.core.loggging.AccessLogProperties;
import cn.techoc.oriongateway.core.loggging.UpstreamTimingGatewayFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.embedded.netty.NettyReactiveWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@EnableConfigurationProperties(AccessLogProperties.class)
public class GateWayAccessLogAutoConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "gateway.access-log", name = "enabled", havingValue = "true")
    public AccessLogWebFilter accessLogFilter(AccessLogProperties props) {
        return new AccessLogWebFilter(props);
    }

    // 关闭 netty 的访问日志
    @Bean
    @ConditionalOnProperty(prefix = "gateway.access-log", name = "enabled", havingValue = "true")
    public WebServerFactoryCustomizer<NettyReactiveWebServerFactory> nettyCustomizer() {
        return factory -> factory.addServerCustomizers(httpServer ->
                httpServer.accessLog(false) // 关闭访问日志
        );
    }

    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "gateway.access-log", name = "enabled", havingValue = "true")
    public UpstreamTimingGatewayFilter upstreamTimingGatewayFilter() {
        return new UpstreamTimingGatewayFilter();
    }
}