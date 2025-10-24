package cn.techoc.oriongateway.core.loggging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import java.net.InetSocketAddress;
import java.net.URI;

import static org.mockito.Mockito.*;

public class AccessLogWebFilterTest {

    @InjectMocks
    private AccessLogWebFilter filter;

    @Mock
    private AccessLogProperties props;

    @Mock
    private Logger logger;

    private AutoCloseable closeable;

    @BeforeEach
    public void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
        when(props.getPattern()).thenReturn(
                "$remote_addr - $remote_user [$time_local] \\\"$request\\\" $status $body_bytes_sent " +
                        "\\\"$http_referer\\\" \\\"$http_user_agent\\\" \\\"$http_x_forwarded_for\\\" $upstream_addr " +
                        "ups_resp_time: $upstream_response_time request_time: $request_time"
        );

        // 现在可以使用带logger参数的构造函数，便于测试
        filter = new AccessLogWebFilter(props, logger);
    }

    @AfterEach
    public void tearDown() throws Exception {
        closeable.close();
    }

    /**
     * 正常情况下的日志输出验证
     */
    @Test
    public void testLogAccess_NormalCase() {
        // 构造请求
        MockServerHttpRequest request = MockServerHttpRequest.get("/test")
                .remoteAddress(new InetSocketAddress("localhost", 8080))
                .header("Referer", "https://example.com")
                .header("User-Agent", "Mozilla/5.0")
                .header("X-Forwarded-For", "10.0.0.1")
                .header("Authorization", "Basic b3Jpb246b3Jpb24=")
                .build();

        // 构造响应
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getResponse().setStatusCode(HttpStatus.OK);
        exchange.getResponse().getHeaders().setContentLength(1024L);

        // 设置路由URI
        URI routeUri = URI.create("http://backend-service:8080");
        exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR, routeUri);

        // 执行方法
        long startTime = System.currentTimeMillis() - 500; // 模拟500ms前开始处理
        filter.logAccess(exchange, startTime);

        // 验证日志输出
        verify(logger, times(1)).info(anyString());
    }

    /**
     * remoteAddress 为 null 的场景
     */
    @Test
    public void testLogAccess_RemoteAddressIsNull() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/test").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getResponse().setStatusCode(HttpStatus.NOT_FOUND);

        long startTime = System.currentTimeMillis() - 300;
        filter.logAccess(exchange, startTime);

        verify(logger, times(1)).info(anyString());
    }

    /**
     * routeUri 为 null 的场景
     */
    @Test
    public void testLogAccess_RouteUriIsNull() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/data")
                .remoteAddress(InetSocketAddress.createUnresolved("127.0.0.1", 9090))
                .build();

        ServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getResponse().setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
        exchange.getAttributes().remove(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR); // 显式移除

        long startTime = System.currentTimeMillis() - 1000;
        filter.logAccess(exchange, startTime);

        verify(logger, times(1)).info(anyString());
    }

    /**
     * 响应对象或状态码为 null 的场景
     */
    @Test
    public void testLogAccess_ResponseOrStatusIsNull() {
        MockServerHttpRequest request = MockServerHttpRequest.delete("/delete")
                .remoteAddress(InetSocketAddress.createUnresolved("172.16.0.1", 80))
                .build();

        ServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getResponse().setStatusCode(null); // 状态码为null

        long startTime = System.currentTimeMillis() - 200;
        filter.logAccess(exchange, startTime);

        verify(logger, times(1)).info(anyString());
    }

    /**
     * 缺失部分 header 字段的场景
     */
    @Test
    public void testLogAccess_MissingHeaders() {
        MockServerHttpRequest request = MockServerHttpRequest.options("/options")
                .remoteAddress(InetSocketAddress.createUnresolved("10.10.10.10", 8080))
                .build();

        ServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getResponse().setStatusCode(HttpStatus.ACCEPTED);
        exchange.getResponse().getHeaders().setContentLength(-1L); // 表示无 content-length

        long startTime = System.currentTimeMillis() - 600;
        filter.logAccess(exchange, startTime);

        verify(logger, times(1)).info(anyString());
    }
}