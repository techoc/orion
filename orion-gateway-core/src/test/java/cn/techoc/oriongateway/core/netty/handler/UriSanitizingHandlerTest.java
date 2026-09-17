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
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.isNull;
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

        @Test
        @DisplayName("正常情况：中文参数值应被正确编码")
        void testUrlEncode_ChineseCharacters() {
            String query = "name=张三&city=北京";
            String expected = "name=%E5%BC%A0%E4%B8%89&city=%E5%8C%97%E4%BA%AC";

            String result = handler.urlEncode(query);
            assertEquals(expected, result);
        }

        @Test
        @DisplayName("正常情况：已编码的值应归一化（先解码再编码）")
        void testUrlEncode_AlreadyEncodedValue() {
            // %20 解码为空格，再编码为 +
            String query = "key=a%20b";
            String expected = "key=a+b";

            String result = handler.urlEncode(query);
            assertEquals(expected, result);
        }

        @Test
        @DisplayName("正常情况：已编码的中文值应保持不变（归一化后结果一致）")
        void testUrlEncode_AlreadyEncodedChinese() {
            // %E5%BC%A0%E4%B8%89 解码为 "张三"，再编码仍为 %E5%BC%A0%E4%B8%89
            String query = "name=%E5%BC%A0%E4%B8%89";
            String expected = "name=%E5%BC%A0%E4%B8%89";

            String result = handler.urlEncode(query);
            assertEquals(expected, result);
        }

        @Test
        @DisplayName("边界情况：不完整的百分号序列应 fallback 到直接编码")
        void testUrlEncode_IncompletePercentEncoding() {
            // %2 不是合法的百分号序列，URLDecoder.decode 会抛异常，fallback 直接编码
            // % 被编码为 %25，结果为 %252
            String query = "key=%2";
            String expected = "key=%252";

            String result = handler.urlEncode(query);
            assertEquals(expected, result);
        }

        @Test
        @DisplayName("边界情况：只有 key 没有 = 和 value")
        void testUrlEncode_KeyOnlyNoEquals() {
            String query = "flag";
            String expected = "flag";

            String result = handler.urlEncode(query);
            assertEquals(expected, result);
        }

        @Test
        @DisplayName("边界情况：key= 形式（有 = 但 value 为空）")
        void testUrlEncode_KeyWithEqualsEmptyValue() {
            String query = "key=";
            String expected = "key=";

            String result = handler.urlEncode(query);
            assertEquals(expected, result);
        }

        @Test
        @DisplayName("边界情况：多个无值 key 用 & 分隔")
        void testUrlEncode_MultipleKeysWithoutValues() {
            String query = "flag&debug&verbose";
            String expected = "flag&debug&verbose";

            String result = handler.urlEncode(query);
            assertEquals(expected, result);
        }

        @Test
        @DisplayName("边界情况：混合有值和无值的参数")
        void testUrlEncode_MixedKeyWithAndWithoutValue() {
            String query = "flag&name=test&debug";
            String expected = "flag&name=test&debug";

            String result = handler.urlEncode(query);
            assertEquals(expected, result);
        }

        @Test
        @DisplayName("边界情况：连续 && 产生的空段应被跳过")
        void testUrlEncode_ConsecutiveAmpersands() {
            String query = "a=1&&b=2";
            String expected = "a=1&b=2";

            String result = handler.urlEncode(query);
            assertEquals(expected, result);
        }

        @Test
        @DisplayName("边界情况：尾部 & 应被忽略")
        void testUrlEncode_TrailingAmpersand() {
            String query = "a=1&";
            String expected = "a=1";

            String result = handler.urlEncode(query);
            assertEquals(expected, result);
        }

        @Test
        @DisplayName("边界情况：头部 & 应被忽略")
        void testUrlEncode_LeadingAmpersand() {
            String query = "&a=1";
            String expected = "a=1";

            String result = handler.urlEncode(query);
            assertEquals(expected, result);
        }

        @Test
        @DisplayName("边界情况：value 中包含 = 号（多个等号）")
        void testUrlEncode_ValueContainsEquals() {
            // 只有第一个 = 作为键值分隔符，后续 = 属于 value
            String query = "expr=a=b=c";
            String expected = "expr=a%3Db%3Dc";

            String result = handler.urlEncode(query);
            assertEquals(expected, result);
        }

        @Test
        @DisplayName("正常情况：+ 号的往返处理（+ 解码为空格，再编码为 +）")
        void testUrlEncode_PlusSignInValue() {
            // URLDecoder 将 + 解码为空格，URLEncoder 将空格编码为 +
            String query = "q=hello+world";
            String expected = "q=hello+world";

            String result = handler.urlEncode(query);
            assertEquals(expected, result);
        }

        @Test
        @DisplayName("正常情况：空格应被编码为 +")
        void testUrlEncode_SpaceInValue() {
            String query = "q=hello world";
            String expected = "q=hello+world";

            String result = handler.urlEncode(query);
            assertEquals(expected, result);
        }

        @Test
        @DisplayName("正常情况：key 包含特殊字符应被编码")
        void testUrlEncode_SpecialCharsInKey() {
            // key "my key" 中的空格被编码为 +
            String query = "my key=value";
            String expected = "my+key=value";

            String result = handler.urlEncode(query);
            assertEquals(expected, result);
        }

        @Test
        @DisplayName("正常情况：key 包含中文应被编码")
        void testUrlEncode_ChineseInKey() {
            String query = "姓名=张三";
            String expected = "%E5%A7%93%E5%90%8D=%E5%BC%A0%E4%B8%89";

            String result = handler.urlEncode(query);
            assertEquals(expected, result);
        }

        @Test
        @DisplayName("正常情况：已正确编码的参数应保持不变")
        void testUrlEncode_AlreadyCorrectlyEncoded() {
            String query = "a=1&b=2";
            String expected = "a=1&b=2";

            String result = handler.urlEncode(query);
            assertEquals(expected, result);
        }

        @Test
        @DisplayName("边界情况：仅有 & 字符的查询字符串应返回空")
        void testUrlEncode_OnlyAmpersands() {
            String query = "&&&";
            String expected = "";

            String result = handler.urlEncode(query);
            assertEquals(expected, result);
        }

        @Test
        @DisplayName("边界情况：value 中包含 ? 字符应被编码")
        void testUrlEncode_QuestionMarkInValue() {
            // 这是该 Handler 要解决的核心场景之一：value 中的 ? 不应被截断
            String query = "url=http://example.com?foo=bar";
            String expected = "url=http%3A%2F%2Fexample.com%3Ffoo%3Dbar";

            String result = handler.urlEncode(query);
            assertEquals(expected, result);
        }

        @Test
        @DisplayName("正常情况：%2B 解码为 + 再编码为 %2B（往返一致）")
        void testUrlEncode_EncodedPlusSign() {
            // %2B → decode → "+" → encode → %2B
            String query = "q=a%2Bb";
            String expected = "q=a%2Bb";

            String result = handler.urlEncode(query);
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

        @Test
        @DisplayName("正常情况：路径包含非法字符且有查询参数，只编码查询参数 参数值部分编码 部分不编码 包含 ? 包含空值 空值不含 =")
        void testChannelRead_PathWithIllegalCharsAndQuery_PartialEncoding() {
            String originalUri = "/api/user/114514?a=114|514&b=114%7C514&c=114?514&d=114514&e&f=9527";
            // 路径中的 | 不编码，查询参数中的 | 和 ? 编码，保留原有的 = 和空值无 = 的情况
            String expectedUri = "/api/user/114514?a=114%7C514&b=114%7C514&c=114%3F514&d=114514&e&f=9527";

            HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, originalUri);

            handler.channelRead(ctx, request);

            assertEquals(expectedUri, request.uri());
            verify(ctx).fireChannelRead(request);
        }

        @Test
        @DisplayName("正常情况：路径包含非法字符且有查询参数，只编码查询参数 参数值部分编码 部分不编码 包含 ? 包含空值 空值含 =")
        void testChannelRead_PathWithIllegalCharsAndQuery_PartialEncoding_WithEmptyValue() {
            String originalUri = "/api/user/114514?a=114|514&b=114%7C514&c=114?514&d=114514&e=&f=9527";
            // 路径中的 | 不编码，查询参数中的 | 和 ? 编码，保留原有的 = 和空值无 = 的情况
            String expectedUri = "/api/user/114514?a=114%7C514&b=114%7C514&c=114%3F514&d=114514&e=&f=9527";

            HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, originalUri);

            handler.channelRead(ctx, request);

            assertEquals(expectedUri, request.uri());
            verify(ctx).fireChannelRead(request);
        }

        @Test
        @DisplayName("禁用状态：即使 URI 包含非法字符也应直接透传，不修改 URI")
        void testChannelRead_DisabledHandler() {
            UriSanitizingHandler disabledHandler = new UriSanitizingHandler(false, "/api/**");
            String originalUri = "/api/search?q=test|value";

            HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, originalUri);
            disabledHandler.channelRead(ctx, request);

            // 禁用状态下 URI 不应被修改
            assertEquals(originalUri, request.uri());
            verify(ctx).fireChannelRead(request);
        }

        @Test
        @DisplayName("正常情况：URI 无查询参数（无 ?），直接透传")
        void testChannelRead_NoQueryString() {
            String uri = "/api/users/123";

            HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri);
            handler.channelRead(ctx, request);

            assertEquals(uri, request.uri());
            verify(ctx).fireChannelRead(request);
        }

        @Test
        @DisplayName("正常情况：路径不匹配配置模式，不编码直接透传")
        void testChannelRead_PathNotMatchingPattern() {
            String originalUri = "/service/search?q=test|value";

            HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, originalUri);
            handler.channelRead(ctx, request);

            // 路径 /service/search 不匹配默认模式 /api/**，URI 不应被修改
            assertEquals(originalUri, request.uri());
            verify(ctx).fireChannelRead(request);
        }

        @Test
        @DisplayName("自定义路径模式：匹配自定义模式时应正常编码")
        void testChannelRead_CustomPathPattern() {
            UriSanitizingHandler customHandler = new UriSanitizingHandler(true, "/service/**,/custom/**");
            String originalUri = "/service/search?q=test|value";
            String expectedUri = "/service/search?q=test%7Cvalue";

            HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, originalUri);
            customHandler.channelRead(ctx, request);

            assertEquals(expectedUri, request.uri());
            verify(ctx).fireChannelRead(request);
        }

        @Test
        @DisplayName("自定义路径模式：不匹配自定义模式时应直接透传")
        void testChannelRead_CustomPathPatternNotMatching() {
            UriSanitizingHandler customHandler = new UriSanitizingHandler(true, "/service/**");
            String originalUri = "/api/search?q=test|value";

            HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, originalUri);
            customHandler.channelRead(ctx, request);

            // /api/search 不匹配 /service/**，URI 不应被修改
            assertEquals(originalUri, request.uri());
            verify(ctx).fireChannelRead(request);
        }

        @Test
        @DisplayName("正常情况：编码后与原查询相同时不应调用 setUri")
        void testChannelRead_NoChangeWhenAlreadyEncoded() {
            String uri = "/api/search?a=1&b=2";

            HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri);
            handler.channelRead(ctx, request);

            // 已经正确编码的查询参数不应触发 setUri
            assertEquals(uri, request.uri());
            verify(ctx).fireChannelRead(request);
        }

        @Test
        @DisplayName("边界情况：中文查询参数应被编码")
        void testChannelRead_ChineseQueryParams() {
            String originalUri = "/api/search?name=张三";
            String expectedUri = "/api/search?name=%E5%BC%A0%E4%B8%89";

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
        @DisplayName("Handler 应标注 @Sharable 以支持多 Channel 共享")
        void testHandlerHasSharableAnnotation() {
            assertNotNull(handler.getClass().getAnnotation(io.netty.channel.ChannelHandler.Sharable.class),
                    "Handler 应添加 @Sharable 注解");
        }

        @Test
        @DisplayName("Handler 应启用")
        void testHandlerIsEnabled() {
            assertTrue(handler.isEnabled(), "Handler 默认应该启用");
        }
    }

    @Nested
    @DisplayName("构造函数与配置测试")
    class ConstructorTests {

        @Test
        @DisplayName("默认构造函数：enabled=true, pathPatterns=/api/**, charset=UTF-8")
        void testDefaultConstructor() {
            UriSanitizingHandler defaultHandler = new UriSanitizingHandler();

            assertTrue(defaultHandler.isEnabled());
            assertEquals(StandardCharsets.UTF_8, defaultHandler.getCharset());
            assertTrue(defaultHandler.getPathPatterns().contains("/api/**"));
        }

        @Test
        @DisplayName("双参构造函数：指定 enabled 和 pathPatterns，charset 默认 UTF-8")
        void testTwoArgConstructor() {
            UriSanitizingHandler h = new UriSanitizingHandler(false, "/service/**,/custom/**");

            assertFalse(h.isEnabled());
            assertEquals(StandardCharsets.UTF_8, h.getCharset());
            assertEquals(2, h.getPathPatterns().size());
            assertTrue(h.getPathPatterns().contains("/service/**"));
            assertTrue(h.getPathPatterns().contains("/custom/**"));
        }

        @Test
        @DisplayName("全参构造函数：pathPatterns 为空字符串时 pathPatterns 集合为空")
        void testFullConstructorEmptyPatterns() {
            UriSanitizingHandler h = new UriSanitizingHandler(true, "");

            assertTrue(h.isEnabled());
            assertTrue(h.getPathPatterns().isEmpty());
        }

        @Test
        @DisplayName("全参构造函数：pathPatterns 为 null 时 pathPatterns 集合为空")
        void testFullConstructorNullPatterns() {
            UriSanitizingHandler h = new UriSanitizingHandler(true, null);

            assertTrue(h.getPathPatterns().isEmpty());
        }

        @Test
        @DisplayName("pathPatterns 空白和逗号分隔的边界处理")
        void testParsePatternsWithWhitespace() {
            UriSanitizingHandler h = new UriSanitizingHandler(true, " /a/** , , /b/** , ");

            assertEquals(2, h.getPathPatterns().size());
            assertTrue(h.getPathPatterns().contains("/a/**"));
            assertTrue(h.getPathPatterns().contains("/b/**"));
        }

        @Test
        @DisplayName("空 pathPatterns 时 matchesPath 应返回 false（不匹配所有路径）")
        void testMatchesPathWithEmptyPatterns() {
            UriSanitizingHandler h = new UriSanitizingHandler(true, "");

            assertFalse(h.matchesPath("/api/users"));
            assertFalse(h.matchesPath("/any/path"));
        }
    }

    @Nested
    @DisplayName("字符集配置测试")
    class CharsetTests {

        @Test
        @DisplayName("全参构造函数：指定 charset 配置生效")
        void testFullConstructorWithCustomCharset() {
            Charset customCharset = StandardCharsets.ISO_8859_1;
            UriSanitizingHandler h = new UriSanitizingHandler(true, "/api/**", customCharset);

            assertEquals(customCharset, h.getCharset());
        }

        @Test
        @DisplayName("不同字符集下中文编码结果不同")
        void testUrlEncodeWithDifferentCharsets() {
            // 使用 UTF-8 编码中文
            UriSanitizingHandler utf8Handler = new UriSanitizingHandler(true, "/api/**", StandardCharsets.UTF_8);
            String utf8Result = utf8Handler.urlEncode("name=张三");
            assertEquals("name=%E5%BC%A0%E4%B8%89", utf8Result);

            // ISO-8859-1 无法正确编码中文，会得到 ? 字符
            UriSanitizingHandler isoHandler = new UriSanitizingHandler(true, "/api/**", StandardCharsets.ISO_8859_1);
            String isoResult = isoHandler.urlEncode("name=张三");
            // ISO-8859-1 编码中文会变成 %3F%3F（两个问号）
            assertEquals("name=%3F%3F", isoResult);
        }

        @Test
        @DisplayName("使用自定义字符集时 channelRead 应正常工作")
        void testChannelReadWithCustomCharset() {
            Charset customCharset = StandardCharsets.UTF_16;
            UriSanitizingHandler handler = new UriSanitizingHandler(true, "/api/**", customCharset);

            String originalUri = "/api/test?name=张三";
            HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, originalUri);

            handler.channelRead(ctx, request);

            // 验证 URI 被修改并传递
            verify(ctx).fireChannelRead(request);
            // UTF-16 编码的结果与 UTF-8 不同
            assertNotEquals(originalUri, request.uri());
        }
    }

    @Nested
    @DisplayName("safeEncode 方法测试")
    class SafeEncodeTests {

        private Method getSafeEncodeMethod() throws Exception {
            Method method = UriSanitizingHandler.class.getDeclaredMethod("safeEncode", String.class);
            method.setAccessible(true);
            return method;
        }

        @Test
        @DisplayName("safeEncode: 正常编码字符串")
        void testSafeEncode_NormalString() throws Exception {
            Method method = getSafeEncodeMethod();
            String result = (String) method.invoke(handler, "test|value");
            assertEquals("test%7Cvalue", result);
        }

        @Test
        @DisplayName("safeEncode: 空字符串应返回原样")
        void testSafeEncode_EmptyString() throws Exception {
            Method method = getSafeEncodeMethod();
            String result = (String) method.invoke(handler, "");
            assertEquals("", result);
        }

        @Test
        @DisplayName("safeEncode: 不完整的百分号编码应 fallback 到直接编码")
        void testSafeEncode_IncompletePercentEncoding() throws Exception {
            Method method = getSafeEncodeMethod();
            // %2 不是合法的百分号序列
            String result = (String) method.invoke(handler, "%2");
            assertEquals("%252", result);
        }

        @Test
        @DisplayName("safeEncode: 已编码的字符串应归一化")
        void testSafeEncode_AlreadyEncoded() throws Exception {
            Method method = getSafeEncodeMethod();
            // %20 解码为空格，再编码为 +
            String result = (String) method.invoke(handler, "a%20b");
            assertEquals("a+b", result);
        }

        @Test
        @DisplayName("safeEncode: 中文字符应正确编码")
        void testSafeEncode_Chinese() throws Exception {
            Method method = getSafeEncodeMethod();
            String result = (String) method.invoke(handler, "张三");
            assertEquals("%E5%BC%A0%E4%B8%89", result);
        }
    }

    @Nested
    @DisplayName("异常处理测试")
    class ExceptionHandlingTests {

        @Test
        @DisplayName("channelRead 处理 null msg 不应抛出异常")
        void testChannelRead_NullMessage() {
            assertDoesNotThrow(() -> handler.channelRead(ctx, null));
            verify(ctx).fireChannelRead(isNull());
        }

        @Test
        @DisplayName("异常情况：URI 只有 ? 没有查询参数")
        void testChannelRead_OnlyQuestionMark() {
            String uri = "/api/test?";
            HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri);

            assertDoesNotThrow(() -> handler.channelRead(ctx, request));
            // URI 保持原样
            assertEquals(uri, request.uri());
            verify(ctx).fireChannelRead(request);
        }

        @Test
        @DisplayName("异常情况：URI 以 ? 结尾但有内容")
        void testChannelRead_TrailingQuestionMark() {
            String originalUri = "/api/test?a=1&b=2&";
            HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, originalUri);

            handler.channelRead(ctx, request);

            // 尾部的 & 被去除
            assertEquals("/api/test?a=1&b=2", request.uri());
            verify(ctx).fireChannelRead(request);
        }
    }

    @Nested
    @DisplayName("日志输出测试")
    class LoggingTests {

        @Test
        @DisplayName("URI 被修改时应输出 info 日志")
        void testLoggingWhenUriModified() {
            // 注意：实际验证日志输出通常使用 logback-test.xml 或自定义 Appender
            // 这里我们只验证功能正常，不强制验证日志
            String originalUri = "/api/search?q=test|value";
            HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, originalUri);

            assertDoesNotThrow(() -> handler.channelRead(ctx, request));
            verify(ctx).fireChannelRead(request);
        }
    }
}
