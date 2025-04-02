package org.example.Service;


import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient(name = "service-provider-1")
public interface ProviderClient {

    @GetMapping("/api/hello")
    String callHello();
}

