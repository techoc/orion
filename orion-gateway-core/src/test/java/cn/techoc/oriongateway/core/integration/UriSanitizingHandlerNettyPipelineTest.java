package cn.techoc.oriongateway.core.integration;

import cn.techoc.oriongateway.core.netty.handler.UriSanitizingHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;
import org.junit.jupiter.api.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * UriSanitizingHandler 深层 Netty Pipeline 集成测试
 * 测试在真实的 Netty 服务器环境中的 Handler 行为
 *
 * <p>注意：现在只编码查询参数，路径保持不变
 */
@DisplayName("UriSanitizingHandler Netty Pipeline 深层集成测试")
class UriSanitizingHandlerNettyPipelineTest {

    private static final int TEST_PORT = 0;
    private NioEventLoopGroup bossGroup;
    private NioEventLoopGroup workerGroup;
    private Channel serverChannel;
    private int port;
    private List<String> receivedUris;

    @BeforeEach
    void setUp() throws Exception {
        receivedUris = Collections.synchronizedList(new ArrayList<>());
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup(1);

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast(new HttpServerCodec());
                        pipeline.addLast(new UriSanitizingHandler());
                        pipeline.addLast(new HttpObjectAggregator(65536));
                        pipeline.addLast(new SimpleChannelInboundHandler<FullHttpRequest>() {
                            @Override
                            protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
                                receivedUris.add(request.uri());

                                FullHttpResponse response = new DefaultFullHttpResponse(
                                        HttpVersion.HTTP_1_1,
                                        HttpResponseStatus.OK,
                                        Unpooled.copiedBuffer("OK", CharsetUtil.UTF_8));
                                response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
                                response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());

                                ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
                            }
                        });
                    }
                });

        serverChannel = bootstrap.bind(TEST_PORT).sync().channel();
        port = ((InetSocketAddress) serverChannel.localAddress()).getPort();
        System.out.println("Netty 测试服务器启动，端口: " + port);
        Thread.sleep(200); // 等待服务器完全启动
    }

    @AfterEach
    void tearDown() throws Exception {
        if (serverChannel != null && serverChannel.isActive()) {
            serverChannel.close().sync();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS).sync();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS).sync();
        }
        System.out.println("Netty 测试服务器已关闭");
    }

    private String sendHttpRequest(String uri) throws Exception {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("localhost", port), 10000);
            socket.setSoTimeout(10000);

            String request = "GET " + uri + " HTTP/1.1\r\n" +
                    "Host: localhost:" + port + "\r\n" +
                    "Connection: close\r\n\r\n";
            OutputStream out = socket.getOutputStream();
            out.write(request.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            out.flush();

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
            String statusLine = reader.readLine();
            assertEquals(200, Integer.parseInt(statusLine.split(" ")[1]),
                    "HTTP 响应码应该是 200");

            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();

            return response.toString();
        }
    }

    @Nested
    @DisplayName("Pipeline 位置与调用顺序测试")
    class PipelinePositionTests {

        @Test
        @DisplayName("深层测试：路径包含非法字符但无查询参数 - 路径保持不变")
        void testHandlerPositionInPipeline() throws Exception {
            // 默认 path-patterns 是 /api/**，匹配 /test|path
            String originalUri = "/test|path";
            // 路径中的非法字符不应被编码（没有查询参数）
            String expectedUri = "/test|path";

            sendHttpRequest(originalUri);

            assertEquals(1, receivedUris.size());
            assertEquals(expectedUri, receivedUris.get(0));
        }

        @Test
        @DisplayName("深层测试：不包含非法字符的 URI 保持不变")
        void testNormalUri() throws Exception {
            String uri = "/api/users/123";

            sendHttpRequest(uri);

            assertEquals(1, receivedUris.size());
            assertEquals(uri, receivedUris.get(0));
        }
    }

    @Nested
    @DisplayName("复杂 URI 场景测试")
    class ComplexUriTests {

        @Test
        @DisplayName("深层测试：路径包含非法字符但无查询参数 - 路径保持不变")
        void testAllIllegalCharsCombined() throws Exception {
            // 路径中的非法字符不应被编码
            String originalUri = "/test|{id}/[path]^section";
            String expectedUri = "/test|{id}/[path]^section";

            sendHttpRequest(originalUri);

            assertEquals(1, receivedUris.size());
            assertEquals(expectedUri, receivedUris.get(0));
        }

        @Test
        @DisplayName("深层测试：带有查询字符串的 URI - 只编码查询参数")
        void testComplexUriWithQueryParams() throws Exception {
            String originalUri = "/api/search?q=test|value&filter={category}";
            String expectedEncodedUri = "/api/search?q=test%7Cvalue&filter=%7Bcategory%7D";

            sendHttpRequest(originalUri);

            assertEquals(1, receivedUris.size());
            assertEquals(expectedEncodedUri, receivedUris.get(0));
        }
    }

    @Nested
    @DisplayName("真实场景集成测试")
    class RealWorldScenarioTests {

        @Test
        @DisplayName("真实场景：模拟 API 网关的路由场景")
        void testGatewayRoutingScenario() throws Exception {
            String[] gatewayUris = {
                    "/api/users/{id}",      // 路径包含 {} 但无查询参数，保持不变
                    "/api/items?q=test|value"  // 查询参数包含非法字符，应被编码
            };

            String[] expectedUris = {
                    "/api/users/{id}",               // 路径不变
                    "/api/items?q=test%7Cvalue"       // 查询参数编码
            };

            for (int i = 0; i < gatewayUris.length; i++) {
                sendHttpRequest(gatewayUris[i]);
                assertEquals(expectedUris[i], receivedUris.get(i),
                        "URI " + gatewayUris[i] + " 应该被正确处理");
            }
        }

        @Test
        @DisplayName("真实场景：查询参数编码测试")
        void testQueryParamEncoding() throws Exception {
            String originalUri = "/api/search?q={name}|[test]^value";
            String expectedUri = "/api/search?q=%7Bname%7D%7C%5Btest%5D%5Evalue";

            sendHttpRequest(originalUri);

            assertEquals(1, receivedUris.size());
            assertEquals(expectedUri, receivedUris.get(0));
        }
    }
}
