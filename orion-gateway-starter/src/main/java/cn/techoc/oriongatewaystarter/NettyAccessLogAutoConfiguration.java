package cn.techoc.oriongatewaystarter;

import cn.techoc.oriongateway.core.loggging.AccessLogGlobalFilter;
import cn.techoc.oriongateway.core.loggging.AccessLogProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AccessLogProperties.class)
public class NettyAccessLogAutoConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "gateway.access-log", name = "enabled", havingValue = "true", matchIfMissing = true)
    public AccessLogGlobalFilter accessLogFilter(AccessLogProperties props) {
        return new AccessLogGlobalFilter(props);
    }
}