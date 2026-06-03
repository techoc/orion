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
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
@DisplayName("UriSanitizingHandler 网关集成测试")
public class RealGatewayIntegrationTest {

    private static final int PORT = 18080;
    private static final List<String> receivedUris = new ArrayList<>();
    private static NioEventLoopGroup bossGroup;
    private static NioEventLoopGroup workerGroup;
    private static Channel serverChannel;
    private static CountDownLatch serverReadyLatch;
    private static final AtomicInteger requestCounter = new AtomicInteger(0);

    @BeforeAll
    static void startServer() throws Exception {
        serverReadyLatch = new CountDownLatch(1);
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup(1);

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 1024)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast(new HttpServerCodec());
                        pipeline.addLast(new HttpObjectAggregator(1024 * 1024));
                        pipeline.addLast(new UriSanitizingHandler());
                        pipeline.addLast(new SimpleChannelInboundHandler<FullHttpRequest>() {
                            @Override
                            protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
                                String uri = request.uri();
                                synchronized (receivedUris) {
                                    receivedUris.add(uri);
                                }
                                int requestNum = requestCounter.incrementAndGet();
                                System.out.println("Request #" + requestNum + " received: " + uri);

                                String responseBody = "{\"status\":\"success\",\"uri\":\"" + uri + "\"}";
                                byte[] responseBytes = responseBody.getBytes(CharsetUtil.UTF_8);

                                FullHttpResponse response = new DefaultFullHttpResponse(
                                        HttpVersion.HTTP_1_1,
                                        HttpResponseStatus.OK,
                                        Unpooled.wrappedBuffer(responseBytes)
                                );
                                response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=UTF-8");
                                response.headers().set(HttpHeaderNames.CONTENT_LENGTH, responseBytes.length);
                                response.headers().set(HttpHeaderNames.CONNECTION, "close");

                                ChannelFuture future = ctx.writeAndFlush(response);
                                future.addListener(ChannelFutureListener.CLOSE);
                            }

                            @Override
                            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                log.error("Exception caught in gateway handler: {}", cause.getMessage(), cause);
                                ctx.close();
                            }
                        });
                    }
                });

        serverChannel = bootstrap.bind(PORT).sync().channel();
        serverReadyLatch.countDown();
        System.out.println("Test gateway server started on port: " + PORT);
    }

    @AfterAll
    static void stopServer() throws Exception {
        if (serverChannel != null) {
            serverChannel.close().sync();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully(100, 100, TimeUnit.MILLISECONDS).sync();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully(100, 100, TimeUnit.MILLISECONDS).sync();
        }
        System.out.println("Test gateway server stopped");
    }

    @BeforeEach
    void clearReceivedUris() throws InterruptedException {
        assertTrue(serverReadyLatch.await(10, TimeUnit.SECONDS), "Server should be ready");
        synchronized (receivedUris) {
            receivedUris.clear();
        }
        Thread.sleep(100);
    }

    private String sendRequest(String uri) throws Exception {
        URL url = new URL("http://localhost:" + PORT + uri);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);
        connection.setRequestProperty("Connection", "close");

        int responseCode = connection.getResponseCode();
        assertEquals(200, responseCode, "Response should be 200 OK");

        BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        reader.close();
        connection.disconnect();

        String responseStr = response.toString();
        System.out.println("Response received: " + responseStr);
        return responseStr;
    }

    @Nested
    @DisplayName("基本 URI 清理测试")
    class BasicUriSanitizationTests {

        @Test
        @DisplayName("测试包含 | 的 URI")
        void testUriWithPipeCharacter() throws Exception {
            String response = sendRequest("/test|path");

            synchronized (receivedUris) {
                assertEquals(1, receivedUris.size());
                assertEquals("/test%7Cpath", receivedUris.get(0));
            }

            assertTrue(response.contains("/test%7Cpath"), "Response should contain sanitized URI");
        }

        @Test
        @DisplayName("测试包含 { 和 } 的 URI")
        void testUriWithCurlyBraces() throws Exception {
            String response = sendRequest("/api/user{id}");

            synchronized (receivedUris) {
                assertEquals(1, receivedUris.size());
                assertEquals("/api/user%7Bid%7D", receivedUris.get(0));
            }

            assertTrue(response.contains("/api/user%7Bid%7D"), "Response should contain sanitized URI");
        }

        @Test
        @DisplayName("测试包含查询参数的 URI")
        void testUriWithQueryParams() throws Exception {
            String response = sendRequest("/search?q=test|value&filter={category}");

            synchronized (receivedUris) {
                assertEquals(1, receivedUris.size());
                assertEquals("/search?q=test%7Cvalue&filter=%7Bcategory%7D", receivedUris.get(0));
            }

            assertTrue(response.contains("test%7Cvalue"), "Query params should be sanitized");
        }
    }

    @Nested
    @DisplayName("边界场景测试")
    class EdgeCaseTests {

        @Test
        @DisplayName("测试正常 URI - 不包含非法字符")
        void testNormalUri() throws Exception {
            String response = sendRequest("/api/users/123");

            synchronized (receivedUris) {
                assertEquals(1, receivedUris.size());
                assertEquals("/api/users/123", receivedUris.get(0));
            }

            assertTrue(response.contains("/api/users/123"), "Normal URI should remain unchanged");
        }

        @Test
        @DisplayName("测试多个非法字符混合")
        void testMixedIllegalChars() throws Exception {
            String response = sendRequest("/test|path{with}|multiple[brackets]");

            synchronized (receivedUris) {
                assertEquals(1, receivedUris.size());
                assertEquals("/test%7Cpath%7Bwith%7D%7Cmultiple%5Bbrackets%5D", receivedUris.get(0));
            }

            assertTrue(response.contains("%7C"), "Should encode | characters");
            assertTrue(response.contains("%7B"), "Should encode { characters");
        }

        @Test
        @DisplayName("测试连续多个非法字符")
        void testMultiplePipeCharacters() throws Exception {
            String response = sendRequest("/path||with||multiple||pipes");

            synchronized (receivedUris) {
                assertEquals(1, receivedUris.size());
                assertEquals("/path%7C%7Cwith%7C%7Cmultiple%7C%7Cpipes", receivedUris.get(0));
            }
        }
    }

    @Nested
    @DisplayName("多个请求测试")
    class MultipleRequestTests {

        @Test
        @DisplayName("测试多个连续请求")
        void testMultipleRequests() throws Exception {
            String[] testUris = {
                    "/first|request",
                    "/second{param}",
                    "/third[test]",
                    "/normal/path"
            };

            String[] expectedUris = {
                    "/first%7Crequest",
                    "/second%7Bparam%7D",
                    "/third%5Btest%5D",
                    "/normal/path"
            };

            for (String uris : testUris) {
                sendRequest(uris);
                Thread.sleep(50);
            }

            synchronized (receivedUris) {
                assertEquals(testUris.length, receivedUris.size());
                for (int i = 0; i < expectedUris.length; i++) {
                    assertEquals(expectedUris[i], receivedUris.get(i),
                            "Request " + (i + 1) + " should be properly sanitized");
                }
            }
        }
    }
}
