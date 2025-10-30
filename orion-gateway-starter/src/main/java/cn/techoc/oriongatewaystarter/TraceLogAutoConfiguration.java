package cn.techoc.oriongatewaystarter;

import cn.techoc.oriongateway.core.trace.LinkTracingGlobalFilter;
import cn.techoc.oriongateway.core.trace.LinkTracingProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(LinkTracingProperties.class)
@ConditionalOnProperty(prefix = "gateway.link-tracing", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TraceLogAutoConfiguration {

    @Bean
    public LinkTracingGlobalFilter raceGlobalFilter(LinkTracingProperties linkTracingProperties) {
        return new LinkTracingGlobalFilter(linkTracingProperties);
    }


}