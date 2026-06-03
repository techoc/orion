package cn.techoc.oriongatewaystarter;

import cn.techoc.oriongateway.core.netty.handler.UriSanitizingHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.web.embedded.netty.NettyReactiveWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.netty.http.server.HttpServer;

import java.nio.charset.Charset;

@Configuration
public class GatewayNettyPipelineAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(GatewayNettyPipelineAutoConfiguration.class);

    @Value("${orion.gateway.enable-gateway-access-log:false}")
    private Boolean enableGatewayAccessLog;

    @Value("${orion.gateway.uri-sanitizing.enabled:false}")
    private Boolean enableNettyUriSanitizing;

    @Value("${orion.gateway.uri-sanitizing.path-patterns:/**}")
    private String pathPatterns;

    @Value("${orion.gateway.uri-sanitizing.charset:UTF-8}")
    private String charsetName;

    @Bean
    @ConditionalOnMissingBean
    public UriSanitizingHandler uriSanitizingHandler() {
        Charset charset = Charset.forName(charsetName);
        return new UriSanitizingHandler(enableNettyUriSanitizing, pathPatterns, charset);
    }

    @Bean
    public WebServerFactoryCustomizer<NettyReactiveWebServerFactory> nettyServerCustomizer(UriSanitizingHandler uriSanitizingHandler) {
        log.info("GatewayNettyPipelineAutoConfiguration loaded, enableNettyUriSanitizing={}", enableNettyUriSanitizing);
        return factory -> factory.addServerCustomizers(httpServer -> {
                    HttpServer server = httpServer;
                    if (enableNettyUriSanitizing != null && enableNettyUriSanitizing) {
                        log.info("Registering orion.handler in Netty pipeline");
                        server = server.doOnChannelInit((connectionObserver, channel, address) -> {
                            log.debug("Adding orion.handler to channel {}", channel.id().asShortText());
                            channel.pipeline().addAfter("reactor.left.httpCodec", "orion.handler", uriSanitizingHandler);
                        });
                    } else {
                        log.warn("orion.handler NOT registered: enableNettyUriSanitizing={}", enableNettyUriSanitizing);
                    }
                    if (enableGatewayAccessLog != null && enableGatewayAccessLog) {
                        server = server.accessLog(false);
                    }
                    return server;
                }
        );
    }
}
