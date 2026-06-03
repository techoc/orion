package cn.techoc.oriongateway.core.netty.handler;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpRequest;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * URI 预处理器：在解码层将 URI 查询参数进行 URL 编码，
 * 确保后续 URI.create() 不会抛出 IllegalArgumentException。
 *
 * <p>使用 Spring 的 AntPathMatcher 进行路径匹配。
 *
 * <p>配置示例 (application.yaml)：
 * <pre>
 * orion:
 *   gateway:
 *     uri-sanitizing:
 *       enabled: true
 *       path-patterns: /api/**,/service/**
 * </pre>
 *
 * <p>用户可通过继承此类并添加 @Component 注解来覆盖默认实现。
 */
@Getter
@ChannelHandler.Sharable
public class UriSanitizingHandler extends ChannelInboundHandlerAdapter {

    private static final Logger log = LoggerFactory.getLogger(UriSanitizingHandler.class);
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final boolean enabled;
    private final Set<String> pathPatterns;
    private final Charset charset;

    public UriSanitizingHandler() {
        this(true, "/api/**", StandardCharsets.UTF_8);
    }

    public UriSanitizingHandler(boolean enabled, String pathPatterns) {
        this(enabled, pathPatterns, StandardCharsets.UTF_8);
    }

    public UriSanitizingHandler(
            boolean enabled,
            String pathPatterns,
            Charset charset) {
        this.enabled = enabled;
        this.pathPatterns = parsePatterns(pathPatterns);
        this.charset = charset;
    }

    private Set<String> parsePatterns(String patterns) {
        if (!StringUtils.hasText(patterns)) {
            return Collections.emptySet();
        }
        return Arrays.stream(patterns.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (!enabled || !(msg instanceof HttpRequest)) {
            ctx.fireChannelRead(msg);
            return;
        }

        HttpRequest request = (HttpRequest) msg;
        String uri = request.uri();

        if (log.isDebugEnabled()) {
            log.debug("orion.handler processing URI: {}", uri);
        }

        int queryIndex = uri.indexOf('?');

        if (queryIndex < 0) {
            ctx.fireChannelRead(msg);
            return;
        }

        String path = uri.substring(0, queryIndex);
        if (!matchesPath(path)) {
            if (log.isDebugEnabled()) {
                log.debug("orion.handler skipping URI (path '{}' not in patterns {})", path, pathPatterns);
            }
            ctx.fireChannelRead(msg);
            return;
        }

        String query = uri.substring(queryIndex + 1);
        String encodedQuery = urlEncode(query);

        if (!query.equals(encodedQuery)) {
            log.info("orion.handler sanitized URI: {} -> {}?{}", uri, path, encodedQuery);
            request.setUri(path + "?" + encodedQuery);
        }

        ctx.fireChannelRead(msg);
    }

    protected boolean matchesPath(String path) {
        if (pathPatterns.isEmpty()) {
            return true;
        }
        return pathPatterns.stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, path));
    }

    protected String urlEncode(String query) {
        if (!StringUtils.hasLength(query)) {
            return query;
        }

        MultiValueMap<String, String> params =
                UriComponentsBuilder.newInstance().query(query).build().getQueryParams();

        StringBuilder result = new StringBuilder(query.length() + 32);
        boolean first = true;
        for (Map.Entry<String, List<String>> entry : params.entrySet()) {
            String key = entry.getKey();
            for (String value : entry.getValue()) {
                if (!first) {
                    result.append('&');
                }
                result.append(key).append('=').append(URLEncoder.encode(value, charset));
                first = false;
            }
        }

        return result.toString();
    }

}
