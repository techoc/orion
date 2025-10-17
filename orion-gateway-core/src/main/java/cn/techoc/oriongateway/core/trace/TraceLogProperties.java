package cn.techoc.oriongateway.core.trace;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "gateway.trace-log")
public class TraceLogProperties {
    private boolean enabled = true;
    private String pattern = "";


}
