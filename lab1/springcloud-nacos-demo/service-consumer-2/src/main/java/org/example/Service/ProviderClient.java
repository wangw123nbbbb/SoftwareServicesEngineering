package org.example.Service;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient("service-provider-2")
public interface ProviderClient {
    @GetMapping("/provider/hello")
    String getHello();
}



