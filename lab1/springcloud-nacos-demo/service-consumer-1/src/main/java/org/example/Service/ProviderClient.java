package org.example.Service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@FeignClient("service-provider-1")
public interface ProviderClient {
    @GetMapping("/provider/hello")
    String getHello();
}



