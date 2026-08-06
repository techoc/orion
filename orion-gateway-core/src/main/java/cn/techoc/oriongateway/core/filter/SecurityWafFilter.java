package cn.techoc.oriongateway.core.filter;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.lang.NonNull;
import org.springframework.util.CollectionUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriUtils;
import reactor.core.publisher.Mono;

@Slf4j
public class SecurityWafFilter implements GlobalFilter, Ordered {

    private final SecurityWafProperties properties;
    private final InMemoryTokenBucketRateLimiter rateLimiter;

    private volatile List<Pattern> denyUaPatterns = List.of();

    public SecurityWafFilter(SecurityWafProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.rateLimiter = new InMemoryTokenBucketRateLimiter(properties);
        compileUserAgentPatterns();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!properties.isEnabled()) {
            return chain.filter(exchange);
        }

        String path = exchange.getRequest().getURI().getPath();
        if (shouldSkipPath(path)) {
            return chain.filter(exchange);
        }

        String clientIp = resolveClientIp(exchange);
        WafDecision ipDecision = evaluateIpRules(clientIp);
        if (ipDecision.blocked) {
            return txBlock(exchange, ipDecision);
        }

        WafDecision rlDecision = evaluateRateLimit(clientIp);
        if (rlDecision.blocked) {
            return txBlock(exchange, rlDecision);
        }

        if (properties.isInspectBody() && mayHaveBody(exchange.getRequest().getMethod())) {
            return cacheRequestBodyIfNeeded(exchange).flatMap(cached -> {
                WafDecision decision = evaluateRequest(cached);
                if (decision.blocked) {
                    return txBlock(cached, decision);
                }
                return chain.filter(cached);
            });
        }

        WafDecision decision = evaluateRequest(exchange);
        if (decision.blocked) {
            return txBlock(exchange, decision);
        }
        return chain.filter(exchange);
    }

    private boolean shouldSkipPath(String path) {
        List<String> prefixes = properties.getSkipPathPrefixes();
        if (CollectionUtils.isEmpty(prefixes) || path == null) {
            return false;
        }
        for (String prefix : prefixes) {
            if (prefix != null && !prefix.isBlank() && path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private WafDecision evaluateIpRules(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            return WafDecision.allow();
        }
        List<String> allow = properties.getIpAllowList();
        if (!CollectionUtils.isEmpty(allow)) {
            if (!IpMatcher.matchesAny(clientIp, allow)) {
                return WafDecision.block("ip_not_in_allow_list");
            }
        }
        List<String> deny = properties.getIpDenyList();
        if (!CollectionUtils.isEmpty(deny)) {
            if (IpMatcher.matchesAny(clientIp, deny)) {
                return WafDecision.block("ip_in_deny_list");
            }
        }
        return WafDecision.allow();
    }

    @NonNull
    private Mono<Void> txBlock(@NonNull ServerWebExchange exchange, @NonNull WafDecision decision) {
        int status = properties.getBlockStatus() > 0 ? properties.getBlockStatus() : 403;
        exchange.getResponse().setStatusCode(HttpStatusCode.valueOf(status));
        exchange.getResponse().getHeaders().set(HttpHeaders.CONTENT_TYPE, properties.getBlockResponseContentType());

        String body = properties.getBlockResponseBody();
        if (properties.isIncludeReasonInResponse() && decision.reason != null) {
            body = body.replace("}", ",\"reason\":\"" + escapeJson(decision.reason) + "\"}");
        }

        if (properties.isLogBlocked()) {
            String ip = resolveClientIp(exchange);
            String method = String.valueOf(exchange.getRequest().getMethod());
            String uri = String.valueOf(exchange.getRequest().getURI());
            log.warn("WAF blocked: reason={} ip={} method={} uri={}", decision.reason, ip, method, uri);
        }

        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        return exchange.getResponse()
                .writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
    }

    @Override
    public int getOrder() {
        return -1;
    }

    private WafDecision evaluateRateLimit(String clientIp) {
        if (!properties.isRateLimitEnabled()) {
            return WafDecision.allow();
        }
        if (clientIp == null || clientIp.isBlank()) {
            return WafDecision.allow();
        }
        if (!rateLimiter.tryConsume(clientIp)) {
            return WafDecision.block("rate_limited");
        }
        return WafDecision.allow();
    }

    private WafDecision evaluateRequest(ServerWebExchange exchange) {
        String rawUri = exchange.getRequest().getURI().toString();
        if (rawUri.length() > properties.getMaxUriLength()) {
            return WafDecision.block("uri_too_long");
        }

        if (properties.isInspectHeaders()) {
            WafDecision headerDecision = inspectHeaders(exchange.getRequest().getHeaders());
            if (headerDecision.blocked) {
                return headerDecision;
            }
        }

        if (properties.isInspectPath()) {
            String rawPath = exchange.getRequest().getURI().getRawPath();
            String decodedPath = safeDecode(rawPath);
            WafDecision pathDecision = inspectValue(decodedPath, "path");
            if (pathDecision.blocked) {
                return pathDecision;
            }
        }

        if (properties.isInspectQuery()) {
            for (Map.Entry<String, List<String>> entry :
                    exchange.getRequest().getQueryParams().entrySet()) {
                for (String val : entry.getValue()) {
                    WafDecision qpDecision = inspectValue(val, "query");
                    if (qpDecision.blocked) {
                        return qpDecision;
                    }
                }
            }
        }

        if (properties.isInspectBody()) {
            Object cached = exchange.getAttribute("__orion_waf_cached_body");
            if (cached instanceof byte[]) {
                String body = new String((byte[]) cached, StandardCharsets.UTF_8);
                WafDecision bodyDecision = inspectValue(body, "body");
                if (bodyDecision.blocked) {
                    return bodyDecision;
                }
            }
        }

        return WafDecision.allow();
    }

    private WafDecision inspectHeaders(HttpHeaders headers) {
        if (headers == null || headers.isEmpty()) {
            return WafDecision.allow();
        }

        String userAgent = headers.getFirst(HttpHeaders.USER_AGENT);
        if (userAgent != null && !denyUaPatterns.isEmpty()) {
            for (Pattern p : denyUaPatterns) {
                if (p.matcher(userAgent).find()) {
                    return WafDecision.block("deny_user_agent");
                }
            }
        }

        // Inspect a small set of high-signal headers (avoid Authorization by default).
        List<String> candidates = List.of(
                HttpHeaders.USER_AGENT, HttpHeaders.REFERER, HttpHeaders.COOKIE, "X-Forwarded-For", "X-Real-IP");

        for (String name : candidates) {
            List<String> values = headers.get(name);
            if (values == null) {
                continue;
            }
            for (String val : values) {
                WafDecision d = inspectValue(val, "header:" + name);
                if (d.blocked) {
                    return d;
                }
            }
        }

        return WafDecision.allow();
    }

    private WafDecision inspectValue(String value, String source) {
        if (value == null || value.isBlank()) {
            return WafDecision.allow();
        }

        String truncated = value;
        if (truncated.length() > properties.getMaxValueLength()) {
            truncated = truncated.substring(0, properties.getMaxValueLength());
        }

        String decoded = safeDecode(truncated).toLowerCase(Locale.ROOT);

        if (properties.isDetectPathTraversal() && looksLikePathTraversal(decoded)) {
            return WafDecision.block("path_traversal:" + source);
        }
        if (properties.isDetectSqlInjection() && looksLikeSqlInjection(decoded)) {
            return WafDecision.block("sql_injection:" + source);
        }
        if (properties.isDetectXss() && looksLikeXss(decoded)) {
            return WafDecision.block("xss:" + source);
        }

        return WafDecision.allow();
    }

    private boolean looksLikePathTraversal(String s) {
        if (s == null) {
            return false;
        }
        if (s.contains("..\\") || s.contains("../")) {
            return true;
        }
        // Common encoded traversal patterns
        return s.contains("%2e%2e") || s.contains("%2f") && s.contains("%2e");
    }

    private boolean looksLikeSqlInjection(String s) {
        if (s == null) {
            return false;
        }
        int score = 0;

        if (s.contains("--") || s.contains("/*") || s.contains("*/")) {
            score += 2;
        }
        if (s.contains(";")) {
            score += 1;
        }
        if (s.contains(" union ") && s.contains(" select ")) {
            score += 3;
        }
        if (s.contains(" information_schema") || s.contains(" pg_catalog")) {
            score += 2;
        }
        if (s.contains(" sleep(") || s.contains(" benchmark(") || s.contains(" pg_sleep(")) {
            score += 3;
        }
        if (s.contains(" or ")
                && (s.contains("=1") || s.contains("= 1") || s.contains("'1'='1") || s.contains("\"1\"=\"1"))) {
            score += 2;
        }
        if (s.contains(" drop ")
                || s.contains(" truncate ")
                || s.contains(" delete ")
                || s.contains(" update ")
                || s.contains(" insert ")) {
            score += 2;
        }

        // Require multiple strong signals to reduce false positives.
        return score >= 4;
    }

    private boolean looksLikeXss(String s) {
        if (s == null) {
            return false;
        }
        int score = 0;
        if (s.contains("<script") || s.contains("</script")) {
            score += 4;
        }
        if (s.contains("javascript:")) {
            score += 3;
        }
        if (s.contains("onerror=") || s.contains("onload=") || s.contains("onclick=")) {
            score += 3;
        }
        if (s.contains("<img") || s.contains("<svg") || s.contains("<iframe")) {
            score += 2;
        }
        if (s.contains("<") && s.contains(">")) {
            score += 1;
        }
        return score >= 4;
    }

    private boolean mayHaveBody(HttpMethod method) {
        if (method == null) {
            return false;
        }
        return method == HttpMethod.POST || method == HttpMethod.PUT || method == HttpMethod.PATCH;
    }

    private Mono<ServerWebExchange> cacheRequestBodyIfNeeded(ServerWebExchange exchange) {
        MediaType contentType = exchange.getRequest().getHeaders().getContentType();
        if (contentType == null) {
            return Mono.just(exchange);
        }

        if (!isEligibleForBodyInspection(contentType)) {
            return Mono.just(exchange);
        }

        return DataBufferUtils.join(exchange.getRequest().getBody(), properties.getMaxInspectBodyBytes())
                .map(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);

                    exchange.getAttributes().put("__orion_waf_cached_body", bytes);

                    ServerHttpRequestDecorator decorator = new ServerHttpRequestDecorator(exchange.getRequest()) {
                        @Override
                        @NonNull
                        public reactor.core.publisher.Flux<org.springframework.core.io.buffer.DataBuffer> getBody() {
                            return Mono.just(exchange.getResponse()
                                            .bufferFactory()
                                            .wrap(bytes))
                                    .flux();
                        }

                        @Override
                        @NonNull
                        public HttpHeaders getHeaders() {
                            HttpHeaders headers = new HttpHeaders();
                            headers.putAll(super.getHeaders());
                            headers.setContentLength(bytes.length);
                            return headers;
                        }
                    };
                    return exchange.mutate().request(decorator).build();
                })
                .onErrorResume(
                        DataBufferLimitException.class, e -> txBlock(exchange, WafDecision.block("body_too_large"))
                                .then(Mono.<ServerWebExchange>empty()))
                .onErrorResume(t -> {
                    log.debug("WAF body cache failed; skipping body inspection", t);
                    return Mono.just(exchange);
                });
    }

    private boolean isEligibleForBodyInspection(MediaType contentType) {
        String ct = contentType.toString().toLowerCase(Locale.ROOT);
        for (String allowed : properties.getInspectBodyContentTypes()) {
            if (allowed == null || allowed.isBlank()) {
                continue;
            }
            if (ct.startsWith(allowed.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String resolveClientIp(ServerWebExchange exchange) {
        if (exchange == null) {
            return null;
        }
        if (properties.isTrustProxyHeaders()) {
            for (String header : properties.getClientIpHeaders()) {
                if (header == null || header.isBlank()) {
                    continue;
                }
                String val = exchange.getRequest().getHeaders().getFirst(header);
                String ip = firstIpFromHeader(val);
                if (ip != null) {
                    return ip;
                }
            }
        }
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        return remote == null
                ? null
                : remote.getAddress() == null ? null : remote.getAddress().getHostAddress();
    }

    private String firstIpFromHeader(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return null;
        }
        String[] parts = headerValue.split(",");
        if (parts.length == 0) {
            return null;
        }
        String ip = parts[0].trim();
        return ip.isEmpty() ? null : ip;
    }

    private String safeDecode(String value) {
        if (value == null) {
            return null;
        }
        try {
            return UriUtils.decode(value, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return value;
        }
    }

    private void compileUserAgentPatterns() {
        List<String> regexes = properties.getDenyUserAgentRegexes();
        if (CollectionUtils.isEmpty(regexes)) {
            denyUaPatterns = List.of();
            return;
        }
        List<Pattern> compiled = new ArrayList<>();
        for (String re : regexes) {
            if (re == null || re.isBlank()) {
                continue;
            }
            try {
                compiled.add(Pattern.compile(re));
            } catch (Exception ignored) {
                // ignore invalid regex
            }
        }
        denyUaPatterns = List.copyOf(compiled);
    }

    private String escapeJson(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static final class WafDecision {
        private final boolean blocked;
        private final String reason;

        private WafDecision(boolean blocked, String reason) {
            this.blocked = blocked;
            this.reason = reason;
        }

        static WafDecision allow() {
            return new WafDecision(false, null);
        }

        static WafDecision block(String reason) {
            return new WafDecision(true, reason);
        }
    }

    private static final class IpMatcher {
        static boolean matchesAny(String ip, List<String> rules) {
            for (String rule : rules) {
                if (rule == null || rule.isBlank()) {
                    continue;
                }
                if (matches(ip, rule.trim())) {
                    return true;
                }
            }
            return false;
        }

        static boolean matches(String ip, String rule) {
            if (rule.contains("/")) {
                return matchesIpv4Cidr(ip, rule);
            }
            return rule.equals(ip);
        }

        private static boolean matchesIpv4Cidr(String ip, String cidr) {
            try {
                String[] parts = cidr.split("/", 2);
                String baseIp = parts[0];
                int prefix = Integer.parseInt(parts[1]);
                if (prefix < 0 || prefix > 32) {
                    return false;
                }
                long ipLong = ipv4ToLong(ip);
                long baseLong = ipv4ToLong(baseIp);
                long mask = prefix == 0 ? 0 : 0xffffffffL << (32 - prefix);
                return (ipLong & mask) == (baseLong & mask);
            } catch (Exception e) {
                return false;
            }
        }

        private static long ipv4ToLong(String ip) {
            String[] octets = ip.split("\\.");
            if (octets.length != 4) {
                throw new IllegalArgumentException("not ipv4");
            }
            long out = 0;
            for (int i = 0; i < 4; i++) {
                int v = Integer.parseInt(octets[i]);
                if (v < 0 || v > 255) {
                    throw new IllegalArgumentException("bad octet");
                }
                out = (out << 8) | v;
            }
            return out;
        }
    }

    private static final class InMemoryTokenBucketRateLimiter {
        private final SecurityWafProperties props;
        private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
        private final AtomicLong lastCleanupNanos = new AtomicLong(System.nanoTime());

        private InMemoryTokenBucketRateLimiter(SecurityWafProperties props) {
            this.props = props;
        }

        boolean tryConsume(String key) {
            maybeCleanup();
            Bucket bucket = buckets.computeIfAbsent(
                    key, k -> new Bucket(props.getRateLimitCapacity(), props.getRateLimitRefillPerSecond()));
            return bucket.tryConsume(1);
        }

        private void maybeCleanup() {
            long now = System.nanoTime();
            long last = lastCleanupNanos.get();
            if (Duration.ofSeconds(30).toNanos() + last > now) {
                return;
            }
            if (!lastCleanupNanos.compareAndSet(last, now)) {
                return;
            }
            int max = Math.max(1, props.getRateLimitMaxEntries());
            if (buckets.size() <= max) {
                evictIdle(now);
                return;
            }
            // If over capacity, evict idle first, then random-sample eviction to cap memory.
            evictIdle(now);
            int target = max;
            int over = buckets.size() - target;
            if (over <= 0) {
                return;
            }
            int samples = Math.min(over, 512);
            List<String> keys = new ArrayList<>(samples);
            for (String k : buckets.keySet()) {
                keys.add(k);
                if (keys.size() >= samples) {
                    break;
                }
            }
            for (String k : keys) {
                buckets.remove(k);
                over--;
                if (over <= 0) {
                    break;
                }
            }
        }

        private void evictIdle(long nowNanos) {
            long idleNanos = Duration.ofSeconds(Math.max(1, props.getRateLimitIdleEvictSeconds()))
                    .toNanos();
            for (Map.Entry<String, Bucket> e : buckets.entrySet()) {
                Bucket b = e.getValue();
                if (nowNanos - b.lastSeenNanos.get() > idleNanos) {
                    buckets.remove(e.getKey(), b);
                }
            }
        }

        private static final class Bucket {
            private final int capacity;
            private final double refillPerSecond;
            private final AtomicLong tokens;
            private final AtomicLong lastRefillNanos;
            private final AtomicLong lastSeenNanos;

            private Bucket(int capacity, double refillPerSecond) {
                this.capacity = Math.max(1, capacity);
                this.refillPerSecond = Math.max(0.01, refillPerSecond);
                this.tokens = new AtomicLong(this.capacity);
                long now = System.nanoTime();
                this.lastRefillNanos = new AtomicLong(now);
                this.lastSeenNanos = new AtomicLong(now);
            }

            boolean tryConsume(long n) {
                long now = System.nanoTime();
                lastSeenNanos.set(now);
                refill(now);
                while (true) {
                    long cur = tokens.get();
                    if (cur < n) {
                        return false;
                    }
                    if (tokens.compareAndSet(cur, cur - n)) {
                        return true;
                    }
                }
            }

            private void refill(long nowNanos) {
                long last = lastRefillNanos.get();
                if (nowNanos <= last) {
                    return;
                }
                long elapsed = nowNanos - last;
                double toAdd = (elapsed / 1_000_000_000.0) * refillPerSecond;
                if (toAdd < 0.5) {
                    return;
                }
                long add = (long) toAdd;
                if (!lastRefillNanos.compareAndSet(last, nowNanos)) {
                    return;
                }
                while (true) {
                    long cur = tokens.get();
                    long next = Math.min(capacity, cur + add);
                    if (tokens.compareAndSet(cur, next)) {
                        return;
                    }
                }
            }
        }
    }
}
