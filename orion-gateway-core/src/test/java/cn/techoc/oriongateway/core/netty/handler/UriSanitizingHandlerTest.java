package cn.techoc.oriongateway.core.netty.handler;

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

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;

@DisplayName("UriSanitizingHandler 测试")
class UriSanitizingHandlerTest {

    private UriSanitizingHandler handler;

    @Mock
    private ChannelHandlerContext ctx;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        handler = new UriSanitizingHandler();
    }

    @Nested
    @DisplayName("urlEncode 方法测试")
    class UrlEncodeTests {

        @Test
        @DisplayName("正常情况：查询参数中包含单个非法字符 |")
        void testUrlEncode_QueryParamWithPipeChar() throws Exception {
            String query = "q=test|value";
            // 使用 split("=", 2) 只在第一个 = 处分割，所以 key=q, value=test|value
            String expected = "q=test%7Cvalue";

            Method method = UriSanitizingHandler.class.getDeclaredMethod("urlEncode", String.class);
            method.setAccessible(true);
            String result = (String) method.invoke(handler, query);

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("正常情况：查询参数中包含大括号 { 和 }")
        void testUrlEncode_QueryParamWithCurlyBraces() throws Exception {
            String query = "filter={type}";
            String expected = "filter=%7Btype%7D";

            Method method = UriSanitizingHandler.class.getDeclaredMethod("urlEncode", String.class);
            method.setAccessible(true);
            String result = (String) method.invoke(handler, query);

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("正常情况：查询参数中包含反斜杠 \\")
        void testUrlEncode_QueryParamWithBackslash() throws Exception {
            String query = "file=path\\to\\file";
            // 反斜杠 \ 在 URLEncoder 中编码为 %5C
            String expected = "file=path%5Cto%5Cfile";

            Method method = UriSanitizingHandler.class.getDeclaredMethod("urlEncode", String.class);
            method.setAccessible(true);
            String result = (String) method.invoke(handler, query);

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("正常情况：查询参数中包含脱字符 ^")
        void testUrlEncode_QueryParamWithCaret() throws Exception {
            String query = "q=test^value";
            String expected = "q=test%5Evalue";

            Method method = UriSanitizingHandler.class.getDeclaredMethod("urlEncode", String.class);
            method.setAccessible(true);
            String result = (String) method.invoke(handler, query);

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("正常情况：查询参数中包含方括号 [ 和 ]")
        void testUrlEncode_QueryParamWithSquareBrackets() throws Exception {
            String query = "q=items[name]";
            String expected = "q=items%5Bname%5D";

            Method method = UriSanitizingHandler.class.getDeclaredMethod("urlEncode", String.class);
            method.setAccessible(true);
            String result = (String) method.invoke(handler, query);

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("正常情况：查询参数中包含反引号 `")
        void testUrlEncode_QueryParamWithBacktick() throws Exception {
            String query = "q=`test`";
            String expected = "q=%60test%60";

            Method method = UriSanitizingHandler.class.getDeclaredMethod("urlEncode", String.class);
            method.setAccessible(true);
            String result = (String) method.invoke(handler, query);

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("正常情况：查询参数中混合多种非法字符")
        void testUrlEncode_QueryParamWithMixedIllegalChars() throws Exception {
            String query = "q={id}|[test]^value";
            String expected = "q=%7Bid%7D%7C%5Btest%5D%5Evalue";

            Method method = UriSanitizingHandler.class.getDeclaredMethod("urlEncode", String.class);
            method.setAccessible(true);
            String result = (String) method.invoke(handler, query);

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("边界情况：空字符串")
        void testUrlEncode_EmptyString() throws Exception {
            String query = "";

            Method method = UriSanitizingHandler.class.getDeclaredMethod("urlEncode", String.class);
            method.setAccessible(true);
            String result = (String) method.invoke(handler, query);

            assertEquals("", result);
        }

        @Test
        @DisplayName("边界情况：null 值")
        void testUrlEncode_NullValue() throws Exception {
            Method method = UriSanitizingHandler.class.getDeclaredMethod("urlEncode", String.class);
            method.setAccessible(true);
            String result = (String) method.invoke(handler, (String) null);

            assertNull(result);
        }

        @Test
        @DisplayName("正常情况：多个查询参数")
        void testUrlEncode_MultipleQueryParams() throws Exception {
            String query = "q=test|value&filter={type}";
            // URLEncoder 对空格编码为 +
            String expected = "q=test%7Cvalue&filter=%7Btype%7D";

            Method method = UriSanitizingHandler.class.getDeclaredMethod("urlEncode", String.class);
            method.setAccessible(true);
            String result = (String) method.invoke(handler, query);

            assertEquals(expected, result);
        }
    }

    @Nested
    @DisplayName("matchesPath 方法测试")
    class MatchesPathTests {

        @Test
        @DisplayName("默认路径模式：/api/** 应该匹配 /api/users")
        void testMatchesPath_DefaultPattern() throws Exception {
            Method method = UriSanitizingHandler.class.getDeclaredMethod("matchesPath", String.class);
            method.setAccessible(true);

            assertTrue((boolean) method.invoke(handler, "/api/users"));
            assertTrue((boolean) method.invoke(handler, "/api/users/123"));
            assertTrue((boolean) method.invoke(handler, "/api/v1/items"));
        }

        @Test
        @DisplayName("默认路径模式：/api/** 不应该匹配 /service/users")
        void testMatchesPath_NotMatching() throws Exception {
            Method method = UriSanitizingHandler.class.getDeclaredMethod("matchesPath", String.class);
            method.setAccessible(true);

            assertFalse((boolean) method.invoke(handler, "/service/users"));
            assertFalse((boolean) method.invoke(handler, "/other/api/users"));
        }

        @Test
        @DisplayName("路径包含非法字符但路径模式匹配，应继续处理")
        void testMatchesPath_WithIllegalChars() throws Exception {
            Method method = UriSanitizingHandler.class.getDeclaredMethod("matchesPath", String.class);
            method.setAccessible(true);

            assertTrue((boolean) method.invoke(handler, "/api/items|detail"));
        }
    }

    @Nested
    @DisplayName("channelRead 方法测试")
    class ChannelReadTests {

        @Test
        @DisplayName("正常情况：HttpRequest 包含查询参数中的非法字符，应被处理并传递")
        void testChannelRead_HttpRequestWithQueryParamsIllegalChars() {
            String originalUri = "/api/search?q=test|value&filter={type}";
            // 查询参数值中的 | 和 {} 应被编码，= 和 & 应保留
            String expectedUri = "/api/search?q=test%7Cvalue&filter=%7Btype%7D";

            HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, originalUri);

            handler.channelRead(ctx, request);

            assertEquals(expectedUri, request.uri());
            verify(ctx).fireChannelRead(request);
        }

        @Test
        @DisplayName("正常情况：HttpRequest 不包含非法字符，保持不变并传递")
        void testChannelRead_HttpRequestWithoutIllegalChars() {
            String uri = "/api/users/123";

            HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri);

            handler.channelRead(ctx, request);

            assertEquals(uri, request.uri());
            verify(ctx).fireChannelRead(request);
        }

        @Test
        @DisplayName("边界情况：非 HttpRequest 类型消息，应直接传递")
        void testChannelRead_NonHttpRequest() {
            Object nonHttpRequest = new Object();

            handler.channelRead(ctx, nonHttpRequest);

            verify(ctx).fireChannelRead(nonHttpRequest);
        }

        @Test
        @DisplayName("正常情况：路径包含非法字符应保持原样（只编码查询参数）")
        void testChannelRead_PathWithIllegalChars() {
            String uri = "/api/items|detail";

            HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri);

            handler.channelRead(ctx, request);

            // 路径中的非法字符不应被编码（因为没有查询参数）
            assertEquals(uri, request.uri());
            verify(ctx).fireChannelRead(request);
        }

        @Test
        @DisplayName("正常情况：路径包含非法字符但无查询参数，保持不变")
        void testChannelRead_PathWithIllegalCharsNoQuery() {
            String uri = "/api/items|detail";

            HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri);

            handler.channelRead(ctx, request);

            assertEquals(uri, request.uri());
            verify(ctx).fireChannelRead(request);
        }

        @Test
        @DisplayName("正常情况：路径包含非法字符且有查询参数，只编码查询参数")
        void testChannelRead_PathWithIllegalCharsAndQuery() {
            String originalUri = "/api/items|detail?q=test|value";
            // 路径中的 | 不编码，查询参数中的 | 编码
            String expectedUri = "/api/items|detail?q=test%7Cvalue";

            HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, originalUri);

            handler.channelRead(ctx, request);

            assertEquals(expectedUri, request.uri());
            verify(ctx).fireChannelRead(request);
        }
    }

    @Nested
    @DisplayName("Handler 特性测试")
    class HandlerCharacteristicsTests {

        @Test
        @DisplayName("Handler 应为 Spring 管理的组件")
        void testHandlerHasComponentAnnotation() {
            assertNotNull(handler.getClass().getAnnotation(org.springframework.stereotype.Component.class),
                    "Handler 应添加 @Component 注解");
        }

        @Test
        @DisplayName("Handler 应启用")
        void testHandlerIsEnabled() {
            assertTrue(handler.isEnabled(), "Handler 默认应该启用");
        }
    }
}
