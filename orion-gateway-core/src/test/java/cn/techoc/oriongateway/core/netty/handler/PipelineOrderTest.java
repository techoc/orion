package cn.techoc.oriongateway.core.netty.handler;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Pipeline 顺序验证测试")
public class PipelineOrderTest {

    @Test
    @DisplayName("UriSanitizingHandler 应在下游 handler 之前清洗 URI 中的 | 字符")
    void testHandlerSanitizesBeforeDownstream() throws Exception {
        UriSanitizingHandler handler = new UriSanitizingHandler(true, "/**");
        CompletableFuture<String> capturedUri = new CompletableFuture<>();

        ChannelHandler trafficHandler = new ChannelInboundHandlerAdapter() {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) {
                if (msg instanceof io.netty.handler.codec.http.HttpRequest) {
                    capturedUri.complete(((io.netty.handler.codec.http.HttpRequest) msg).uri());
                }
                ctx.fireChannelRead(msg);
            }
        };

        EventLoopGroup group = new NioEventLoopGroup(2);
        try {
            // Server: HttpCodec → orion.handler → traffic → aggregator → response
            ServerBootstrap sb = new ServerBootstrap()
                    .group(group)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline p = ch.pipeline();
                            p.addLast("httpCodec", new HttpServerCodec());
                            p.addLast("orion.handler", handler);
                            p.addLast("traffic", trafficHandler);
                            p.addLast("aggregator", new HttpObjectAggregator(65536));
                            p.addLast("response", new SimpleChannelInboundHandler<FullHttpRequest>() {
                                @Override
                                protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest req) {
                                    FullHttpResponse resp = new DefaultFullHttpResponse(
                                            req.protocolVersion(), HttpResponseStatus.OK);
                                    ctx.writeAndFlush(resp);
                                }
                            });
                        }
                    });

            ChannelFuture sf = sb.bind(0).sync();
            int port = ((java.net.InetSocketAddress) sf.channel().localAddress()).getPort();

            // Client: send raw HTTP with | in query via plain socket
            try (Socket socket = new Socket("127.0.0.1", port)) {
                OutputStream out = socket.getOutputStream();
                out.write("GET /get?aaa=114|514 HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n".getBytes(StandardCharsets.UTF_8));
                out.flush();

                InputStream in = socket.getInputStream();
                byte[] buf = new byte[4096];
                int read = in.read(buf);
                String response = new String(buf, 0, read, StandardCharsets.UTF_8);
                System.out.println("Raw response: " + response.split("\r\n")[0]);
            }

            String uri = capturedUri.get(5, TimeUnit.SECONDS);
            System.out.println("Captured URI in traffic handler: " + uri);

            assertNotNull(uri, "Traffic handler should have received the request");
            assertFalse(uri.contains("|"),
                    "URI should have been sanitized, but got: " + uri);
            assertTrue(uri.contains("%7C"),
                    "URI should contain encoded pipe, but got: " + uri);

            sf.channel().close().sync();
        } finally {
            group.shutdownGracefully().sync();
        }
    }
}
