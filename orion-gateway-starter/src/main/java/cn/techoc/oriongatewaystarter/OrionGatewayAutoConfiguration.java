package cn.techoc.oriongatewaystarter;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import({GateWayAccessLogAutoConfiguration.class, TraceLogAutoConfiguration.class})
public class OrionGatewayAutoConfiguration {
    // Main auto-configuration class that imports both access log and trace configurations
}