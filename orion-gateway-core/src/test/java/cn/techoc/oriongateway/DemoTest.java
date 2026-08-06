package cn.techoc.oriongateway;

import java.nio.charset.StandardCharsets;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

public class DemoTest {
    public static void main(String[] args) {
        AntPathMatcher antPathMatcher = new AntPathMatcher();
        UriComponents build = UriComponentsBuilder.fromUriString("http://localhost:8080/api/user/114514?aaa=b||bb")
                .encode(StandardCharsets.UTF_8)
                .build();
        System.out.println(build.getPath());
        System.out.println(antPathMatcher.match("/api/**", build.getPath()));
    }
}
