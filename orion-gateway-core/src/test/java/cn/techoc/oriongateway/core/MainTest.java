package cn.techoc.oriongateway.core;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URISyntaxException;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class MainTest {

    @Test
    public void test() {
        String rawQuery = "wd=|{}|\\^[]`";

        // URI.create() cannot handle these illegal chars — this is the problem UriSanitizingHandler solves
        assertThrows(IllegalArgumentException.class, () -> URI.create("https://www.baidu.com/s?" + rawQuery));

        // Using the multi-arg constructor avoids the strict validation
        URI uri;
        try {
            uri = new URI("https", null, "www.baidu.com", -1, "/s", rawQuery, null);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        System.out.println(uri);
    }
}
