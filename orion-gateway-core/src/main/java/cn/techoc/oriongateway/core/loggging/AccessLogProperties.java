package cn.techoc.oriongateway.core.loggging;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Setter
@Getter
@ConfigurationProperties(prefix = "gateway.access-log")
public class AccessLogProperties {
    private boolean enabled = true;
    private String pattern = "$remote_addr - $remote_user [$time_local] \"$request\" $status $body_bytes_sent \"$http_referer\" \"$http_user_agent\" \"$http_x_forwarded_for\" $upstream_addr ups_resp_time: $upstream_response_time request_time: $request_time";

}
