package cn.techoc.oriongateway.core.integration;

import cn.techoc.oriongateway.core.netty.handler.UriSanitizingHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

/**
 * UriSanitizingHandler 网关层深入测试
 * 测试 Handler 在真实场景中的行为
 *
 * <p>注意：现在只编码查询参数，路径保持不变
 */
@DisplayName("UriSanitizingHandler 网关层深入测试")
class UriSanitizingHandlerGatewayDeepTest {

    private UriSanitizingHandler handler;

    @Mock
    private ChannelHandlerContext ctx;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        handler = new UriSanitizingHandler();
    }

    @Nested
    @DisplayName("网关请求场景测试")
    class GatewayRequestScenarios {

        @Test
        @DisplayName("网关场景：REST API 路径参数包含非法字符 - 路径不变，查询参数编码")
        void testRestApiPathParamWithIllegalChars() {
            // 路径中的非法字符保持不变（因为没有查询参数）
            String originalUri = "/api/users/{userId}|details";
            String expectedUri = "/api/users/{userId}|details";

            HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, originalUri);

            handler.channelRead(ctx, request);

            assertEquals(expectedUri, request.uri());
            verify(ctx).fireChannelRead(request);
        }

        @Test
        @DisplayName("网关场景：查询参数包含非法字符")
        void testQueryParamsWithIllegalChars() {
            String originalUri = "/api/search?q=test|value&filter={category}";
            String expectedUri = "/api/search?q=test%7Cvalue&filter=%7Bcategory%7D";

            HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, originalUri);

            handler.channelRead(ctx, request);

            assertEquals(expectedUri, request.uri());
            verify(ctx).fireChannelRead(request);
        }

        @Test
        @DisplayName("网关场景：微服务路由路径（无查询参数）- 路径不变")
        void testMicroserviceRoutePath() {
            String originalUri = "/service-a/api/items|search";
            String expectedUri = "/service-a/api/items|search";

            HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, originalUri);

            handler.channelRead(ctx, request);

            // 默认 path-patterns 是 /api/**，不匹配 /service-a/**，所以不变
            assertEquals(expectedUri, request.uri());
            verify(ctx).fireChannelRead(request);
        }

        @Test
        @DisplayName("网关场景：WebSocket 路径 - 路径不变")
        void testWebSocketPath() {
            String originalUri = "/ws/channel|room[123]";
            String expectedUri = "/ws/channel|room[123]";

            HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, originalUri);

            handler.channelRead(ctx, request);

            // 默认 path-patterns 是 /api/**，不匹配 /ws/**，所以不变
            assertEquals(expectedUri, request.uri());
            verify(ctx).fireChannelRead(request);
        }

        @Test
        @DisplayName("网关场景：查询参数包含非法字符，路径保持不变")
        void testQueryParamsWithIllegalCharsAndPathUnchanged() {
            String originalUri = "/api/items|detail?q=test|value";
            String expectedUri = "/api/items|detail?q=test%7Cvalue";

            HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, originalUri);

            handler.channelRead(ctx, request);

            assertEquals(expectedUri, request.uri());
            verify(ctx).fireChannelRead(request);
        }
    }

    @Nested
    @DisplayName("边界场景测试")
    class EdgeCases {

        @Test
        @DisplayName("边界场景：超长 URI 路径 - 路径不变")
        void testVeryLongUri() {
            StringBuilder longUri = new StringBuilder("/api/");
            for (int i = 0; i < 50; i++) {
                longUri.append("segment|").append(i).append("/");
            }

            HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, longUri.toString());

            handler.channelRead(ctx, request);

            // 路径中的 | 不应被编码（没有查询参数）
            assertEquals(longUri.toString(), request.uri());
            verify(ctx).fireChannelRead(request);
        }

        @Test
        @DisplayName("边界场景：URI 仅包含路径无查询参数")
        void testUriWithOnlyPathNoQuery() {
            String originalUri = "/api/test|value";
            String expectedUri = "/api/test|value";

            HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, originalUri);

            handler.channelRead(ctx, request);

            assertEquals(expectedUri, request.uri());
            verify(ctx).fireChannelRead(request);
        }

        @Test
        @DisplayName("边界场景：空 URI")
        void testEmptyUri() {
            HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "");

            handler.channelRead(ctx, request);

            assertEquals("", request.uri());
            verify(ctx).fireChannelRead(request);
        }
    }

    @Nested
    @DisplayName("HTTP 方法覆盖测试")
    class HttpMethodTests {

        @Test
        @DisplayName("HTTP GET 请求 - 查询参数包含非法字符")
        void testGetMethod() {
            HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/api/test?q=test|value");
            handler.channelRead(ctx, request);
            assertEquals("/api/test?q=test%7Cvalue", request.uri());
        }

        @Test
        @DisplayName("HTTP POST 请求 - 查询参数包含非法字符")
        void testPostMethod() {
            HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/api/test?q=test|value");
            handler.channelRead(ctx, request);
            assertEquals("/api/test?q=test%7Cvalue", request.uri());
        }

        @Test
        @DisplayName("HTTP PUT 请求 - 查询参数包含非法字符")
        void testPutMethod() {
            HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.PUT, "/api/test?q=test|value");
            handler.channelRead(ctx, request);
            assertEquals("/api/test?q=test%7Cvalue", request.uri());
        }

        @Test
        @DisplayName("HTTP DELETE 请求 - 查询参数包含非法字符")
        void testDeleteMethod() {
            HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.DELETE, "/api/test?q=test|value");
            handler.channelRead(ctx, request);
            assertEquals("/api/test?q=test%7Cvalue", request.uri());
        }

        @Test
        @DisplayName("HTTP PATCH 请求 - 查询参数包含非法字符")
        void testPatchMethod() {
            HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.PATCH, "/api/test?q=test|value");
            handler.channelRead(ctx, request);
            assertEquals("/api/test?q=test%7Cvalue", request.uri());
        }
    }

    @Nested
    @DisplayName("安全性测试")
    class SecurityTests {

        @Test
        @DisplayName("安全：路径遍历攻击尝试 - 路径保持不变")
        void testPathTraversalAttempt() {
            String maliciousUri = "/api/../../etc/passwd";
            HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, maliciousUri);

            handler.channelRead(ctx, request);

            // UriSanitizingHandler 只编码查询参数，路径保持不变
            assertEquals(maliciousUri, request.uri());
            verify(ctx).fireChannelRead(request);
        }

        @Test
        @DisplayName("安全：SQL 注入尝试 - 单引号会被 URL 编码")
        void testSqlInjectionAttempt() {
            String maliciousUri = "/api/users?id=1' OR '1'='1";
            // URLEncoder 对空格编码为 +
            String expectedUri = "/api/users?id=1%27+OR+%271%27%3D%271";

            HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, maliciousUri);

            handler.channelRead(ctx, request);

            assertEquals(expectedUri, request.uri());
            verify(ctx).fireChannelRead(request);
        }

        @Test
        @DisplayName("安全：XSS 攻击尝试 - 特殊字符会被 URL 编码")
        void testXssAttempt() {
            String maliciousUri = "/api/search?q=<script>alert('xss')</script>";
            String expectedUri = "/api/search?q=%3Cscript%3Ealert%28%27xss%27%29%3C%2Fscript%3E";

            HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, maliciousUri);

            handler.channelRead(ctx, request);

            assertEquals(expectedUri, request.uri());
            verify(ctx).fireChannelRead(request);
        }
    }
}
