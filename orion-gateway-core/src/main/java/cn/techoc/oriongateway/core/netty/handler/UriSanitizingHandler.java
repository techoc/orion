package cn.techoc.oriongateway.core.netty.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
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
@Component
public class UriSanitizingHandler extends ChannelInboundHandlerAdapter {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final boolean enabled;
    private final Set<String> pathPatterns;

    public UriSanitizingHandler() {
        this(true, "/api/**");
    }

    public UriSanitizingHandler(
            @Value("${orion.gateway.uri-sanitizing.enabled:true}") boolean enabled,
            @Value("${orion.gateway.uri-sanitizing.path-patterns:/api/**}") String pathPatterns) {
        this.enabled = enabled;
        this.pathPatterns = parsePatterns(pathPatterns);
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
        int queryIndex = uri.indexOf('?');

        if (queryIndex < 0) {
            ctx.fireChannelRead(msg);
            return;
        }

        String path = uri.substring(0, queryIndex);
        if (!matchesPath(path)) {
            ctx.fireChannelRead(msg);
            return;
        }

        String query = uri.substring(queryIndex + 1);
        String encodedQuery = urlEncode(query);

        if (!query.equals(encodedQuery)) {
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

    protected String urlEncode(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

}
