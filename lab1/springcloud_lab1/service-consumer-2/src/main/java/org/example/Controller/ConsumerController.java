package org.example.Controller;

import org.example.Service.ProviderClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ConsumerController {

    @Autowired
    private ProviderClient providerClient;

    @GetMapping("/consumer/hello")
    public String getHello() {
        return providerClient.callHello();
    }
}

