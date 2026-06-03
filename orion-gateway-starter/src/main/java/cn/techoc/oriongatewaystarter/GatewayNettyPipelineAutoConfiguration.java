package cn.techoc.oriongatewaystarter;

import cn.techoc.oriongateway.core.netty.handler.UriSanitizingHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
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

    /**
     * 默认的 UriSanitizingHandler Bean
     * 如果用户自定义了 UriSanitizingHandler，这个 Bean 不会被创建
     */
    @Bean
    @ConditionalOnMissingBean
    public UriSanitizingHandler uriSanitizingHandler() {
        return new UriSanitizingHandler();
    }

    @Bean
    public WebServerFactoryCustomizer<NettyReactiveWebServerFactory> nettyServerCustomizer(UriSanitizingHandler handler) {
        return factory -> {
            factory.addServerCustomizers(httpServer -> {
                        if (enableNettyUriSanitizing != null && enableNettyUriSanitizing) {
                            httpServer.doOnChannelInit((connectionObserver, channel, address) ->
                                    channel.pipeline().addAfter("reactor.left.httpCodec", "uriSanitizingHandler", handler));
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
