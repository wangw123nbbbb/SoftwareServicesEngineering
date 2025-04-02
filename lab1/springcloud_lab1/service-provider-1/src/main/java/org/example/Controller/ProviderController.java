package org.example.Controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProviderController {

    @GetMapping("/api/hello")
    public String sayHello() {
        return "Hello from Service Provider 1!";
    }
}
