package org.example.Service;

import org.example.Controller.ConsumerController.ProviderController.User;
import org.example.config.LoadBalancerConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@FeignClient(name = "service-provider-1", 
             configuration = LoadBalancerConfig.class, 
             contextId = "standardProviderClient",
             fallback = ProviderClientFallback.class)
public interface ProviderClient {

    @GetMapping("/api/hello")
    String callHello();
    
    @GetMapping("/api/users")
    Map<String, Object> getUsers();
    
    @GetMapping("/api/users/{id}")
    Map<String, Object> getUserById(@PathVariable("id") Long id);
    
    @PostMapping("/api/users")
    Map<String, Object> addUser(@RequestBody User user);
    
    @PutMapping("/api/users/{id}")
    Map<String, Object> updateUser(@PathVariable("id") Long id, @RequestBody User user);
    
    @DeleteMapping("/api/users/{id}")
    Map<String, Object> deleteUser(@PathVariable("id") Long id);
}

