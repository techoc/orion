package cn.techoc.oriongateway.core.trace;

import org.springframework.web.server.ServerWebExchange;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 请求上下文持有者，用于在不同阶段之间传递请求信息
 */
public class LinkTracingContextHolder {

    private static final Map<String, ServerWebExchange> EXCHANGE_MAP = new ConcurrentHashMap<>();
    // 缓存请求体，便于在不同阶段（如 Request_suffix）输出
    private static final Map<String, String> REQUEST_BODY_MAP = new ConcurrentHashMap<>();

    /**
     * 存储请求交换对象
     *
     * @param requestId 请求ID
     * @param exchange  请求交换对象
     */
    public static void setExchange(String requestId, ServerWebExchange exchange) {
        EXCHANGE_MAP.put(requestId, exchange);
    }

    /**
     * 获取请求交换对象
     *
     * @param requestId 请求ID
     * @return 请求交换对象
     */
    public static ServerWebExchange getExchange(String requestId) {
        return EXCHANGE_MAP.get(requestId);
    }

    /**
     * 移除请求交换对象
     *
     * @param requestId 请求ID
     */
    public static void removeExchange(String requestId) {
        EXCHANGE_MAP.remove(requestId);
    }

    /**
     * 存储请求体内容
     *
     * @param requestId 请求ID
     * @param body      请求体字符串
     */
    public static void setRequestBody(String requestId, String body) {
        REQUEST_BODY_MAP.put(requestId, body);
    }

    /**
     * 获取已缓存的请求体内容
     *
     * @param requestId 请求ID
     * @return 请求体字符串，若不存在则返回null
     */
    public static String getRequestBody(String requestId) {
        return REQUEST_BODY_MAP.get(requestId);
    }

    /**
     * 移除已缓存的请求体内容
     *
     * @param requestId 请求ID
     */
    public static void removeRequestBody(String requestId) {
        REQUEST_BODY_MAP.remove(requestId);
    }
}
