package cn.techoc.oriongateway.core.trace;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Collections;
import java.util.Set;

/**
 * 链路追踪配置属性类
 * <p>
 * 该类定义了全链路追踪功能的各种配置选项，包括：
 * - 是否启用追踪功能
 * - 各个阶段是否记录请求/响应的头部和体
 * - 最大记录体大小
 * - 需要排除的头部信息
 * - 日志分组选项
 */
@Data
@ConfigurationProperties(prefix = "gateway.link-tracing")
public class LinkTracingProperties {

    /**
     * 全局启用或禁用链路追踪功能
     * 默认值：false（禁用）
     */
    private boolean enabled = false;

    /**
     * 追踪上游请求进入网关时的信息（记录请求行和头部）
     * 如果 traceRequestBody 为 true，也会记录请求体
     * 默认值：false
     */
    private boolean traceRequestPrefix = false;

    /**
     * 追踪上游请求经过网关处理后要离开网关进入下游时的信息（记录请求行和头部）
     * 如果 traceRequestBody 为 true，也会记录请求体
     * 默认值：false
     */
    private boolean traceRequestSuffix = false;

    /**
     * 追踪下游响应进入网关时的信息（记录状态行和头部）
     * 如果 traceResponseBody 为 true，也会记录响应体
     * 默认值：false
     */
    private boolean traceResponsePrefix = false;

    /**
     * 追踪下游响应经过网关处理后离开网关时的信息（记录状态行和头部）
     * 如果 traceResponseBody 为 true，也会记录响应体
     * 默认值：false
     */
    private boolean traceResponseSuffix = false;

    /**
     * 记录请求体内容
     * 需要 traceRequestPrefix 或 traceRequestSuffix 至少有一个为 true 才会生效
     * 默认值：false
     */
    private boolean traceRequestBody = false;

    /**
     * 记录响应体内容
     * 需要 traceResponsePrefix 或 traceResponseSuffix 至少有一个为 true 才会生效
     * 默认值：false
     */
    private boolean traceResponseBody = false;

    /**
     * 最大记录体大小（以字节为单位）
     * 超过此大小的体将被截断或仅记录消息
     * 设置为 -1 表示无限制（谨慎使用）
     * 默认值：10KB (1024 * 10)
     */
    private int maxBodySize = 1024 * 10;

    /**
     * 需要从日志中排除的HTTP头部（不区分大小写）
     * 对于敏感头部如 Authorization、Cookie 等非常有用
     * 默认值：空集合
     */
    private Set<String> excludeHeaders = Collections.emptySet();

    /**
     * 记录请求参数（URL查询参数）
     * 默认值：false
     */
    private boolean traceRequestParams = false;

    /**
     * 记录请求头信息
     * 默认值：false
     */
    private boolean traceRequestHeaders = false;

    /**
     * 记录响应头信息
     * 默认值：false
     */
    private boolean traceResponseHeaders = false;

    /**
     * 是否按阶段分组日志
     * 当为 true 时，同一阶段的所有日志会被分组在一起
     * 当为 false 时，所有日志按顺序打印
     * 默认值：false
     */
    private boolean groupLogsByPhase = false;
}