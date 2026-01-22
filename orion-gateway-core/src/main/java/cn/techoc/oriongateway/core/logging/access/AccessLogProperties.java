package cn.techoc.oriongateway.core.logging.access;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;

@Data
@ConfigurationProperties(prefix = "gateway.access-log")
@RefreshScope
public class AccessLogProperties {
    private boolean enabled = true;
    private String zoneId = "Asia/Shanghai";
    private String pattern = "$remote_addr - $remote_user [$time_local] \"$request\" $status $body_bytes_sent \"$http_referer\" \"$http_user_agent\" \"$http_x_forwarded_for\" $upstream_addr ups_resp_time: $upstream_response_time request_time: $request_time";

    // 细粒度开关（可热更新）
    private boolean includeReferer = true;
    private boolean includeUserAgent = true;
    private boolean includeXForwardedFor = true;
    private boolean includeUpstreamAddr = true;
    private boolean includeTimes = true; // 控制 request_time 与 upstream_response_time
    private boolean resolveRemoteUserFromBasicAuth = true;

    // 聚合头部输出占位符（默认不输出，需要在 pattern 中加入 $req_headers/$resp_headers）
    private boolean includeRequestHeaders = false;
    private boolean includeResponseHeaders = false;
    private int headersMaxLength = 1024; // 聚合字符串最大长度，避免日志爆炸

    public boolean isEnabled() {
        return enabled;

    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getZoneId() {
        return zoneId;

    }

    public void setZoneId(String zoneId) {
        this.zoneId = zoneId;
    }

    public String getPattern() {
        return pattern;

    }

    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    public boolean isIncludeReferer() {
        return includeReferer;

    }

    public void setIncludeReferer(boolean includeReferer) {
        this.includeReferer = includeReferer;
    }

    public boolean isIncludeUserAgent() {
        return includeUserAgent;

    }

    public void setIncludeUserAgent(boolean includeUserAgent) {
        this.includeUserAgent = includeUserAgent;
    }

    public boolean isIncludeXForwardedFor() {
        return includeXForwardedFor;

    }

    public void setIncludeXForwardedFor(boolean includeXForwardedFor) {
        this.includeXForwardedFor = includeXForwardedFor;
    }

    public boolean isIncludeUpstreamAddr() {
        return includeUpstreamAddr;

    }

    public void setIncludeUpstreamAddr(boolean includeUpstreamAddr) {
        this.includeUpstreamAddr = includeUpstreamAddr;
    }

    public boolean isIncludeTimes() {
        return includeTimes;

    }

    public void setIncludeTimes(boolean includeTimes) {
        this.includeTimes = includeTimes;
    }

    public boolean isResolveRemoteUserFromBasicAuth() {
        return resolveRemoteUserFromBasicAuth;

    }

    public void setResolveRemoteUserFromBasicAuth(boolean resolveRemoteUserFromBasicAuth) {
        this.resolveRemoteUserFromBasicAuth = resolveRemoteUserFromBasicAuth;
    }

    public boolean isIncludeRequestHeaders() {
        return includeRequestHeaders;

    }

    public void setIncludeRequestHeaders(boolean includeRequestHeaders) {
        this.includeRequestHeaders = includeRequestHeaders;
    }

    public boolean isIncludeResponseHeaders() {
        return includeResponseHeaders;

    }

    public void setIncludeResponseHeaders(boolean includeResponseHeaders) {
        this.includeResponseHeaders = includeResponseHeaders;
    }

    public int getHeadersMaxLength() {
        return headersMaxLength;

    }

    public void setHeadersMaxLength(int headersMaxLength) {
        this.headersMaxLength = headersMaxLength;
    }
}