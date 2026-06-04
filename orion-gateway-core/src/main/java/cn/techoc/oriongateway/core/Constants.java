package cn.techoc.oriongateway.core;

import java.util.UUID;

public class Constants {
    public static final String UPSTREAM_START_TIME_ATTR = "orion.access.log.upstream.start.time";
    public static final String UPSTREAM_END_TIME_ATTR = "orion.access.log.upstream.end.time";

    /**
     * Exchange 属性键：标记请求是否经过 URI 清洗
     * <p>
     * 值为 Boolean.TRUE 时表示 URI 被 UriSanitizingHandler 清洗过
     */
    public static final String URI_SANITIZED_ATTR = "orion.uri.sanitized";
    /**
     * Exchange 属性键：清洗前的原始 URI
     * <p>
     * 值为 String 类型，仅在 URI 被清洗时才设置此属性。
     * 可用于日志记录、审计等场景，获取客户端实际发送的 URI。
     */
    public static final String URI_ORIGINAL_ATTR = "orion.uri.original";
    /**
     * URI 清洗标记 Header 前缀（Netty 层 → WebFlux 层的桥接机制）
     * <p>
     * 完整的 Header 名在 JVM 启动时动态生成（前缀 + 随机后缀），
     * 避免与业务 Header 冲突，同时防止外部伪造。
     * <p>
     * UriSanitizingHandler 在清洗 URI 时添加此 Header，
     * UriSanitizingMarkerWebFilter 读取后写入 Exchange 属性并移除该 Header，
     * 防止泄漏到下游服务。
     */
    private static final String URI_SANITIZED_HEADER_PREFIX = "X-Orion-Uri-Sanitized-";
    private static final String HEADER_SUFFIX = generateShortId();
    /**
     * URI 清洗标记 Header 名称（JVM 启动时动态生成，包含随机后缀）
     * <p>
     * 示例：{@code X-Orion-Uri-Sanitized-a3f2c1e0}
     * <ul>
     *   <li>动态后缀：每次 JVM 启动时生成，外部无法预知，防止伪造</li>
     *   <li>命名空间：X-Orion- 前缀避免与业务 Header 冲突</li>
     *   <li>临时性：该 Header 在 WebFilter 层被消费并移除，不会泄漏到下游</li>
     * </ul>
     */
    public static final String URI_SANITIZED_HEADER = URI_SANITIZED_HEADER_PREFIX + HEADER_SUFFIX;
    /**
     * 原始 URI 桥接 Header 前缀（携带清洗前的原始 URI）
     * <p>
     * 与 {@link #URI_SANITIZED_HEADER} 共享相同的随机后缀，确保配对一致。
     */
    private static final String URI_ORIGINAL_HEADER_PREFIX = "X-Orion-Uri-Original-";
    /**
     * 原始 URI 桥接 Header 名称（JVM 启动时动态生成，包含随机后缀）
     * <p>
     * 示例：{@code X-Orion-Uri-Original-a3f2c1e0}
     * <p>
     * UriSanitizingHandler 在清洗 URI 时将原始 URI 写入此 Header，
     * UriSanitizingMarkerWebFilter 读取后写入 Exchange 属性并移除该 Header。
     */
    public static final String URI_ORIGINAL_HEADER = URI_ORIGINAL_HEADER_PREFIX + HEADER_SUFFIX;

    /**
     * 生成短随机标识符（8 位十六进制），用于 Header 名称后缀
     */
    private static String generateShortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
