package cn.techoc.oriongatewaystarter;

import cn.techoc.oriongateway.core.trace.LinkTracingGlobalFilter;
import cn.techoc.oriongateway.core.trace.LinkTracingProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(LinkTracingProperties.class)
public class TraceLogAutoConfiguration {

    @Bean
    public LinkTracingGlobalFilter raceGlobalFilter(LinkTracingProperties linkTracingProperties) {
        return new LinkTracingGlobalFilter(linkTracingProperties);
    }


}