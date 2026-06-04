package cn.techoc.oriongateway.core.netty.handler;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpRequest;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * URI 预处理器：在解码层将 URI 查询参数进行 URL 编码，
 * 确保后续 URI.create() 不会抛出 IllegalArgumentException。
 *
 * <p>该 Handler 工作在 Netty Pipeline 中，拦截入站的 {@link HttpRequest} 消息，
 * 对匹配的请求路径中的查询参数执行 URL 编码（percent-encoding），
 * 从而避免因查询参数中包含非法字符（如中文、特殊符号等）导致下游解析失败。
 *
 * <p>核心处理流程：
 * <ol>
 *   <li>检查是否启用（enabled）以及消息是否为 {@link HttpRequest}</li>
 *   <li>解析 URI 中的路径部分，使用 {@link AntPathMatcher} 与配置的路径模式进行匹配</li>
 *   <li>对匹配请求的查询参数部分进行 URL 编码</li>
 *   <li>若编码后结果与原查询不同，则替换请求的 URI</li>
 * </ol>
 *
 * <p>使用 Spring 的 {@link AntPathMatcher} 进行路径匹配，支持 Ant 风格的通配符（如 {@code /api/**}）。
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
 * <p>用户可通过继承此类并添加 {@code @Component} 注解来覆盖默认实现。
 *
 * <p>注意：此类标注了 {@link ChannelHandler.Sharable}，意味着同一个实例可以被多个 Channel 共享，
 * 因此必须保证线程安全。当前实现中所有字段均为 final 或无状态，满足线程安全要求。
 *
 * @see ChannelInboundHandlerAdapter
 * @see AntPathMatcher
 * @see URLEncoder
 */
@Getter
@ChannelHandler.Sharable
public class UriSanitizingHandler extends ChannelInboundHandlerAdapter {

    /**
     * 日志记录器
     */
    private static final Logger log = LoggerFactory.getLogger(UriSanitizingHandler.class);

    /**
     * Ant 风格路径匹配器，用于判断请求路径是否匹配配置的模式
     */
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    /**
     * 是否启用 URI 清洗功能
     */
    private final boolean enabled;

    /**
     * 需要进行 URI 清洗的路径模式集合（Ant 风格），为空时匹配所有路径
     */
    private final Set<String> pathPatterns;

    /**
     * URL 编码使用的字符集，默认为 UTF-8
     */
    private final Charset charset;

    /**
     * 默认构造函数：启用清洗功能，默认匹配 {@code /api/**} 路径，使用 UTF-8 编码。
     */
    public UriSanitizingHandler() {
        this(true, "/api/**", StandardCharsets.UTF_8);
    }

    /**
     * 构造函数：指定启用状态和路径模式，使用 UTF-8 编码。
     *
     * @param enabled      是否启用 URI 清洗
     * @param pathPatterns 逗号分隔的路径模式字符串，例如 {@code "/api/**,/service/**"}
     */
    public UriSanitizingHandler(boolean enabled, String pathPatterns) {
        this(enabled, pathPatterns, StandardCharsets.UTF_8);
    }

    /**
     * 全参数构造函数。
     *
     * @param enabled      是否启用 URI 清洗
     * @param pathPatterns 逗号分隔的路径模式字符串，例如 {@code "/api/**,/service/**"}
     * @param charset      URL 编码使用的字符集
     */
    public UriSanitizingHandler(
            boolean enabled,
            String pathPatterns,
            Charset charset) {
        this.enabled = enabled;
        this.pathPatterns = parsePatterns(pathPatterns);
        this.charset = charset;
    }

    /**
     * 将逗号分隔的路径模式字符串解析为去重、去空白后的集合。
     *
     * @param patterns 逗号分隔的路径模式字符串
     * @return 解析后的路径模式集合；若输入为空则返回空集合
     */
    private Set<String> parsePatterns(String patterns) {
        // 如果输入为空或仅包含空白字符，返回空集合（后续 matchesPath 会匹配所有路径）
        if (!StringUtils.hasText(patterns)) {
            return Collections.emptySet();
        }
        // 按逗号拆分 → 去除每个模式两端的空白 → 过滤掉空串 → 收集为去重的 Set
        return Arrays.stream(patterns.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
    }

    /**
     * 处理入站的 Channel 读取事件。
     *
     * <p>对 {@link HttpRequest} 类型的消息进行 URI 清洗：
     * <ol>
     *   <li>若未启用或消息非 HttpRequest，直接透传</li>
     *   <li>若 URI 中不包含查询参数（无 {@code ?}），直接透传</li>
     *   <li>若请求路径不匹配配置的模式，直接透传</li>
     *   <li>对查询参数进行 URL 编码，若编码后与原值不同则替换 URI</li>
     * </ol>
     *
     * @param ctx Channel 处理器上下文
     * @param msg 入站消息对象
     */
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        // 1. 前置检查：如果 URI 清洗功能未启用，或者当前消息不是 HTTP 请求，直接透传给下一个 Handler
        if (!enabled || !(msg instanceof HttpRequest)) {
            ctx.fireChannelRead(msg);
            return;
        }

        // 2. 将消息强转为 HttpRequest，获取原始 URI 字符串（包含路径和查询参数）
        HttpRequest request = (HttpRequest) msg;
        String uri = request.uri();

        // 3. 调试日志：记录当前正在处理的 URI（仅在 DEBUG 级别输出，避免生产环境影响性能）
        if (log.isDebugEnabled()) {
            log.debug("orion.handler processing URI: {}", uri);
        }

        // 4. 查找 '?' 的位置，用于分离路径部分和查询参数部分
        int queryIndex = uri.indexOf('?');

        // 5. 如果 URI 中没有 '?'，说明没有查询参数，无需清洗，直接透传
        if (queryIndex < 0) {
            ctx.fireChannelRead(msg);
            return;
        }

        // 6. 截取 '?' 之前的路径部分，用于后续与配置的路径模式进行匹配
        String path = uri.substring(0, queryIndex);

        // 7. 判断请求路径是否匹配配置的路径模式（如 /api/**），不匹配则跳过清洗
        if (!matchesPath(path)) {
            if (log.isDebugEnabled()) {
                log.debug("orion.handler skipping URI (path '{}' not in patterns {})", path, pathPatterns);
            }
            ctx.fireChannelRead(msg);
            return;
        }

        // 8. 截取 '?' 之后的查询参数部分（不含 '?' 本身）
        String query = uri.substring(queryIndex + 1);

        // 9. 对查询参数进行 URL 编码：手动按 & 和 = 拆分键值对，对 value 先解码再重新编码
        String encodedQuery = urlEncode(query);

        // 10. 仅在编码结果与原始查询不同时才替换 URI，避免不必要的对象修改
        if (!query.equals(encodedQuery)) {
            log.info("orion.handler sanitized URI: {} -> {}?{}", uri, path, encodedQuery);
            // 将编码后的查询参数拼接回路径，替换原始 URI
            request.setUri(path + "?" + encodedQuery);
        }

        // 11. 无论是否修改了 URI，都继续传递给 Pipeline 中的下一个 Handler
        ctx.fireChannelRead(msg);
    }

    /**
     * 判断请求路径是否匹配任一配置的路径模式。
     *
     * <p>若 {@code pathPatterns} 为空，则匹配所有路径。
     *
     * @param path 请求路径（不含查询参数）
     * @return 匹配返回 {@code true}，否则返回 {@code false}
     */
    protected boolean matchesPath(String path) {
        // 如果未配置任何路径模式，则默认不匹配所有路径
        if (pathPatterns.isEmpty()) {
            return false;
        }
        // 遍历所有配置的路径模式，使用 AntPathMatcher 进行 Ant 风格匹配（支持 *, **, ? 通配符）
        return pathPatterns.stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, path));
    }

    /**
     * 对查询参数字符串进行 URL 编码。
     *
     * <p>手动按 {@code &} 和 {@code =} 拆分键值对，不使用 UriComponentsBuilder，
     * 避免参数值中包含 {@code ?} 时被截断。对 value 先解码再重新编码，确保编码一致性。
     *
     * @param query 原始查询参数字符串（不含 {@code ?}）
     * @return 编码后的查询参数字符串
     */
    protected String urlEncode(String query) {
        // 1. 空值检查：如果查询字符串为空或 null，直接返回原值
        if (!StringUtils.hasLength(query)) {
            return query;
        }

        // 2. 创建 StringBuilder，预分配容量 = 原始长度 + 32（为编码后的字符增长预留缓冲空间）
        StringBuilder result = new StringBuilder(query.length() + 32);
        // 3. 记录查询字符串总长度，作为循环终止条件
        int len = query.length();
        // 4. 当前扫描位置指针，从 0 开始逐步向后推进
        int pos = 0;
        // 5. 标记是否为第一个参数，用于控制是否在参数前添加 '&' 分隔符
        boolean first = true;

        // 6. 循环扫描整个查询字符串，逐个提取 key=value 键值对
        while (pos < len) {
            // 6a. 从当前位置查找最近的 '=' 号位置（键值分隔符）
            int eqIdx = query.indexOf('=', pos);
            // 6b. 从当前位置查找最近的 '&' 号位置（参数分隔符）
            int ampIdx = query.indexOf('&', pos);

            // 6c. 判断 '=' 是否属于当前参数：
            //     - 如果 '=' 不存在（eqIdx < 0），说明当前参数没有值
            //     - 如果 '=' 在 '&' 之后（eqIdx > ampIdx），说明 '=' 属于下一个参数，当前参数无值
            if (eqIdx < 0 || (ampIdx >= 0 && eqIdx > ampIdx)) {
                eqIdx = -1; // 标记当前参数没有有效的 '='
            }

            String key;      // 参数名
            String value;    // 参数值
            boolean hasEquals; // 是否包含 '=' 号（用于区分 key= 和 key 两种形式）

            if (eqIdx >= 0) {
                // 7a. 存在有效的 '='：提取 key 和 value
                key = query.substring(pos, eqIdx);                           // key: 从当前位置到 '=' 之前
                int valueEnd = (ampIdx >= 0) ? ampIdx : len;                 // value 的结束位置：'&' 之前或字符串末尾
                value = query.substring(eqIdx + 1, valueEnd);                // value: '=' 之后到 '&' 之前
                pos = valueEnd + 1;                                          // 移动指针到下一个参数的起始位置
                hasEquals = true;                                            // 标记存在 '=' 号
            } else if (ampIdx >= 0) {
                // 7b. 没有 '=' 但有 '&'：当前参数只有 key 没有 value（如 "flag&name=xx"）
                key = query.substring(pos, ampIdx);                          // key: 从当前位置到 '&' 之前
                value = "";                                                  // value 为空
                pos = ampIdx + 1;                                            // 移动指针跳过 '&'
                hasEquals = false;                                           // 标记无 '=' 号
            } else {
                // 7c. 既没有 '=' 也没有 '&'：剩余部分全部作为 key（最后一个参数且无值）
                key = query.substring(pos);                                  // key: 从当前位置到字符串末尾
                value = "";                                                  // value 为空
                pos = len;                                                   // 移动指针到末尾，结束循环
                hasEquals = false;                                           // 标记无 '=' 号
            }

            // 8. 跳过空参数：当 key 和 value 都为空时（如连续的 "&&" 产生的空段），直接跳过
            if (key.isEmpty() && value.isEmpty()) {
                continue;
            }

            // 9. 对 value 进行编码：先解码再编码，确保编码一致性
            //    - 如果 value 已经是部分编码的（如 %E4%BD），先解码为原始字符再重新编码
            //    - 如果 value 包含不完整的 percent-encoded 序列（如 %2），解码会抛 IllegalArgumentException
            //      此时直接对原始值进行编码
            String encodedValue;
            try {
                encodedValue = URLEncoder.encode(URLDecoder.decode(value, charset), charset);
            } catch (IllegalArgumentException e) {
                encodedValue = URLEncoder.encode(value, charset);
            }

            // 10. 拼接参数分隔符 '&'（第一个参数前不加 '&'）
            if (!first) {
                result.append('&');
            }
            // 11. 拼接参数名
            result.append(key);
            // 12. 如果原始参数包含 '='，则补上 '=' 号（保留 key= 和 key 的区别）
            if (hasEquals) {
                result.append('=');
            }
            // 13. 拼接编码后的参数值
            result.append(encodedValue);
            // 14. 标记已不是第一个参数
            first = false;
        }

        // 15. 返回拼接完成的编码后查询字符串
        return result.toString();
    }

}
