package cn.techoc.oriongateway.core.filter;

import cn.techoc.oriongateway.core.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.lang.NonNull;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.LinkedHashSet;

/**
 * URI 清洗标记桥接 WebFilter
 * <p>
 * 该 Filter 的职责是将 Netty Pipeline 层 {@link cn.techoc.oriongateway.core.netty.handler.UriSanitizingHandler}
 * 设置的桥接 Header 转换为 {@link ServerWebExchange} 属性，并移除所有桥接 Header 防止泄漏到下游服务。
 * <p>
 * 转换的属性：
 * <ul>
 *   <li>{@link Constants#URI_SANITIZED_ATTR} — 标记请求是否被 URI 清洗过（Boolean.TRUE）</li>
 *   <li>{@link Constants#URI_ORIGINAL_ATTR} — 清洗前的原始 URI（String），可用于日志记录、审计等场景</li>
 *   <li>{@link ServerWebExchangeUtils#GATEWAY_ORIGINAL_REQUEST_URL_ATTR} — 替换为真正的原始 URI，
 *       因为 UriSanitizingHandler 在 Netty 层已经修改了 URI，导致 SCG 记录的"原始 URL"实际是清洗后的</li>
 * </ul>
 * <p>
 * 使用方式：
 * <pre>
 * Boolean sanitized = exchange.getAttribute(Constants.URI_SANITIZED_ATTR);
 * if (Boolean.TRUE.equals(sanitized)) {
 *     String originalUri = exchange.getAttribute(Constants.URI_ORIGINAL_ATTR);
 *     // originalUri 就是客户端实际发送的原始 URI
 * }
 * // 或者直接使用 SCG 标准属性（已替换为真实原始 URI）
 * LinkedHashSet&lt;URI&gt; originalUrls = exchange.getAttribute(
 *     ServerWebExchangeUtils.GATEWAY_ORIGINAL_REQUEST_URL_ATTR);
 * </pre>
 * <p>
 * 设计说明：
 * <ul>
 *   <li>UriSanitizingHandler 运行在 Netty Pipeline 层，无法直接访问 ServerWebExchange</li>
 *   <li>通过 HTTP Header 作为临时桥接通道，是 Netty → WebFlux 跨层信息传递的标准实践</li>
 *   <li>该 Filter 必须在其他需要判断 URI 清洗状态的 Filter 之前执行</li>
 *   <li>桥接 Header 名称在 JVM 启动时动态生成（带随机后缀），防止外部伪造和与业务 Header 冲突</li>
 * </ul>
 */
@Order(Ordered.LOWEST_PRECEDENCE - 1)
public class UriSanitizingMarkerWebFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(UriSanitizingMarkerWebFilter.class);

    @Override
    @NonNull
    public Mono<Void> filter(@NonNull ServerWebExchange exchange, @NonNull WebFilterChain chain) {
        String sanitizedHeader = exchange.getRequest().getHeaders().getFirst(Constants.URI_SANITIZED_HEADER);

        if ("true".equalsIgnoreCase(sanitizedHeader)) {
            // 将标记写入 Exchange 属性，供后续 Filter 读取
            exchange.getAttributes().put(Constants.URI_SANITIZED_ATTR, Boolean.TRUE);

            // 读取原始 URI 并写入 Exchange 属性
            String originalUri = exchange.getRequest().getHeaders().getFirst(Constants.URI_ORIGINAL_HEADER);
            if (originalUri != null) {
                exchange.getAttributes().put(Constants.URI_ORIGINAL_ATTR, originalUri);

                // 替换 SCG 的 GATEWAY_ORIGINAL_REQUEST_URL_ATTR 为真正的原始 URI
                // 因为 UriSanitizingHandler 在 Netty 层已修改了 URI，
                // SCG 的 RouteToRequestUrlFilter 记录的"原始 URL"实际是清洗后的，需要修正
                restoreGatewayOriginalRequestUrl(exchange, originalUri);
            }

            // 移除所有桥接 Header，防止泄漏到下游服务
            ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                    .headers(headers -> {
                        headers.remove(Constants.URI_SANITIZED_HEADER);
                        headers.remove(Constants.URI_ORIGINAL_HEADER);
                    })
                    .build();

            if (log.isDebugEnabled()) {
                log.debug("URI sanitized detected. Original: [{}], Sanitized: [{}]",
                        originalUri, exchange.getRequest().getURI());
            }

            return chain.filter(exchange.mutate().request(mutatedRequest).build());
        }

        return chain.filter(exchange);
    }

    /**
     * 替换 SCG 的 {@link ServerWebExchangeUtils#GATEWAY_ORIGINAL_REQUEST_URL_ATTR} 为真正的原始 URI。
     * <p>
     * SCG 的 RouteToRequestUrlFilter 在路由匹配阶段会将请求 URL 追加到
     * GATEWAY_ORIGINAL_REQUEST_URL_ATTR（类型为 {@link LinkedHashSet}&lt;{@link URI}&gt;）。
     * 但由于 UriSanitizingHandler 在 Netty 层已经修改了 URI，
     * SCG 看到的"原始 URL"其实是清洗后的，而非客户端真正发送的 URL。
     * <p>
     * 该方法在集合头部插入真正的原始 URI，并移除被污染的清洗后 URI，
     * 确保后续组件通过此属性获取到的是真实的客户端请求 URL。
     *
     * @param exchange    ServerWebExchange 对象
     * @param originalUri 客户端实际发送的原始 URI 字符串
     */
    @SuppressWarnings("unchecked")
    private void restoreGatewayOriginalRequestUrl(ServerWebExchange exchange, String originalUri) {
        try {
            URI originalUriObj = URI.create(originalUri);

            LinkedHashSet<URI> originalUrls = exchange.getAttribute(
                    ServerWebExchangeUtils.GATEWAY_ORIGINAL_REQUEST_URL_ATTR);

            if (originalUrls == null) {
                // 属性尚未被 SCG 设置（可能在 RouteToRequestUrlFilter 之前执行），
                // 创建新集合并放入原始 URI
                originalUrls = new LinkedHashSet<>();
                originalUrls.add(originalUriObj);
                exchange.getAttributes().put(
                        ServerWebExchangeUtils.GATEWAY_ORIGINAL_REQUEST_URL_ATTR, originalUrls);
            } else {
                // SCG 已设置该属性，需要替换集合中清洗后的 URI 为真正的原始 URI
                // 策略：在头部插入原始 URI，移除清洗后的 URI
                URI sanitizedUri = exchange.getRequest().getURI();
                LinkedHashSet<URI> restored = new LinkedHashSet<>();
                restored.add(originalUriObj);
                for (URI uri : originalUrls) {
                    // 跳过与清洗后 URI 相同的条目（即被污染的记录）
                    if (!uri.equals(sanitizedUri)) {
                        restored.add(uri);
                    }
                }
                exchange.getAttributes().put(
                        ServerWebExchangeUtils.GATEWAY_ORIGINAL_REQUEST_URL_ATTR, restored);
            }
        } catch (Exception e) {
            // URI 解析失败时仅记录警告，不中断请求处理
            log.warn("Failed to restore GATEWAY_ORIGINAL_REQUEST_URL_ATTR for original URI: [{}]",
                    originalUri, e);
        }
    }

}
