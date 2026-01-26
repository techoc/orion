package cn.techoc.oriongatewaysimple;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class OrionGatewaySimpleApplicationTests {

    private static final Logger log = LoggerFactory.getLogger("cn.techoc.yaml.test");

    @Test
    void contextLoads() {
        log.info("OrionGatewaySimpleApplicationTests info");
        log.debug("OrionGatewaySimpleApplicationTests debug");
        log.warn("OrionGatewaySimpleApplicationTests warn");
        log.error("OrionGatewaySimpleApplicationTests error");
    }
}
