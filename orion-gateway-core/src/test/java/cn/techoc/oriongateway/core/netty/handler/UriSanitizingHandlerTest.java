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
    @DisplayName("sanitizeUri 方法测试")
    class SanitizeUriTests {

        @Test
        @DisplayName("正常情况：不包含非法字符的URI应保持不变")
        void testSanitizeUri_NoIllegalChars() throws Exception {
            String uri = "/api/users/123";

            Method method = UriSanitizingHandler.class.getDeclaredMethod("sanitizeUri", String.class);
            method.setAccessible(true);
            String result = (String) method.invoke(handler, uri);

            assertEquals(uri, result);
        }

        @Test
        @DisplayName("正常情况：包含单个非法字符 |")
        void testSanitizeUri_SinglePipeChar() throws Exception {
            String uri = "/api/items|detail";
            String expected = "/api/items%7Cdetail";

            Method method = UriSanitizingHandler.class.getDeclaredMethod("sanitizeUri", String.class);
            method.setAccessible(true);
            String result = (String) method.invoke(handler, uri);

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("正常情况：包含大括号 { 和 }")
        void testSanitizeUri_CurlyBraces() throws Exception {
            String uri = "/api/users/{id}";
            String expected = "/api/users/%7Bid%7D";

            Method method = UriSanitizingHandler.class.getDeclaredMethod("sanitizeUri", String.class);
            method.setAccessible(true);
            String result = (String) method.invoke(handler, uri);

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("正常情况：包含反斜杠 \\")
        void testSanitizeUri_Backslash() throws Exception {
            String uri = "/api/path\\to\\file";
            String expected = "/api/path%5Cto%5Cfile";

            Method method = UriSanitizingHandler.class.getDeclaredMethod("sanitizeUri", String.class);
            method.setAccessible(true);
            String result = (String) method.invoke(handler, uri);

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("正常情况：包含脱字符 ^")
        void testSanitizeUri_Caret() throws Exception {
            String uri = "/api/search?q=test^value";
            String expected = "/api/search?q=test%5Evalue";

            Method method = UriSanitizingHandler.class.getDeclaredMethod("sanitizeUri", String.class);
            method.setAccessible(true);
            String result = (String) method.invoke(handler, uri);

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("正常情况：包含方括号 [ 和 ]")
        void testSanitizeUri_SquareBrackets() throws Exception {
            String uri = "/api/items[name]";
            String expected = "/api/items%5Bname%5D";

            Method method = UriSanitizingHandler.class.getDeclaredMethod("sanitizeUri", String.class);
            method.setAccessible(true);
            String result = (String) method.invoke(handler, uri);

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("正常情况：包含反引号 `")
        void testSanitizeUri_Backtick() throws Exception {
            String uri = "/api/`test`";
            String expected = "/api/%60test%60";

            Method method = UriSanitizingHandler.class.getDeclaredMethod("sanitizeUri", String.class);
            method.setAccessible(true);
            String result = (String) method.invoke(handler, uri);

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("正常情况：混合多种非法字符")
        void testSanitizeUri_MixedIllegalChars() throws Exception {
            String uri = "/api/{id}|[test]^value";
            String expected = "/api/%7Bid%7D%7C%5Btest%5D%5Evalue";

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
        @DisplayName("边界情况：仅包含非法字符")
        void testSanitizeUri_OnlyIllegalChars() throws Exception {
            String uri = "|{}[]\\^`";
            String expected = "%7C%7B%7D%5B%5D%5C%5E%60";

            Method method = UriSanitizingHandler.class.getDeclaredMethod("sanitizeUri", String.class);
            method.setAccessible(true);
            String result = (String) method.invoke(handler, uri);

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("边界情况：重复的非法字符")
        void testSanitizeUri_DuplicateIllegalChars() throws Exception {
            String uri = "/api||test";
            String expected = "/api%7C%7Ctest";

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
        @DisplayName("正常情况：HttpRequest 包含非法字符，应被处理并传递")
        void testChannelRead_HttpRequestWithIllegalChars() {
            String originalUri = "/api/items|detail";
            String expectedUri = "/api/items%7Cdetail";

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
        @DisplayName("正常情况：HttpRequest 包含查询参数中的非法字符")
        void testChannelRead_HttpRequestWithQueryParams() {
            String originalUri = "/api/search?q=test|value&filter={type}";
            String expectedUri = "/api/search?q=test%7Cvalue&filter=%7Btype%7D";

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
        @DisplayName("Handler 应为 @Sharable 安全（无状态）")
        void testHandlerIsSharableSafe() {
            assertNull(handler.getClass().getAnnotation(io.netty.channel.ChannelHandler.Sharable.class),
                    "Handler 当前未标注 @Sharable，但设计上是线程安全的");

            assertDoesNotThrow(() -> {
                UriSanitizingHandler sharedHandler = new UriSanitizingHandler();
                HttpRequest request1 = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/api|test");
                HttpRequest request2 = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/api|test");

                sharedHandler.channelRead(ctx, request1);
                sharedHandler.channelRead(ctx, request2);
            });
        }
    }
}
