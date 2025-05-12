package org.example.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.DefaultResponse;
import org.springframework.cloud.client.loadbalancer.EmptyResponse;
import org.springframework.cloud.client.loadbalancer.Request;
import org.springframework.cloud.client.loadbalancer.Response;
import org.springframework.cloud.loadbalancer.core.ReactorServiceInstanceLoadBalancer;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import reactor.core.publisher.Mono;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

/**
 * 自定义负载均衡器 - 基于客户端主机名的哈希一致性选择策略
 * 每个相同的客户端主机名固定路由到同一个服务实例
 */
public class CustomLoadBalancer implements ReactorServiceInstanceLoadBalancer {

    private final String serviceId;
    private final ObjectProvider<ServiceInstanceListSupplier> serviceInstanceListSupplierProvider;

    public CustomLoadBalancer(ObjectProvider<ServiceInstanceListSupplier> serviceInstanceListSupplierProvider, String serviceId) {
        this.serviceId = serviceId;
        this.serviceInstanceListSupplierProvider = serviceInstanceListSupplierProvider;
    }

    @Override
    public Mono<Response<ServiceInstance>> choose(Request request) {
        ServiceInstanceListSupplier supplier = serviceInstanceListSupplierProvider.getIfAvailable();
        return supplier.get().next().map(serviceInstances -> {
            if (serviceInstances.isEmpty()) {
                return new EmptyResponse();
            }
            return new DefaultResponse(selectInstance(serviceInstances));
        });
    }

    private ServiceInstance selectInstance(List<ServiceInstance> instances) {
        if (instances.isEmpty()) {
            return null;
        }
        if (instances.size() == 1) {
            return instances.get(0);
        }
        
        // 获取当前主机名作为负载均衡的基础依据
        String hostname;
        try {
            hostname = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            // 如果无法获取主机名，则使用默认值
            hostname = "default-host";
        }
        
        // 使用主机名的哈希值作为选择器
        int hashCode = hostname.hashCode();
        // 确保为正数
        if (hashCode < 0) {
            hashCode = Math.abs(hashCode);
        }
        
        // 对实例列表长度取模，确定选择哪个实例
        int index = hashCode % instances.size();
        
        return instances.get(index);
    }
} 