package org.example.Controller;

import org.example.Service.ProviderClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/consumer")
public class ConsumerController {

    @Autowired
    private ProviderClient providerClient;

    @GetMapping("/call")
    public String callProvider() {
        return providerClient.getHello();
    }
}
