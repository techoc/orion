package cn.techoc.oriongatewaystarter;

import cn.techoc.oriongateway.core.logging.access.AccessLogProperties;
import cn.techoc.oriongateway.core.logging.access.AccessLogWebFilter;
import cn.techoc.oriongateway.core.logging.access.AccessLongGlobalFilter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.embedded.netty.NettyReactiveWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AccessLogProperties.class)
public class GateWayAccessLogAutoConfiguration {

    @Bean
    public AccessLogWebFilter accessLogFilter(AccessLogProperties props) {
        return new AccessLogWebFilter(props);
    }

    // 关闭 netty 的访问日志
    @Bean
    public WebServerFactoryCustomizer<NettyReactiveWebServerFactory> nettyCustomizer() {
        return factory -> factory.addServerCustomizers(
                httpServer -> httpServer.accessLog(false) // 关闭访问日志
        );
    }

    @Bean
    public AccessLongGlobalFilter accessLongGlobalFilter() {
        return new AccessLongGlobalFilter();
    }
}
