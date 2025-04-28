package org.example.Controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api")
public class ProviderController {
    
    @Value("${server.port}")
    private String serverPort;
    
    // 模拟数据库
    private static final Map<Long, User> userMap = new ConcurrentHashMap<>();
    
    static {
        userMap.put(1L, new User(1L, "张三", 20));
        userMap.put(2L, new User(2L, "李四", 25));
    }

    @GetMapping("/hello")
    public String sayHello() {
        return "你好，来自服务提供者1，端口：" + serverPort;
    }
    
//    @GetMapping("/users")
//    public Map<String, Object> getAllUsers() {
//        Map<String, Object> result = new HashMap<>();
//        result.put("code", 200);
//        result.put("message", "获取所有用户成功");
//        result.put("data", userMap.values());
//        result.put("port", serverPort);
//        return result;
//    }
// 在服务提供者代码中注入延迟
    @GetMapping("/users")
    public Map<String, Object> getUsers() {
        try {
            // 添加3秒延迟以模拟慢调用
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        // 原始代码...
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "获取所有用户成功");
        result.put("data", userMap.values());
        result.put("port", serverPort);
        return result;
    }
    
    @GetMapping("/users/{id}")
    public Map<String, Object> getUserById(@PathVariable("id") Long id) {
        Map<String, Object> result = new HashMap<>();
        User user = userMap.get(id);
        if (user != null) {
            result.put("code", 200);
            result.put("message", "获取用户成功");
            result.put("data", user);
        } else {
            result.put("code", 404);
            result.put("message", "用户不存在");
        }
        result.put("port", serverPort);
        return result;
    }
    
    @PostMapping("/users")
    public Map<String, Object> addUser(@RequestBody User user) {
        Map<String, Object> result = new HashMap<>();
        if (user.getId() == null) {
            result.put("code", 400);
            result.put("message", "用户ID不能为空");
            return result;
        }
        
        if (userMap.containsKey(user.getId())) {
            result.put("code", 400);
            result.put("message", "用户ID已存在");
            return result;
        }
        
        userMap.put(user.getId(), user);
        result.put("code", 200);
        result.put("message", "添加用户成功");
        result.put("data", user);
        result.put("port", serverPort);
        return result;
    }
    
    @PutMapping("/users/{id}")
    public Map<String, Object> updateUser(@PathVariable("id") Long id, @RequestBody User user) {
        Map<String, Object> result = new HashMap<>();
        if (!userMap.containsKey(id)) {
            result.put("code", 404);
            result.put("message", "用户不存在");
            return result;
        }
        
        user.setId(id);
        userMap.put(id, user);
        result.put("code", 200);
        result.put("message", "更新用户成功");
        result.put("data", user);
        result.put("port", serverPort);
        return result;
    }
    
    @DeleteMapping("/users/{id}")
    public Map<String, Object> deleteUser(@PathVariable("id") Long id) {
        Map<String, Object> result = new HashMap<>();
        if (!userMap.containsKey(id)) {
            result.put("code", 404);
            result.put("message", "用户不存在");
            return result;
        }
        
        User user = userMap.remove(id);
        result.put("code", 200);
        result.put("message", "删除用户成功");
        result.put("data", user);
        result.put("port", serverPort);
        return result;
    }
    
    // 用户实体类
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
