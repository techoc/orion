package cn.techoc.oriongatewaystarter;

import cn.techoc.oriongateway.core.netty.handler.UriSanitizingHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.embedded.netty.NettyReactiveWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayNettyPipelineAutoConfiguration {

    @Value("${orion.gateway.enable-gateway-access-log:false}")
    private Boolean enableGatewayAccessLog;

    @Value("${orion.gateway.enable-netty-uri-sanitizing:false}")
    private Boolean enableNettyUriSanitizing;

    @Bean
    public WebServerFactoryCustomizer<NettyReactiveWebServerFactory> nettyServerCustomizer() {
        return factory -> {
            factory.addServerCustomizers(httpServer -> {
                        if (enableNettyUriSanitizing != null && enableNettyUriSanitizing) {
                            httpServer.doOnChannelInit((connectionObserver, channel, address) -> channel.pipeline().addAfter("reactor.left.httpCodec", "uriSanitizingHandler", new UriSanitizingHandler()));
                        }
                        if (enableGatewayAccessLog != null && enableGatewayAccessLog) {
                            httpServer.accessLog(true);
                        }
                        return httpServer;
                    }
            );
        };
    }
}
