package cn.techoc.oriongateway.core.loadbalance;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalancerProperties;
import org.springframework.cloud.client.loadbalancer.Request;
import org.springframework.cloud.client.loadbalancer.Response;
import org.springframework.cloud.client.loadbalancer.reactive.ReactiveLoadBalancer;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class StaticLoadBalancer implements ReactiveLoadBalancer<ServiceInstance> {
    
    private final String serviceId;
    private final List<String> staticAddresses;
    private final AtomicInteger position = new AtomicInteger(0);
    
    public StaticLoadBalancer(LoadBalancerProperties properties) {
        this.serviceId = "static-service";
        // 从配置中读取静态地址列表
        this.staticAddresses = properties.getStaticAddresses();
    }
    
    @Override
    public Mono<Response<ServiceInstance>> choose(Request request) {
        if (staticAddresses.isEmpty()) {
            return Mono.empty();
        }
        
        // 轮询算法选择地址
        int pos = Math.abs(position.getAndIncrement()) % staticAddresses.size();
        String address = staticAddresses.get(pos);
        
        try {
            URI uri = URI.create(address);
            ServiceInstance instance = ServiceInstanceSuppliers.toServiceInstance(serviceId, uri);
            return Mono.just(new Response<>(instance));
        } catch (Exception e) {
            return Mono.empty();
        }
    }
    
    @Override
    public String getServiceId() {
        return serviceId;
    }
}
