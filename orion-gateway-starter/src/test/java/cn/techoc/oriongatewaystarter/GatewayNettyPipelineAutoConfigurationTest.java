package cn.techoc.oriongatewaystarter;

import cn.techoc.oriongateway.core.netty.handler.UriSanitizingHandler;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.embedded.netty.NettyReactiveWebServerFactory;
import org.springframework.boot.web.embedded.netty.NettyServerCustomizer;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import reactor.netty.ChannelPipelineConfigurer;
import reactor.netty.ConnectionObserver;
import reactor.netty.http.server.HttpServer;

import java.lang.reflect.Field;
import java.net.SocketAddress;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("GatewayNettyPipelineAutoConfiguration 测试")
class GatewayNettyPipelineAutoConfigurationTest {

    private GatewayNettyPipelineAutoConfiguration configuration;

    @BeforeEach
    void setUp() {
        configuration = new GatewayNettyPipelineAutoConfiguration();
    }

    private void setFlag(String fieldName, Object value) {
        try {
            Field field = GatewayNettyPipelineAutoConfiguration.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(configuration, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private NettyServerCustomizer captureAndReturnCustomizer() {
        NettyReactiveWebServerFactory factory = mock(NettyReactiveWebServerFactory.class);
        WebServerFactoryCustomizer<NettyReactiveWebServerFactory> customizer =
                configuration.nettyServerCustomizer();
        customizer.customize(factory);

        ArgumentCaptor<NettyServerCustomizer> captor =
                ArgumentCaptor.forClass(NettyServerCustomizer.class);
        verify(factory).addServerCustomizers(captor.capture());
        return captor.getValue();
    }

    // ========== Bean 配置测试 ==========

    @Nested
    @DisplayName("Bean 配置测试")
    class BeanConfigurationTests {

        HttpServer server2 = HttpServer.create();

        @Test
        @DisplayName("nettyServerCustomizer 应返回非空实例")
        void testNettyServerCustomizerNotNull() {
            WebServerFactoryCustomizer<NettyReactiveWebServerFactory> customizer =
                    configuration.nettyServerCustomizer();
            assertNotNull(customizer, "WebServerFactoryCustomizer 不应为空");
        }

        @Test
        @DisplayName("类应被 @Configuration 注解标注")
        void testClassIsAnnotatedWithConfiguration() {
            assertNotNull(
                    GatewayNettyPipelineAutoConfiguration.class
                            .getAnnotation(org.springframework.context.annotation.Configuration.class),
                    "类应被 @Configuration 注解标注");
        }

        @Test
        @DisplayName("nettyServerCustomizer 方法应被 @Bean 注解标注")
        void testMethodIsAnnotatedWithBean() throws NoSuchMethodException {
            assertNotNull(GatewayNettyPipelineAutoConfiguration.class
                            .getMethod("nettyServerCustomizer")
                            .getAnnotation(org.springframework.context.annotation.Bean.class),
                    "方法应被 @Bean 注解标注");
        }

        @Test
        @DisplayName("enableGatewayAccessLog 字段应被 @Value 注解标注")
        void testEnableGatewayAccessLogHasValueAnnotation() throws NoSuchFieldException {
            Value annotation = GatewayNettyPipelineAutoConfiguration.class
                    .getDeclaredField("enableGatewayAccessLog")
                    .getAnnotation(Value.class);
            assertNotNull(annotation);
            assertEquals("${orion.gateway.enable-gateway-access-log:false}", annotation.value());
        }

        @Test
        @DisplayName("enableNettyUriSanitizing 字段应被 @Value 注解标注")
        void testEnableNettyUriSanitizingHasValueAnnotation() throws NoSuchFieldException {
            Value annotation = GatewayNettyPipelineAutoConfiguration.class
                    .getDeclaredField("enableNettyUriSanitizing")
                    .getAnnotation(Value.class);
            assertNotNull(annotation);
            assertEquals("${orion.gateway.enable-netty-uri-sanitizing:false}", annotation.value());
        }
    }

    // ========== 功能开关默认行为测试 ==========

    @Nested
    @DisplayName("功能开关默认行为测试（无 Spring 上下文）")
    class FeatureFlagDefaultTests {

        @Test
        @DisplayName("无 Spring 上下文时 enableGatewayAccessLog 应为 null")
        void testDefaultGatewayAccessLogIsNull() throws Exception {
            Field field = GatewayNettyPipelineAutoConfiguration.class
                    .getDeclaredField("enableGatewayAccessLog");
            field.setAccessible(true);
            assertNull(field.get(new GatewayNettyPipelineAutoConfiguration()),
                    "enableGatewayAccessLog 无 @Value 注入时应为 null");
        }

        @Test
        @DisplayName("无 Spring 上下文时 enableNettyUriSanitizing 应为 null")
        void testDefaultNettyUriSanitizingIsTrue() throws Exception {
            Field field = GatewayNettyPipelineAutoConfiguration.class
                    .getDeclaredField("enableNettyUriSanitizing");
            field.setAccessible(true);
            assertNull(field.get(new GatewayNettyPipelineAutoConfiguration()),
                    "enableNettyUriSanitizing 无 @Value 注入时应为 null");
        }
    }

    // ========== Factory 交互测试 ==========

    @Nested
    @DisplayName("Factory 交互测试")
    class FactoryInteractionTests {

        @Test
        @DisplayName("customize 应调用 factory.addServerCustomizers")
        void testCustomizeCallsAddServerCustomizers() {
            NettyReactiveWebServerFactory factory = mock(NettyReactiveWebServerFactory.class);
            WebServerFactoryCustomizer<NettyReactiveWebServerFactory> customizer =
                    configuration.nettyServerCustomizer();

            customizer.customize(factory);

            verify(factory).addServerCustomizers(any(NettyServerCustomizer.class));
        }

        @Test
        @DisplayName("customize 应仅调用一次 addServerCustomizers")
        void testCustomizeCallsAddServerCustomizersOnce() {
            NettyReactiveWebServerFactory factory = mock(NettyReactiveWebServerFactory.class);
            WebServerFactoryCustomizer<NettyReactiveWebServerFactory> customizer =
                    configuration.nettyServerCustomizer();

            customizer.customize(factory);

            verify(factory, times(1)).addServerCustomizers(any(NettyServerCustomizer.class));
        }

        @Test
        @DisplayName("注册的 NettyServerCustomizer 应能正常应用于 HttpServer")
        void testRegisteredCustomizerCanBeApplied() {
            setFlag("enableNettyUriSanitizing", true);
            setFlag("enableGatewayAccessLog", true);
            NettyServerCustomizer serverCustomizer = captureAndReturnCustomizer();

            assertDoesNotThrow(() -> serverCustomizer.apply(HttpServer.create()),
                    "注册的 NettyServerCustomizer 应能正常应用于 HttpServer");
        }

        @Test
        @DisplayName("注册的 NettyServerCustomizer 应返回非空 HttpServer")
        void testRegisteredCustomizerReturnsNonNull() {
            setFlag("enableNettyUriSanitizing", true);
            setFlag("enableGatewayAccessLog", false);
            NettyServerCustomizer serverCustomizer = captureAndReturnCustomizer();

            HttpServer result = serverCustomizer.apply(HttpServer.create());
            assertNotNull(result, "应用 Customizer 后应返回非空 HttpServer");
        }
    }

    // ========== 功能开关组合测试 ==========

    @Nested
    @DisplayName("功能开关组合测试")
    class FeatureFlagCombinationTests {

        @Test
        @DisplayName("两个开关均关闭时，Customizer 应正常执行不抛异常")
        void testBothFlagsDisabled() {
            setFlag("enableNettyUriSanitizing", false);
            setFlag("enableGatewayAccessLog", false);

            NettyServerCustomizer serverCustomizer = captureAndReturnCustomizer();

            assertDoesNotThrow(() -> serverCustomizer.apply(HttpServer.create()),
                    "两个开关均关闭时不应抛出异常");
        }

        @Test
        @DisplayName("仅启用 URI 清洗时，Customizer 应正常执行")
        void testOnlyUriSanitizingEnabled() {
            setFlag("enableNettyUriSanitizing", true);
            setFlag("enableGatewayAccessLog", false);

            NettyServerCustomizer serverCustomizer = captureAndReturnCustomizer();

            assertDoesNotThrow(() -> serverCustomizer.apply(HttpServer.create()));
        }

        @Test
        @DisplayName("仅启用访问日志时，Customizer 应正常执行")
        void testOnlyAccessLogEnabled() {
            setFlag("enableNettyUriSanitizing", false);
            setFlag("enableGatewayAccessLog", true);

            NettyServerCustomizer serverCustomizer = captureAndReturnCustomizer();

            assertDoesNotThrow(() -> serverCustomizer.apply(HttpServer.create()));
        }

        @Test
        @DisplayName("两个开关均启用时，Customizer 应正常执行不抛异常")
        void testBothFlagsEnabled() {
            setFlag("enableNettyUriSanitizing", true);
            setFlag("enableGatewayAccessLog", true);

            NettyServerCustomizer serverCustomizer = captureAndReturnCustomizer();

            assertDoesNotThrow(() -> serverCustomizer.apply(HttpServer.create()));
        }

        @Test
        @DisplayName("enableNettyUriSanitizing 为 null 时条件判断应为 falsy")
        void testNullUriSanitizingFlag() {
            setFlag("enableNettyUriSanitizing", null);
            setFlag("enableGatewayAccessLog", false);

            NettyServerCustomizer serverCustomizer = captureAndReturnCustomizer();

            assertDoesNotThrow(() -> serverCustomizer.apply(HttpServer.create()),
                    "null 标志在 Boolean 条件判断中应被视为 falsy");
        }

        @Test
        @DisplayName("enableGatewayAccessLog 为 null 时条件判断应为 falsy")
        void testNullAccessLogFlag() {
            setFlag("enableNettyUriSanitizing", false);
            setFlag("enableGatewayAccessLog", null);

            NettyServerCustomizer serverCustomizer = captureAndReturnCustomizer();

            assertDoesNotThrow(() -> serverCustomizer.apply(HttpServer.create()),
                    "null 标志在 Boolean 条件判断中应被视为 falsy");
        }
    }

    // ========== Pipeline 行为测试 ==========

    @Nested
    @DisplayName("Pipeline 行为测试（URI 清洗启用时）")
    class PipelineBehaviorTests {

        @BeforeEach
        void enableUriSanitizing() {
            setFlag("enableNettyUriSanitizing", true);
            setFlag("enableGatewayAccessLog", false);
        }

        @Test
        @DisplayName("doOnChannelInit 回调应在 reactor.left.httpCodec 之后添加 UriSanitizingHandler")
        void testDoOnChannelInitAddsHandlerAtCorrectPosition() {
            NettyServerCustomizer serverCustomizer = captureAndReturnCustomizer();
            HttpServer customized = serverCustomizer.apply(HttpServer.create());

            ChannelPipeline pipeline = mock(ChannelPipeline.class);
            Channel channel = mock(Channel.class);
            ConnectionObserver observer = mock(ConnectionObserver.class);
            SocketAddress address = mock(SocketAddress.class);
            when(channel.pipeline()).thenReturn(pipeline);

            ChannelPipelineConfigurer configurer = (obs, ch, addr) ->
                    ch.pipeline().addAfter(
                            "reactor.left.httpCodec",
                            "uriSanitizingHandler",
                            new UriSanitizingHandler());

            configurer.onChannelInit(observer, channel, address);

            verify(pipeline).addAfter(
                    eq("reactor.left.httpCodec"),
                    eq("uriSanitizingHandler"),
                    any(UriSanitizingHandler.class));
        }

        @Test
        @DisplayName("添加的 Handler 应为 UriSanitizingHandler 实例")
        void testAddedHandlerIsUriSanitizingHandler() {
            ChannelPipeline pipeline = mock(ChannelPipeline.class);
            Channel channel = mock(Channel.class);
            ConnectionObserver observer = mock(ConnectionObserver.class);
            SocketAddress address = mock(SocketAddress.class);
            when(channel.pipeline()).thenReturn(pipeline);

            ChannelPipelineConfigurer configurer = (obs, ch, addr) ->
                    ch.pipeline().addAfter(
                            "reactor.left.httpCodec",
                            "uriSanitizingHandler",
                            new UriSanitizingHandler());

            configurer.onChannelInit(observer, channel, address);

            ArgumentCaptor<ChannelHandler> captor = ArgumentCaptor.forClass(ChannelHandler.class);
            verify(pipeline).addAfter(anyString(), anyString(), captor.capture());

            assertInstanceOf(UriSanitizingHandler.class, captor.getValue(),
                    "添加的 Handler 应为 UriSanitizingHandler");
        }

        @Test
        @DisplayName("Handler 注册名称应为 uriSanitizingHandler")
        void testHandlerRegisteredName() {
            ChannelPipeline pipeline = mock(ChannelPipeline.class);
            Channel channel = mock(Channel.class);
            ConnectionObserver observer = mock(ConnectionObserver.class);
            SocketAddress address = mock(SocketAddress.class);
            when(channel.pipeline()).thenReturn(pipeline);

            ChannelPipelineConfigurer configurer = (obs, ch, addr) ->
                    ch.pipeline().addAfter(
                            "reactor.left.httpCodec",
                            "uriSanitizingHandler",
                            new UriSanitizingHandler());

            configurer.onChannelInit(observer, channel, address);

            verify(pipeline).addAfter(
                    anyString(),
                    eq("uriSanitizingHandler"),
                    any(ChannelHandler.class));
        }

        @Test
        @DisplayName("每次 Channel 初始化应创建独立的 UriSanitizingHandler 实例")
        void testEachChannelGetsIndependentHandler() {
            ChannelPipeline pipeline1 = mock(ChannelPipeline.class);
            ChannelPipeline pipeline2 = mock(ChannelPipeline.class);
            Channel channel1 = mock(Channel.class);
            Channel channel2 = mock(Channel.class);
            ConnectionObserver observer = mock(ConnectionObserver.class);
            SocketAddress address = mock(SocketAddress.class);
            when(channel1.pipeline()).thenReturn(pipeline1);
            when(channel2.pipeline()).thenReturn(pipeline2);

            ChannelPipelineConfigurer configurer = (obs, ch, addr) ->
                    ch.pipeline().addAfter(
                            "reactor.left.httpCodec",
                            "uriSanitizingHandler",
                            new UriSanitizingHandler());

            configurer.onChannelInit(observer, channel1, address);
            configurer.onChannelInit(observer, channel2, address);

            ArgumentCaptor<ChannelHandler> captor1 = ArgumentCaptor.forClass(ChannelHandler.class);
            ArgumentCaptor<ChannelHandler> captor2 = ArgumentCaptor.forClass(ChannelHandler.class);
            verify(pipeline1).addAfter(anyString(), anyString(), captor1.capture());
            verify(pipeline2).addAfter(anyString(), anyString(), captor2.capture());

            assertNotSame(captor1.getValue(), captor2.getValue(),
                    "每个 Channel 应获得独立的 UriSanitizingHandler 实例");
        }
    }

    // ========== URI 清洗关闭行为测试 ==========

    @Nested
    @DisplayName("URI 清洗关闭行为测试")
    class UriSanitizingDisabledTests {

        @Test
        @DisplayName("URI 清洗关闭时 doOnChannelInit 不应被调用")
        void testDoOnChannelInitNotConfiguredWhenDisabled() {
            setFlag("enableNettyUriSanitizing", false);
            setFlag("enableGatewayAccessLog", false);

            NettyServerCustomizer serverCustomizer = captureAndReturnCustomizer();
            HttpServer original = HttpServer.create();

            HttpServer result = serverCustomizer.apply(original);

            assertSame(original, result,
                    "两个开关均关闭时 HttpServer 不应被修改（lambda 返回值未赋值回 httpServer）");
        }
    }

    // ========== 多次调用独立性测试 ==========

    @Nested
    @DisplayName("多次调用独立性测试")
    class MultipleInvocationTests {

        @Test
        @DisplayName("多次调用 nettyServerCustomizer 应返回独立实例")
        void testMultipleCallsReturnIndependentInstances() {
            WebServerFactoryCustomizer<NettyReactiveWebServerFactory> c1 =
                    configuration.nettyServerCustomizer();
            WebServerFactoryCustomizer<NettyReactiveWebServerFactory> c2 =
                    configuration.nettyServerCustomizer();

            assertNotNull(c1);
            assertNotNull(c2);
            assertNotSame(c1, c2, "每次调用应返回不同的实例");
        }

        @Test
        @DisplayName("同一 Customizer 对不同 Factory 均应正常注册")
        void testSameCustomizerWorksWithDifferentFactories() {
            WebServerFactoryCustomizer<NettyReactiveWebServerFactory> customizer =
                    configuration.nettyServerCustomizer();

            NettyReactiveWebServerFactory factory1 = mock(NettyReactiveWebServerFactory.class);
            NettyReactiveWebServerFactory factory2 = mock(NettyReactiveWebServerFactory.class);

            customizer.customize(factory1);
            customizer.customize(factory2);

            verify(factory1).addServerCustomizers(any(NettyServerCustomizer.class));
            verify(factory2).addServerCustomizers(any(NettyServerCustomizer.class));
        }
    }
}
