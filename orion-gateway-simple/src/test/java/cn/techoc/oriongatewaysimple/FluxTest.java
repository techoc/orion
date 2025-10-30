package cn.techoc.oriongatewaysimple;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

public class FluxTest {
    @Test
    public void test1() {
        Flux.range(1, 10)
                .scan(0, (x, y) -> {
                    System.out.println(x + " + " + y);
                    return x + y;
                })
                .subscribe(System.out::println);
    }

    @Test
    public void test2() {
    }
}
