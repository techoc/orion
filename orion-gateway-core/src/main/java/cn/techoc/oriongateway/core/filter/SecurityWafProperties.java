package cn.techoc.oriongateway.core.filter;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "gateway.waf")
@RefreshScope
public class SecurityWafProperties {
    /**
     * Master switch.
     */
    private boolean enabled = true;

    /**
     * Skip WAF checks for paths with these prefixes.
     * Example: ["/actuator", "/favicon.ico"]
     */
    private List<String> skipPathPrefixes = new ArrayList<>();

    /**
     * Trust proxy headers (X-Forwarded-For, X-Real-IP) for client IP detection.
     * If false, uses the TCP remote address.
     */
    private boolean trustProxyHeaders = true;

    /**
     * Header names used to resolve client IP when {@link #trustProxyHeaders} is enabled.
     * The first non-empty header wins.
     */
    private List<String> clientIpHeaders = new ArrayList<>(List.of("X-Forwarded-For", "X-Real-IP"));

    /**
     * If allowList is non-empty, only these IPs/CIDRs are allowed.
     * Supports exact IPv4 and IPv4 CIDR (e.g. 10.0.0.0/8).
     */
    private List<String> ipAllowList = new ArrayList<>();

    /**
     * IPs/CIDRs to block. Supports exact IPv4 and IPv4 CIDR (e.g. 192.168.0.0/16).
     */
    private List<String> ipDenyList = new ArrayList<>();

    /**
     * Enable in-memory rate limiting per client IP.
     */
    private boolean rateLimitEnabled = true;

    /**
     * Token bucket capacity (burst).
     */
    private int rateLimitCapacity = 50;

    /**
     * Tokens refilled per second.
     */
    private double rateLimitRefillPerSecond = 20.0;

    /**
     * Max distinct IP entries in memory before we start evicting idle ones.
     */
    private int rateLimitMaxEntries = 10_000;

    /**
     * Evict buckets idle for N seconds.
     */
    private int rateLimitIdleEvictSeconds = 600;

    /**
     * Enable request inspection on URI/path/query/headers/body.
     */
    private boolean inspectPath = true;

    private boolean inspectQuery = true;

    private boolean inspectHeaders = true;

    /**
     * Body inspection is off by default to avoid impacting streaming and large payloads.
     * When enabled, the filter will cache the request body (up to {@link #maxInspectBodyBytes})
     * and re-inject it so downstream filters/routes can still read it.
     */
    private boolean inspectBody = false;

    /**
     * Maximum request URI length allowed (raw).
     */
    private int maxUriLength = 8192;

    /**
     * Maximum single value length (header/query/body snippet field) we will inspect.
     */
    private int maxValueLength = 2048;

    /**
     * Maximum body bytes to cache and inspect when {@link #inspectBody} is enabled.
     * Exceeding this limit results in a block.
     */
    private int maxInspectBodyBytes = 8192;

    /**
     * Content-Types eligible for body inspection (prefix match, case-insensitive).
     */
    private List<String> inspectBodyContentTypes =
            new ArrayList<>(List.of("application/json", "application/x-www-form-urlencoded", "text/plain"));

    /**
     * Enable specific detectors.
     */
    private boolean detectSqlInjection = true;

    private boolean detectXss = true;

    private boolean detectPathTraversal = true;

    /**
     * Optional deny-list regexes for User-Agent.
     * Keep empty by default to reduce false positives.
     */
    private List<String> denyUserAgentRegexes = new ArrayList<>();

    /**
     * Response behavior.
     */
    private int blockStatus = 403;

    private boolean logBlocked = true;

    /**
     * Avoid leaking detailed reasons to clients by default.
     */
    private boolean includeReasonInResponse = false;

    private String blockResponseContentType = "application/json";

    private String blockResponseBody = "{\"code\":403,\"message\":\"forbidden\"}";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getSkipPathPrefixes() {
        return skipPathPrefixes;
    }

    public void setSkipPathPrefixes(List<String> skipPathPrefixes) {
        this.skipPathPrefixes = skipPathPrefixes;
    }

    public boolean isTrustProxyHeaders() {
        return trustProxyHeaders;
    }

    public void setTrustProxyHeaders(boolean trustProxyHeaders) {
        this.trustProxyHeaders = trustProxyHeaders;
    }

    public List<String> getClientIpHeaders() {
        return clientIpHeaders;
    }

    public void setClientIpHeaders(List<String> clientIpHeaders) {
        this.clientIpHeaders = clientIpHeaders;
    }

    public List<String> getIpAllowList() {
        return ipAllowList;
    }

    public void setIpAllowList(List<String> ipAllowList) {
        this.ipAllowList = ipAllowList;
    }

    public List<String> getIpDenyList() {
        return ipDenyList;
    }

    public void setIpDenyList(List<String> ipDenyList) {
        this.ipDenyList = ipDenyList;
    }

    public boolean isRateLimitEnabled() {
        return rateLimitEnabled;
    }

    public void setRateLimitEnabled(boolean rateLimitEnabled) {
        this.rateLimitEnabled = rateLimitEnabled;
    }

    public int getRateLimitCapacity() {
        return rateLimitCapacity;
    }

    public void setRateLimitCapacity(int rateLimitCapacity) {
        this.rateLimitCapacity = rateLimitCapacity;
    }

    public double getRateLimitRefillPerSecond() {
        return rateLimitRefillPerSecond;
    }

    public void setRateLimitRefillPerSecond(double rateLimitRefillPerSecond) {
        this.rateLimitRefillPerSecond = rateLimitRefillPerSecond;
    }

    public int getRateLimitMaxEntries() {
        return rateLimitMaxEntries;
    }

    public void setRateLimitMaxEntries(int rateLimitMaxEntries) {
        this.rateLimitMaxEntries = rateLimitMaxEntries;
    }

    public int getRateLimitIdleEvictSeconds() {
        return rateLimitIdleEvictSeconds;
    }

    public void setRateLimitIdleEvictSeconds(int rateLimitIdleEvictSeconds) {
        this.rateLimitIdleEvictSeconds = rateLimitIdleEvictSeconds;
    }

    public boolean isInspectPath() {
        return inspectPath;
    }

    public void setInspectPath(boolean inspectPath) {
        this.inspectPath = inspectPath;
    }

    public boolean isInspectQuery() {
        return inspectQuery;
    }

    public void setInspectQuery(boolean inspectQuery) {
        this.inspectQuery = inspectQuery;
    }

    public boolean isInspectHeaders() {
        return inspectHeaders;
    }

    public void setInspectHeaders(boolean inspectHeaders) {
        this.inspectHeaders = inspectHeaders;
    }

    public boolean isInspectBody() {
        return inspectBody;
    }

    public void setInspectBody(boolean inspectBody) {
        this.inspectBody = inspectBody;
    }

    public int getMaxUriLength() {
        return maxUriLength;
    }

    public void setMaxUriLength(int maxUriLength) {
        this.maxUriLength = maxUriLength;
    }

    public int getMaxValueLength() {
        return maxValueLength;
    }

    public void setMaxValueLength(int maxValueLength) {
        this.maxValueLength = maxValueLength;
    }

    public int getMaxInspectBodyBytes() {
        return maxInspectBodyBytes;
    }

    public void setMaxInspectBodyBytes(int maxInspectBodyBytes) {
        this.maxInspectBodyBytes = maxInspectBodyBytes;
    }

    public List<String> getInspectBodyContentTypes() {
        return inspectBodyContentTypes;
    }

    public void setInspectBodyContentTypes(List<String> inspectBodyContentTypes) {
        this.inspectBodyContentTypes = inspectBodyContentTypes;
    }

    public boolean isDetectSqlInjection() {
        return detectSqlInjection;
    }

    public void setDetectSqlInjection(boolean detectSqlInjection) {
        this.detectSqlInjection = detectSqlInjection;
    }

    public boolean isDetectXss() {
        return detectXss;
    }

    public void setDetectXss(boolean detectXss) {
        this.detectXss = detectXss;
    }

    public boolean isDetectPathTraversal() {
        return detectPathTraversal;
    }

    public void setDetectPathTraversal(boolean detectPathTraversal) {
        this.detectPathTraversal = detectPathTraversal;
    }

    public List<String> getDenyUserAgentRegexes() {
        return denyUserAgentRegexes;
    }

    public void setDenyUserAgentRegexes(List<String> denyUserAgentRegexes) {
        this.denyUserAgentRegexes = denyUserAgentRegexes;
    }

    public int getBlockStatus() {
        return blockStatus;
    }

    public void setBlockStatus(int blockStatus) {
        this.blockStatus = blockStatus;
    }

    public boolean isLogBlocked() {
        return logBlocked;
    }

    public void setLogBlocked(boolean logBlocked) {
        this.logBlocked = logBlocked;
    }

    public boolean isIncludeReasonInResponse() {
        return includeReasonInResponse;
    }

    public void setIncludeReasonInResponse(boolean includeReasonInResponse) {
        this.includeReasonInResponse = includeReasonInResponse;
    }

    public String getBlockResponseContentType() {
        return blockResponseContentType;
    }

    public void setBlockResponseContentType(String blockResponseContentType) {
        this.blockResponseContentType = blockResponseContentType;
    }

    public String getBlockResponseBody() {
        return blockResponseBody;
    }

    public void setBlockResponseBody(String blockResponseBody) {
        this.blockResponseBody = blockResponseBody;
    }
}
