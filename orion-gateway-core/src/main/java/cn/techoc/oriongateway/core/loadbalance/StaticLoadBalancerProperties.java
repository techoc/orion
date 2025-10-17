package cn.techoc.oriongateway.core.loadbalance;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.client.loadbalancer.LoadBalancerProperties;
import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "spring.cloud.loadbalancer")
public class StaticLoadBalancerProperties extends LoadBalancerProperties {
    
    private List<String> staticAddresses = new ArrayList<>();
    
    public List<String> getStaticAddresses() {
        return staticAddresses;
    }
    
    public void setStaticAddresses(List<String> staticAddresses) {
        this.staticAddresses = staticAddresses;
    }
}
