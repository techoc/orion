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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.verify;

/**
 * UriSanitizingHandler 网关层深入测试
 * 测试 Handler 在真实场景中的行为
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
        @DisplayName("网关场景：REST API 路径参数包含非法字符")
        void testRestApiPathParamWithIllegalChars() {
            String originalUri = "/api/users/{userId}|details";
            String expectedUri = "/api/users/%7BuserId%7D%7Cdetails";

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
        @DisplayName("网关场景：微服务路由路径")
        void testMicroserviceRoutePath() {
            String originalUri = "/service-a/api/items|search";
            String expectedUri = "/service-a/api/items%7Csearch";

            HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, originalUri);

            handler.channelRead(ctx, request);

            assertEquals(expectedUri, request.uri());
            verify(ctx).fireChannelRead(request);
        }

        @Test
        @DisplayName("网关场景：WebSocket 路径")
        void testWebSocketPath() {
            String originalUri = "/ws/channel|room[123]";
            String expectedUri = "/ws/channel%7Croom%5B123%5D";

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
        @DisplayName("边界场景：超长 URI 路径")
        void testVeryLongUri() {
            StringBuilder longUri = new StringBuilder("/api/");
            for (int i = 0; i < 50; i++) {
                longUri.append("segment|").append(i).append("/");
            }

            HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, longUri.toString());

            handler.channelRead(ctx, request);

            // 验证所有 | 都被编码了
            assertFalse(request.uri().contains("|"), "编码后的 URI 不应该包含 |");
            verify(ctx).fireChannelRead(request);
        }

        @Test
        @DisplayName("边界场景：URI 仅包含非法字符")
        void testUriWithOnlyIllegalChars() {
            String originalUri = "|{}[]\\^`";
            String expectedUri = "%7C%7B%7D%5B%5D%5C%5E%60";

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
        @DisplayName("HTTP GET 请求")
        void testGetMethod() {
            HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/api/test|value");
            handler.channelRead(ctx, request);
            assertEquals("/api/test%7Cvalue", request.uri());
        }

        @Test
        @DisplayName("HTTP POST 请求")
        void testPostMethod() {
            HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/api/test|value");
            handler.channelRead(ctx, request);
            assertEquals("/api/test%7Cvalue", request.uri());
        }

        @Test
        @DisplayName("HTTP PUT 请求")
        void testPutMethod() {
            HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.PUT, "/api/test|value");
            handler.channelRead(ctx, request);
            assertEquals("/api/test%7Cvalue", request.uri());
        }

        @Test
        @DisplayName("HTTP DELETE 请求")
        void testDeleteMethod() {
            HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.DELETE, "/api/test|value");
            handler.channelRead(ctx, request);
            assertEquals("/api/test%7Cvalue", request.uri());
        }

        @Test
        @DisplayName("HTTP PATCH 请求")
        void testPatchMethod() {
            HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.PATCH, "/api/test|value");
            handler.channelRead(ctx, request);
            assertEquals("/api/test%7Cvalue", request.uri());
        }
    }

    @Nested
    @DisplayName("安全性测试")
    class SecurityTests {

        @Test
        @DisplayName("安全：路径遍历攻击尝试")
        void testPathTraversalAttempt() {
            String maliciousUri = "/api/../../etc/passwd";
            HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, maliciousUri);

            handler.channelRead(ctx, request);

            // UriSanitizingHandler 不处理路径遍历，只处理非法字符
            assertEquals(maliciousUri, request.uri());
            verify(ctx).fireChannelRead(request);
        }

        @Test
        @DisplayName("安全：SQL 注入尝试")
        void testSqlInjectionAttempt() {
            String maliciousUri = "/api/users?id=1' OR '1'='1";
            HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, maliciousUri);

            handler.channelRead(ctx, request);

            // 单引号不是非法字符，保持不变
            assertEquals(maliciousUri, request.uri());
            verify(ctx).fireChannelRead(request);
        }

        @Test
        @DisplayName("安全：XSS 攻击尝试")
        void testXssAttempt() {
            String maliciousUri = "/api/search?q=<script>alert('xss')</script>";
            HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, maliciousUri);

            handler.channelRead(ctx, request);

            // HTML 标签字符不是非法字符，保持不变
            assertEquals(maliciousUri, request.uri());
            verify(ctx).fireChannelRead(request);
        }
    }
}
