package cn.techoc.oriongateway.core;

import org.junit.jupiter.api.Test;

import java.net.URI;

public class MainTest {

    @Test
    public void test() {
        String url = "https://www.baidu.com/s?wd=|{}|\\^[]`";

        URI uri = URI.create(url);
        System.out.println(uri);
    }
}
