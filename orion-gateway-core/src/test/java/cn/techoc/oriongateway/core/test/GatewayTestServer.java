package cn.techoc.oriongateway.core.test;

import cn.techoc.oriongateway.core.netty.handler.UriSanitizingHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class GatewayTestServer {

    private static final int PORT = 8080;
    private final List<String> receivedUris = new ArrayList<>();
    private Channel serverChannel;
    private CountDownLatch startLatch;

    public static void main(String[] args) {
        try {
            GatewayTestServer server = new GatewayTestServer();
            new Thread(() -> {
                try {
                    server.start();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();

            System.out.println("Server startup in progress...");
            server.waitForStart();
            System.out.println("\n=== Test Instructions ===");
            System.out.println("1. Open your browser or use curl to test:");
            System.out.println("   - http://localhost:" + PORT + "/test|path");
            System.out.println("   - http://localhost:" + PORT + "/api/user{id}");
            System.out.println("   - http://localhost:" + PORT + "/search?q=test|value");
            System.out.println("\n2. Check the console logs to see URIs sanitization results.");
            System.out.println("\n3. Press Ctrl+C to stop the server.\n");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void start() throws Exception {
        startLatch = new CountDownLatch(1);
        NioEventLoopGroup bossGroup = new NioEventLoopGroup(1);
        NioEventLoopGroup workerGroup = new NioEventLoopGroup(1);

        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline pipeline = ch.pipeline();
                            pipeline.addLast(new HttpServerCodec());
                            pipeline.addLast(new UriSanitizingHandler());
                            pipeline.addLast(new SimpleChannelInboundHandler<FullHttpRequest>() {
                                @Override
                                protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
                                    String uri = request.uri();
                                    System.out.println("[Gateway] Received URI: " + uri);
                                    synchronized (receivedUris) {
                                        receivedUris.add(uri);
                                    }

                                    FullHttpResponse response = new DefaultFullHttpResponse(
                                            HttpVersion.HTTP_1_1,
                                            HttpResponseStatus.OK,
                                            Unpooled.copiedBuffer(
                                                    "{\"status\":\"success\",\"uri\":\"" + uri + "\"}",
                                                    CharsetUtil.UTF_8
                                            )
                                    );
                                    response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json");
                                    response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());

                                    ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
                                }
                            });
                        }
                    });

            serverChannel = bootstrap.bind(PORT).sync().channel();
            System.out.println("=== Gateway Test Server Started ===");
            System.out.println("Server listening on port: " + PORT);
            System.out.println("=================================");
            startLatch.countDown();

            serverChannel.closeFuture().sync();
        } finally {
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
        }
    }

    public List<String> getReceivedUris() {
        synchronized (receivedUris) {
            return new ArrayList<>(receivedUris);
        }
    }

    public void waitForStart() throws InterruptedException {
        if (startLatch != null) {
            startLatch.await();
        }
    }
}
