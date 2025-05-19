package org.example.Controller;

import org.example.Service.ProviderClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/consumer")
@RefreshScope
public class ConsumerController {

    @Autowired
    private ProviderClient providerClient;
    
    @Autowired
    private RestTemplate restTemplate;
    
    private static final String SERVICE_URL = "http://service-provider-1/api";



    // 负载均衡测试接口 - 循环调用10次，验证负载均衡效果
    @GetMapping("/lb-test/random")
    public Map<String, Object> testRandomLoadBalancer() {
        Map<String, Object> result = new HashMap<>();
        Map<String, Integer> portStats = new HashMap<>();

        // 调用10次，观察负载均衡效果
        for (int i = 0; i < 100000; i++) {
            String response = restTemplate.getForObject(SERVICE_URL + "/hello", String.class);
            // 从响应中提取端口
            String port = response.substring(response.lastIndexOf("：") + 1);
            // 统计各端口的调用次数
            portStats.put(port, portStats.getOrDefault(port, 0) + 1);
        }

        result.put("message", "随机负载均衡测试");
        result.put("port_statistics", portStats);
        return result;
    }

    // 负载均衡测试接口 - 循环调用10次，验证自定义负载均衡效果
    @GetMapping("/lb-test/custom")
    public Map<String, Object> testCustomLoadBalancer() {
        Map<String, Object> result = new HashMap<>();
        Map<String, Integer> portStats = new HashMap<>();

        // 使用Feign客户端调用10次，观察负载均衡效果
        for (int i = 0; i < 100000; i++) {
            String response = providerClient.callHello();
            // 从响应中提取端口
            String port = response.substring(response.lastIndexOf("：") + 1);
            // 统计各端口的调用次数
            portStats.put(port, portStats.getOrDefault(port, 0) + 1);
        }

        result.put("message", "自定义负载均衡测试");
        result.put("port_statistics", portStats);
        return result;
    }

    // 使用Feign调用的接口
    @GetMapping("/feign/hello")
    public String getFeignHello() {
        return providerClient.callHello();
    }
    
    @GetMapping("/feign/users")
    public Map<String, Object> getFeignUsers() {
        return providerClient.getUsers();
    }
    
    @GetMapping("/feign/users/{id}")
    public Map<String, Object> getFeignUserById(@PathVariable("id") Long id) {
        return providerClient.getUserById(id);
    }
    
    @PostMapping("/feign/users")
    public Map<String, Object> addFeignUser(@RequestBody ProviderController.User user) {
        return providerClient.addUser(user);
    }
    
    @PutMapping("/feign/users/{id}")
    public Map<String, Object> updateFeignUser(@PathVariable("id") Long id, @RequestBody ProviderController.User user) {
        return providerClient.updateUser(id, user);
    }
    
    @DeleteMapping("/feign/users/{id}")
    public Map<String, Object> deleteFeignUser(@PathVariable("id") Long id) {
        return providerClient.deleteUser(id);
    }
    
    // 使用RestTemplate调用的接口
    @GetMapping("/rest/hello")
    public String getRestHello() {
        return restTemplate.getForObject(SERVICE_URL + "/hello", String.class);
    }
    
    @GetMapping("/rest/users")
    public Object getRestUsers() {
        return restTemplate.getForObject(SERVICE_URL + "/users", Object.class);
    }
    
    @GetMapping("/rest/users/{id}")
    public Object getRestUserById(@PathVariable("id") Long id) {
        return restTemplate.getForObject(SERVICE_URL + "/users/{id}", Object.class, id);
    }
    
    @PostMapping("/rest/users")
    public Object addRestUser(@RequestBody ProviderController.User user) {
        return restTemplate.postForObject(SERVICE_URL + "/users", user, Object.class);
    }
    
    @PutMapping("/rest/users/{id}")
    public Object updateRestUser(@PathVariable("id") Long id, @RequestBody ProviderController.User user) {
        HttpEntity<ProviderController.User> requestEntity = new HttpEntity<>(user);
        ResponseEntity<Object> response = restTemplate.exchange(
                SERVICE_URL + "/users/{id}", 
                HttpMethod.PUT, 
                requestEntity, 
                Object.class, 
                id);
        return response.getBody();
    }
    
    @DeleteMapping("/rest/users/{id}")
    public Object deleteRestUser(@PathVariable("id") Long id) {
        HttpEntity<Object> requestEntity = new HttpEntity<>(null);
        ResponseEntity<Object> response = restTemplate.exchange(
                SERVICE_URL + "/users/{id}", 
                HttpMethod.DELETE, 
                requestEntity, 
                Object.class, 
                id);
        return response.getBody();
    }
    
    // 静态内部类，与提供者中的User类一致
    public static class ProviderController {
        public static class User {
            private Long id;
            private String name;
            private Integer age;
            
            public User() {
            }
            
            public User(Long id, String name, Integer age) {
                this.id = id;
                this.name = name;
                this.age = age;
            }
            
            public Long getId() {
                return id;
            }
            
            public void setId(Long id) {
                this.id = id;
            }
            
            public String getName() {
                return name;
            }
            
            public void setName(String name) {
                this.name = name;
            }
            
            public Integer getAge() {
                return age;
            }
            
            public void setAge(Integer age) {
                this.age = age;
            }
        }
    }
}

