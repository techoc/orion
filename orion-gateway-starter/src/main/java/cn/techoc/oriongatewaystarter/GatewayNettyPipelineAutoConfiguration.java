package cn.techoc.oriongatewaystarter;

import cn.techoc.oriongateway.core.netty.handler.UriSanitizingHandler;
import org.springframework.boot.web.embedded.netty.NettyServerCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayNettyPipelineAutoConfiguration {
    @Bean
    public NettyServerCustomizer uriSanitizingCustomizer() {
        return httpServer -> httpServer
                // 先配置解码器放行
                .httpRequestDecoder(spec -> spec.validateHeaders(false))
                // 在 Pipeline 中注入 URI 预处理器
                .doOnChannelInit((observer, channel, remoteAddress) -> {
                    // 在 HttpServerCodec 之后添加
                    channel.pipeline()
                            .addAfter(
                                    "reactor.left.httpCodec", // HttpServerCodec 的名称
                                    "uriSanitizer", // 自定义 Handler 名称
                                    new UriSanitizingHandler());
                });
    }
}
