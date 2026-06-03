package cn.techoc.oriongatewaystarter;

import cn.techoc.oriongateway.core.logging.access.AccessLogProperties;
import cn.techoc.oriongateway.core.logging.access.AccessLogWebFilter;
import cn.techoc.oriongateway.core.logging.access.AccessLongGlobalFilter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AccessLogProperties.class)
public class GateWayAccessLogAutoConfiguration {

    @Bean
    public AccessLogWebFilter accessLogFilter(AccessLogProperties props) {
        return new AccessLogWebFilter(props);
    }

    @Bean
    public AccessLongGlobalFilter accessLongGlobalFilter() {
        return new AccessLongGlobalFilter();
    }
}
