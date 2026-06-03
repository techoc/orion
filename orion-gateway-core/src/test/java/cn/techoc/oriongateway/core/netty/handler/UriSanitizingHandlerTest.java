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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
    @DisplayName("sanitizeUri 方法测试")
    class SanitizeUriTests {

        @Test
        @DisplayName("正常情况：不含查询参数的URI应保持不变")
        void testSanitizeUri_NoQueryParams() throws Exception {
            String uri = "/api/users/123";

            Method method = UriSanitizingHandler.class.getDeclaredMethod("sanitizeUri", String.class);
            method.setAccessible(true);
            String result = (String) method.invoke(handler, uri);

            assertEquals(uri, result);
        }

        @Test
        @DisplayName("正常情况：查询参数中包含单个非法字符 |")
        void testSanitizeUri_QueryParamWithPipeChar() throws Exception {
            String uri = "/api/search?q=test|value";
            String expected = "/api/search?q=test%7Cvalue";

            Method method = UriSanitizingHandler.class.getDeclaredMethod("sanitizeUri", String.class);
            method.setAccessible(true);
            String result = (String) method.invoke(handler, uri);

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("正常情况：查询参数中包含大括号 { 和 }")
        void testSanitizeUri_QueryParamWithCurlyBraces() throws Exception {
            String uri = "/api/search?filter={type}";
            String expected = "/api/search?filter=%7Btype%7D";

            Method method = UriSanitizingHandler.class.getDeclaredMethod("sanitizeUri", String.class);
            method.setAccessible(true);
            String result = (String) method.invoke(handler, uri);

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("正常情况：查询参数中包含反斜杠 \\")
        void testSanitizeUri_QueryParamWithBackslash() throws Exception {
            String uri = "/api/path?file=path\\to\\file";
            String expected = "/api/path?file=path%5Cto%5Cfile";

            Method method = UriSanitizingHandler.class.getDeclaredMethod("sanitizeUri", String.class);
            method.setAccessible(true);
            String result = (String) method.invoke(handler, uri);

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("正常情况：查询参数中包含脱字符 ^")
        void testSanitizeUri_QueryParamWithCaret() throws Exception {
            String uri = "/api/search?q=test^value";
            String expected = "/api/search?q=test%5Evalue";

            Method method = UriSanitizingHandler.class.getDeclaredMethod("sanitizeUri", String.class);
            method.setAccessible(true);
            String result = (String) method.invoke(handler, uri);

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("正常情况：查询参数中包含方括号 [ 和 ]")
        void testSanitizeUri_QueryParamWithSquareBrackets() throws Exception {
            String uri = "/api/search?q=items[name]";
            String expected = "/api/search?q=items%5Bname%5D";

            Method method = UriSanitizingHandler.class.getDeclaredMethod("sanitizeUri", String.class);
            method.setAccessible(true);
            String result = (String) method.invoke(handler, uri);

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("正常情况：查询参数中包含反引号 `")
        void testSanitizeUri_QueryParamWithBacktick() throws Exception {
            String uri = "/api/search?q=`test`";
            String expected = "/api/search?q=%60test%60";

            Method method = UriSanitizingHandler.class.getDeclaredMethod("sanitizeUri", String.class);
            method.setAccessible(true);
            String result = (String) method.invoke(handler, uri);

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("正常情况：查询参数中混合多种非法字符")
        void testSanitizeUri_QueryParamWithMixedIllegalChars() throws Exception {
            String uri = "/api/search?q={id}|[test]^value";
            String expected = "/api/search?q=%7Bid%7D%7C%5Btest%5D%5Evalue";

            Method method = UriSanitizingHandler.class.getDeclaredMethod("sanitizeUri", String.class);
            method.setAccessible(true);
            String result = (String) method.invoke(handler, uri);

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("边界情况：空字符串")
        void testSanitizeUri_EmptyString() throws Exception {
            String uri = "";

            Method method = UriSanitizingHandler.class.getDeclaredMethod("sanitizeUri", String.class);
            method.setAccessible(true);
            String result = (String) method.invoke(handler, uri);

            assertEquals("", result);
        }

        @Test
        @DisplayName("正常情况：路径包含非法字符应保持不变（仅编码查询参数）")
        void testSanitizeUri_PathWithIllegalChars_ShouldNotBeEncoded() throws Exception {
            String uri = "/api/items|detail";

            Method method = UriSanitizingHandler.class.getDeclaredMethod("sanitizeUri", String.class);
            method.setAccessible(true);
            String result = (String) method.invoke(handler, uri);

            // 路径中的非法字符不应被编码
            assertEquals(uri, result);
        }

        @Test
        @DisplayName("正常情况：多个查询参数")
        void testSanitizeUri_MultipleQueryParams() throws Exception {
            String uri = "/api/search?q=test|value&filter={type}";
            String expected = "/api/search?q=test%7Cvalue&filter=%7Btype%7D";

            Method method = UriSanitizingHandler.class.getDeclaredMethod("sanitizeUri", String.class);
            method.setAccessible(true);
            String result = (String) method.invoke(handler, uri);

            assertEquals(expected, result);
        }
    }

    @Nested
    @DisplayName("channelRead 方法测试")
    class ChannelReadTests {

        @Test
        @DisplayName("正常情况：HttpRequest 包含查询参数中的非法字符，应被处理并传递")
        void testChannelRead_HttpRequestWithQueryParamsIllegalChars() {
            String originalUri = "/api/search?q=test|value&filter={type}";
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
        @DisplayName("正常情况：路径包含非法字符应保持原样")
        void testChannelRead_PathWithIllegalChars() {
            String uri = "/api/items|detail";

            HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri);

            handler.channelRead(ctx, request);

            // 路径中的非法字符不应被编码
            assertEquals(uri, request.uri());
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

//        @Test
//        @DisplayName("Handler 默认应包含 8 个非法字符映射")
//        void testHandlerHasDefaultMappings() {
//            assertEquals(8, handler.getMappingCount(),
//                    "Handler 应包含 8 个默认的非法字符映射");
//        }
    }
}
